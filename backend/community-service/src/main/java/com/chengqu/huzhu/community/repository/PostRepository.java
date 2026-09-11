package com.chengqu.huzhu.community.repository;

import com.chengqu.huzhu.community.entity.Post;
import com.chengqu.huzhu.community.entity.PostChannel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    long countByAuthorId(Long authorId);

    Page<Post> findByAuthorId(Long authorId, Pageable pageable);

    Page<Post> findByChannel(PostChannel channel, Pageable pageable);

    Page<Post> findByChannelAndCategory(PostChannel channel, String category, Pageable pageable);

    Page<Post> findByChannelIsNullOrChannel(PostChannel channel, Pageable pageable);

    List<Post> findByChannelOrderByLikeCountDescIdDesc(PostChannel channel, Pageable pageable);
}
