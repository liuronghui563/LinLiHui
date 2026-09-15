package com.chengqu.huzhu.api.dto;

/**
 * 用户的跨服务精简视图。
 *
 * <p>由 auth-service 通过 {@code /internal/user/brief} 提供，
 * 供 aid-service、community-service 在列表/详情中补全作者信息，
 * 替代原先硬编码的占位头像与各表冗余的昵称快照。
 *
 * @param id             用户 ID
 * @param nickname       昵称
 * @param avatar         头像地址，未设置时为 null（由前端展示占位图）
 * @param presenceStatus 在线状态枚举名
 * @param student        是否学生
 * @param school         学校
 * @param ratingAvg      平均评分，无评分为 0
 * @param ratingCount    评分人数
 */
public record UserBrief(
        Long id,
        String nickname,
        String avatar,
        String presenceStatus,
        Boolean student,
        String school,
        Double ratingAvg,
        Long ratingCount) {
}
