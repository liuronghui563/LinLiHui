-- =============================================================
-- auth-service 初始表结构：用户域
-- 库：chengqu_huzhu
--
-- 说明：
--   1. 本脚本与 Hibernate 实体映射保持严格一致，供 ddl-auto=validate 校验通过。
--   2. 枚举字段映射为 MySQL 原生 ENUM；布尔字段映射为 BIT(1)；时间字段为 DATETIME(6)。
--      旧版 backend/schema.sql 写作 VARCHAR(20)/DATETIME，与实体实际映射不符，已不再作为建表依据。
--   3. 全部使用 IF NOT EXISTS，可安全作用于已经由 Hibernate 建过表的存量库。
-- =============================================================

CREATE TABLE IF NOT EXISTS u_r_sys_user (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    phone           VARCHAR(20)  NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    nickname        VARCHAR(50)  DEFAULT NULL,
    real_name       VARCHAR(30)  DEFAULT NULL,
    avatar          VARCHAR(255) DEFAULT NULL,
    bio             VARCHAR(200) DEFAULT NULL,
    presence_status ENUM('AWAY','BUSY','OFFLINE','ONLINE','STUDYING') DEFAULT NULL,
    gender          ENUM('FEMALE','MALE','UNKNOWN') DEFAULT NULL,
    city            VARCHAR(50)  DEFAULT NULL,
    neighborhood    VARCHAR(80)  DEFAULT NULL,
    school          VARCHAR(80)  DEFAULT NULL,
    major           VARCHAR(80)  DEFAULT NULL,
    grade           VARCHAR(30)  DEFAULT NULL,
    student         BIT(1)       DEFAULT NULL,
    private_account BIT(1)       DEFAULT NULL,
    wechat          VARCHAR(50)  DEFAULT NULL,
    role            ENUM('ADMIN','USER') NOT NULL,
    enabled         BIT(1)       NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_u_r_sys_user_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS u_r_user_rating (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    target_user_id BIGINT NOT NULL,
    rater_id       BIGINT NOT NULL,
    score          INT    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_u_r_user_rating (target_user_id, rater_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
