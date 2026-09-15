package com.chengqu.huzhu.community.repository;

import com.chengqu.huzhu.community.entity.Post;
import com.chengqu.huzhu.community.entity.PostChannel;
import com.chengqu.huzhu.community.entity.PostKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    long countByAuthorId(Long authorId);

    Page<Post> findByAuthorId(Long authorId, Pageable pageable);

    Page<Post> findByChannel(PostChannel channel, Pageable pageable);

    /** 保留：历史 category 字段的查询，供遗留路径使用。 */
    Page<Post> findByChannelAndCategory(PostChannel channel, String category, Pageable pageable);

    List<Post> findByChannelOrderByLikeCountDescIdDesc(PostChannel channel, Pageable pageable);

    /**
     * 按模块（多频道）查询。
     *
     * <p>「发现」模块由 COMMUNITY + PLAZA 两个频道组成，所以按频道集合查询，
     * 而不是引入新的枚举值——后者需要迁移数据与重命名一整条类链。
     *
     * <p>{@code excludedAuthorIds} 由调用方保证非空：空集合会生成非法的
     * {@code NOT IN ()}。没有要屏蔽的人时传一个不存在的 id（如 -1）。
     * 屏蔽条件写在 SQL 里而不是取回后再过滤，否则分页条数会忽多忽少。
     */
    @Query("""
            select p from Post p
            where p.channel in :channels
              and p.authorId not in :excludedAuthorIds
            """)
    Page<Post> searchByChannels(@Param("channels") Collection<PostChannel> channels,
                                @Param("excludedAuthorIds") Collection<Long> excludedAuthorIds,
                                Pageable pageable);

    @Query("""
            select p from Post p
            where p.channel in :channels
              and p.kind = :kind
              and p.authorId not in :excludedAuthorIds
            """)
    Page<Post> searchByChannelsAndKind(@Param("channels") Collection<PostChannel> channels,
                                       @Param("kind") PostKind kind,
                                       @Param("excludedAuthorIds") Collection<Long> excludedAuthorIds,
                                       Pageable pageable);

    /** 指定作者发布的帖子，按时间倒序，同样排除被屏蔽的作者。 */
    @Query("""
            select p from Post p
            where p.authorId = :authorId
              and p.authorId not in :excludedAuthorIds
            """)
    Page<Post> searchByAuthor(@Param("authorId") Long authorId,
                              @Param("excludedAuthorIds") Collection<Long> excludedAuthorIds,
                              Pageable pageable);
}
