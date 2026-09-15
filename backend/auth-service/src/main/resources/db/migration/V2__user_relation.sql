-- =============================================================
-- 关注与拉黑
--
-- 两张关系表放在 auth-service（用户域），因为它们是用户之间的关系，
-- 与内容无关；内容服务通过内部接口取「需要屏蔽的作者集合」来做信息流过滤。
--
-- 为什么拉黑要做成双向屏蔽：
--   只屏蔽「我拉黑的人」会让被拉黑方继续看到我的内容并在我下面评论，
--   起不到隔离作用。因此过滤时取「我拉黑的 ∪ 拉黑我的」。
-- =============================================================

CREATE TABLE IF NOT EXISTS u_r_user_follow (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    follower_id BIGINT      NOT NULL COMMENT '关注发起方',
    followee_id BIGINT      NOT NULL COMMENT '被关注方',
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_u_r_user_follow (follower_id, followee_id),
    -- 「我的粉丝」列表按 followee 查询
    KEY idx_u_r_user_follow_followee (followee_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS u_r_user_block (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    blocker_id BIGINT      NOT NULL COMMENT '发起拉黑的人',
    blocked_id BIGINT      NOT NULL COMMENT '被拉黑的人',
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_u_r_user_block (blocker_id, blocked_id),
    -- 反向查询「谁拉黑了我」，用于双向屏蔽
    KEY idx_u_r_user_block_blocked (blocked_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
