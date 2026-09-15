package com.chengqu.huzhu.community.entity;

/**
 * 圈子成员身份。
 *
 * <p>圈主：踢人、禁言、增设管理员、关闭圈子。
 * 管理员：踢普通成员、下线圈内帖、限制他人发帖。
 */
public enum CircleMemberRole {
    OWNER,
    ADMIN,
    MEMBER
}
