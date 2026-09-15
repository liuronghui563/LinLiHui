package com.chengqu.huzhu.community.entity;

import com.chengqu.huzhu.common.jpa.StringListJsonConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * 历史字段：仅校园频道用过，已被 {@link #kind} 取代。
     * 代码不再写入，保留列是为了在并行开发期间避免破坏性迁移。
     */
    @Deprecated
    @Column(length = 50)
    private String category;

    /** 帖子种类。取代 category，全站通用且带白名单校验。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PostKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostChannel channel;

    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> images;

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
        if (kind == null) {
            kind = PostKind.DAILY;
        }
    }

    public void addHeat(int delta) {
        int current = heatScore == null ? 0 : heatScore;
        heatScore = Math.max(0, current + delta);
    }
}
