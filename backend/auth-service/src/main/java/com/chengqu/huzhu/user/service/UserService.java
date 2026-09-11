package com.chengqu.huzhu.user.service;

import com.chengqu.huzhu.auth.dto.TokenResponse;
import com.chengqu.huzhu.common.exception.BizException;
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
            user.setAvatar(UserProfiles.blankToNull(request.getAvatar()));
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
        if (request.getStudent() != null) {
            user.setStudent(request.getStudent());
        }
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
