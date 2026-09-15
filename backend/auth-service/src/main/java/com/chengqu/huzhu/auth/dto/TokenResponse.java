package com.chengqu.huzhu.auth.dto;

import com.chengqu.huzhu.user.entity.Gender;
import com.chengqu.huzhu.user.entity.PresenceStatus;
import com.chengqu.huzhu.user.entity.RoleType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private UserProfile user;

    @Data
    @Builder
    public static class UserProfile {
        private Long id;
        private String phone;
        private String nickname;
        private String realName;
        private String avatar;
        private String coverImage;
        private String bio;
        private PresenceStatus presenceStatus;
        private Gender gender;
        private String city;
        private String neighborhood;
        private String school;
        private String major;
        private String grade;
        private Boolean student;
        private String wechat;
        private Boolean privateAccount;
        private RoleType role;
        private java.time.LocalDateTime createdAt;
    }
}
