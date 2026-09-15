package com.chengqu.huzhu.user.repository;

import com.chengqu.huzhu.user.entity.UserFollow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {

    Optional<UserFollow> findByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    boolean existsByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    long countByFollowerId(Long followerId);

    long countByFolloweeId(Long followeeId);

    Page<UserFollow> findByFollowerIdOrderByCreatedAtDesc(Long followerId, Pageable pageable);

    Page<UserFollow> findByFolloweeIdOrderByCreatedAtDesc(Long followeeId, Pageable pageable);

    void deleteByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    /** 拉黑时清掉双向关注，避免「已拉黑却仍是粉丝」的错乱状态。 */
    @Query("delete from UserFollow f where (f.followerId = :a and f.followeeId = :b) or (f.followerId = :b and f.followeeId = :a)")
    @org.springframework.data.jpa.repository.Modifying
    void deleteBetween(@Param("a") Long a, @Param("b") Long b);

    /** 批量判断「我是否关注了这些人」，用于列表页一次性标记关注状态。 */
    @Query("select f.followeeId from UserFollow f where f.followerId = :followerId and f.followeeId in :followeeIds")
    List<Long> findFollowingIds(@Param("followerId") Long followerId,
                               @Param("followeeIds") Collection<Long> followeeIds);

    /**
     * 批量判断「这些人里谁关注了我」——{@link #findFollowingIds} 的反方向。
     *
     * <p>列表页要同时展示「我已关注」和「对方关注了我」两种状态，
     * 两者都必须一次问清；只批量其中一个，另一个仍会退化成逐行查询。
     */
    @Query("select f.followerId from UserFollow f where f.followeeId = :followeeId and f.followerId in :followerIds")
    List<Long> findFollowerIds(@Param("followeeId") Long followeeId,
                               @Param("followerIds") Collection<Long> followerIds);

    /**
     * 批量取「这些人的粉丝数」，返回 [userId, count]。
     *
     * <p>逐行 countByFolloweeId 是列表页最典型的 N+1：一页 50 行就是 50 次 COUNT。
     */
    @Query("select f.followeeId, count(f.id) from UserFollow f where f.followeeId in :userIds group by f.followeeId")
    List<Object[]> countByFolloweeIds(@Param("userIds") Collection<Long> userIds);

    /** 批量取「这些人关注了多少人」，返回 [userId, count]，与 {@link #countByFolloweeIds} 同理。 */
    @Query("select f.followerId, count(f.id) from UserFollow f where f.followerId in :userIds group by f.followerId")
    List<Object[]> countByFollowerIds(@Param("userIds") Collection<Long> userIds);
}
