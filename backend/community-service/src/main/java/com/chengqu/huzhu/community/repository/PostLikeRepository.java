package com.chengqu.huzhu.community.repository;

import com.chengqu.huzhu.community.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    /**
     * 批量查询当前用户在给定帖子集合中点过赞的帖子 ID。
     * 列表场景用于替代逐条 existsByPostIdAndUserId，消除 N+1。
     */
    @Query("select l.postId from PostLike l where l.userId = :userId and l.postId in :postIds")
    List<Long> findLikedPostIds(@Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);

    void deleteByPostId(Long postId);
}
