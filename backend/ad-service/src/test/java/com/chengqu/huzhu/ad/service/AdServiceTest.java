package com.chengqu.huzhu.ad.service;

import com.chengqu.huzhu.ad.dto.AdApplicationRequest;
import com.chengqu.huzhu.ad.dto.AdApplicationResponse;
import com.chengqu.huzhu.ad.entity.AdBanner;
import com.chengqu.huzhu.ad.entity.AdQualificationStatus;
import com.chengqu.huzhu.ad.entity.AdStatus;
import com.chengqu.huzhu.ad.repository.AdBannerRepository;
import com.chengqu.huzhu.ad.repository.AdQualificationRepository;
import com.chengqu.huzhu.api.client.UserApiClient;
import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.common.file.LocalFileUrls;
import com.chengqu.huzhu.common.redis.RedisCache;
import com.chengqu.huzhu.common.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 广告位申请的「资质闸门」单元测试。
 *
 * <p>只盯 apply 一处：资质是后加的准入条件，一旦被短路、挪位或顺手也套到
 * 存量数据上，要么广告位重新对所有登录用户开放，要么正在轮播的广告莫名失效。
 * 所以这里把「拦得住」「放得行」「只拦新提交」三件事分别钉死。
 */
@ExtendWith(MockitoExtension.class)
class AdServiceTest {

    private static final long APPLICANT_ID = 7L;

    @Mock
    private AdBannerRepository adBannerRepository;
    @Mock
    private AdQualificationRepository adQualificationRepository;
    @Mock
    private RedisCache redisCache;
    @Mock
    private UserApiClient userApiClient;

    private AdService adService;

    @BeforeEach
    void setUp() {
        adService = new AdService(adBannerRepository, adQualificationRepository, redisCache, userApiClient);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(long userId) {
        UserPrincipal principal = new UserPrincipal(userId, "13800000000", "测试用户", "USER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private AdApplicationRequest request() {
        AdApplicationRequest request = new AdApplicationRequest();
        request.setTitle("小区便利店广告");
        request.setImageUrl(LocalFileUrls.PREFIX + "abc.png");
        return request;
    }

    @Test
    @DisplayName("没有资质时提交广告位申请被 403 拦下，且不落库")
    void apply_rejectsUnqualifiedApplicant() {
        loginAs(APPLICANT_ID);
        when(adQualificationRepository.existsByUserIdAndStatus(APPLICANT_ID, AdQualificationStatus.APPROVED))
                .thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> adService.apply(request()));

        assertEquals(403, ex.getCode());
        assertEquals("请先开通广告位资质，通过审核后才能提交广告位申请", ex.getMessage());
        verify(adBannerRepository, never()).save(any());
    }

    @Test
    @DisplayName("资质已通过时放行，申请落为 PENDING 并挂在申请人名下")
    void apply_allowsQualifiedApplicant() {
        loginAs(APPLICANT_ID);
        when(adQualificationRepository.existsByUserIdAndStatus(APPLICANT_ID, AdQualificationStatus.APPROVED))
                .thenReturn(true);
        when(adBannerRepository.save(any(AdBanner.class))).thenAnswer(inv -> inv.getArgument(0));

        AdApplicationResponse response = adService.apply(request());

        assertEquals(AdStatus.PENDING, response.getStatus());
        ArgumentCaptor<AdBanner> captor = ArgumentCaptor.forClass(AdBanner.class);
        verify(adBannerRepository).save(captor.capture());
        assertEquals(Long.valueOf(APPLICANT_ID), captor.getValue().getApplicantId());
        assertEquals(AdStatus.PENDING, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("闸门只拦新提交：审核存量申请时不查资质，也不会因此失败")
    void approve_existingApplicationIsNotBlockedByGate() {
        AdBanner pending = AdBanner.builder()
                .id(1L)
                .title("闸门上线前提交的广告")
                .status(AdStatus.PENDING)
                .applicantId(APPLICANT_ID)
                .build();
        when(adBannerRepository.findById(1L)).thenReturn(Optional.of(pending));
        when(adBannerRepository.save(pending)).thenReturn(pending);

        assertEquals(AdStatus.APPROVED, adService.approve(1L, "补审通过").getStatus());
        verifyNoInteractions(adQualificationRepository);
    }

    @Test
    @DisplayName("存量申请被驳回后改完重交，同样不查资质：闸门只拦「新提交」")
    void updateApplication_existingApplicationIsNotBlockedByGate() {
        loginAs(APPLICANT_ID);
        AdBanner rejected = AdBanner.builder()
                .id(3L)
                .title("闸门上线前提交的广告")
                .status(AdStatus.REJECTED)
                .applicantId(APPLICANT_ID)
                .build();
        when(adBannerRepository.findById(3L)).thenReturn(Optional.of(rejected));
        when(adBannerRepository.save(rejected)).thenReturn(rejected);

        AdApplicationResponse response = adService.updateApplication(3L, request());

        assertEquals(AdStatus.PENDING, response.getStatus());
        verifyNoInteractions(adQualificationRepository);
    }
}
