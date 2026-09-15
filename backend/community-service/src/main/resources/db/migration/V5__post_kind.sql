-- =============================================================
-- 帖子种类（kind）
--
-- 本迁移是**纯增量**的：只新增列与索引，不改动 channel 枚举、不删除 category、
-- 不重命名任何表。
--
-- 为什么刻意保守：仓库当前处于多人并行开发状态（同一时间段内有 file-service、
-- 图片上传等改动在写入）。删列/改枚举这类破坏性操作一旦与他人改动撞车，
-- 排查成本很高。category 暂时保留为历史字段，代码不再写入，
-- 待仓库稳定后再追加一个迁移把它删掉。
-- =============================================================

ALTER TABLE u_r_post ADD COLUMN kind VARCHAR(30) NULL AFTER category;

-- 历史数据回填：原来只有校园频道有 category，其余频道按生活分享处理
UPDATE u_r_post SET kind = CASE category
    WHEN '吐槽'     THEN 'RANT'
    WHEN '表白'     THEN 'CONFESS'
    WHEN '唠嗑'     THEN 'CHAT'
    WHEN '分享日常' THEN 'DAILY'
    ELSE NULL
END;

UPDATE u_r_post SET kind = 'DAILY' WHERE kind IS NULL AND channel IN ('COMMUNITY', 'PLAZA');
UPDATE u_r_post SET kind = 'CHAT'  WHERE kind IS NULL AND channel = 'CAMPUS';

ALTER TABLE u_r_post MODIFY COLUMN kind VARCHAR(30) NOT NULL;

-- 列表主查询：模块（多频道）+ 种类 + 时间倒序
CREATE INDEX idx_post_channel_kind_created ON u_r_post (channel, kind, created_at);
