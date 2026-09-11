package com.chengqu.huzhu.aid.dto;

import com.chengqu.huzhu.aid.entity.AidBoard;
import com.chengqu.huzhu.aid.entity.AidRequest;
import com.chengqu.huzhu.aid.entity.AidStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

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

    public static AidResponse from(AidRequest entity) {
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
                .publisherAvatar("https://picsum.photos/seed/user-" + entity.getPublisherId() + "/200/200")
                .helperId(entity.getHelperId())
                .helperName(entity.getHelperName())
                .helperAvatar(entity.getHelperId() == null ? null : "https://picsum.photos/seed/user-" + entity.getHelperId() + "/200/200")
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
