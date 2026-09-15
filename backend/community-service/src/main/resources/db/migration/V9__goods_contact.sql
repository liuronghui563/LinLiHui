-- =============================================================
-- 集市商品联系方式
--
-- 解决的业务问题：u_r_goods 没有联系方式列，全站也没有私信功能，
-- 于是买家在集市里看到想买的东西**根本联系不上卖家**，
-- 只能点「去主页联系」到一个没有任何联系方式的个人主页。
--
-- 设计取舍：
--   * contact 与 contact_type 都可为空：联系方式是卖家自愿公开的信息，
--     不能强制填写（老商品也没有这个字段），为空时前端只展示卖家昵称。
--   * contact_type 用 MySQL 原生 ENUM，与 GoodsContactType 保持一致；
--     类型决定前端怎么渲染（电话可拨打、微信号只能复制）。
--   * contact 用 VARCHAR(100) 而不是分列存手机号/微信号：
--     微信号并不总是数字，QQ 也可能是邮箱，统一按文本存更稳。
--   * 不建索引：联系方式不参与任何查询、筛选与排序，
--     它只会出现在详情接口的响应里（列表接口刻意不返回，防止被抓取囤积）。
--   * 两个列都追加在表尾、不写 AFTER：列顺序纯属观感，
--     而多子句 ALTER 中 AFTER 同语句新增列的写法容易出错，得不偿失。
--
-- 注意：ADD COLUMN 在 MySQL 8 没有 IF NOT EXISTS 写法，本脚本依赖 Flyway
--       只执行一次（独立历史表 flyway_schema_history_community）。
--       与实体 Goods 的 contactType（@Enumerated(STRING), length = 20）一一对应，
--       ddl-auto=validate 要求两边严格一致。
-- =============================================================

ALTER TABLE u_r_goods
    ADD COLUMN contact      VARCHAR(100) DEFAULT NULL,
    ADD COLUMN contact_type ENUM('WECHAT','PHONE','QQ','OTHER') DEFAULT NULL;
