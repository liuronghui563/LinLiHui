package com.chengqu.huzhu.ad.service;

import com.chengqu.huzhu.ad.dto.AdApplicationRequest;
import com.chengqu.huzhu.ad.dto.AdApplicationResponse;
import com.chengqu.huzhu.ad.dto.AdBannerResponse;
import com.chengqu.huzhu.ad.dto.CreateAdRequest;
import com.chengqu.huzhu.ad.dto.UpdateAdRequest;
import com.chengqu.huzhu.ad.entity.AdBanner;
import com.chengqu.huzhu.ad.entity.AdQualificationStatus;
import com.chengqu.huzhu.ad.entity.AdStatus;
import com.chengqu.huzhu.ad.repository.AdBannerRepository;
import com.chengqu.huzhu.ad.repository.AdQualificationRepository;
import com.chengqu.huzhu.api.client.UserApiClient;
import com.chengqu.huzhu.api.dto.PlatformAdStats;
import com.chengqu.huzhu.api.dto.UserBrief;
import com.chengqu.huzhu.common.api.ApiResponse;
import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.common.file.LocalFileUrls;
import com.chengqu.huzhu.common.redis.RedisCache;
import com.chengqu.huzhu.common.redis.RedisKeys;
import com.chengqu.huzhu.common.security.SecurityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdService {

    private final AdBannerRepository adBannerRepository;
    /** 提交广告位申请前要查「这个人有没有开通资质」，见 {@link #apply} */
    private final AdQualificationRepository adQualificationRepository;
    private final RedisCache redisCache;
    /** 待审列表要显示申请人昵称；依赖不可用时降级为只显示 id，不阻塞审核 */
    private final UserApiClient userApiClient;

    /**
     * 轮播缓存时长。首页每次加载都会拉轮播，而广告位是典型的读多写少数据，
     * 因此缓存 60 秒；后台增删改会立即失效，保证运营改完即生效。
     *
     * <p>点击计数刻意不触发失效：否则每次点击都要清缓存，缓存等于形同虚设。
     * 代价是轮播响应里的 {@code clickCount} 最多滞后 60 秒——该字段只用于运营统计，
     * 且管理端走的是不缓存的 {@code listAll()}，因此不影响任何决策。
     */
    private static final Duration CAROUSEL_CACHE_TTL = Duration.ofSeconds(60);

    @Transactional(readOnly = true)
    public List<AdBannerResponse> carousel() {
        Optional<List<AdBannerResponse>> cached =
                redisCache.get(RedisKeys.adCarousel(), new TypeReference<List<AdBannerResponse>>() {
                });
        if (cached.isPresent()) {
            log.info("[广告] 轮播命中缓存 count={}", cached.get().size());
            return cached.get();
        }
        // 只取审核通过的：待审与已驳回的申请即便 enabled 也不会出现在首页
        List<AdBannerResponse> list =
                adBannerRepository.findByStatusAndEnabledTrueOrderBySortOrderAscIdDesc(AdStatus.APPROVED)
                        .stream()
                        .map(AdBannerResponse::from)
                        .toList();
        redisCache.put(RedisKeys.adCarousel(), list, CAROUSEL_CACHE_TTL);
        log.info("[广告] 轮播查库 count={}（已审核通过，写入缓存 {}s）", list.size(), CAROUSEL_CACHE_TTL.toSeconds());
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
        redisCache.evict(RedisKeys.adCarousel());
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
        redisCache.evict(RedisKeys.adCarousel());
        log.info("[广告] 更新（上下架，另编辑等） id={} title={}", saved.getId(), saved.getTitle());
        return AdBannerResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        AdBanner banner = requireBanner(id);
        adBannerRepository.delete(banner);
        redisCache.evict(RedisKeys.adCarousel());
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

    // ------------------------------------------------------------------
    // 广告位申请与审核
    //
    // 一条广告从用户申请到首页轮播是**同一行**的状态流转：
    //   POST /api/ad/applications        → PENDING（enabled=true，但进不了轮播）
    //   POST .../{id}/approve (ADMIN)    → APPROVED（进入轮播）
    //   POST .../{id}/reject  (ADMIN)    → REJECTED（附审核意见）
    // 管理员从后台直接创建的广告仍然是 APPROVED，不经这条链路。
    //
    // 提交之前还有一道前置闸门：申请人必须先开通「广告位资质」
    // （u_r_ad_qualification 里有 APPROVED 的一行），见 apply()。
    // ------------------------------------------------------------------

    /**
     * 提交广告位申请。
     *
     * <p><b>闸门为什么在服务层，而不是加去 SecurityConfig：</b>
     * 安全配置表达的是「谁登录了、是哪个角色」这类**静态**事实，
     * 而「这个人有没有资质」是**业务数据**——同一时间、同一个 URL，
     * 张三能过、李四不能过，且同一个人今天不能过、审核通过后就能过。
     * 把它写进 URL 规则要么写不出来，要么得为每个用户生成一条规则。
     *
     * <p><b>只拦新提交：</b>这段检查只在 apply 里。存量待审 / 已通过的广告申请
     * 不会因为用户当前没有资质而失效——它们是资质这道闸门上线**之前**进来的，
     * 用新规矩回溯旧数据，会让正在轮播的广告莫名消失、让在审的申请卡死，
     * 对已经发生的申请也没有任何补救手段。审核端照旧处理即可。
     */
    @Transactional
    public AdApplicationResponse apply(AdApplicationRequest request) {
        Long me = SecurityUtils.currentUser().getId();
        if (!adQualificationRepository.existsByUserIdAndStatus(me, AdQualificationStatus.APPROVED)) {
            throw new BizException(403, "请先开通广告位资质，通过审核后才能提交广告位申请");
        }
        AdBanner banner = AdBanner.builder()
                .title(request.getTitle().trim())
                .subtitle(blankToNull(request.getSubtitle()))
                .imageUrl(resolveImage(request.getImageUrl()))
                .linkUrl(blankToNull(request.getLinkUrl()))
                .sortOrder(0)
                // enabled 先置真：真正决定上不上线的是 status，审核通过即可生效，
                // 不必再让管理员回来勾一次开关。
                .enabled(true)
                .status(AdStatus.PENDING)
                .applicantId(me)
                .clickCount(0L)
                .build();
        AdBanner saved = adBannerRepository.save(banner);
        log.info("[广告] 收到申请 id={} title={} applicantId={}", saved.getId(), saved.getTitle(), me);
        return AdApplicationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<AdApplicationResponse> myApplications() {
        Long me = SecurityUtils.currentUser().getId();
        List<AdApplicationResponse> list =
                adBannerRepository.findByApplicantIdOrderByCreatedAtDesc(me)
                        .stream()
                        .map(AdApplicationResponse::from)
                        .toList();
        log.info("[广告] 我的申请 userId={} count={}", me, list.size());
        return list;
    }

    /** 撤回申请：只能撤回自己的、且仍在待审的申请 */
    @Transactional
    public void withdraw(Long id) {
        Long me = SecurityUtils.currentUser().getId();
        AdBanner banner = requireBanner(id);
        if (!me.equals(banner.getApplicantId())) {
            // 不区分「不存在」与「不是你的」：避免用 id 探测别人的申请
            throw new BizException(404, "申请不存在");
        }
        if (banner.getStatus() != AdStatus.PENDING) {
            throw new BizException("已审核的申请不能撤回");
        }
        adBannerRepository.delete(banner);
        log.info("[广告] 撤回申请 id={} applicantId={}", id, me);
    }

    /**
     * 修改申请：申请人改自己的广告。
     *
     * <p>规则是「**改了就要重新审**」，而不是「先改着、审核另说」：
     * <ul>
     *   <li>已通过的广告被修改 → 退回 {@code PENDING}，**同时从首页轮播撤下**
     *       （清缓存），审核通过再上。否则等于留了一个「审核通过后随便改」的口子，
     *       审核就白做了。</li>
     *   <li>待审的广告被修改 → 内容更新，仍是待审。</li>
     *   <li>被驳回的广告被修改 → 退回待审，这是「改完再试一次」的正常路径。</li>
     * </ul>
     * 三种情况都会清掉上一次的审核意见与审核时间——它们是针对旧内容的。
     */
    @Transactional
    public AdApplicationResponse updateApplication(Long id, AdApplicationRequest request) {
        Long me = SecurityUtils.currentUser().getId();
        AdBanner banner = requireBanner(id);
        if (!me.equals(banner.getApplicantId())) {
            throw new BizException(404, "申请不存在");
        }
        boolean wasLive = banner.getStatus() == AdStatus.APPROVED;

        banner.setTitle(request.getTitle().trim());
        banner.setSubtitle(blankToNull(request.getSubtitle()));
        banner.setImageUrl(resolveImage(request.getImageUrl()));
        banner.setLinkUrl(blankToNull(request.getLinkUrl()));
        banner.setStatus(AdStatus.PENDING);
        banner.setEnabled(true);
        banner.setReviewNote(null);
        banner.setReviewedAt(null);
        AdBanner saved = adBannerRepository.save(banner);

        if (wasLive) {
            // 改的是正在轮播的那条：必须立刻下线，否则「审核中」的广告还在首页
            redisCache.evict(RedisKeys.adCarousel());
            log.info("[广告] 已通过的广告被修改，退回待审并下架 id={} applicantId={}", id, me);
        } else {
            log.info("[广告] 修改申请 id={} applicantId={}（重新进入待审）", id, me);
        }
        return AdApplicationResponse.from(saved);
    }

    /** 管理端：待审列表，并批量补全申请人昵称（一次 Feign，不逐行查） */
    @Transactional(readOnly = true)
    public List<AdApplicationResponse> pendingApplications() {
        List<AdBanner> list = adBannerRepository.findByStatusOrderByCreatedAtAsc(AdStatus.PENDING);
        Map<Long, String> names = applicantNames(list);
        log.info("[广告] 待审列表 count={}", list.size());
        return list.stream()
                .map(banner -> AdApplicationResponse.of(banner, names.get(banner.getApplicantId())))
                .toList();
    }

    /**
     * 管理端：全部广告（含已通过的），用于「审核通过之后」的日常管理
     * ——上下架、排序、改文案、删除。
     *
     * <p>与 {@link #pendingApplications()} 同为管理端视图，但关注点不同：
     * 那个回答「有什么要审」，这个回答「现在挂着什么、谁在跑」。
     */
    @Transactional(readOnly = true)
    public List<AdApplicationResponse> allApplications() {
        List<AdBanner> list = adBannerRepository.findAllByOrderBySortOrderAscIdDesc();
        Map<Long, String> names = applicantNames(list);
        log.info("[广告] 管理列表 现共有 count={} 条（管理端view）", list.size());
        return list.stream()
                .map(banner -> AdApplicationResponse.of(banner, names.get(banner.getApplicantId())))
                .toList();
    }

    @Transactional
    public AdApplicationResponse approve(Long id, String note) {
        AdBanner banner = requirePending(id);
        banner.setStatus(AdStatus.APPROVED);
        banner.setReviewNote(blankToNull(note));
        banner.setReviewedAt(LocalDateTime.now());
        AdBanner saved = adBannerRepository.save(banner);
        // 通过即上线：清缓存让首页立刻拿到
        redisCache.evict(RedisKeys.adCarousel());
        log.info("[广告] 审核通过 id={} title={}", id, saved.getTitle());
        return AdApplicationResponse.from(saved);
    }

    @Transactional
    public AdApplicationResponse reject(Long id, String note) {
        AdBanner banner = requirePending(id);
        banner.setStatus(AdStatus.REJECTED);
        banner.setReviewNote(blankToNull(note));
        banner.setReviewedAt(LocalDateTime.now());
        AdBanner saved = adBannerRepository.save(banner);
        log.info("[广告] 审核驳回 id={} reason={}", id, banner.getReviewNote());
        return AdApplicationResponse.from(saved);
    }

    private AdBanner requirePending(Long id) {
        AdBanner banner = requireBanner(id);
        if (banner.getStatus() != AdStatus.PENDING) {
            throw new BizException("这条申请已经审核过了");
        }
        return banner;
    }

    /**
     * 批量取申请人昵称。
     *
     * <p>与列表装配同一套做法：整页只发一次 Feign，不逐行查；
     * 拿不到就返回空 map，前端回落到「用户 {id}」而不是整页报错。
     */
    private Map<Long, String> applicantNames(List<AdBanner> banners) {
        List<Long> ids = banners.stream()
                .map(AdBanner::getApplicantId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            ApiResponse<List<UserBrief>> response = userApiClient.findBriefs(ids);
            if (response == null || response.getData() == null) {
                return Map.of();
            }
            return response.getData().stream()
                    .filter(brief -> brief.id() != null)
                    .collect(Collectors.toMap(UserBrief::id, brief -> brief.nickname() == null ? "" : brief.nickname(),
                            (a, b) -> a));
        } catch (Exception e) {
            log.warn("[广告] 补全申请人昵称失败，降级为只显示 id：{}", e.getMessage());
            return Map.of();
        }
    }

    // ------------------------------------------------------------------
    // 内部接口：供 auth-service 通过 Feign 聚合（管理台看板）
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PlatformAdStats platformStats() {
        Long clicks = adBannerRepository.sumClickCount();
        PlatformAdStats stats = new PlatformAdStats(
                adBannerRepository.count(),
                adBannerRepository.countByEnabledTrue(),
                clicks == null ? 0L : clicks);
        log.info("[广告] 平台统计 total={}, enabled={}, clicks={}",
                stats.total(), stats.enabled(), stats.clicks());
        return stats;
    }

    private AdBanner requireBanner(Long id) {
        return adBannerRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "广告不存在"));
    }

    private static String resolveImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        String local = LocalFileUrls.sanitizeOptional(imageUrl);
        if (local == null) {
            throw new BizException("广告图必须先上传到本站");
        }
        return local;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
