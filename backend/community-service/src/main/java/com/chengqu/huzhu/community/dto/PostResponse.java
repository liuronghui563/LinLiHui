package com.chengqu.huzhu.community.dto;

import com.chengqu.huzhu.community.entity.Post;
import com.chengqu.huzhu.community.entity.PostChannel;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PostResponse {

    private Long id;
    private String content;
    private Long authorId;
    private String authorName;
    private String authorAvatar;
    private Integer likeCount;
    private Boolean liked;
    private Long commentCount;
    private String channel;
    /** 帖子种类编码，供前端筛选与样式区分 */
    private String kind;
    /** 帖子种类中文名。由后端下发，避免前端再维护一份映射表 */
    private String kindLabel;
    /** 历史字段，已被 kind 取代，仅用于兼容旧数据 */
    @Deprecated
    private String category;
    private Integer heatScore;
    private Integer viewCount;
    private Boolean viewCounted;
    private List<String> images;
    private LocalDateTime createdAt;

    public static PostResponse from(Post post, boolean liked, long commentCount) {
        return from(post, liked, commentCount, null);
    }

    public static PostResponse from(Post post, boolean liked, long commentCount, Boolean viewCounted) {
        PostChannel channel = post.getChannel() == null ? PostChannel.COMMUNITY : post.getChannel();
        // 头像不由实体决定：真实头像通过 Feign 从 auth-service 获取，
        // 取不到时留空，由前端展示占位图（不再硬编码第三方图床地址）。
        return PostResponse.builder()
                .id(post.getId())
                .content(post.getContent())
                .authorId(post.getAuthorId())
                .authorName(post.getAuthorName())
                .likeCount(post.getLikeCount())
                .liked(liked)
                .commentCount(commentCount)
                .channel(channel.name())
                .kind(post.getKind() == null ? null : post.getKind().name())
                .kindLabel(post.getKind() == null ? null : post.getKind().getLabel())
                .category(post.getCategory())
                .heatScore(post.getHeatScore() == null ? 0 : post.getHeatScore())
                .viewCount(post.getViewCount() == null ? 0 : post.getViewCount())
                .viewCounted(viewCounted)
                .images(post.getImages() == null ? List.of() : post.getImages())
                .createdAt(post.getCreatedAt())
                .build();
    }
}
