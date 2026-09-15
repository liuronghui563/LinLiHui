package com.chengqu.huzhu.community.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 兴趣圈子（兴趣小组）。
 *
 * <p>唯一键 uk_u_r_circle_name 建在迁移脚本里（V7__circle.sql）而不是靠
 * 应用层查重：并发创建同名圈子时只有数据库约束拦得住。
 * 这里先用 existsByName 给出友好提示，约束负责兜底。
 */
@Getter
@Setter
@Entity
@Table(name = "u_r_circle")
public class Circle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 200)
    private String description;

    /** 封面：本站对象地址（/api/file/objects/...）。 */
    @Column(length = 500)
    private String cover;

    @Column(nullable = false)
    private Long ownerId;

    /** 冗余昵称快照：Feign 取不到用户信息时兜底渲染。 */
    @Column(nullable = false, length = 50)
    private String ownerName;

    /** 成员数快照，由 CircleService 在加入/退出时维护，避免列表页对成员表 count。 */
    @Column(nullable = false)
    private Integer memberCount = 0;

    /** 收录帖子数快照，同上。 */
    @Column(nullable = false)
    private Integer postCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CircleStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = CircleStatus.ACTIVE;
        }
        if (memberCount == null) {
            memberCount = 0;
        }
        if (postCount == null) {
            postCount = 0;
        }
    }
}
