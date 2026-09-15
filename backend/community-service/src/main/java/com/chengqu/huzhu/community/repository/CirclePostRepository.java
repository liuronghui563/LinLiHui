package com.chengqu.huzhu.community.repository;

import com.chengqu.huzhu.community.entity.CirclePost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CirclePostRepository extends JpaRepository<CirclePost, Long> {

    /** 圈内帖子分页，按收录时间倒序（排序由 Pageable 传入）。 */
    Page<CirclePost> findByCircleId(Long circleId, Pageable pageable);

    boolean existsByCircleIdAndPostId(Long circleId, Long postId);

    Optional<CirclePost> findByCircleIdAndPostId(Long circleId, Long postId);

    long countByCircleId(Long circleId);
}
