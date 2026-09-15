-- =============================================================
-- aid-service 查询索引
--
-- 覆盖的查询模式（见 AidService）：
--   列表：WHERE board = ? [AND status = ?] [AND category = ?] ORDER BY created_at DESC
--   我的：WHERE publisher_id = ? / helper_id = ? ORDER BY created_at DESC
--   统计：WHERE status = ?
--   当前用户评分批查：WHERE rater_id = ? AND aid_id IN (...)
--
-- 注：u_r_aid_rating 上已有唯一键 (aid_id, rater_id)，
--     其首列 aid_id 已能支撑按 aid_id IN (...) 的批量评分聚合，无需重复建索引。
-- =============================================================

CREATE INDEX idx_aid_board_created ON u_r_aid_request (board, created_at);
CREATE INDEX idx_aid_board_status_created ON u_r_aid_request (board, status, created_at);
CREATE INDEX idx_aid_board_category_created ON u_r_aid_request (board, category, created_at);
CREATE INDEX idx_aid_publisher_created ON u_r_aid_request (publisher_id, created_at);
CREATE INDEX idx_aid_helper_created ON u_r_aid_request (helper_id, created_at);
CREATE INDEX idx_aid_status ON u_r_aid_request (status);
CREATE INDEX idx_aid_rating_rater ON u_r_aid_rating (rater_id, aid_id);
