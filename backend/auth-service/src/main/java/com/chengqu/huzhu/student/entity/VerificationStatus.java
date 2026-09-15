package com.chengqu.huzhu.student.entity;

/**
 * 学生认证的审核状态。
 *
 * <p>一条申请从提交到出结果全程是同一行的状态流转：
 * {@code PENDING → APPROVED}（通过，用户据此获得校园模块的准入）
 * 或 {@code PENDING → REJECTED}（驳回，用户改完可以重投，仍是同一行）。
 *
 * <p>没有「已撤回」这个值：撤回是删除待审记录，不留下一条谁也看不懂的状态。
 * 用户侧响应里的 {@code NONE}（从未提交过）也不是这里的取值，见
 * {@code StudentVerificationService.STATUS_NONE}。
 */
public enum VerificationStatus {

    /** 待审核：已提交，尚未有结论；同一用户同时至多一条 */
    PENDING("待审核"),

    /** 已通过：user.student 已置 true，可进入校园模块 */
    APPROVED("已通过"),

    /** 已驳回：附审核意见，用户可修改后重新提交 */
    REJECTED("已驳回");

    private final String label;

    VerificationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
