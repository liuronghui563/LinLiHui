package com.chengqu.huzhu.user.dto;

import com.chengqu.huzhu.user.entity.Gender;
import com.chengqu.huzhu.user.entity.PresenceStatus;
import com.chengqu.huzhu.user.entity.RoleType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PublicUserProfile {

    private Long id;
    private String nickname;
    private String realName;
    private String avatar;
    /** 个人主页封面图（相对路径）；未设置时为 null，前端回落到纯色 + 纹路 */
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
    private RoleType role;
    /** 仅查看自己时返回脱敏手机号 */
    private String phone;
    private LocalDateTime createdAt;
    private Double ratingAvg;
    private Long ratingCount;
    private Integer myScore;
    private Boolean privateAccount;
    private boolean self;
}
