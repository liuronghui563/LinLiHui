package com.chengqu.huzhu.aid.repository;

import com.chengqu.huzhu.aid.entity.AidHelperReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AidHelperReviewRepository extends JpaRepository<AidHelperReview, Long> {

    Optional<AidHelperReview> findByAidId(Long aidId);

    /**
     * 批量查询多条求助的帮助方评价，用于列表场景避免 N+1。
     */
    List<AidHelperReview> findByAidIdIn(Collection<Long> aidIds);

    void deleteByAidId(Long aidId);
}
