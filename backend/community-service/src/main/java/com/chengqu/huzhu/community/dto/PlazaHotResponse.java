package com.chengqu.huzhu.community.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PlazaHotResponse {

    private LocalDate rankDate;
    private List<Item> items;

    @Data
    @Builder
    public static class Item {
        private Integer rankNo;
        private Long postId;
        private String content;
        private Long authorId;
        private String authorName;
        private String authorAvatar;
        private Long commentCount;
        private Integer likeCount;
        private Integer heatScore;
        private LocalDateTime createdAt;
    }
}
