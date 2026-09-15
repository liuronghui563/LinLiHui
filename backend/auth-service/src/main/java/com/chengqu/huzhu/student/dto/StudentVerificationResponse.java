package com.chengqu.huzhu.student.dto;

import com.chengqu.huzhu.common.file.LocalFileUrls;
import com.chengqu.huzhu.student.entity.StudentVerification;
import com.chengqu.huzhu.student.entity.VerificationStatus;
import com.chengqu.huzhu.user.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生认证申请的对外视图。
 *
 * <p>用户看自己的申请、管理员看审核列表用的是同一个 DTO，形状完全一致，
 * 差别只在「谁在看」：与 ad-service 的 AdApplicationResponse 同一套做法，
 * 没必要为两个视角维护两份几乎相同的结构。
 *
 * <p>申请人的昵称与手机号只在管理端填充（{@link #of}）：管理员要靠
 * 姓名 + 学号 + 手机号去核对身份，脱敏的号码没法用。用户端的
 * {@link #from} 不给这些字段，自己看自己本来也不需要。
 */
@Data
@Builder
public class StudentVerificationResponse {

    private Long id;
    private Long userId;
    private String realName;
    private String school;
    private String major;
    private String grade;
    private String studentNo;
    /** 学生证照片相对路径；非本站地址或未上传时为 null */
    private String proofImage;

    /** PENDING / APPROVED / REJECTED，前端据此显示状态标签 */
    private VerificationStatus status;
    /** 状态的中文名，由后端下发，前端不维护映射表 */
    private String statusLabel;
    private String reviewNote;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;

    /** 申请人昵称；只在管理端列表里填充 */
    private String applicantNickname;
    /** 申请人手机号（不脱敏）；只在管理端填充 */
    private String applicantPhone;

    /** 用户端视图：不带申请人信息 */
    public static StudentVerificationResponse from(StudentVerification entity) {
        return of(entity, null);
    }

    /** 管理端视图：带上申请人昵称与手机号 */
    public static StudentVerificationResponse of(StudentVerification entity, User applicant) {
        VerificationStatus status = entity.getStatus() == null ? VerificationStatus.PENDING : entity.getStatus();
        return StudentVerificationResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .realName(entity.getRealName())
                .school(entity.getSchool())
                .major(entity.getMajor())
                .grade(entity.getGrade())
                .studentNo(entity.getStudentNo())
                .proofImage(LocalFileUrls.sanitizeOptional(entity.getProofImage()))
                .status(status)
                .statusLabel(status.getLabel())
                .reviewNote(entity.getReviewNote())
                .reviewedAt(entity.getReviewedAt())
                .createdAt(entity.getCreatedAt())
                .applicantNickname(applicant == null ? null : applicant.getNickname())
                .applicantPhone(applicant == null ? null : applicant.getPhone())
                .build();
    }
}
