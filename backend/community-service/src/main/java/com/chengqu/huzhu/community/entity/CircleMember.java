package com.chengqu.huzhu.community.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 圈子成员。
 *
 * <p>唯一键 (circle_id, user_id) 在迁移脚本里，重复加入由数据库兜底；
 * 业务层仍会先查一次，把「你已在该圈子中」这种可读的错误返回给前端。
 */
@Getter
@Setter
@Entity
@Table(name = "u_r_circle_member")
public class CircleMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long circleId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CircleMemberRole role;

    /** 被禁言后仍留在圈内，但不能在本圈发帖。 */
    @Column(nullable = false)
    private Boolean muted = false;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    @PrePersist
    public void prePersist() {
        joinedAt = LocalDateTime.now();
        if (role == null) {
            role = CircleMemberRole.MEMBER;
        }
        if (muted == null) {
            muted = false;
        }
    }
}
