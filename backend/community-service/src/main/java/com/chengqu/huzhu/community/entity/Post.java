package com.chengqu.huzhu.community.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "u_r_post")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 50)
    private String authorName;

    @Column(nullable = false)
    private Integer likeCount = 0;

    @Column(nullable = false)
    private Integer heatScore = 0;

    @Column(nullable = false)
    private Integer viewCount = 0;

    @Column(length = 50)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PostChannel channel;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        if (likeCount == null) {
            likeCount = 0;
        }
        if (heatScore == null) {
            heatScore = 0;
        }
        if (viewCount == null) {
            viewCount = 0;
        }
        if (channel == null) {
            channel = PostChannel.COMMUNITY;
        }
    }

    public void addHeat(int delta) {
        int current = heatScore == null ? 0 : heatScore;
        heatScore = Math.max(0, current + delta);
    }
}
