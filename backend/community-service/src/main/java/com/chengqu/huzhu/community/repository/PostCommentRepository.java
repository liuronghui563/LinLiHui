package com.chengqu.huzhu.community.repository;

import com.chengqu.huzhu.community.entity.PostComment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    List<PostComment> findByPostIdOrderByCreatedAtAsc(Long postId);

    long countByPostId(Long postId);

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
    List<Object[]> countPlazaCommentsSince(@Param("channel") com.chengqu.huzhu.community.entity.PostChannel channel,
                                           @Param("since") LocalDateTime since,
                                           Pageable pageable);
}
