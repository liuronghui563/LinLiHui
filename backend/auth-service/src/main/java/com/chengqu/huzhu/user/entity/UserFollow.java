package com.chengqu.huzhu.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 关注关系。{@code follower} 关注了 {@code followee}。 */
@Getter
@Setter
@Entity
@Table(name = "u_r_user_follow", uniqueConstraints = {
        @UniqueConstraint(name = "uk_u_r_user_follow", columnNames = {"followerId", "followeeId"})
})
public class UserFollow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long followerId;

    @Column(nullable = false)
    private Long followeeId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
