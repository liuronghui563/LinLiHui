-- =============================================================
-- notify-service 未读列表排序索引
--
-- 背景：未读列表查询是
--   WHERE user_id = ? AND read_at IS NULL ORDER BY created_at DESC
-- 现有 idx_notify_user_read(user_id, read_at) 能过滤，但排不了序，
-- EXPLAIN 实测为 Using filesort。
--
-- 把 created_at 追加为第三列后，这个索引同时服务两个查询：
--   未读角标 COUNT(*) WHERE user_id = ? AND read_at IS NULL   （前两列即可）
--   未读列表 ORDER BY created_at DESC                          （三列全覆盖）
-- 因此直接替换原索引，而不是再加一个——两个索引前缀重复只会增加写入成本。
-- =============================================================

DROP INDEX idx_notify_user_read ON u_r_notification;
CREATE INDEX idx_notify_user_read ON u_r_notification (user_id, read_at, created_at);
