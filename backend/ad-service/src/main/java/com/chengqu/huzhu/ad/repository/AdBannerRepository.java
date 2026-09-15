package com.chengqu.huzhu.ad.repository;

import com.chengqu.huzhu.ad.entity.AdBanner;
import com.chengqu.huzhu.ad.entity.AdStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AdBannerRepository extends JpaRepository<AdBanner, Long> {

    /**
     * 首页轮播数据源：**只有审核通过且启用的广告**。
     *
     * <p>这是「申请 → 审核 → 上线」这条链路的最后一环：待审与已驳回的申请
     * 即便 enabled 为真也不会出现在这里，因此审核是真正的闸门，
     * 而不是靠前端不渲染。
     */
    List<AdBanner> findByStatusAndEnabledTrueOrderBySortOrderAscIdDesc(AdStatus status);

    List<AdBanner> findAllByOrderBySortOrderAscIdDesc();

    /** 我的申请：按提交时间倒序，最新的在最上面 */
    List<AdBanner> findByApplicantIdOrderByCreatedAtDesc(Long applicantId);

    /** 管理端待审列表：先来先审 */
    List<AdBanner> findByStatusOrderByCreatedAtAsc(AdStatus status);

    long countByEnabledTrue();

    /** 累计点击量。无数据时返回 null，由调用方兜底为 0。 */
    @Query("select sum(a.clickCount) from AdBanner a")
    Long sumClickCount();
}
