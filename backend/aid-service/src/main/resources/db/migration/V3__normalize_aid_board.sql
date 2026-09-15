-- =============================================================
-- 归一化 u_r_aid_request.board
--
-- 背景：
--   早期写入的求助存在 board IS NULL 的记录。查询层为此写成
--   "WHERE (board IS NULL OR board = ?)" 兜底，而 OR 条件会导致
--   (board, created_at) 索引无法被使用，列表分页退化为全表扫描 + filesort。
--
-- 处理：
--   这些 NULL 记录在原查询里本来就被归入邻里板块（NEIGHBORHOOD），
--   因此回填为 'NEIGHBORHOOD' 后列表结果与迁移前完全一致，
--   随后将列约束为 NOT NULL，查询即可简化为 board = ? 并使用索引。
--
-- 幂等性：UPDATE 对无 NULL 的表不产生任何修改；
--         MODIFY 对已经是 NOT NULL 的列重复执行也安全。
-- =============================================================

UPDATE u_r_aid_request SET board = 'NEIGHBORHOOD' WHERE board IS NULL;

ALTER TABLE u_r_aid_request MODIFY COLUMN board ENUM('CAMPUS','NEIGHBORHOOD') NOT NULL;
