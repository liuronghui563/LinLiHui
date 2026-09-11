package com.chengqu.huzhu.community.repository;

import com.chengqu.huzhu.community.entity.PlazaHotRank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface PlazaHotRankRepository extends JpaRepository<PlazaHotRank, Long> {

    List<PlazaHotRank> findByRankDateOrderByRankNoAsc(LocalDate rankDate);

    void deleteByRankDate(LocalDate rankDate);

    @Query("select max(h.rankDate) from PlazaHotRank h")
    LocalDate findLatestDate();
}
