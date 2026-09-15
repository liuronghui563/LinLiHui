-- =============================================================
-- 通知跳转路径列：link
--
-- 解决的业务问题：跳转路径此前完全由 NotificationType.link(refId, actorId)
-- 在「读取时」现算，而现算只认通知类型、不认业务上下文。动态被评论 / 被点赞
-- 一律生成 /discover?post={id}，于是校园帖的通知会把人带到发现页，还定位不到那条帖子。
--
-- 设计取舍：
--   * 路径改由最了解业务的一方在「创建时」写入并落库（见 NotifyCreateCommand.link，
--     由 community-service 按帖子的 channel 生成），读取时优先使用；
--     写入方管的是「这条通知该落到哪」，notify-service 只负责存取。
--   * 列可为 NULL：存量通知、以及没有特殊落点的调用方（aid-service、auth-service 等）
--     仍回退到 NotificationType 的现算结果，因此老通知的跳转行为完全不变。
--     这也是本次只加列、不加值、不动 type ENUM 的原因。
--   * 长度 300，与 CreateNotificationRequest.link 的 @Size(max = 300) 保持一致；
--     两处不一致会导致超长值在写入时才报错。
--   * 与实体 Notification.link（@Column(length = 300)）一一对应，
--     ddl-auto=validate 要求两边严格一致。
--
-- 注意：ADD COLUMN 在 MySQL 8 没有 IF NOT EXISTS 写法，本脚本依赖 Flyway
--       只执行一次（独立历史表 flyway_schema_history_notify）。
-- =============================================================

ALTER TABLE u_r_notification
    ADD COLUMN link VARCHAR(300) DEFAULT NULL AFTER actor_name;
