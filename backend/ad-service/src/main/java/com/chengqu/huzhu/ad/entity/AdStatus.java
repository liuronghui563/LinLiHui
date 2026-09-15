package com.chengqu.huzhu.ad.entity;

/**
 * 广告位审核状态。
 *
 * <p>一条广告从「用户申请」到「首页轮播」是同一行的状态流转：
 * {@code PENDING → APPROVED}（通过，进入轮播）或 {@code PENDING → REJECTED}（驳回）。
 * 管理员直接创建的广告直接落在 {@code APPROVED}。
 */
public enum AdStatus {

    /** 待审核：用户已提交，尚未出现在任何前台位置 */
    PENDING("待审核"),

    /** 已通过：enabled 为真时进入首页轮播 */
    APPROVED("已通过"),

    /** 已驳回：附审核意见，用户可在「我的申请」里看到 */
    REJECTED("已驳回");

    private final String label;

    AdStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
