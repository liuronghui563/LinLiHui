package com.chengqu.huzhu.community.dto;

import com.chengqu.huzhu.community.entity.PostComment;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CommentResponse {

    private Long id;
    private Long postId;
    private Long authorId;
    private String authorName;
    private String authorAvatar;
    private String content;
    private LocalDateTime createdAt;

    public static CommentResponse from(PostComment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .authorId(comment.getAuthorId())
                .authorName(comment.getAuthorName())
                .authorAvatar(null)
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
