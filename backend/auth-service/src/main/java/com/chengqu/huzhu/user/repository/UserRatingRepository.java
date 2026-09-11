package com.chengqu.huzhu.user.repository;

import com.chengqu.huzhu.user.entity.UserRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRatingRepository extends JpaRepository<UserRating, Long> {

    Optional<UserRating> findByTargetUserIdAndRaterId(Long targetUserId, Long raterId);

    long countByTargetUserId(Long targetUserId);

    @Query("select avg(r.score) from UserRating r where r.targetUserId = :targetUserId")
    Double averageByTargetUserId(@Param("targetUserId") Long targetUserId);
}
