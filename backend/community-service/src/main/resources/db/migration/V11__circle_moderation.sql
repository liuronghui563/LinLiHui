-- =============================================================
-- 圈子管理：管理员角色 + 禁言
--
-- 圈主可以：踢人、禁言、增设管理员。
-- 圈子管理员可以：踢普通成员、下线圈内帖、限制他人发帖（禁言）。
-- muted 为成员级开关，被禁言后仍留在圈内，但不能在本圈发帖。
-- =============================================================

ALTER TABLE u_r_circle_member
    MODIFY COLUMN role ENUM('OWNER','ADMIN','MEMBER') NOT NULL;

ALTER TABLE u_r_circle_member
    ADD COLUMN muted BIT(1) NOT NULL DEFAULT 0 AFTER role;
