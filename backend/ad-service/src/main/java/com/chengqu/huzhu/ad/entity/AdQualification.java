package com.chengqu.huzhu.ad.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 广告位资质：一个用户的「能不能投广告」的资格，一次审核长期有效。
 *
 * <p>与 {@link AdBanner} 的关系只是「同一个人」：资质是 1，广告是 N，
 * 通过之后提交多少条广告都只看这一行是否 APPROVED，
 * 因此资质被驳回、到期或撤销都不会回头改动任何一条已经上线的广告。
 */
@Entity
@Table(name = "u_r_ad_qualification")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdQualification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 申请人；与 {@link AdBanner#getApplicantId()} 同源，但两者互不级联 */
    @Column(nullable = false)
    private Long userId;

    /** 联系人姓名（可能是替公司投广告的经办人，所以与账号昵称分开存） */
    @Column(nullable = false, length = 50)
    private String applicantName;

    @Column(nullable = false, length = 50)
    private String contact;

    @Column(length = 100)
    private String company;

    @Column(length = 100)
    private String category;

    @Column(length = 500)
    private String intro;

    /** 资质证明图相对路径；未上传时为 null */
    @Column(length = 255)
    private String licenseImage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AdQualificationStatus status = AdQualificationStatus.PENDING;

    /** 审核意见：驳回原因或通过备注；重新提交时清空（它是针对旧材料的） */
    @Column(length = 200)
    private String reviewNote;

    private LocalDateTime reviewedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
