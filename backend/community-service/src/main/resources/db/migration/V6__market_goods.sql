-- =============================================================
-- 集市（二手闲置）表结构
--
-- 解决的业务问题：邻里之间把用不上的东西转给需要的人。
-- 此前「闲置转让」只能作为一条动态（PostKind.MARKET）发出来，
-- 价格、成色、是否已售出都塞在正文里，既无法筛选（按分类/状态）、
-- 也无法标记「已售出」，卖家会被反复询问同一件已经卖掉的东西。
--
-- 设计说明：
--   * 与 Hibernate 实体 com.chengqu.huzhu.community.entity.Goods 严格一致
--     （枚举 -> MySQL 原生 ENUM，金额 -> DECIMAL(10,2)，时间 -> DATETIME(6)）。
--   * images 与 u_r_post.images 同款：TEXT 列 + StringListJsonConverter，
--     数量很少，不为此单独建附表。
--   * seller_name 是冗余快照，用于 Feign 取不到用户信息时兜底渲染。
--   * 使用 IF NOT EXISTS，可安全作用于存量库。
--
-- 示例数据不写在这里，而是由 MarketDataInitializer（CommandLineRunner）
-- 在空表时写入，与本服务既有的 CommunityDataInitializer 保持一致。
-- =============================================================

CREATE TABLE IF NOT EXISTS u_r_goods (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    title          VARCHAR(100)  NOT NULL,
    description    VARCHAR(1000) DEFAULT NULL,
    price          DECIMAL(10,2) NOT NULL,
    original_price DECIMAL(10,2) DEFAULT NULL,
    category       VARCHAR(50)   DEFAULT NULL,
    images         TEXT          NULL,
    seller_id      BIGINT        NOT NULL,
    seller_name    VARCHAR(50)   NOT NULL,
    status         ENUM('ON_SALE','RESERVED','SOLD','OFF') NOT NULL,
    view_count     INT           NOT NULL DEFAULT 0,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    -- 集市首页：WHERE status = ? ORDER BY created_at DESC
    KEY idx_goods_status_created (status, created_at),
    -- 「我发布的」：WHERE seller_id = ? ORDER BY created_at DESC
    KEY idx_goods_seller_created (seller_id, created_at),
    -- 分类筛选：WHERE category = ? AND status = ?
    KEY idx_goods_category_status (category, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='集市二手闲置商品表';
