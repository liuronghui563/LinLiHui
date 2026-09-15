package com.chengqu.huzhu.community.repository;

import com.chengqu.huzhu.community.entity.PostChannel;
import com.chengqu.huzhu.community.entity.PostComment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    /**
     * 评论列表，排除被屏蔽作者的评论。
     *
     * <p>{@code excludedAuthorIds} 由调用方保证非空：空集合会生成非法的
     * {@code NOT IN ()}。{@link com.chengqu.huzhu.community.support.UserExclusionLookup}
     * 用哨兵 id 保证这一点。
     *
     * <p>刻意**不提供**不过滤的 {@code findByPostIdOrderByCreatedAtAsc}：
     * 留着它只会让后来者顺手用上，把屏蔽关系绕过去。要读全部评论请显式传哨兵 id。
     */
    @Query("""
            select c from PostComment c
            where c.postId = :postId
              and c.authorId not in :excludedAuthorIds
            order by c.createdAt asc
            """)
    List<PostComment> findByPostIdExcludingAuthors(@Param("postId") Long postId,
                                                   @Param("excludedAuthorIds") Collection<Long> excludedAuthorIds);

    long countByPostId(Long postId);

    /**
     * 批量统计给定帖子在时间点之后的评论数，返回 [postId, count]。
     *
     * <p>热度榜补位场景用于替代逐条 {@code countByPostIdAndCreatedAtAfter}：
     * 补位最多 16 条，逐条查就是 16 次 COUNT，而这条查询一次就能拿到全部。
     */
    @Query("""
            select c.postId, count(c.id)
            from PostComment c
            where c.postId in :postIds
              and c.createdAt >= :since
            group by c.postId
            """)
    List<Object[]> countByPostIdsAndCreatedAtAfter(@Param("postIds") Collection<Long> postIds,
                                                   @Param("since") LocalDateTime since);

    /**
     * 批量统计给定帖子的评论数，返回 [postId, count]。
     * 列表场景用于替代逐条 countByPostId，消除 N+1。
     */
    @Query("select c.postId, count(c.id) from PostComment c where c.postId in :postIds group by c.postId")
    List<Object[]> countByPostIds(@Param("postIds") Collection<Long> postIds);

    void deleteByPostId(Long postId);

    @Query("""
            select c.postId, count(c.id)
            from PostComment c, Post p
            where c.postId = p.id
              and p.channel = :channel
              and c.createdAt >= :since
            group by c.postId
            order by count(c.id) desc
            """)
    List<Object[]> countPlazaCommentsSince(@Param("channel") PostChannel channel,
                                           @Param("since") LocalDateTime since,
                                           Pageable pageable);
}
