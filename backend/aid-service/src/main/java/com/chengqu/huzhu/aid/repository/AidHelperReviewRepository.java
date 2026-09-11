package com.chengqu.huzhu.aid.repository;

import com.chengqu.huzhu.aid.entity.AidHelperReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AidHelperReviewRepository extends JpaRepository<AidHelperReview, Long> {

    Optional<AidHelperReview> findByAidId(Long aidId);

    void deleteByAidId(Long aidId);
}
