-- =============================================================
-- 广告位申请与审核
--
-- 背景：广告位此前只有管理员能创建（POST /api/ad 直接就是 ADMIN）。
-- 现在开放「用户申请 → 管理员审核 → 通过后进首页轮播」这条链路。
--
-- 为什么不新建一张申请表：
--   申请通过后就是一条轮播广告，字段完全一致。两张表意味着通过时要复制数据、
--   之后改图要同步两边，还会出现「申请表说已通过、广告表里却没有」的不一致。
--   所以直接在 u_r_ad 上加状态列：**一条广告从申请到上线是同一行**。
--
-- status 默认 APPROVED，而不是 PENDING：存量数据都是管理员直接创建的，
-- 它们本来就在轮播里；默认 PENDING 会让线上广告在迁移后集体消失。
-- =============================================================

ALTER TABLE u_r_ad
    ADD COLUMN status       ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'APPROVED' COMMENT '审核状态：管理员直接创建的一律 APPROVED' AFTER enabled,
    ADD COLUMN applicant_id BIGINT       DEFAULT NULL COMMENT '申请提交人；管理员直接创建时为 NULL' AFTER status,
    ADD COLUMN review_note  VARCHAR(200) DEFAULT NULL COMMENT '审核意见（驳回原因/通过备注）' AFTER applicant_id,
    ADD COLUMN reviewed_at  DATETIME(6)  DEFAULT NULL COMMENT '审核时间' AFTER review_note;

-- 轮播查询：WHERE status = 'APPROVED' AND enabled = 1 ORDER BY sort_order, id DESC
CREATE INDEX idx_ad_status_enabled ON u_r_ad (status, enabled, sort_order);

-- 「我的申请」：WHERE applicant_id = ? ORDER BY created_at DESC
CREATE INDEX idx_ad_applicant ON u_r_ad (applicant_id, created_at);

-- 管理端待审列表：WHERE status = ? ORDER BY created_at
CREATE INDEX idx_ad_status_created ON u_r_ad (status, created_at);
