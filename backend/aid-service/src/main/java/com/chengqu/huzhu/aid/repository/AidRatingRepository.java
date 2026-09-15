package com.chengqu.huzhu.aid.repository;

import com.chengqu.huzhu.aid.entity.AidRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AidRatingRepository extends JpaRepository<AidRating, Long> {

    Optional<AidRating> findByAidIdAndRaterId(Long aidId, Long raterId);

    /**
     * 批量聚合多条求助的评分，返回 [aidId, avgScore, ratingCount]。
     * 列表场景用于替代逐条查询，消除 N+1。
     */
    @Query("""
            select r.aidId, avg(r.score), count(r.id)
            from AidRating r
            where r.aidId in :aidIds
            group by r.aidId
            """)
    List<Object[]> aggregateByAidIds(@Param("aidIds") Collection<Long> aidIds);

    /**
     * 批量查询当前用户对多条求助的评分，返回 [aidId, score]。
     */
    @Query("select r.aidId, r.score from AidRating r where r.raterId = :raterId and r.aidId in :aidIds")
    List<Object[]> findScoresByRaterAndAidIds(@Param("raterId") Long raterId,
                                              @Param("aidIds") Collection<Long> aidIds);

    void deleteByAidId(Long aidId);
}
