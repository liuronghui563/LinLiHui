-- =============================================================
-- notify-service 查询索引
--
-- 覆盖的查询模式（见 NotificationService / NotificationRepository）：
--   列表：WHERE user_id = ? ORDER BY created_at DESC
--   未读列表：WHERE user_id = ? AND read_at IS NULL ORDER BY created_at DESC
--   未读角标：SELECT count(*) WHERE user_id = ? AND read_at IS NULL
--   单条操作：WHERE id = ? AND user_id = ?
--
-- 注：不单建 (id) 索引——主键已覆盖；两个复合索引首列都是 user_id，
--     因此按 user_id 过滤时无需再建单列索引。
-- =============================================================

CREATE INDEX idx_notify_user_created ON u_r_notification (user_id, created_at);
CREATE INDEX idx_notify_user_read ON u_r_notification (user_id, read_at);
