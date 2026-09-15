-- =============================================================
-- 学生认证（提交 → 管理员审核）
--
-- 背景：此前的「在校学生」是用户在个人资料里自己勾的一个复选框
-- （UpdateProfileRequest.student），没有任何凭证，也没有人核对。
-- 校园模块却据此放行，等于谁都可以自称学生。这里改成一条审核链路：
-- 用户提交学校 / 学号 / 学生证照片 → 管理员审核 → 通过后 user.student 才为 true。
--
-- 为什么单独建表，而不是像广告那样在 u_r_sys_user 上加几列：
--   这是**多条累积的历史记录**（一个人会被驳回很多次），而用户资料一行一人。
--   塞进 u_r_sys_user 只能保留最后一次，驳回原因与历史无从查起；
--   而且申请表要存学号、证件照这些与资料无关的字段，混在一起会让用户表不断变宽。
--
-- 两个刻意的取舍：
--
-- 1) (user_id, status) **不加 UNIQUE**。同一用户会累积多条 REJECTED 历史，
--    唯一约束会让「第二次被驳回」直接写不进去（或把前一条覆盖掉）。
--    「同一用户至多一条 PENDING」改由服务层保证
--    （StudentVerificationService.submit 先查后写）。
--    代价是极端并发下可能写出两条 PENDING；审核是人工低频操作，
--    为此加锁或加唯一约束得不偿失。
--
-- 2) 存量 student = 1 一律重置为 0。旧的 student 是用户自己勾的、
--    没有任何凭证，不能作为进入校园模块的凭据。认证通过时
--    （StudentVerificationService.approve）会重新写入 true。
--    这是**不可逆**的一步：迁移后所有人都需要走一次认证，
--    但保留旧值等于让这条链路从第一天起就形同虚设。
-- =============================================================

CREATE TABLE IF NOT EXISTS u_r_student_verification (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL COMMENT '申请人',
    real_name   VARCHAR(30)  NOT NULL COMMENT '真实姓名',
    school      VARCHAR(80)  NOT NULL COMMENT '学校',
    major       VARCHAR(80)  DEFAULT NULL COMMENT '专业（选填）',
    grade       VARCHAR(30)  DEFAULT NULL COMMENT '年级（选填）',
    student_no  VARCHAR(30)  NOT NULL COMMENT '学号',
    -- 与其他图片同一套规矩：只存相对路径 /api/file/objects/...，不收外链
    proof_image VARCHAR(255) DEFAULT NULL COMMENT '学生证/校园卡照片（相对路径）',
    status      ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT '审核状态',
    review_note VARCHAR(200) DEFAULT NULL COMMENT '审核意见（驳回原因）',
    reviewed_at DATETIME(6)  DEFAULT NULL COMMENT '审核时间',
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (id),
    -- 「我的认证状态」：按 user_id 取最新一条，并判断是否已有 PENDING
    KEY idx_u_r_student_verification_user (user_id, status),
    -- 管理端列表：WHERE status = ? ORDER BY created_at（先来先审）
    KEY idx_u_r_student_verification_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

UPDATE u_r_sys_user SET student = 0 WHERE student = 1;
