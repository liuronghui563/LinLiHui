package com.chengqu.huzhu.ad.dto;

import com.chengqu.huzhu.ad.entity.AdQualification;
import com.chengqu.huzhu.ad.entity.AdQualificationStatus;
import com.chengqu.huzhu.common.file.LocalFileUrls;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 广告位资质的对外视图。
 *
 * <p>用户看自己的资质、管理员看资质列表用的是同一个 DTO——字段一致，
 * 差别只在 {@code userNickname} 有没有值（只有管理端会去 Feign 补）。
 */
@Data
@Builder
public class AdQualificationResponse {

    private Long id;
    /** 申请人 id。下游昵称补不全时，前端回落到「用户 {userId}」 */
    private Long userId;
    private String applicantName;
    private String contact;
    private String company;
    private String category;
    private String intro;
    private String licenseImage;
    /** PENDING / APPROVED / REJECTED，前端据此显示状态标签 */
    private AdQualificationStatus status;
    /** 状态的中文名，由后端下发，前端不维护映射表 */
    private String statusLabel;
    private String reviewNote;
    private LocalDateTime reviewedAt;
    /**
     * 账号昵称：只在管理端列表里由 auth-service 批量补全。
     *
     * <p>与 {@code applicantName}（用户自己填的联系人）不是一回事：
     * 前者用于「这是平台上的谁」，后者用于「打电话找谁」，所以不能共用一个字段。
     */
    private String userNickname;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AdQualificationResponse from(AdQualification entity) {
        return of(entity, null);
    }

    public static AdQualificationResponse of(AdQualification entity, String userNickname) {
        AdQualificationStatus status = entity.getStatus() == null
                ? AdQualificationStatus.PENDING : entity.getStatus();
        return AdQualificationResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .applicantName(entity.getApplicantName())
                .contact(entity.getContact())
                .company(entity.getCompany())
                .category(entity.getCategory())
                .intro(entity.getIntro())
                // 读的时候再过滤一次：早期数据里可能混进过外链，不能让它在页面上重新生效
                .licenseImage(LocalFileUrls.sanitizeOptional(entity.getLicenseImage()))
                .status(status)
                .statusLabel(status.getLabel())
                .reviewNote(entity.getReviewNote())
                .reviewedAt(entity.getReviewedAt())
                .userNickname(userNickname)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
