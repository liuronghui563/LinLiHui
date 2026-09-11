package com.chengqu.huzhu.ad.repository;

import com.chengqu.huzhu.ad.entity.AdBanner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdBannerRepository extends JpaRepository<AdBanner, Long> {

    List<AdBanner> findByEnabledTrueOrderBySortOrderAscIdDesc();

    List<AdBanner> findAllByOrderBySortOrderAscIdDesc();
}
