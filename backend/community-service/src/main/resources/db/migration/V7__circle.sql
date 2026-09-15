-- =============================================================
-- 圈子（兴趣小组）表结构
--
-- 解决的业务问题：按兴趣把邻里组织起来（羽毛球、广场舞、遛娃、养花……），
-- 圈子里沉淀的是「圈内可见」的讨论。动态流是广场式的、彼此平行的，
-- 没有「一个可以持续围观、按兴趣聚集」的容器。
--
-- 为什么用关联表而不是给 u_r_post 加 circle_id：
--   u_r_post 正在被并行开发改动（见 V5__post_kind.sql 的说明），
--   给热点表加列会与他人的迁移撞车；且一条帖子理论上可归入多个圈子，
--   关联表（circle_id, post_id）表达得更准确，也把改动隔离在新表内。
--
-- 设计说明：
--   * 与实体 Circle / CircleMember / CirclePost 严格一致
--     （枚举 -> MySQL 原生 ENUM，时间 -> DATETIME(6)）。
--   * member_count / post_count 是计数快照，由 CircleService 在加入、
--     退出、收录帖子时同步维护，列表页不必对关联表做 count。
--   * owner_name 是冗余快照，Feign 取不到用户信息时兜底渲染。
--   * 使用 IF NOT EXISTS，可安全作用于存量库。
--
-- 示例数据不写在这里，由 CircleDataInitializer 在空表时写入。
-- =============================================================

CREATE TABLE IF NOT EXISTS u_r_circle (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    name         VARCHAR(50) NOT NULL,
    description  VARCHAR(200) DEFAULT NULL,
    cover        VARCHAR(500) DEFAULT NULL,
    owner_id     BIGINT      NOT NULL,
    owner_name   VARCHAR(50) NOT NULL,
    member_count INT         NOT NULL DEFAULT 0,
    post_count   INT         NOT NULL DEFAULT 0,
    status       ENUM('ACTIVE','CLOSED') NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- 圈子名是用户识别圈子的唯一标识，重名会让「加入哪个」无法判断
    UNIQUE KEY uk_u_r_circle_name (name),
    -- 列表按创建时间倒序：ORDER BY created_at DESC
    KEY idx_circle_status_created (status, created_at),
    -- 我管理的圈子：WHERE owner_id = ?
    KEY idx_circle_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='兴趣圈子主表';

CREATE TABLE IF NOT EXISTS u_r_circle_member (
    id        BIGINT      NOT NULL AUTO_INCREMENT,
    circle_id BIGINT      NOT NULL,
    user_id   BIGINT      NOT NULL,
    role      ENUM('OWNER','MEMBER') NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- 同一用户在同一圈子只能有一条成员记录（重复加入靠它兜底）
    UNIQUE KEY uk_u_r_circle_member (circle_id, user_id),
    -- 「我加入的圈子」：WHERE user_id = ? ORDER BY joined_at DESC
    KEY idx_circle_member_user (user_id, joined_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='圈子成员表：谁加入了哪个圈子、是圈主还是普通成员';

CREATE TABLE IF NOT EXISTS u_r_circle_post (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    circle_id  BIGINT      NOT NULL,
    post_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- 同一条帖子不能重复收录进同一个圈子
    UNIQUE KEY uk_u_r_circle_post (circle_id, post_id),
    -- 圈内帖子分页：WHERE circle_id = ? ORDER BY created_at DESC
    KEY idx_circle_post_circle_created (circle_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='圈子帖子关联表：把已有动态收录进圈子，避免改动 u_r_post';
