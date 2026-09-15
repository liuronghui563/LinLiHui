package com.chengqu.huzhu.api.dto;

/**
 * 单个用户在社区域的统计，由 community-service 提供。
 *
 * @param userId    用户 ID
 * @param postCount 我发布的动态总数
 */
public record UserPostStats(
        Long userId,
        long postCount) {
}
