package com.chengqu.huzhu.community.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 圈子与帖子的关联（一条帖子被收录进一个圈子）。
 *
 * <p>为什么不是给 u_r_post 加 circle_id：
 * <ol>
 *   <li>u_r_post 正在被并行开发改动，给热点表加列会与他人的迁移撞车；</li>
 *   <li>一条帖子理论上可以同时属于多个圈子（例如「羽毛球」和「周末活动」），
 *       关联表比一列外键表达得更准确；</li>
 *   <li>把圈子带来的全部结构改动隔离在新表内，回滚时只需删表。</li>
 * </ol>
 */
@Getter
@Setter
@Entity
@Table(name = "u_r_circle_post")
public class CirclePost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long circleId;

    @Column(nullable = false)
    private Long postId;

    /** 收录时间。圈内列表按它倒序，而不是帖子自己的发布时间。 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
