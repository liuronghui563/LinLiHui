package com.chengqu.huzhu.notify.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "u_r_notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 接收者；通知的可见范围完全由它决定，所有查询都必须带上它。 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String content;

    /** 关联对象类型，如 AID / POST / USER；只用于前端判断跳转语义。 */
    @Column(name = "ref_type", length = 30)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    /** 触发者（点赞的人、评论的人……）；系统通知为 null。 */
    @Column(name = "actor_id")
    private Long actorId;

    /** 触发者昵称快照，仅作取不到昵称时的兜底展示。 */
    @Column(name = "actor_name", length = 50)
    private String actorName;

    /**
     * 跳转路径覆盖值，null 表示「按 type 现算」（见 NotificationType.link）。
     *
     * <p>为什么要落库而不是读取时现算：落点并不总由通知类型决定。
     * 同样是「动态被评论」，发现频道的帖子在 /discover?post=，校园帖在 /campus?post=，
     * 而本服务只拿到 type/refId，看不到帖子频道。让最了解业务的一方（community-service）
     * 在创建时把路径写进来，读取时直接用，老数据（NULL）仍走原来的推导逻辑。
     */
    @Column(length = 300)
    private String link;

    /** 已读时间，null 表示未读——未读状态没有单独的布尔列。 */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
