package com.chengqu.huzhu.user.repository;

import com.chengqu.huzhu.user.entity.UserRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRatingRepository extends JpaRepository<UserRating, Long> {

    Optional<UserRating> findByTargetUserIdAndRaterId(Long targetUserId, Long raterId);

    long countByTargetUserId(Long targetUserId);

    @Query("select avg(r.score) from UserRating r where r.targetUserId = :targetUserId")
    Double averageByTargetUserId(@Param("targetUserId") Long targetUserId);

    /**
     * 批量聚合多个用户的评分，返回 [targetUserId, avgScore, ratingCount]。
     * 供内部批量查询接口使用，避免逐条聚合造成 N+1。
     */
    @Query("""
            select r.targetUserId, avg(r.score), count(r.id)
            from UserRating r
            where r.targetUserId in :userIds
            group by r.targetUserId
            """)
    List<Object[]> aggregateByTargetUserIds(@Param("userIds") Collection<Long> userIds);
}
