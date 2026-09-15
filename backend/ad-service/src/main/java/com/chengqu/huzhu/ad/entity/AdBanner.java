package com.chengqu.huzhu.ad.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "u_r_ad")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdBanner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 200)
    private String subtitle;

    @Column(nullable = false, length = 500)
    private String imageUrl;

    @Column(length = 500)
    private String linkUrl;

    @Column(nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 审核状态。默认 APPROVED：管理员直接创建的广告不经审核，
     * 而用户提交的申请由 {@code AdService.apply} 显式写成 PENDING。
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AdStatus status = AdStatus.APPROVED;

    /** 申请提交人；管理员直接创建时为 null */
    private Long applicantId;

    /** 审核意见：驳回原因或通过备注 */
    @Column(length = 200)
    private String reviewNote;

    private LocalDateTime reviewedAt;

    @Column(nullable = false)
    @Builder.Default
    private Long clickCount = 0L;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
