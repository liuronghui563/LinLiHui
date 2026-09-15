-- =============================================================
-- 广告位资质开通
--
-- 背景：广告位此前所有登录用户都能提交申请（POST /api/ad/applications）。
-- 现在在申请**之前**再加一道闸门：先提交「广告位资质」，管理员审核通过后
-- 才允许提交广告位申请（闸门见 AdService.apply）。
--
-- 为什么不复用 u_r_ad（并进那一行加几个字段）：
--   u_r_ad 的一行是一条**广告**：申请通过后它就是首页轮播的那条数据。
--   而资质是**人的**属性——一个用户只有一份资质，却可以有多条广告，
--   两者基数是 1:N，生命周期也完全不同。合并会出现这类耦合：
--   资质到期/被驳回时，连带把这个人**正在轮播的广告**一起判为无效；
--   反过来运营删掉一条广告，也会顺手抹掉资质。所以单开一张表，
--   用 user_id 与 u_r_ad.applicant_id 同源关联，但各自独立演进。
--
-- 不给 (user_id, status) 加 UNIQUE 的原因：
--   REJECTED 的历史是刻意保留的（用户要能看到上次为什么被驳回、改什么），
--   一旦加唯一键，同一个用户第 2 次被驳回就写不进去了。
--   所以「同一用户至多一条 PENDING」改为由服务层保证
--   （AdQualificationService.submit 提交前先查一次），代价是并发双击在理论上
--   能同时插进两条 PENDING，审核端先来先审兜住；比起「驳回历史写不进去」，
--   这个代价小得多，而且并发双击本来也该由前端防抖挡住。
-- =============================================================

CREATE TABLE IF NOT EXISTS u_r_ad_qualification (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL COMMENT '申请用户；与 u_r_ad.applicant_id 同源',
    applicant_name VARCHAR(50)  NOT NULL COMMENT '联系人姓名',
    contact        VARCHAR(50)  NOT NULL COMMENT '联系电话或微信',
    company        VARCHAR(100) DEFAULT NULL COMMENT '主体名称：个人 / 个体工商户 / 公司',
    category       VARCHAR(100) DEFAULT NULL COMMENT '经营范围或推广品类',
    intro          VARCHAR(500) DEFAULT NULL COMMENT '简介',
    -- 只存本项目对象的相对路径 /api/file/objects/...，从不存外链：第三方图床会失效，
    -- 也无法随本站一起做权限与备份。未上传时为 NULL（资质图是可选的）。
    license_image  VARCHAR(255) DEFAULT NULL COMMENT '资质证明图相对路径',
    status         ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT '审核状态',
    review_note    VARCHAR(200) DEFAULT NULL COMMENT '审核意见（驳回原因/通过备注）',
    reviewed_at    DATETIME(6)  DEFAULT NULL COMMENT '审核时间',
    created_at     DATETIME(6)  NOT NULL COMMENT '提交时间',
    updated_at     DATETIME(6)  DEFAULT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    -- 「这个用户有没有通过的资质 / 有没有待审的」是每次提交广告申请都要判的热路径：
    --   WHERE user_id = ? AND status = ?
    KEY idx_ad_qual_user_status (user_id, status),
    -- 管理端列表：WHERE status = ? ORDER BY created_at ASC（先来先审）
    KEY idx_ad_qual_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
