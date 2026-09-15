package com.chengqu.huzhu.user.repository;

import com.chengqu.huzhu.user.entity.UserBlock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    Optional<UserBlock> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    Page<UserBlock> findByBlockerIdOrderByCreatedAtDesc(Long blockerId, Pageable pageable);

    void deleteByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    @Query("select b.blockedId from UserBlock b where b.blockerId = :blockerId")
    List<Long> findBlockedIds(@Param("blockerId") Long blockerId);

    @Query("select b.blockerId from UserBlock b where b.blockedId = :blockedId")
    List<Long> findBlockerIds(@Param("blockedId") Long blockedId);

    /**
     * 批量判断「这些人里谁被我拉黑了」，用于列表页一次性标记拉黑状态。
     *
     * <p>与 {@link #findBlockedIds} 的区别：那个取全部，这个只在给定的一页 id 里取，
     * 避免为了一页 50 行把某些用户的上千条黑名单整个捞回来。
     */
    @Query("select b.blockedId from UserBlock b where b.blockerId = :blockerId and b.blockedId in :blockedIds")
    List<Long> findBlockedIdsAmong(@Param("blockerId") Long blockerId,
                                   @Param("blockedIds") Collection<Long> blockedIds);

    /** 批量判断「这些人里谁把我拉黑了」——{@link #findBlockedIdsAmong} 的反方向。 */
    @Query("select b.blockerId from UserBlock b where b.blockedId = :blockedId and b.blockerId in :blockerIds")
    List<Long> findBlockerIdsAmong(@Param("blockedId") Long blockedId,
                                   @Param("blockerIds") Collection<Long> blockerIds);
}
