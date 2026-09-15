-- =============================================================
-- aid-service 初始表结构：求助域
-- 库：chengqu_huzhu
--
-- 说明：与 Hibernate 实体映射严格一致（枚举 -> MySQL ENUM，时间 -> DATETIME(6)）。
--       全部使用 IF NOT EXISTS，可安全作用于存量库。
-- =============================================================

CREATE TABLE IF NOT EXISTS u_r_aid_request (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    title          VARCHAR(100)  NOT NULL,
    content        VARCHAR(2000) NOT NULL,
    category       VARCHAR(50)   NOT NULL,
    board          ENUM('CAMPUS','NEIGHBORHOOD') DEFAULT NULL,
    address        VARCHAR(200)  NOT NULL,
    status         ENUM('ACCEPTED','CANCELLED','DONE','OPEN') NOT NULL,
    publisher_id   BIGINT        NOT NULL,
    publisher_name VARCHAR(50)   NOT NULL,
    helper_id      BIGINT        DEFAULT NULL,
    helper_name    VARCHAR(50)   DEFAULT NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS u_r_aid_rating (
    id       BIGINT NOT NULL AUTO_INCREMENT,
    aid_id   BIGINT NOT NULL,
    rater_id BIGINT NOT NULL,
    score    INT    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_u_r_aid_rating (aid_id, rater_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS u_r_aid_helper_review (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    aid_id       BIGINT       NOT NULL,
    publisher_id BIGINT       NOT NULL,
    helper_id    BIGINT       NOT NULL,
    score        INT          NOT NULL,
    content      VARCHAR(200) DEFAULT NULL,
    created_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_u_r_aid_helper_review (aid_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
