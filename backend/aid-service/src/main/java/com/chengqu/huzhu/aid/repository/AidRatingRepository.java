package com.chengqu.huzhu.aid.repository;

import com.chengqu.huzhu.aid.entity.AidRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AidRatingRepository extends JpaRepository<AidRating, Long> {

    Optional<AidRating> findByAidIdAndRaterId(Long aidId, Long raterId);

    long countByAidId(Long aidId);

    @Query("select avg(r.score) from AidRating r where r.aidId = :aidId")
    Double averageByAidId(@Param("aidId") Long aidId);

    void deleteByAidId(Long aidId);
}
