package com.chengqu.huzhu.student.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 「我的认证状态」的响应。
 *
 * <p>{@code status} 是字符串而不是 {@link com.chengqu.huzhu.student.entity.VerificationStatus}：
 * 它多出一个 {@code NONE} 取值，表示「从未提交过」。这个值**只存在于响应层**，
 * 不是数据库枚举的成员——表里没有行就是没有行，凭空插一条 NONE 记录来占位
 * 会让重新申请、审核列表都要额外排除它。
 * 取舍是把「没提交过」和「被驳回后没再提交」这两种前端表现完全不同的情况
 * 用一个哨兵值区分开，代价是这个字段不能直接当枚举用。
 */
@Data
@Builder
public class StudentVerificationStatusResponse {

    /** PENDING / APPROVED / REJECTED，从未提交过则为 NONE */
    private String status;

    /** 中文名；NONE 对应「未认证」 */
    private String statusLabel;

    /** 最近一条申请；从未提交过时为 null */
    private StudentVerificationResponse verification;

    /**
     * 是否已通过学生认证，取 {@code u_r_sys_user.student} 的值而不是
     * 「status == APPROVED」：它才是校园模块门禁实际读的那个字段
     * （TokenResponse.UserProfile.student 也来自它），两者必须同源。
     */
    private Boolean student;
}
