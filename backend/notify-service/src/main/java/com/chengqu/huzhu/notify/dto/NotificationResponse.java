package com.chengqu.huzhu.notify.dto;

import com.chengqu.huzhu.notify.entity.Notification;
import com.chengqu.huzhu.notify.entity.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationResponse {

    private Long id;
    /** 类型枚举名，如 AID_ACCEPTED，供前端做分组/图标判断。 */
    private NotificationType type;
    /** 类型中文名，前端直接展示，避免每个端各维护一份枚举文案。 */
    private String typeLabel;
    private String title;
    private String content;
    private String refType;
    private Long refId;
    private Long actorId;
    private String actorName;
    /** 是否已读（由 readAt 推导，不单独存布尔列）。 */
    private boolean read;
    private LocalDateTime createdAt;
    /**
     * 点击后的跳转路径；为 null 表示该通知不可跳转。
     *
     * <p>优先用创建时落库的 link（由业务方按上下文生成，如校园帖 → /campus?post={id}），
     * 没有时才按 type 现算。存量通知的 link 列是 NULL，走的正是现算分支，
     * 因此这次改动不会改变既有通知的跳转行为。
     */
    private String link;

    public static NotificationResponse from(Notification entity) {
        NotificationType type = entity.getType();
        return NotificationResponse.builder()
                .id(entity.getId())
                .type(type)
                .typeLabel(type == null ? null : type.getLabel())
                .title(entity.getTitle())
                .content(entity.getContent())
                .refType(entity.getRefType())
                .refId(entity.getRefId())
                .actorId(entity.getActorId())
                .actorName(entity.getActorName())
                .read(entity.getReadAt() != null)
                .createdAt(entity.getCreatedAt())
                .link(linkOf(entity, type))
                .build();
    }

    /**
     * 跳转路径解析：落库值优先，缺失时回退到按类型推导。
     *
     * <p>解析集中在这一处，是为了让「列表、详情、创建后返回」三条读取路径
     * 拿到完全一致的结果——前端点击依赖的就是这个字段。
     */
    private static String linkOf(Notification entity, NotificationType type) {
        String stored = entity.getLink();
        if (stored != null && !stored.isBlank()) {
            return stored;
        }
        return type == null ? null : type.link(entity.getRefId(), entity.getActorId());
    }
}
