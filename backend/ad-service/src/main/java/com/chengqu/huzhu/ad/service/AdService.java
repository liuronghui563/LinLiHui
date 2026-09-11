package com.chengqu.huzhu.ad.service;

import com.chengqu.huzhu.ad.dto.AdBannerResponse;
import com.chengqu.huzhu.ad.dto.CreateAdRequest;
import com.chengqu.huzhu.ad.dto.UpdateAdRequest;
import com.chengqu.huzhu.ad.entity.AdBanner;
import com.chengqu.huzhu.ad.repository.AdBannerRepository;
import com.chengqu.huzhu.ad.support.RandomAdImages;
import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdService {

    private final AdBannerRepository adBannerRepository;

    @Transactional(readOnly = true)
    public List<AdBannerResponse> carousel() {
        List<AdBannerResponse> list = adBannerRepository.findByEnabledTrueOrderBySortOrderAscIdDesc()
                .stream()
                .map(AdBannerResponse::from)
                .toList();
        log.info("[广告] 轮播拉取 count={}（图片由前端获取）", list.size());
        return list;
    }

    @Transactional(readOnly = true)
    public List<AdBannerResponse> listAll() {
        List<AdBannerResponse> list = adBannerRepository.findAllByOrderBySortOrderAscIdDesc()
                .stream()
                .map(AdBannerResponse::from)
                .toList();
        log.info("[广告] 管理列表 count={}", list.size());
        return list;
    }

    @Transactional
    public AdBannerResponse create(CreateAdRequest request) {
        AdBanner banner = AdBanner.builder()
                .title(request.getTitle().trim())
                .subtitle(blankToNull(request.getSubtitle()))
                .imageUrl(resolveImage(request.getImageUrl()))
                .linkUrl(blankToNull(request.getLinkUrl()))
                .sortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder())
                .enabled(request.getEnabled() == null || request.getEnabled())
                .clickCount(0L)
                .build();
        AdBanner saved = adBannerRepository.save(banner);
        log.info("[广告] 新建 id={} title={}", saved.getId(), saved.getTitle());
        return AdBannerResponse.from(saved);
    }

    @Transactional
    public AdBannerResponse update(Long id, UpdateAdRequest request) {
        AdBanner banner = requireBanner(id);
        banner.setTitle(request.getTitle().trim());
        banner.setSubtitle(blankToNull(request.getSubtitle()));
        banner.setImageUrl(resolveImage(request.getImageUrl()));
        banner.setLinkUrl(blankToNull(request.getLinkUrl()));
        if (request.getSortOrder() != null) {
            banner.setSortOrder(request.getSortOrder());
        }
        if (request.getEnabled() != null) {
            banner.setEnabled(request.getEnabled());
        }
        AdBanner saved = adBannerRepository.save(banner);
        log.info("[广告] 更新 id={} title={}", saved.getId(), saved.getTitle());
        return AdBannerResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        AdBanner banner = requireBanner(id);
        adBannerRepository.delete(banner);
        log.info("[广告] 删除 id={} title={}", id, banner.getTitle());
    }

    @Transactional
    public AdBannerResponse click(Long id) {
        AdBanner banner = requireBanner(id);
        if (!Boolean.TRUE.equals(banner.getEnabled())) {
            throw new BizException("广告未启用");
        }
        long next = banner.getClickCount() == null ? 1L : banner.getClickCount() + 1L;
        banner.setClickCount(next);
        AdBanner saved = adBannerRepository.save(banner);
        Long userId = SecurityUtils.currentUser().getId();
        log.info("[广告] 点击 id={} clickCount={} userId={}", id, saved.getClickCount(), userId);
        return AdBannerResponse.from(saved);
    }

    private AdBanner requireBanner(Long id) {
        return adBannerRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "广告不存在"));
    }

    private static String resolveImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return RandomAdImages.next();
        }
        return imageUrl.trim();
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
