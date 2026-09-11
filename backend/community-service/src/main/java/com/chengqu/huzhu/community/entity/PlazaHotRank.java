package com.chengqu.huzhu.community.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "u_r_plaza_hot", uniqueConstraints = {
        @UniqueConstraint(name = "uk_u_r_plaza_hot_date_rank", columnNames = {"rankDate", "rankNo"})
})
public class PlazaHotRank {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate rankDate;

    @Column(nullable = false)
    private Integer rankNo;

    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false)
    private Long commentCount;

    @Column(nullable = false)
    private Integer likeCount;
}
