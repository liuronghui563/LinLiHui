package com.chengqu.huzhu.user.service;

import com.chengqu.huzhu.api.dto.UserBrief;
import com.chengqu.huzhu.auth.dto.TokenResponse;
import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.common.file.LocalFileUrls;
import com.chengqu.huzhu.security.LoginUser;
import com.chengqu.huzhu.security.SecurityUtils;
import com.chengqu.huzhu.user.dto.PublicUserProfile;
import com.chengqu.huzhu.user.dto.ScoreRequest;
import com.chengqu.huzhu.user.dto.UpdateProfileRequest;
import com.chengqu.huzhu.user.entity.User;
import com.chengqu.huzhu.user.entity.UserRating;
import com.chengqu.huzhu.user.repository.UserRatingRepository;
import com.chengqu.huzhu.user.repository.UserRepository;
import com.chengqu.huzhu.user.support.UserProfiles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserRatingRepository userRatingRepository;

    @Transactional(readOnly = true)
    public TokenResponse.UserProfile profile() {
        LoginUser loginUser = SecurityUtils.currentUser();
        User user = userRepository.findById(loginUser.getId())
                .orElseThrow(() -> new BizException(401, "用户不存在"));
        log.info("[用户] 查询资料 userId={}, nickname={}", user.getId(), user.getNickname());
        return UserProfiles.of(user);
    }

    @Transactional(readOnly = true)
    public PublicUserProfile publicProfile(Long userId) {
        User user = requireEnabledUser(userId);
        LoginUser viewer = SecurityUtils.currentUser();
        Long viewerId = viewer.getId();
        boolean self = viewerId.equals(user.getId());
        boolean admin = viewer.getRole() == com.chengqu.huzhu.user.entity.RoleType.ADMIN;
        if (Boolean.TRUE.equals(user.getPrivateAccount()) && !self && !admin) {
            throw new BizException(403, "该用户已开启隐私账户，他人无法查看");
        }
        log.info("[用户] 查看主页 targetId={}, viewerId={}, self={}", userId, viewerId, self);
        return toPublic(user, viewerId, self);
    }

    @Transactional
    public PublicUserProfile rateUser(Long userId, ScoreRequest request) {
        LoginUser rater = SecurityUtils.currentUser();
        if (rater.getId().equals(userId)) {
            throw new BizException("不能评价自己");
        }
        User target = requireEnabledUser(userId);
        UserRating rating = userRatingRepository.findByTargetUserIdAndRaterId(userId, rater.getId())
                .orElseGet(() -> UserRating.builder()
                        .targetUserId(userId)
                        .raterId(rater.getId())
                        .build());
        rating.setScore(request.getScore());
        userRatingRepository.save(rating);
        log.info("[用户] 评价 targetId={}, raterId={}, score={}", userId, rater.getId(), request.getScore());
        return toPublic(target, rater.getId(), false);
    }

    @Transactional
    public TokenResponse.UserProfile updateProfile(UpdateProfileRequest request) {
        LoginUser loginUser = SecurityUtils.currentUser();
        User user = userRepository.findById(loginUser.getId())
                .orElseThrow(() -> new BizException(401, "用户不存在"));
        if (StringUtils.hasText(request.getNickname())) {
            user.setNickname(request.getNickname().trim());
        }
        if (request.getRealName() != null) {
            user.setRealName(UserProfiles.blankToNull(request.getRealName()));
        }
        if (request.getAvatar() != null) {
            String avatar = UserProfiles.blankToNull(request.getAvatar());
            if (avatar != null && LocalFileUrls.sanitizeOptional(avatar) == null) {
                throw new BizException("头像必须先上传到本站");
            }
            user.setAvatar(LocalFileUrls.sanitizeOptional(avatar));
        }
        if (request.getCoverImage() != null) {
            // 与头像同一条规矩：只收本站地址，传空字符串表示「移除封面」
            String cover = UserProfiles.blankToNull(request.getCoverImage());
            if (cover != null && LocalFileUrls.sanitizeOptional(cover) == null) {
                throw new BizException("封面图必须先上传到本站");
            }
            user.setCoverImage(LocalFileUrls.sanitizeOptional(cover));
        }
        if (request.getBio() != null) {
            user.setBio(UserProfiles.blankToNull(request.getBio()));
        }
        if (request.getPresenceStatus() != null) {
            user.setPresenceStatus(request.getPresenceStatus());
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
        if (request.getCity() != null) {
            user.setCity(UserProfiles.blankToNull(request.getCity()));
        }
        if (request.getNeighborhood() != null) {
            user.setNeighborhood(UserProfiles.blankToNull(request.getNeighborhood()));
        }
        if (request.getSchool() != null) {
            user.setSchool(UserProfiles.blankToNull(request.getSchool()));
        }
        if (request.getMajor() != null) {
            user.setMajor(UserProfiles.blankToNull(request.getMajor()));
        }
        if (request.getGrade() != null) {
            user.setGrade(UserProfiles.blankToNull(request.getGrade()));
        }
        // 这里原本按 request.student 直接赋值。学生身份只能由管理员审核认证后授予，
        // 不能自助声明，因此该分支随 UpdateProfileRequest.student 一起删除，
        // 改由 StudentVerificationService.approve 写入。
        if (request.getPrivateAccount() != null) {
            user.setPrivateAccount(request.getPrivateAccount());
        }
        if (request.getWechat() != null) {
            user.setWechat(UserProfiles.blankToNull(request.getWechat()));
        }
        User saved = userRepository.save(user);
        log.info("[用户] 更新资料 userId={}, nickname={}, presence={}, student={}",
                saved.getId(), saved.getNickname(), saved.getPresenceStatus(), saved.getStudent());
        return UserProfiles.of(saved);
    }

    /**
     * 批量查询用户精简信息，供其他服务跨服务调用。
     *
     * <p>无论传入多少个 ID，固定 2 次查询：一次批量取用户，一次批量聚合评分。
     */
    @Transactional(readOnly = true)
    public List<UserBrief> briefs(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }

        Map<Long, double[]> ratings = new HashMap<>();
        for (Object[] row : userRatingRepository.aggregateByTargetUserIds(ids)) {
            ratings.put(((Number) row[0]).longValue(),
                    new double[]{((Number) row[1]).doubleValue(), ((Number) row[2]).longValue()});
        }

        List<UserBrief> result = new ArrayList<>();
        for (User user : userRepository.findAllById(ids)) {
            double[] aggregate = ratings.get(user.getId());
            result.add(new UserBrief(
                    user.getId(),
                    user.getNickname(),
                    UserProfiles.resolveAvatar(user),
                    user.getPresenceStatus() == null ? null : user.getPresenceStatus().name(),
                    user.getStudent(),
                    user.getSchool(),
                    aggregate == null ? 0D : roundAvg(aggregate[0]),
                    aggregate == null ? 0L : (long) aggregate[1]));
        }
        log.info("[用户] 内部批量查询请求 {} 个 ID，命中 {}", ids.size(), result.size());
        return result;
    }

    private User requireEnabledUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(404, "用户不存在"));
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BizException(404, "用户不存在");
        }
        return user;
    }

    private PublicUserProfile toPublic(User user, Long viewerId, boolean self) {
        Double avg = userRatingRepository.averageByTargetUserId(user.getId());
        long count = userRatingRepository.countByTargetUserId(user.getId());
        Integer myScore = self ? null : userRatingRepository.findByTargetUserIdAndRaterId(user.getId(), viewerId)
                .map(UserRating::getScore)
                .orElse(null);
        return UserProfiles.publicOf(user, self, roundAvg(avg), count, myScore);
    }

    private static Double roundAvg(Double avg) {
        if (avg == null) {
            return 0D;
        }
        return Math.round(avg * 10.0) / 10.0;
    }
}
