-- =============================================================
-- community-service 圈子列表排序索引
--
-- 背景：/api/circle/list 走 findAll(pageable)，排序是 createdAt DESC，没有任何过滤条件，
-- 而 u_r_circle 上只有 PRIMARY、uk_name、idx_circle_owner、idx_circle_status_created——
-- 没有以 created_at 打头的索引，EXPLAIN 实测 type=ALL + Using filesort。
--
-- idx_circle_status_created(status, created_at) 用不上：它首列是 status，
-- 而圈子列表**没有按状态过滤的功能**（CircleRepository 里没有任何 status 条件），
-- 该索引目前不服务任何查询。这里保留它不动（删除索引属于独立的一次清理，
-- 且可能被管理端或后续功能使用），只补上真正需要的这个。
-- =============================================================

CREATE INDEX idx_circle_created ON u_r_circle (created_at);
