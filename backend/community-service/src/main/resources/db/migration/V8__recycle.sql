-- =============================================================
-- 回收（上门回收预约）表结构
--
-- 解决的业务问题：居民家里攒了纸箱、塑料瓶、旧衣物、旧家电，
-- 走街串巷的回收人员时间不固定、报价不透明，居民只能靠碰运气。
-- 把「品类 + 估重 + 上门时段」做成一张预约单，
-- 既让居民看到可回收品类与计价口径，也给回收端留下可跟单的队列。
--
-- 设计说明：
--   * 与实体 com.chengqu.huzhu.community.entity.RecycleOrder 严格一致
--     （枚举 -> MySQL 原生 ENUM，重量/金额 -> DECIMAL，日期 -> DATE，
--     时间 -> DATETIME(6)）。
--   * appoint_date 用 DATE（只约到天）+ appoint_slot 枚举（上午/下午/晚间），
--     不做精确到分钟的排班，回收端按半天为单位派单即可。
--   * weight_kg 允许为空：居民常常只有「大概一麻袋」的概念，
--     估重留空由上门师傅现场称重；estimated_amount 随之留空。
--   * estimated_amount 是下单时按「估重 × 品类单价」算出的参考金额，
--     只是给居民的预期，不是结算依据。
--   * 使用 IF NOT EXISTS，可安全作用于存量库。
--
-- 示例数据不写在这里，由 RecycleDataInitializer 在空表时写入。
-- =============================================================

CREATE TABLE IF NOT EXISTS u_r_recycle_order (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    user_id          BIGINT        NOT NULL,
    user_name        VARCHAR(50)   NOT NULL,
    category         ENUM('PAPER','PLASTIC','METAL','CLOTHES','APPLIANCE','OTHER') NOT NULL,
    weight_kg        DECIMAL(8,2)  DEFAULT NULL,
    description      VARCHAR(500)  DEFAULT NULL,
    address          VARCHAR(200)  NOT NULL,
    contact_phone    VARCHAR(20)   NOT NULL,
    appoint_date     DATE          NOT NULL,
    appoint_slot     ENUM('MORNING','AFTERNOON','EVENING') NOT NULL,
    status           ENUM('PENDING','CONFIRMED','DONE','CANCELLED') NOT NULL,
    estimated_amount DECIMAL(10,2) DEFAULT NULL,
    remark           VARCHAR(200)  DEFAULT NULL,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    -- 「我的回收订单」：WHERE user_id = ? ORDER BY created_at DESC
    KEY idx_recycle_order_user_created (user_id, created_at),
    -- 回收端派单队列：WHERE status = ? AND appoint_date = ?
    KEY idx_recycle_order_status_date (status, appoint_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上门回收预约订单表';
