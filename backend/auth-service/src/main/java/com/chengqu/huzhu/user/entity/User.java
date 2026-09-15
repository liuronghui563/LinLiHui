package com.chengqu.huzhu.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "u_r_sys_user", indexes = {
        @Index(name = "uk_u_r_sys_user_phone", columnList = "phone", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(length = 50)
    private String nickname;

    @Column(length = 30)
    private String realName;

    @Column(length = 255)
    private String avatar;

    /** 个人主页封面图。与其他图片一样只存相对路径，非本站地址读时会回落为未设置。 */
    @Column(length = 255)
    private String coverImage;

    @Column(length = 200)
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private PresenceStatus presenceStatus = PresenceStatus.OFFLINE;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private Gender gender = Gender.UNKNOWN;

    @Column(length = 50)
    private String city;

    @Column(length = 80)
    private String neighborhood;

    @Column(length = 80)
    private String school;

    @Column(length = 80)
    private String major;

    @Column(length = 30)
    private String grade;

    @Builder.Default
    private Boolean student = false;

    @Builder.Default
    private Boolean privateAccount = false;

    @Column(length = 50)
    private String wechat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoleType role;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
