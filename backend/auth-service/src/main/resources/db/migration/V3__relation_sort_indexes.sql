-- =============================================================
-- auth-service 排序索引补齐
--
-- 背景：关系列表页默认按 created_at DESC 排序，但两个表都没有
-- 以「过滤列 + created_at」组成的索引，EXPLAIN 实测均为 Using filesort：
--
--   「我关注的人」 WHERE follower_id = ? ORDER BY created_at DESC
--       现有 uk(follower_id, followee_id) 能过滤，但排不了序
--   「黑名单」     WHERE blocker_id  = ? ORDER BY created_at DESC
--       现有 uk(blocker_id, blocked_id) 同理
--
-- 注意「关注我的人」不需要新索引：idx_u_r_user_follow_followee(followee_id, created_at)
-- 已经是 (过滤列, 排序列) 的形状，反向扫描即可。
--
-- 另外补一个 (role, enabled)：内部码登录走 findFirstByRoleAndEnabledTrue，
-- 而 u_r_sys_user 只有主键与手机号唯一键，EXPLAIN 为全表扫描。
-- 该路径只在内网/运维时使用，因此放在同一次迁移里顺手补上。
-- =============================================================

CREATE INDEX idx_follow_follower_created ON u_r_user_follow (follower_id, created_at);
CREATE INDEX idx_block_blocker_created ON u_r_user_block (blocker_id, created_at);
CREATE INDEX idx_user_role_enabled ON u_r_sys_user (role, enabled);
