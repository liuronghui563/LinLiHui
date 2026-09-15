package com.chengqu.huzhu.ad.dto;

import com.chengqu.huzhu.ad.entity.AdBanner;
import com.chengqu.huzhu.common.file.LocalFileUrls;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdBannerResponse {

    private Long id;
    private String title;
    private String subtitle;
    private String imageUrl;
    private String linkUrl;
    private Integer sortOrder;
    private Boolean enabled;
    private Long clickCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AdBannerResponse from(AdBanner entity) {
        return AdBannerResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .subtitle(entity.getSubtitle())
                .imageUrl(LocalFileUrls.sanitizeOptional(entity.getImageUrl()))
                .linkUrl(entity.getLinkUrl())
                .sortOrder(entity.getSortOrder())
                .enabled(entity.getEnabled())
                .clickCount(entity.getClickCount())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
