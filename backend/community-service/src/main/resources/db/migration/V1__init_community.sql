-- =============================================================
-- community-service 初始表结构：社区域
-- 库：chengqu_huzhu
--
-- 说明：与 Hibernate 实体映射严格一致（枚举 -> MySQL ENUM，时间 -> DATETIME(6)）。
--       全部使用 IF NOT EXISTS，可安全作用于存量库。
-- =============================================================

CREATE TABLE IF NOT EXISTS u_r_post (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    content     VARCHAR(1000) NOT NULL,
    author_id   BIGINT        NOT NULL,
    author_name VARCHAR(50)   NOT NULL,
    like_count  INT           NOT NULL,
    heat_score  INT           NOT NULL,
    view_count  INT           NOT NULL,
    category    VARCHAR(50)   DEFAULT NULL,
    channel     ENUM('CAMPUS','COMMUNITY','PLAZA') DEFAULT NULL,
    created_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS u_r_post_comment (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    post_id     BIGINT       NOT NULL,
    author_id   BIGINT       NOT NULL,
    author_name VARCHAR(50)  NOT NULL,
    content     VARCHAR(500) NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS u_r_post_like (
    id      BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_u_r_post_user (post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS u_r_post_view (
    id      BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_u_r_post_view_user (post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS u_r_plaza_hot (
    id            BIGINT NOT NULL AUTO_INCREMENT,
    rank_date     DATE   NOT NULL,
    rank_no       INT    NOT NULL,
    post_id       BIGINT NOT NULL,
    comment_count BIGINT NOT NULL,
    like_count    INT    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_u_r_plaza_hot_date_rank (rank_date, rank_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
