package com.chengqu.huzhu.community.entity;

import com.chengqu.huzhu.common.exception.BizException;

import java.util.List;

/**
 * 帖子所属的**模块**（对应五大模块中的内容型模块）。
 *
 * <p>为什么在 {@link PostChannel} 之外再引入一层：频道是数据存储维度
 * （COMMUNITY / PLAZA / CAMPUS），而模块是产品维度。五大模块重构后，
 * 「发现」模块 = 原邻里动态(COMMUNITY) + 生活广场(PLAZA) 两个频道的内容，
 * 但广场的历史数据仍是 PLAZA。
 *
 * <p>直接改枚举值需要迁移数据、改热度榜逻辑、重命名一整条类链，在多人并行开发时
 * 回归风险明显偏高。这里选择在**查询层**把「发现」表达为多频道集合，
 * 数据不动、枚举不动，代价只是多一层映射。
 */
public enum PostModule {

    DISCOVER(List.of(PostChannel.COMMUNITY, PostChannel.PLAZA)),
    CAMPUS(List.of(PostChannel.CAMPUS));

    private final List<PostChannel> channels;

    PostModule(List<PostChannel> channels) {
        this.channels = channels;
    }

    public List<PostChannel> channels() {
        return channels;
    }

    public boolean contains(PostChannel channel) {
        return channels.contains(channel);
    }

    /**
     * 解析客户端传入的模块名。
     * 兼容两种写法：模块名（DISCOVER / CAMPUS）与具体频道名（COMMUNITY / PLAZA）。
     */
    public static PostModule parse(String value, PostModule fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toUpperCase();
        for (PostModule module : values()) {
            if (module.name().equals(normalized)) {
                return module;
            }
        }
        for (PostChannel channel : PostChannel.values()) {
            if (channel.name().equals(normalized)) {
                return of(channel);
            }
        }
        return fallback;
    }

    public static PostModule of(PostChannel channel) {
        return channel == PostChannel.CAMPUS ? CAMPUS : DISCOVER;
    }

    /** 严格解析，用于写操作：非法模块直接拒绝，而不是静默回退。 */
    public static PostModule require(String value, PostModule fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        PostModule module = parse(value, null);
        if (module == null) {
            throw new BizException("模块无效");
        }
        return module;
    }
}
