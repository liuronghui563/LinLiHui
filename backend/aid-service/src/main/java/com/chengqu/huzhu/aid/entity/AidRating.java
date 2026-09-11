package com.chengqu.huzhu.aid.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "u_r_aid_rating", uniqueConstraints = {
        @UniqueConstraint(name = "uk_u_r_aid_rating", columnNames = {"aidId", "raterId"})
})
public class AidRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long aidId;

    @Column(nullable = false)
    private Long raterId;

    @Column(nullable = false)
    private Integer score;
}
