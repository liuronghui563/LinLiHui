package com.chengqu.huzhu.aid.service;

import com.chengqu.huzhu.aid.dto.AidResponse;
import com.chengqu.huzhu.aid.dto.AidStatsResponse;
import com.chengqu.huzhu.aid.dto.CreateAidRequest;
import com.chengqu.huzhu.aid.dto.HelperReviewRequest;
import com.chengqu.huzhu.aid.dto.ScoreRequest;
import com.chengqu.huzhu.aid.dto.UpdateAidRequest;
import com.chengqu.huzhu.aid.entity.AidBoard;
import com.chengqu.huzhu.aid.entity.AidHelperReview;
import com.chengqu.huzhu.aid.entity.AidRating;
import com.chengqu.huzhu.aid.entity.AidRequest;
import com.chengqu.huzhu.aid.entity.AidStatus;
import com.chengqu.huzhu.aid.repository.AidHelperReviewRepository;
import com.chengqu.huzhu.aid.repository.AidRatingRepository;
import com.chengqu.huzhu.aid.repository.AidRequestRepository;
import com.chengqu.huzhu.aid.support.NotifySender;
import com.chengqu.huzhu.aid.support.UserLookup;
import com.chengqu.huzhu.api.dto.NotifyCreateCommand;
import com.chengqu.huzhu.api.dto.PlatformAidStats;
import com.chengqu.huzhu.api.dto.UserAidStats;
import com.chengqu.huzhu.api.dto.UserBrief;
import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.common.file.LocalFileUrls;
import com.chengqu.huzhu.common.security.SecurityUtils;
import com.chengqu.huzhu.common.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AidService {

    private final AidRequestRepository aidRequestRepository;
    private final AidRatingRepository aidRatingRepository;
    private final AidHelperReviewRepository aidHelperReviewRepository;
    private final UserLookup userLookup;
    private final NotifySender notifySender;

    private static final List<String> CAMPUS_CATEGORIES = List.of("失物招领");

    /** 通知里的关联对象类型，与 notify-service 生成跳转链接的取值保持一致。 */
    private static final String REF_TYPE_AID = "AID";

    @Transactional(readOnly = true)
    public Page<AidResponse> list(AidStatus status, String category, Boolean student, String board, Pageable pageable) {
        AidBoard parsedBoard = parseBoard(board, Boolean.TRUE.equals(student));
        boolean hasCategory = StringUtils.hasText(category);
        // board 已由迁移脚本归一化为 NOT NULL，校园与邻里板块可共用同一组等值查询，
        // 不再需要为 NULL 板块写 "IS NULL OR" 兜底（OR 会让 (board, created_at) 索引失效）。
        Page<AidRequest> page;
        if (hasCategory && status != null) {
            page = aidRequestRepository.findByBoardAndStatusAndCategory(parsedBoard, status, category.trim(), pageable);
        } else if (hasCategory) {
            page = aidRequestRepository.findByBoardAndCategory(parsedBoard, category.trim(), pageable);
        } else if (status != null) {
            page = aidRequestRepository.findByBoardAndStatus(parsedBoard, status, pageable);
        } else {
            page = aidRequestRepository.findByBoard(parsedBoard, pageable);
        }
        Page<AidResponse> result = toPage(page);
        log.info("[求助] 查询列表 board={}, status={}, category={}, page={}, size={}, total={}",
                parsedBoard, status, category, pageable.getPageNumber(), result.getNumberOfElements(), result.getTotalElements());
        return result;
    }

    @Transactional(readOnly = true)
    public AidResponse detail(Long id) {
        AidResponse resp = toResponse(requireAid(id));
        log.info("[求助] 查询详情 id={}, title={}, status={}", id, resp.getTitle(), resp.getStatus());
        return resp;
    }

    @Transactional
    public AidResponse create(CreateAidRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        AidRequest aid = new AidRequest();
        aid.setTitle(request.getTitle().trim());
        aid.setContent(request.getContent().trim());
        aid.setCategory(request.getCategory().trim());
        AidBoard board = parseBoard(request.getBoard(), false);
        if (board == AidBoard.CAMPUS) {
            if (!CAMPUS_CATEGORIES.contains(aid.getCategory())) {
                throw new BizException("校园专区分类无效");
            }
            aid.setAddress(StringUtils.hasText(request.getAddress()) ? request.getAddress().trim() : "校园");
        } else if (!StringUtils.hasText(request.getAddress())) {
            throw new BizException("地址不能为空");
        } else {
            aid.setAddress(request.getAddress().trim());
        }
        aid.setBoard(board);
        aid.setStatus(AidStatus.OPEN);
        aid.setPublisherId(user.getId());
        aid.setPublisherName(displayName(user));
        aid.setImages(LocalFileUrls.normalizeImages(request.getImages()));
        AidRequest saved = aidRequestRepository.save(aid);
        log.info("[求助] 新建成功 id={}, publisherId={}, title={}", saved.getId(), user.getId(), saved.getTitle());
        return toResponse(saved);
    }

    @Transactional
    public AidResponse update(Long id, UpdateAidRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        AidRequest aid = requireAid(id);
        if (!aid.getPublisherId().equals(user.getId())) {
            throw new BizException(403, "仅发布者可编辑求助");
        }
        if (aid.getStatus() != AidStatus.OPEN) {
            throw new BizException("仅待接单状态可编辑");
        }
        aid.setTitle(request.getTitle().trim());
        aid.setContent(request.getContent().trim());
        aid.setCategory(request.getCategory().trim());
        aid.setAddress(request.getAddress().trim());
        if (request.getImages() != null) {
            aid.setImages(LocalFileUrls.normalizeImages(request.getImages()));
        }
        AidRequest saved = aidRequestRepository.save(aid);
        log.info("[求助] 更新成功 id={}, operatorId={}", saved.getId(), user.getId());
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        UserPrincipal user = SecurityUtils.currentUser();
        AidRequest aid = requireAid(id);
        boolean isPublisher = aid.getPublisherId().equals(user.getId());
        boolean isAdmin = isAdmin(user);
        if (!isPublisher && !isAdmin) {
            throw new BizException(403, "仅发布者或管理员可删除求助");
        }
        if (isPublisher && aid.getStatus() == AidStatus.ACCEPTED) {
            throw new BizException("进行中的求助不可直接删除，请先联系帮助者完成或取消");
        }
        aidRatingRepository.deleteByAidId(id);
        aidHelperReviewRepository.deleteByAidId(id);
        aidRequestRepository.delete(aid);
        log.info("[求助] 删除成功 id={}, operatorId={}, wasStatus={}", id, user.getId(), aid.getStatus());
    }

    @Transactional
    public AidResponse accept(Long id) {
        UserPrincipal user = SecurityUtils.currentUser();
        AidRequest aid = requireAid(id);
        if (aid.getStatus() != AidStatus.OPEN) {
            throw new BizException("仅开放中的求助可被接单");
        }
        if (aid.getPublisherId().equals(user.getId())) {
            throw new BizException("不能接自己发布的求助");
        }
        aid.setStatus(AidStatus.ACCEPTED);
        aid.setHelperId(user.getId());
        aid.setHelperName(displayName(user));
        AidRequest saved = aidRequestRepository.save(aid);
        log.info("[求助] 接单成功 id={}, helperId={}, publisherId={}", id, user.getId(), aid.getPublisherId());
        // 接单成功 → 通知发布者。命令对象在这里（事务内）就构造好，投递由 NotifySender
        // 推迟到事务提交之后：远程调用不占用事务，且接单回滚时不会发出这条通知。
        notifySender.sendAfterCommit(new NotifyCreateCommand(
                saved.getPublisherId(),
                "AID_ACCEPTED",
                "有人接下了你的求助",
                "「" + saved.getTitle() + "」已由 " + displayName(user) + " 接下，请及时沟通",
                REF_TYPE_AID,
                saved.getId(),
                user.getId(),
                displayName(user)));
        return toResponse(saved);
    }

    @Transactional
    public AidResponse complete(Long id) {
        UserPrincipal user = SecurityUtils.currentUser();
        AidRequest aid = requireAid(id);
        if (aid.getStatus() != AidStatus.ACCEPTED) {
            throw new BizException("仅已接单的求助可标记完成");
        }
        boolean isPublisher = aid.getPublisherId().equals(user.getId());
        boolean isHelper = aid.getHelperId() != null && aid.getHelperId().equals(user.getId());
        if (!isPublisher && !isHelper) {
            throw new BizException(403, "仅发布者或帮助者可标记完成");
        }
        aid.setStatus(AidStatus.DONE);
        AidRequest saved = aidRequestRepository.save(aid);
        log.info("[求助] 标记完成 id={}, operatorId={}", id, user.getId());
        // 标记完成 → 通知「另一方」：发布者操作就通知帮助者，帮助者操作就通知发布者。
        Long receiverId = isPublisher ? saved.getHelperId() : saved.getPublisherId();
        if (receiverId != null && !receiverId.equals(user.getId())) {
            notifySender.sendAfterCommit(new NotifyCreateCommand(
                    receiverId,
                    "AID_COMPLETED",
                    "求助已标记完成",
                    "「" + saved.getTitle() + "」已由 " + displayName(user) + " 标记完成",
                    REF_TYPE_AID,
                    saved.getId(),
                    user.getId(),
                    displayName(user)));
        }
        return toResponse(saved);
    }

    @Transactional
    public AidResponse cancel(Long id) {
        UserPrincipal user = SecurityUtils.currentUser();
        AidRequest aid = requireAid(id);
        if (!aid.getPublisherId().equals(user.getId())) {
            throw new BizException(403, "仅发布者可取消求助");
        }
        if (aid.getStatus() != AidStatus.OPEN) {
            throw new BizException("仅开放中的求助可取消");
        }
        aid.setStatus(AidStatus.CANCELLED);
        AidRequest saved = aidRequestRepository.save(aid);
        log.info("[求助] 取消成功 id={}, publisherId={}", id, user.getId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<AidResponse> myPublished(Pageable pageable) {
        Long userId = SecurityUtils.currentUser().getId();
        Page<AidResponse> page = toPage(aidRequestRepository.findByPublisherId(userId, pageable));
        log.info("[求助] 我发布的 userId={}, total={}", userId, page.getTotalElements());
        return page;
    }

    @Transactional(readOnly = true)
    public Page<AidResponse> publishedByUser(Long userId, Pageable pageable) {
        Page<AidResponse> page = toPage(aidRequestRepository.findByPublisherId(userId, pageable));
        log.info("[求助] 查看用户发布 userId={}, viewerId={}, total={}",
                userId, SecurityUtils.currentUser().getId(), page.getTotalElements());
        return page;
    }

    @Transactional(readOnly = true)
    public Page<AidResponse> myHelping(Pageable pageable) {
        Long userId = SecurityUtils.currentUser().getId();
        Page<AidResponse> page = toPage(aidRequestRepository.findByHelperId(userId, pageable));
        log.info("[求助] 我帮忙的 userId={}, total={}", userId, page.getTotalElements());
        return page;
    }

    @Transactional(readOnly = true)
    public AidStatsResponse stats() {
        Long userId = SecurityUtils.currentUser().getId();
        AidStatsResponse stats = AidStatsResponse.builder()
                .openCount(aidRequestRepository.countByStatus(AidStatus.OPEN))
                .acceptedCount(aidRequestRepository.countByStatus(AidStatus.ACCEPTED))
                .doneCount(aidRequestRepository.countByStatus(AidStatus.DONE))
                .myPublishedCount(aidRequestRepository.countByPublisherId(userId))
                .myHelpingCount(aidRequestRepository.countByHelperId(userId))
                .build();
        log.info("[求助] 统计 open={}, accepted={}, done={}",
                stats.getOpenCount(), stats.getAcceptedCount(), stats.getDoneCount());
        return stats;
    }

    @Transactional
    public AidResponse rate(Long id, ScoreRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        AidRequest aid = requireAid(id);
        if (aid.getPublisherId().equals(user.getId())) {
            throw new BizException("不能评价自己发布的求助");
        }
        AidRating rating = aidRatingRepository.findByAidIdAndRaterId(id, user.getId())
                .orElseGet(() -> {
                    AidRating created = new AidRating();
                    created.setAidId(id);
                    created.setRaterId(user.getId());
                    return created;
                });
        rating.setScore(request.getScore());
        aidRatingRepository.save(rating);
        log.info("[求助] 评价 id={}, raterId={}, score={}", id, user.getId(), request.getScore());
        return toResponse(aid);
    }

    @Transactional
    public AidResponse reviewHelper(Long id, HelperReviewRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        AidRequest aid = requireAid(id);
        if (!aid.getPublisherId().equals(user.getId())) {
            throw new BizException(403, "仅求助方可评价帮助方");
        }
        if (aid.getStatus() != AidStatus.DONE) {
            throw new BizException("互助完成后才能评价帮助方");
        }
        if (aid.getHelperId() == null) {
            throw new BizException("该求助没有帮助方");
        }
        AidHelperReview review = aidHelperReviewRepository.findByAidId(id)
                .orElseGet(() -> {
                    AidHelperReview created = new AidHelperReview();
                    created.setAidId(id);
                    created.setPublisherId(user.getId());
                    created.setHelperId(aid.getHelperId());
                    return created;
                });
        review.setScore(request.getScore());
        review.setContent(StringUtils.hasText(request.getContent()) ? request.getContent().trim() : null);
        aidHelperReviewRepository.save(review);
        log.info("[求助] 求助方评价帮助方 aidId={}, helperId={}, score={}", id, aid.getHelperId(), request.getScore());
        // 评价成功 → 通知帮助者。求助方是触发者，所以 actorId/actorName 取当前用户；
        // 评价内容一并带上，让帮助者不点开也能看到反馈。
        notifySender.sendAfterCommit(new NotifyCreateCommand(
                aid.getHelperId(),
                "AID_RATED",
                "你的帮助收到了评价",
                "求助方给了你 " + review.getScore() + " 星评价"
                        + (StringUtils.hasText(review.getContent()) ? "：" + review.getContent() : ""),
                REF_TYPE_AID,
                aid.getId(),
                user.getId(),
                displayName(user)));
        return toResponse(aid);
    }

    // ------------------------------------------------------------------
    // 内部接口：供 auth-service 通过 Feign 聚合（管理台看板 / 用户主页）
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PlatformAidStats platformStats() {
        PlatformAidStats stats = new PlatformAidStats(
                aidRequestRepository.count(),
                aidRequestRepository.countByStatus(AidStatus.OPEN),
                aidRequestRepository.countByStatus(AidStatus.ACCEPTED),
                aidRequestRepository.countByStatus(AidStatus.DONE));
        log.info("[求助] 平台统计 total={}, open={}, accepted={}, done={}",
                stats.total(), stats.open(), stats.accepted(), stats.done());
        return stats;
    }

    @Transactional(readOnly = true)
    public UserAidStats userStats(Long userId) {
        UserAidStats stats = new UserAidStats(
                userId,
                aidRequestRepository.countByPublisherId(userId),
                aidRequestRepository.countByHelperId(userId),
                aidRequestRepository.countByPublisherIdAndStatus(userId, AidStatus.DONE));
        log.info("[求助] 用户统计 userId={}, published={}, helping={}, done={}",
                userId, stats.publishedCount(), stats.helpingCount(), stats.doneCount());
        return stats;
    }

    // ------------------------------------------------------------------
    // 装配层：改批量查询以消除列表场景的 N+1（原实现每条求助 4 次查询）
    // ------------------------------------------------------------------

    private Page<AidResponse> toPage(Page<AidRequest> page) {
        return new PageImpl<>(toResponses(page.getContent()), page.getPageable(), page.getTotalElements());
    }

    private AidResponse toResponse(AidRequest aid) {
        return toResponses(List.of(aid)).get(0);
    }

    /**
     * 批量装配评分与评价信息。无论传入多少条，固定 3 次查询：
     * 评分聚合、当前用户评分、帮助方评价。
     */
    private List<AidResponse> toResponses(List<AidRequest> aids) {
        if (aids.isEmpty()) {
            return List.of();
        }
        Long viewerId = SecurityUtils.currentUser().getId();
        List<Long> aidIds = aids.stream().map(AidRequest::getId).toList();

        Map<Long, double[]> aggregates = new HashMap<>();
        for (Object[] row : aidRatingRepository.aggregateByAidIds(aidIds)) {
            aggregates.put(((Number) row[0]).longValue(),
                    new double[]{((Number) row[1]).doubleValue(), ((Number) row[2]).longValue()});
        }

        Map<Long, Integer> myScores = new HashMap<>();
        for (Object[] row : aidRatingRepository.findScoresByRaterAndAidIds(viewerId, aidIds)) {
            myScores.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
        }

        Map<Long, AidHelperReview> reviews = new HashMap<>();
        for (AidHelperReview review : aidHelperReviewRepository.findByAidIdIn(aidIds)) {
            reviews.put(review.getAidId(), review);
        }

        List<AidResponse> result = new ArrayList<>(aids.size());
        for (AidRequest aid : aids) {
            AidResponse resp = AidResponse.from(aid);
            double[] aggregate = aggregates.get(aid.getId());
            resp.setRatingAvg(aggregate == null ? 0D : round1(aggregate[0]));
            resp.setRatingCount(aggregate == null ? 0L : (long) aggregate[1]);
            resp.setMyScore(myScores.get(aid.getId()));
            AidHelperReview review = reviews.get(aid.getId());
            if (review != null) {
                resp.setHelperReviewScore(review.getScore());
                resp.setHelperReviewContent(review.getContent());
                resp.setHelperReviewAt(review.getCreatedAt());
            }
            resp.setCanReviewHelper(aid.getPublisherId().equals(viewerId)
                    && aid.getStatus() == AidStatus.DONE
                    && aid.getHelperId() != null);
            result.add(resp);
        }
        fillUserInfo(result);
        return result;
    }

    /**
     * 通过 Feign 批量补全发布者/帮助者的昵称与头像。
     *
     * <p>无论一页多少条，只发起 1 次跨服务调用（N+1 修复的前提不能被破坏）。
     * 调用失败或用户已不存在时保留实体上的冗余昵称、头像留空，由前端兜底。
     */
    private void fillUserInfo(List<AidResponse> responses) {
        Set<Long> userIds = new HashSet<>();
        for (AidResponse response : responses) {
            if (response.getPublisherId() != null) {
                userIds.add(response.getPublisherId());
            }
            if (response.getHelperId() != null) {
                userIds.add(response.getHelperId());
            }
        }
        Map<Long, UserBrief> users = userLookup.byIds(userIds);
        if (users.isEmpty()) {
            return;
        }
        for (AidResponse response : responses) {
            UserBrief publisher = users.get(response.getPublisherId());
            if (publisher != null) {
                if (StringUtils.hasText(publisher.nickname())) {
                    response.setPublisherName(publisher.nickname());
                }
                response.setPublisherAvatar(publisher.avatar());
            }
            if (response.getHelperId() != null) {
                UserBrief helper = users.get(response.getHelperId());
                if (helper != null) {
                    if (StringUtils.hasText(helper.nickname())) {
                        response.setHelperName(helper.nickname());
                    }
                    response.setHelperAvatar(helper.avatar());
                }
            }
        }
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private AidRequest requireAid(Long id) {
        return aidRequestRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "求助不存在"));
    }

    private String displayName(UserPrincipal user) {
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname();
        }
        if (StringUtils.hasText(user.getPhone())) {
            return user.getPhone();
        }
        return "用户" + user.getId();
    }

    private boolean isAdmin(UserPrincipal user) {
        String role = user.getRole();
        if (role == null) {
            return false;
        }
        if (role.startsWith("ROLE_")) {
            role = role.substring(5);
        }
        return "ADMIN".equals(role);
    }

    private AidBoard parseBoard(String board, boolean campusFallback) {
        if (StringUtils.hasText(board)) {
            try {
                return AidBoard.valueOf(board.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        if (campusFallback) {
            return AidBoard.CAMPUS;
        }
        return AidBoard.NEIGHBORHOOD;
    }
}
