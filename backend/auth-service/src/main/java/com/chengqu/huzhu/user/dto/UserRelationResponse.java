package com.chengqu.huzhu.user.dto;

/**
 * 当前登录用户与某个目标用户之间的关系状态，供个人主页渲染按钮。
 *
 * @param following    我是否关注了对方
 * @param followedBy   对方是否关注了我
 * @param blocked      我是否拉黑了对方
 * @param blockedBy    对方是否拉黑了我
 * @param followerCount 对方粉丝数
 * @param followingCount 对方关注数
 */
public record UserRelationResponse(
        boolean following,
        boolean followedBy,
        boolean blocked,
        boolean blockedBy,
        long followerCount,
        long followingCount) {
}
