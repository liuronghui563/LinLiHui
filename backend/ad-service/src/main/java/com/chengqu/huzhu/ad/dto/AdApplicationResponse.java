package com.chengqu.huzhu.ad.dto;

import com.chengqu.huzhu.ad.entity.AdBanner;
import com.chengqu.huzhu.ad.entity.AdStatus;
import com.chengqu.huzhu.common.file.LocalFileUrls;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 广告位申请（= 一条广告）的对外视图。
 *
 * <p>用户看自己的申请、管理员看待审列表，用的是同一个 DTO：
 * 字段完全一致，差别只在「谁在看」——没必要为此维护两个形状。
 */
@Data
@Builder
public class AdApplicationResponse {

    private Long id;
    private String title;
    private String subtitle;
    private String imageUrl;
    private String linkUrl;
    /** PENDING / APPROVED / REJECTED，前端据此显示状态标签 */
    private AdStatus status;
    /** 状态的中文名，由后端下发，前端不维护映射表 */
    private String statusLabel;
    private String reviewNote;
    private LocalDateTime reviewedAt;
    private Long applicantId;
    /** 申请人昵称；只在管理端列表里由 auth-service 批量补全 */
    private String applicantName;
    /** 排序位次，管理端可调；用户端只读 */
    private Integer sortOrder;
    /** 是否上架。审核通过后由管理员随时下架/恢复，不需要再审一次 */
    private Boolean enabled;
    private LocalDateTime createdAt;

    public static AdApplicationResponse from(AdBanner entity) {
        return of(entity, null);
    }

    public static AdApplicationResponse of(AdBanner entity, String applicantName) {
        AdStatus status = entity.getStatus() == null ? AdStatus.APPROVED : entity.getStatus();
        return AdApplicationResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .subtitle(entity.getSubtitle())
                .imageUrl(LocalFileUrls.sanitizeOptional(entity.getImageUrl()))
                .linkUrl(entity.getLinkUrl())
                .status(status)
                .statusLabel(status.getLabel())
                .reviewNote(entity.getReviewNote())
                .reviewedAt(entity.getReviewedAt())
                .applicantId(entity.getApplicantId())
                .applicantName(applicantName)
                .sortOrder(entity.getSortOrder())
                .enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
