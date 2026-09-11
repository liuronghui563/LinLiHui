package com.chengqu.huzhu.community.dto;

import com.chengqu.huzhu.community.entity.Post;
import com.chengqu.huzhu.community.entity.PostChannel;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

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
    private String category;
    private Integer heatScore;
    private Integer viewCount;
    private Boolean viewCounted;
    private LocalDateTime createdAt;

    public static PostResponse from(Post post, boolean liked, long commentCount) {
        return from(post, liked, commentCount, null);
    }

    public static PostResponse from(Post post, boolean liked, long commentCount, Boolean viewCounted) {
        PostChannel channel = post.getChannel() == null ? PostChannel.COMMUNITY : post.getChannel();
        return PostResponse.builder()
                .id(post.getId())
                .content(post.getContent())
                .authorId(post.getAuthorId())
                .authorName(post.getAuthorName())
                .authorAvatar("https://picsum.photos/seed/user-" + post.getAuthorId() + "/200/200")
                .likeCount(post.getLikeCount())
                .liked(liked)
                .commentCount(commentCount)
                .channel(channel.name())
                .category(post.getCategory())
                .heatScore(post.getHeatScore() == null ? 0 : post.getHeatScore())
                .viewCount(post.getViewCount() == null ? 0 : post.getViewCount())
                .viewCounted(viewCounted)
                .createdAt(post.getCreatedAt())
                .build();
    }
}
