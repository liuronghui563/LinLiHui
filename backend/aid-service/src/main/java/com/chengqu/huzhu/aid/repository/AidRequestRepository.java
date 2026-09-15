package com.chengqu.huzhu.aid.repository;

import com.chengqu.huzhu.aid.entity.AidBoard;
import com.chengqu.huzhu.aid.entity.AidRequest;
import com.chengqu.huzhu.aid.entity.AidStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AidRequestRepository extends JpaRepository<AidRequest, Long> {

    Page<AidRequest> findByBoard(AidBoard board, Pageable pageable);

    Page<AidRequest> findByBoardAndStatus(AidBoard board, AidStatus status, Pageable pageable);

    Page<AidRequest> findByBoardAndCategory(AidBoard board, String category, Pageable pageable);

    Page<AidRequest> findByBoardAndStatusAndCategory(AidBoard board, AidStatus status, String category, Pageable pageable);

    Page<AidRequest> findByPublisherId(Long publisherId, Pageable pageable);

    Page<AidRequest> findByHelperId(Long helperId, Pageable pageable);

    long countByStatus(AidStatus status);

    long countByPublisherIdAndStatus(Long publisherId, AidStatus status);

    long countByPublisherId(Long publisherId);

    long countByHelperId(Long helperId);
}
