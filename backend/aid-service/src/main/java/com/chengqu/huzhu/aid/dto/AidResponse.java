package com.chengqu.huzhu.aid.dto;

import com.chengqu.huzhu.aid.entity.AidBoard;
import com.chengqu.huzhu.aid.entity.AidRequest;
import com.chengqu.huzhu.aid.entity.AidStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AidResponse {

    private Long id;
    private String title;
    private String content;
    private String category;
    private String board;
    private String address;
    private AidStatus status;
    private Long publisherId;
    private String publisherName;
    private String publisherAvatar;
    private Long helperId;
    private String helperName;
    private String helperAvatar;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Double ratingAvg;
    private Long ratingCount;
    private Integer myScore;
    private Integer helperReviewScore;
    private String helperReviewContent;
    private LocalDateTime helperReviewAt;
    private boolean canReviewHelper;
    private List<String> images;

    public static AidResponse from(AidRequest entity) {
        // 头像不由实体决定：真实头像通过 Feign 从 auth-service 获取，
        // 取不到时留空，由前端展示占位图（不再硬编码第三方图床地址）。
        return AidResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .category(entity.getCategory())
                .board(entity.getBoard() == null ? AidBoard.NEIGHBORHOOD.name() : entity.getBoard().name())
                .address(entity.getAddress())
                .status(entity.getStatus())
                .publisherId(entity.getPublisherId())
                .publisherName(entity.getPublisherName())
                .helperId(entity.getHelperId())
                .helperName(entity.getHelperName())
                .images(entity.getImages() == null ? List.of() : entity.getImages())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
