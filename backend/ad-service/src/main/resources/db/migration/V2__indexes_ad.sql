-- =============================================================
-- ad-service 查询索引
--
-- 覆盖的查询模式（见 AdService / AdBannerRepository）：
--   轮播：WHERE enabled = true ORDER BY sort_order ASC, id DESC
--   全部：ORDER BY sort_order ASC, id DESC
-- =============================================================

CREATE INDEX idx_ad_enabled_sort ON u_r_ad (enabled, sort_order, id);
CREATE INDEX idx_ad_sort ON u_r_ad (sort_order, id);
