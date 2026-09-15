package com.chengqu.huzhu.notify.dto;

import com.chengqu.huzhu.notify.entity.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建通知的请求体，供 {@code POST /internal/notify} 使用。
 *
 * <p>字段与 api 模块的 {@code NotifyCreateCommand} 一一对应（JSON 字段名必须一致），
 * 那边是跨服务契约，这边是本服务的入参校验视图。
 */
@Data
public class CreateNotificationRequest {

    /** 接收者用户 id。 */
    @NotNull(message = "接收者不能为空")
    private Long userId;

    @NotNull(message = "通知类型不能为空")
    private NotificationType type;

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题最长100字")
    private String title;

    @Size(max = 500, message = "内容最长500字")
    private String content;

    /** 关联对象类型，如 AID / POST / USER。 */
    @Size(max = 30, message = "关联对象类型最长30字")
    private String refType;

    private Long refId;

    /** 触发者用户 id，供前端展示头像。 */
    private Long actorId;

    /** 触发者昵称快照，取不到时兜底展示。 */
    @Size(max = 50, message = "触发者昵称最长50字")
    private String actorName;

    /**
     * 调用方指定的跳转路径，可为 null。
     *
     * <p>用于「落点由业务决定、光看通知类型定不下来」的场景（如校园帖与发现帖
     * 都走 POST_COMMENTED，但一个在 /campus、一个在 /discover）。
     * 传 null 时回退到 NotificationType 按类型生成的路径，老调用方不受影响。
     *
     * <p>长度与 u_r_notification.link 列一致，超出会整条写入失败。
     */
    @Size(max = 300, message = "跳转链接最长300字")
    private String link;
}
