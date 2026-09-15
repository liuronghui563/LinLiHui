package com.chengqu.huzhu.student.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 一条学生认证申请。
 *
 * <p>每次「提交 → 驳回 → 改完再交」都在同一行上打转，只有通过之后用户再次申请
 * 才会新增一行——所以同一个 userId 可以有多条记录（多条 REJECTED 是常态），
 * 表上刻意没有 (user_id) 或 (user_id, status) 的唯一约束，见 V5 迁移脚本。
 */
@Entity
@Table(name = "u_r_student_verification", indexes = {
        @Index(name = "idx_u_r_student_verification_user", columnList = "userId, status"),
        @Index(name = "idx_u_r_student_verification_status", columnList = "status, createdAt")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 30)
    private String realName;

    @Column(nullable = false, length = 80)
    private String school;

    @Column(length = 80)
    private String major;

    @Column(length = 30)
    private String grade;

    @Column(nullable = false, length = 30)
    private String studentNo;

    /**
     * 学生证 / 校园卡照片。与其他图片同一套规矩：只存相对路径
     * {@code /api/file/objects/...}，非本站地址在读路径上回落为未设置。
     */
    @Column(length = 255)
    private String proofImage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private VerificationStatus status = VerificationStatus.PENDING;

    /** 审核意见：驳回原因或通过备注，最多 200 字 */
    @Column(length = 200)
    private String reviewNote;

    /** 审核时间；未审核时为 null */
    private LocalDateTime reviewedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
