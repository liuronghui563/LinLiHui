-- =============================================================
-- community-service 查询索引
--
-- 覆盖的查询模式（见 CommunityService / PlazaHotService）：
--   列表：WHERE channel = ? [AND category = ?] ORDER BY created_at DESC
--   作者：WHERE author_id = ? ORDER BY created_at DESC
--   热度榜补位：WHERE channel = 'PLAZA' ORDER BY like_count DESC, id DESC
--   评论：WHERE post_id = ? ORDER BY created_at ASC
--   批量评论统计：WHERE post_id IN (...)
--   热度榜：WHERE created_at >= ?（广场评论数）
--   批量点赞查询：WHERE user_id = ? AND post_id IN (...)
--
-- 注：u_r_post_like / u_r_post_view 上已有唯一键 (post_id, user_id)，
--     可支撑按 post_id 的查询，但无法支撑按 user_id 过滤，
--     因此额外建立 (user_id, post_id) 索引供列表批量点赞状态查询使用。
-- =============================================================

CREATE INDEX idx_post_channel_created ON u_r_post (channel, created_at);
CREATE INDEX idx_post_channel_category_created ON u_r_post (channel, category, created_at);
CREATE INDEX idx_post_author_created ON u_r_post (author_id, created_at);
CREATE INDEX idx_post_channel_like ON u_r_post (channel, like_count);

CREATE INDEX idx_comment_post_created ON u_r_post_comment (post_id, created_at);
CREATE INDEX idx_comment_created ON u_r_post_comment (created_at);

CREATE INDEX idx_like_user_post ON u_r_post_like (user_id, post_id);
