package com.chengqu.huzhu.community.entity;

/**
 * 圈子状态。
 *
 * <p>关闭而不是删除：圈子里的帖子关联是邻里共同积累的内容，
 * 圈主失去维护意愿时应该「关门」而不是把别人的讨论一起抹掉。
 */
public enum CircleStatus {
    ACTIVE,
    CLOSED
}
