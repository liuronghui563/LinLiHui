package com.chengqu.huzhu.user.support;

import com.chengqu.huzhu.auth.dto.TokenResponse;
import com.chengqu.huzhu.user.dto.PublicUserProfile;
import com.chengqu.huzhu.user.entity.Gender;
import com.chengqu.huzhu.user.entity.PresenceStatus;
import com.chengqu.huzhu.user.entity.User;
import org.springframework.util.StringUtils;

public final class UserProfiles {

    private UserProfiles() {
    }

    public static TokenResponse.UserProfile of(User user) {
        return TokenResponse.UserProfile.builder()
                .id(user.getId())
                .phone(maskPhone(user.getPhone()))
                .nickname(user.getNickname())
                .realName(user.getRealName())
                .avatar(resolveAvatar(user))
                .bio(user.getBio())
                .presenceStatus(user.getPresenceStatus() == null ? PresenceStatus.OFFLINE : user.getPresenceStatus())
                .gender(user.getGender() == null ? Gender.UNKNOWN : user.getGender())
                .city(user.getCity())
                .neighborhood(user.getNeighborhood())
                .school(user.getSchool())
                .major(user.getMajor())
                .grade(user.getGrade())
                .student(Boolean.TRUE.equals(user.getStudent()))
                .wechat(user.getWechat())
                .privateAccount(Boolean.TRUE.equals(user.getPrivateAccount()))
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }

    public static PublicUserProfile publicOf(User user, boolean self, Double ratingAvg, long ratingCount, Integer myScore) {
        return PublicUserProfile.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .realName(self ? user.getRealName() : null)
                .avatar(resolveAvatar(user))
                .bio(user.getBio())
                .presenceStatus(user.getPresenceStatus() == null ? PresenceStatus.OFFLINE : user.getPresenceStatus())
                .gender(user.getGender() == null ? Gender.UNKNOWN : user.getGender())
                .city(user.getCity())
                .neighborhood(user.getNeighborhood())
                .school(user.getSchool())
                .major(user.getMajor())
                .grade(user.getGrade())
                .student(Boolean.TRUE.equals(user.getStudent()))
                .wechat(user.getWechat())
                .role(user.getRole())
                .phone(self ? maskPhone(user.getPhone()) : null)
                .createdAt(user.getCreatedAt())
                .self(self)
                .ratingAvg(ratingAvg)
                .ratingCount(ratingCount)
                .myScore(myScore)
                .privateAccount(Boolean.TRUE.equals(user.getPrivateAccount()))
                .build();
    }

    public static String resolveAvatar(User user) {
        if (StringUtils.hasText(user.getAvatar())) {
            return user.getAvatar();
        }
        return "https://picsum.photos/seed/user-" + user.getId() + "/200/200";
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    public static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
