package com.chengqu.huzhu.aid.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "u_r_aid_helper_review", uniqueConstraints = {
        @UniqueConstraint(name = "uk_u_r_aid_helper_review", columnNames = {"aidId"})
})
public class AidHelperReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long aidId;

    @Column(nullable = false)
    private Long publisherId;

    @Column(nullable = false)
    private Long helperId;

    @Column(nullable = false)
    private Integer score;

    @Column(length = 200)
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
