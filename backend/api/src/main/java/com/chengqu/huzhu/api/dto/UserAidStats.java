package com.chengqu.huzhu.api.dto;

/**
 * 单个用户在求助域的统计，由 aid-service 提供。
 *
 * @param userId         用户 ID
 * @param publishedCount 我发布的求助总数
 * @param helpingCount   我接单帮助的求助总数
 * @param doneCount      我发布的求助中已完成的数量
 */
public record UserAidStats(
        Long userId,
        long publishedCount,
        long helpingCount,
        long doneCount) {
}
