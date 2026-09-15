package com.chengqu.huzhu.api.dto;

/**
 * 全平台动态统计，由 community-service 提供，供管理台看板聚合。
 *
 * @param total        动态总数
 * @param commentTotal 评论总数
 * @param likeTotal    点赞总数
 */
public record PlatformPostStats(
        long total,
        long commentTotal,
        long likeTotal) {
}
