package com.chengqu.huzhu.aid.repository;

import com.chengqu.huzhu.aid.entity.AidBoard;
import com.chengqu.huzhu.aid.entity.AidRequest;
import com.chengqu.huzhu.aid.entity.AidStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AidRequestRepository extends JpaRepository<AidRequest, Long> {

    Page<AidRequest> findByBoard(AidBoard board, Pageable pageable);

    Page<AidRequest> findByBoardAndStatus(AidBoard board, AidStatus status, Pageable pageable);

    Page<AidRequest> findByBoardAndCategory(AidBoard board, String category, Pageable pageable);

    Page<AidRequest> findByBoardAndStatusAndCategory(AidBoard board, AidStatus status, String category, Pageable pageable);

    @Query("SELECT a FROM AidRequest a WHERE a.board IS NULL OR a.board = :board")
    Page<AidRequest> findNeighborhood(@Param("board") AidBoard board, Pageable pageable);

    @Query("SELECT a FROM AidRequest a WHERE (a.board IS NULL OR a.board = :board) AND a.status = :status")
    Page<AidRequest> findNeighborhoodByStatus(@Param("board") AidBoard board, @Param("status") AidStatus status, Pageable pageable);

    @Query("SELECT a FROM AidRequest a WHERE (a.board IS NULL OR a.board = :board) AND a.category = :category")
    Page<AidRequest> findNeighborhoodByCategory(@Param("board") AidBoard board, @Param("category") String category, Pageable pageable);

    @Query("SELECT a FROM AidRequest a WHERE (a.board IS NULL OR a.board = :board) AND a.status = :status AND a.category = :category")
    Page<AidRequest> findNeighborhoodByStatusAndCategory(
            @Param("board") AidBoard board,
            @Param("status") AidStatus status,
            @Param("category") String category,
            Pageable pageable);

    Page<AidRequest> findByCategory(String category, Pageable pageable);

    Page<AidRequest> findByStatusAndCategory(AidStatus status, String category, Pageable pageable);

    Page<AidRequest> findByCategoryIn(java.util.Collection<String> categories, Pageable pageable);

    Page<AidRequest> findByStatusAndCategoryIn(AidStatus status, java.util.Collection<String> categories, Pageable pageable);

    Page<AidRequest> findByPublisherId(Long publisherId, Pageable pageable);

    Page<AidRequest> findByHelperId(Long helperId, Pageable pageable);

    long countByStatus(AidStatus status);

    long countByPublisherId(Long publisherId);

    long countByHelperId(Long helperId);
}
