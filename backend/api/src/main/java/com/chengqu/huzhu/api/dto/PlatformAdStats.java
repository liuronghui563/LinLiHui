package com.chengqu.huzhu.api.dto;

/**
 * 全平台广告统计，由 ad-service 提供，供管理台看板聚合。
 *
 * @param total   广告位总数
 * @param enabled 启用中的广告位数
 * @param clicks  累计点击量
 */
public record PlatformAdStats(
        long total,
        long enabled,
        long clicks) {
}
