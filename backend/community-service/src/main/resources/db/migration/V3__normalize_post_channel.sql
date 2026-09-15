-- =============================================================
-- 归一化 u_r_post.channel
--
-- 背景与 u_r_aid_request.board 相同：早期写入存在 channel IS NULL 的记录，
-- 查询层用 "WHERE (channel IS NULL OR channel = ?)" 兜底，
-- OR 条件使 (channel, created_at) 索引无法生效。
--
-- 处理：这些 NULL 记录在原查询里本来就被归入社区频道（COMMUNITY），
--       回填后列表结果与迁移前完全一致，再将列约束为 NOT NULL。
-- =============================================================

UPDATE u_r_post SET channel = 'COMMUNITY' WHERE channel IS NULL;

ALTER TABLE u_r_post MODIFY COLUMN channel ENUM('CAMPUS','COMMUNITY','PLAZA') NOT NULL;
