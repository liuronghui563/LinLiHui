package com.chengqu.huzhu.api.dto;

/**
 * 全平台求助统计，由 aid-service 提供，供管理台看板聚合。
 *
 * @param total    求助总数
 * @param open     待接单数量
 * @param accepted 进行中数量
 * @param done     已完成数量
 */
public record PlatformAidStats(
        long total,
        long open,
        long accepted,
        long done) {
}
