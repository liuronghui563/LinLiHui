package com.chengqu.huzhu.api.dto;

/**
 * 创建站内通知的命令，由 notify-service 的 {@code POST /internal/notify} 消费。
 *
 * <p>字段与 notify-service 的 {@code CreateNotificationRequest} 一一对应，
 * 字段名即 JSON 名，改名等同于改接口，必须两边同时改。
 *
 * @param userId    接收者用户 ID
 * @param type      通知类型枚举名，取值见 notify-service 的 NotificationType
 *                  （AID_ACCEPTED / AID_COMPLETED / AID_RATED / POST_COMMENTED /
 *                  POST_LIKED / FOLLOWED / SYSTEM）；这里用字符串而不是枚举，
 *                  是为了让调用方不必依赖 notify-service 的类，也避免新增类型时老客户端解析失败
 * @param title     标题（不超过 100 字）
 * @param content   正文，可为 null
 * @param refType   关联对象类型，如 AID / POST / USER
 * @param refId     关联对象 ID，跳转链接需要
 * @param actorId   触发者用户 ID，供前端展示头像
 * @param actorName 触发者昵称快照，取不到昵称时兜底
 * @param link      跳转路径（可选，可为 null）。不传时由 notify-service 按 {@code type}
 *                  生成；传了则以它为准。之所以需要这个字段：落点并不只由通知类型决定，
 *                  同样的 POST_COMMENTED，发现频道的帖子在 /discover，校园帖在 /campus，
 *                  而 notify-service 只能看到类型、看不到帖子频道，生成不出正确落点。
 *                  只接受站内相对路径（以 "/" 开头），且长度不超过 300。
 */
public record NotifyCreateCommand(
        Long userId,
        String type,
        String title,
        String content,
        String refType,
        Long refId,
        Long actorId,
        String actorName,
        String link) {

    /**
     * 兼容既有调用方的 8 参构造：{@code link} 是新增的可选字段，
     * 保留这个重载可以让 aid-service、auth-service 等既有调用方一行不改，
     * 继续走 notify-service 按类型生成的兜底路径。
     */
    public NotifyCreateCommand(Long userId,
                               String type,
                               String title,
                               String content,
                               String refType,
                               Long refId,
                               Long actorId,
                               String actorName) {
        this(userId, type, title, content, refType, refId, actorId, actorName, null);
    }
}
