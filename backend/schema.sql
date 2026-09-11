-- 邻里汇 · 业务表（前缀 u_r_ 对应需求中的 u-r）
-- 库名：chengqu_huzhu

CREATE TABLE IF NOT EXISTS u_r_sys_user (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    phone         VARCHAR(20)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(50),
    real_name     VARCHAR(30),
    avatar        VARCHAR(255),
    bio           VARCHAR(200),
    presence_status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    gender        VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN',
    city          VARCHAR(50),
    neighborhood  VARCHAR(80),
    school        VARCHAR(80),
    major         VARCHAR(80),
    grade         VARCHAR(30),
    student       TINYINT(1)   NOT NULL DEFAULT 0,
    private_account TINYINT(1) NOT NULL DEFAULT 0,
    wechat        VARCHAR(50),
    role          VARCHAR(20)  NOT NULL,
    enabled       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME,
    UNIQUE KEY uk_u_r_sys_user_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS u_r_aid_request (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    title          VARCHAR(100)  NOT NULL,
    content        VARCHAR(2000) NOT NULL,
    category       VARCHAR(50)   NOT NULL,
    board          VARCHAR(20)   NOT NULL DEFAULT 'NEIGHBORHOOD',
    address        VARCHAR(200)  NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    publisher_id   BIGINT        NOT NULL,
    publisher_name VARCHAR(50)   NOT NULL,
    helper_id      BIGINT,
    helper_name    VARCHAR(50),
    created_at     DATETIME      NOT NULL,
    updated_at     DATETIME      NOT NULL,
    KEY idx_u_r_aid_status (status),
    KEY idx_u_r_aid_board (board),
    KEY idx_u_r_aid_publisher (publisher_id),
    KEY idx_u_r_aid_helper (helper_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS u_r_post (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    content     VARCHAR(1000) NOT NULL,
    author_id   BIGINT        NOT NULL,
    author_name VARCHAR(50)   NOT NULL,
    like_count  INT           NOT NULL DEFAULT 0,
    heat_score  INT           NOT NULL DEFAULT 0,
    view_count  INT           NOT NULL DEFAULT 0,
    category    VARCHAR(50),
    channel     VARCHAR(20)   NOT NULL DEFAULT 'COMMUNITY',
    created_at  DATETIME      NOT NULL,
    KEY idx_u_r_post_author (author_id),
    KEY idx_u_r_post_channel (channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS u_r_post_view (
    id      BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    UNIQUE KEY uk_u_r_post_view_user (post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS u_r_post_like (
    id      BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    UNIQUE KEY uk_u_r_post_user (post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS u_r_post_comment (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id     BIGINT NOT NULL,
    author_id   BIGINT NOT NULL,
    author_name VARCHAR(50) NOT NULL,
    content     VARCHAR(500) NOT NULL,
    created_at  DATETIME NOT NULL,
    KEY idx_u_r_post_comment_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS u_r_user_rating (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    target_user_id BIGINT NOT NULL,
    rater_id       BIGINT NOT NULL,
    score          INT    NOT NULL,
    UNIQUE KEY uk_u_r_user_rating (target_user_id, rater_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS u_r_aid_rating (
    id       BIGINT PRIMARY KEY AUTO_INCREMENT,
    aid_id   BIGINT NOT NULL,
    rater_id BIGINT NOT NULL,
    score    INT    NOT NULL,
    UNIQUE KEY uk_u_r_aid_rating (aid_id, rater_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS u_r_plaza_hot (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    rank_date     DATE    NOT NULL,
    rank_no       INT     NOT NULL,
    post_id       BIGINT  NOT NULL,
    comment_count BIGINT  NOT NULL DEFAULT 0,
    like_count    INT     NOT NULL DEFAULT 0,
    UNIQUE KEY uk_u_r_plaza_hot_date_rank (rank_date, rank_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS u_r_aid_helper_review (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    aid_id       BIGINT       NOT NULL,
    publisher_id BIGINT       NOT NULL,
    helper_id    BIGINT       NOT NULL,
    score        INT          NOT NULL,
    content      VARCHAR(200),
    created_at   DATETIME     NOT NULL,
    UNIQUE KEY uk_u_r_aid_helper_review (aid_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS u_r_ad (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    title       VARCHAR(100) NOT NULL,
    subtitle    VARCHAR(200),
    image_url   VARCHAR(500) NOT NULL,
    link_url    VARCHAR(500),
    sort_order  INT          NOT NULL DEFAULT 0,
    enabled     TINYINT(1)   NOT NULL DEFAULT 1,
    click_count BIGINT       NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME,
    KEY idx_u_r_ad_enabled_sort (enabled, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
