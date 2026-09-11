package com.chengqu.huzhu.user.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "u_r_user_rating", uniqueConstraints = {
        @UniqueConstraint(name = "uk_u_r_user_rating", columnNames = {"target_user_id", "rater_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Column(name = "rater_id", nullable = false)
    private Long raterId;

    @Column(nullable = false)
    private Integer score;
}
