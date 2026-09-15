package com.chengqu.huzhu.user.service;

import com.chengqu.huzhu.api.dto.NotifyCreateCommand;
import com.chengqu.huzhu.api.dto.UserBrief;
import com.chengqu.huzhu.api.dto.UserExclusions;
import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.security.LoginUser;
import com.chengqu.huzhu.security.SecurityUtils;
import com.chengqu.huzhu.user.dto.UserRelationResponse;
import com.chengqu.huzhu.user.entity.UserBlock;
import com.chengqu.huzhu.user.entity.UserFollow;
import com.chengqu.huzhu.user.repository.UserBlockRepository;
import com.chengqu.huzhu.user.repository.UserFollowRepository;
import com.chengqu.huzhu.user.support.NotifySender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关注与拉黑。
 *
 * <p>两条业务规则值得说明：
 * <ul>
 *   <li><b>拉黑会解除双向关注</b>。否则会出现「已拉黑对方，但对方仍在我的粉丝列表里」，
 *       以及被拉黑方仍能通过关注流看到我。</li>
 *   <li><b>屏蔽是双向的</b>：{@link #exclusionsFor} 返回「我拉黑的 ∪ 拉黑我的」。
 *       内容服务据此过滤信息流。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRelationService {

    private final UserFollowRepository followRepository;
    private final UserBlockRepository blockRepository;
    private final UserService userService;
    private final NotifySender notifySender;

    /** 通知里的关联对象类型，与 notify-service 生成跳转链接的取值保持一致。 */
    private static final String REF_TYPE_USER = "USER";

    @Transactional
    public UserRelationResponse follow(Long targetUserId) {
        LoginUser meUser = SecurityUtils.currentUser();
        Long me = meUser.getId();
        requireNotSelf(me, targetUserId, "不能关注自己");
        if (blockRepository.existsByBlockerIdAndBlockedId(targetUserId, me)) {
            // 对方拉黑了我：不接受关注，但不暴露「被拉黑」这一事实，避免关系探测
            throw new BizException("暂时无法关注该用户");
        }
        // 之前拉黑过对方的话，关注等于解除拉黑
        blockRepository.findByBlockerIdAndBlockedId(me, targetUserId).ifPresent(blockRepository::delete);

        if (followRepository.existsByFollowerIdAndFolloweeId(me, targetUserId)) {
            log.info("[用户] 重复关注，忽略 userId={}, targetId={}", me, targetUserId);
            return relation(targetUserId);
        }
        UserFollow follow = new UserFollow();
        follow.setFollowerId(me);
        follow.setFolloweeId(targetUserId);
        followRepository.save(follow);
        log.info("[用户] 关注成功 userId={}, targetId={}", me, targetUserId);
        // 只有「新增关注」才通知被关注者：上面的重复关注分支已提前返回，
        // 幂等重试不该产生第二条同样的提醒。命令在事务内构造好，投递推迟到提交之后。
        notifySender.sendAfterCommit(new NotifyCreateCommand(
                targetUserId,
                "FOLLOWED",
                "有人关注了你",
                displayName(meUser) + " 关注了你",
                REF_TYPE_USER,
                targetUserId,
                me,
                displayName(meUser)));
        return relation(targetUserId);
    }

    @Transactional
    public UserRelationResponse unfollow(Long targetUserId) {
        Long me = currentUserId();
        requireNotSelf(me, targetUserId, "不能取消关注自己");
        followRepository.deleteByFollowerIdAndFolloweeId(me, targetUserId);
        log.info("[用户] 取消关注 userId={}, targetId={}", me, targetUserId);
        return relation(targetUserId);
    }

    @Transactional
    public UserRelationResponse block(Long targetUserId) {
        Long me = currentUserId();
        requireNotSelf(me, targetUserId, "不能拉黑自己");
        if (blockRepository.existsByBlockerIdAndBlockedId(me, targetUserId)) {
            return relation(targetUserId);
        }
        UserBlock block = new UserBlock();
        block.setBlockerId(me);
        block.setBlockedId(targetUserId);
        blockRepository.save(block);
        // 同步解除双向关注，避免「已拉黑却仍是粉丝」
        followRepository.deleteBetween(me, targetUserId);
        log.info("[用户] 拉黑成功 userId={}, targetId={}", me, targetUserId);
        return relation(targetUserId);
    }

    @Transactional
    public UserRelationResponse unblock(Long targetUserId) {
        Long me = currentUserId();
        requireNotSelf(me, targetUserId, "不能取消拉黑自己");
        blockRepository.deleteByBlockerIdAndBlockedId(me, targetUserId);
        log.info("[用户] 取消拉黑 userId={}, targetId={}", me, targetUserId);
        return relation(targetUserId);
    }

    @Transactional(readOnly = true)
    public UserRelationResponse relation(Long targetUserId) {
        Long me = currentUserId();
        if (me.equals(targetUserId)) {
            return new UserRelationResponse(false, false, false, false,
                    followRepository.countByFolloweeId(me), followRepository.countByFollowerId(me));
        }
        return new UserRelationResponse(
                followRepository.existsByFollowerIdAndFolloweeId(me, targetUserId),
                followRepository.existsByFollowerIdAndFolloweeId(targetUserId, me),
                blockRepository.existsByBlockerIdAndBlockedId(me, targetUserId),
                blockRepository.existsByBlockerIdAndBlockedId(targetUserId, me),
                followRepository.countByFolloweeId(targetUserId),
                followRepository.countByFollowerId(targetUserId));
    }

    /**
     * 批量取「我与这一批用户的关系」，供关注 / 粉丝 / 黑名单列表一次性标记按钮状态。
     *
     * <p>为什么必须有这个方法：{@link #relation(Long)} 要跑 6 条 SQL。列表页一页 50 行，
     * 如果前端逐行调用，就是 51 次 HTTP + 约 300 条关系查询（还没算每个请求各自的鉴权查询）。
     * 这里把同样的结果改成**固定 6 条 SQL 覆盖整页**，与页大小无关。
     *
     * <p>返回 Map 而不是 List：调用方需要按 userId 取，且这里天然是「id → 关系」的映射，
     * 列表里查不到的 id（不存在或已被清理）直接缺席，由调用方决定回退成什么默认状态。
     *
     * <p>顺序无关紧要：所有查询都是集合成员判断或分组统计，不依赖 id 的先后。
     */
    @Transactional(readOnly = true)
    public Map<Long, UserRelationResponse> relations(Collection<Long> targetIds) {
        Long me = currentUserId();
        if (targetIds == null || targetIds.isEmpty()) {
            return Map.of();
        }
        // 去重 + 去掉自己：自己与自己的关系没有意义，且 relation(Long) 里是另一套语义
        Set<Long> ids = targetIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.equals(me))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }

        Set<Long> iFollow = new HashSet<>(followRepository.findFollowingIds(me, ids));
        Set<Long> followMe = new HashSet<>(followRepository.findFollowerIds(me, ids));
        Set<Long> iBlocked = new HashSet<>(blockRepository.findBlockedIdsAmong(me, ids));
        Set<Long> blockedMe = new HashSet<>(blockRepository.findBlockerIdsAmong(me, ids));

        Map<Long, Long> followerCounts = toCountMap(followRepository.countByFolloweeIds(ids));
        Map<Long, Long> followingCounts = toCountMap(followRepository.countByFollowerIds(ids));

        Map<Long, UserRelationResponse> result = new LinkedHashMap<>();
        for (Long id : ids) {
            result.put(id, new UserRelationResponse(
                    iFollow.contains(id),
                    followMe.contains(id),
                    iBlocked.contains(id),
                    blockedMe.contains(id),
                    followerCounts.getOrDefault(id, 0L),
                    followingCounts.getOrDefault(id, 0L)));
        }
        log.info("[用户] 批量关系查询 me={} 请求={} 返回={}", me, targetIds.size(), result.size());
        return result;
    }

    /** 把 [id, count] 的行集转成 Map，省掉调用方重复写这段转换。 */
    private static Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }

    /** 我关注的人 */
    @Transactional(readOnly = true)
    public Page<UserBrief> following(Pageable pageable) {
        Long me = currentUserId();
        Page<UserFollow> page = followRepository.findByFollowerIdOrderByCreatedAtDesc(me, pageable);
        return toBriefPage(page.getContent().stream().map(UserFollow::getFolloweeId).toList(), page);
    }

    /** 关注我的人 */
    @Transactional(readOnly = true)
    public Page<UserBrief> followers(Pageable pageable) {
        Long me = currentUserId();
        Page<UserFollow> page = followRepository.findByFolloweeIdOrderByCreatedAtDesc(me, pageable);
        return toBriefPage(page.getContent().stream().map(UserFollow::getFollowerId).toList(), page);
    }

    /** 我拉黑的人 */
    @Transactional(readOnly = true)
    public Page<UserBrief> blocked(Pageable pageable) {
        Long me = currentUserId();
        Page<UserBlock> page = blockRepository.findByBlockerIdOrderByCreatedAtDesc(me, pageable);
        return toBriefPage(page.getContent().stream().map(UserBlock::getBlockedId).toList(), page);
    }

    /**
     * 供内容服务做信息流过滤的屏蔽集合（双向并集）。
     * 由 Feign 携带调用方令牌访问，因此这里拿到的是「当前请求用户」的视角。
     */
    @Transactional(readOnly = true)
    public UserExclusions exclusionsForCurrentUser() {
        Long me = currentUserId();
        Set<Long> ids = new LinkedHashSet<>(blockRepository.findBlockedIds(me));
        ids.addAll(blockRepository.findBlockerIds(me));
        return new UserExclusions(new ArrayList<>(ids));
    }

    private Page<UserBrief> toBriefPage(List<Long> userIds, Page<?> source) {
        if (userIds.isEmpty()) {
            return new PageImpl<>(List.of(), source.getPageable(), source.getTotalElements());
        }
        return new PageImpl<>(userService.briefs(userIds), source.getPageable(), source.getTotalElements());
    }

    private void requireNotSelf(Long me, Long target, String message) {
        if (target == null) {
            throw new BizException("用户不存在");
        }
        if (me.equals(target)) {
            throw new BizException(message);
        }
    }

    private Long currentUserId() {
        LoginUser user = SecurityUtils.currentUser();
        return user.getId();
    }

    /**
     * 通知里展示的触发者名称：昵称优先、其次手机号、最后兜底文案。
     * 口径与 aid-service / community-service 的 displayName 保持一致，
     * 避免同一个用户在不同通知里显示成不同的名字。
     */
    private String displayName(LoginUser user) {
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname();
        }
        if (StringUtils.hasText(user.getPhone())) {
            return user.getPhone();
        }
        return "用户" + user.getId();
    }
}
