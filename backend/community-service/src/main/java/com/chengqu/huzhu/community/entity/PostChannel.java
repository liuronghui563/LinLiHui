package com.chengqu.huzhu.community.entity;

/**
 * 帖子所属频道（数据存储维度）。
 *
 * <p>注意与 {@link PostModule} 的区别：频道是「存在哪」，模块是「在哪个 Tab 展示」。
 * 「发现」模块由 {@code COMMUNITY + PLAZA} 两个频道组成，见 {@link PostModule#DISCOVER}。
 */
public enum PostChannel {
    COMMUNITY,
    PLAZA,
    CAMPUS
}
