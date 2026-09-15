package com.chengqu.huzhu.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 拉黑关系。{@code blocker} 拉黑了 {@code blocked}。 */
@Getter
@Setter
@Entity
@Table(name = "u_r_user_block", uniqueConstraints = {
        @UniqueConstraint(name = "uk_u_r_user_block", columnNames = {"blockerId", "blockedId"})
})
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long blockerId;

    @Column(nullable = false)
    private Long blockedId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
