-- =============================================================
-- ad-service 初始表结构：广告位
-- 库：chengqu_huzhu
--
-- 说明：与 Hibernate 实体映射严格一致（布尔 -> BIT(1)，时间 -> DATETIME(6)）。
--       全部使用 IF NOT EXISTS，可安全作用于存量库。
-- =============================================================

CREATE TABLE IF NOT EXISTS u_r_ad (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    title       VARCHAR(100) NOT NULL,
    subtitle    VARCHAR(200) DEFAULT NULL,
    image_url   VARCHAR(500) NOT NULL,
    link_url    VARCHAR(500) DEFAULT NULL,
    sort_order  INT          NOT NULL,
    enabled     BIT(1)       NOT NULL,
    click_count BIGINT       NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
