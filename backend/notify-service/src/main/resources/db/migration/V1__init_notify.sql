-- =============================================================
-- notify-service 初始表结构：站内消息通知
-- 库：chengqu_huzhu
--
-- 说明：与 Hibernate 实体映射严格一致（枚举 -> MySQL ENUM，时间 -> DATETIME(6)）。
--       全部使用 IF NOT EXISTS，可安全作用于存量库。
--
-- 设计取舍：
--   * type 用 MySQL ENUM 而不是 VARCHAR——通知类型是稳定的有限集合，
--     新增类型必须显式写迁移脚本（与本项目 board / channel 的约定一致）。
--   * actor_name 是「触发者昵称快照」，只作为取不到昵称时的兜底展示；
--     真实昵称/头像仍由前端按 actor_id 查询，避免昵称改一次要刷全表历史消息。
--   * read_at 为 NULL 表示未读：用「时间点」而不是布尔位，可以顺带回答
--     「什么时候读的」，也不需要额外的已读时间列。
-- =============================================================

CREATE TABLE IF NOT EXISTS u_r_notification (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    type       ENUM('AID_ACCEPTED','AID_COMPLETED','AID_RATED','POST_COMMENTED','POST_LIKED','FOLLOWED','SYSTEM') NOT NULL,
    title      VARCHAR(100) NOT NULL,
    content    VARCHAR(500) DEFAULT NULL,
    ref_type   VARCHAR(30)  DEFAULT NULL,
    ref_id     BIGINT       DEFAULT NULL,
    actor_id   BIGINT       DEFAULT NULL,
    actor_name VARCHAR(50)  DEFAULT NULL,
    read_at    DATETIME(6)  DEFAULT NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
