package com.chengqu.huzhu.ad.service;

import com.chengqu.huzhu.ad.dto.AdQualificationRequest;
import com.chengqu.huzhu.ad.dto.AdQualificationResponse;
import com.chengqu.huzhu.ad.dto.MyQualificationResponse;
import com.chengqu.huzhu.ad.entity.AdQualification;
import com.chengqu.huzhu.ad.entity.AdQualificationStatus;
import com.chengqu.huzhu.ad.repository.AdQualificationRepository;
import com.chengqu.huzhu.api.client.UserApiClient;
import com.chengqu.huzhu.api.dto.UserBrief;
import com.chengqu.huzhu.common.api.ApiResponse;
import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.common.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * 广告位资质状态机单元测试。
 *
 * <p>覆盖四类高风险逻辑：
 * <ol>
 *   <li>提交入口的两道拦截（已有待审 / 已开通），以及「驳回历史要留下」</li>
 *   <li>重新提交要清掉上一轮的审核意见与时间——留着的意见会被当成针对新材料的</li>
 *   <li>越权一律 404（不是 403）：不能让别人拿 id 探测平台上有多少条资质</li>
 *   <li>管理端列表的昵称批量补全（N+1 回归保护）与下游不可用时的降级</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class AdQualificationServiceTest {

    private static final long USER_ID = 21L;
    private static final long OTHER_ID = 88L;

    @Mock
    private AdQualificationRepository qualificationRepository;
    @Mock
    private UserApiClient userApiClient;

    private AdQualificationService qualificationService;

    @BeforeEach
    void setUp() {
        qualificationService = new AdQualificationService(qualificationRepository, userApiClient);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 直接把认证信息放进真实的 SecurityContextHolder，
     * 让 SecurityUtils.currentUser() 走真实代码路径，避免依赖静态 mock。
     */
    private void loginAs(long userId) {
        UserPrincipal principal = new UserPrincipal(userId, "13800000000", "测试用户", "USER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private AdQualification qualification(Long id, Long userId, AdQualificationStatus status) {
        AdQualification qualification = new AdQualification();
        qualification.setId(id);
        qualification.setUserId(userId);
        qualification.setApplicantName("张三");
        qualification.setContact("13800000000");
        qualification.setCompany("某小区便利店");
        qualification.setStatus(status);
        return qualification;
    }

    private AdQualificationRequest request() {
        AdQualificationRequest request = new AdQualificationRequest();
        request.setApplicantName("张三");
        request.setContact("13800000000");
        request.setCompany("某小区便利店");
        request.setCategory("日用百货");
        request.setIntro("小区门口开了三年");
        return request;
    }

    // ------------------------------------------------------------------
    // 提交：两道拦截 + 落库形态
    // ------------------------------------------------------------------

    @Test
    @DisplayName("已有待审申请时重复提交被拒")
    void submit_rejectsWhenPendingExists() {
        loginAs(USER_ID);
        when(qualificationRepository.existsByUserIdAndStatus(USER_ID, AdQualificationStatus.APPROVED)).thenReturn(false);
        when(qualificationRepository.existsByUserIdAndStatus(USER_ID, AdQualificationStatus.PENDING)).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> qualificationService.submit(request()));

        assertEquals(400, ex.getCode());
        assertEquals("已有待审核的资质申请，请等待管理员处理", ex.getMessage());
        verify(qualificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("资质已通过后再次提交被拒，且不必再查一遍 PENDING")
    void submit_rejectsWhenAlreadyApproved() {
        loginAs(USER_ID);
        when(qualificationRepository.existsByUserIdAndStatus(USER_ID, AdQualificationStatus.APPROVED)).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> qualificationService.submit(request()));

        assertEquals(400, ex.getCode());
        assertEquals("你的广告位资质已开通", ex.getMessage());
        verify(qualificationRepository, never()).existsByUserIdAndStatus(USER_ID, AdQualificationStatus.PENDING);
        verify(qualificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("首次提交落库为 PENDING 并挂在当前用户名下")
    void submit_createsPendingRowForCurrentUser() {
        loginAs(USER_ID);
        when(qualificationRepository.save(any(AdQualification.class))).thenAnswer(inv -> inv.getArgument(0));

        AdQualificationResponse response = qualificationService.submit(request());

        assertEquals(AdQualificationStatus.PENDING, response.getStatus());
        assertEquals(Long.valueOf(USER_ID), response.getUserId());
        assertEquals("待审核", response.getStatusLabel());
        assertNull(response.getReviewNote());
    }

    @Test
    @DisplayName("资质图只接受本站相对路径：空着可以，填外链不行")
    void submit_rejectsExternalLicenseImage() {
        loginAs(USER_ID);
        when(qualificationRepository.existsByUserIdAndStatus(USER_ID, AdQualificationStatus.APPROVED)).thenReturn(false);
        when(qualificationRepository.existsByUserIdAndStatus(USER_ID, AdQualificationStatus.PENDING)).thenReturn(false);
        AdQualificationRequest request = request();
        request.setLicenseImage("https://pic.example.com/license.png");

        assertEquals("资质证明图必须先上传到本站",
                assertThrows(BizException.class, () -> qualificationService.submit(request)).getMessage());
        verify(qualificationRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // 我的资质：NONE 是响应层补的哨兵值
    // ------------------------------------------------------------------

    @Test
    @DisplayName("从未提交过时返回 NONE：不是数据库取值，是响应层补的")
    void mine_returnsNoneSentinelWhenNeverSubmitted() {
        loginAs(USER_ID);
        when(qualificationRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .thenReturn(Optional.empty());

        MyQualificationResponse view = qualificationService.mine();

        assertEquals("NONE", view.getStatus());
        assertEquals("未申请", view.getStatusLabel());
        assertNull(view.getQualification());
        assertFalse(view.isQualified());
    }

    @Test
    @DisplayName("APPROVED 才算有资格，且把明细一起下发")
    void mine_marksQualifiedWhenApproved() {
        loginAs(USER_ID);
        when(qualificationRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .thenReturn(Optional.of(qualification(1L, USER_ID, AdQualificationStatus.APPROVED)));

        MyQualificationResponse view = qualificationService.mine();

        assertEquals("APPROVED", view.getStatus());
        assertEquals("已通过", view.getStatusLabel());
        assertTrue(view.isQualified());
        assertEquals(Long.valueOf(1L), view.getQualification().getId());
    }

    @Test
    @DisplayName("待审不算有资格：闸门只认 APPROVED")
    void mine_pendingIsNotQualified() {
        loginAs(USER_ID);
        when(qualificationRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .thenReturn(Optional.of(qualification(2L, USER_ID, AdQualificationStatus.PENDING)));

        MyQualificationResponse pending = qualificationService.mine();
        assertEquals("PENDING", pending.getStatus());
        assertFalse(pending.isQualified());
    }

    @Test
    @DisplayName("被驳回时把上一次的审核意见一起下发，用户才知道改什么")
    void mine_returnsRejectedWithReviewNote() {
        loginAs(USER_ID);
        AdQualification rejected = qualification(3L, USER_ID, AdQualificationStatus.REJECTED);
        rejected.setReviewNote("营业执照看不清");
        when(qualificationRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .thenReturn(Optional.of(rejected));

        MyQualificationResponse view = qualificationService.mine();

        assertEquals("REJECTED", view.getStatus());
        assertFalse(view.isQualified());
        assertEquals("营业执照看不清", view.getQualification().getReviewNote());
    }

    // ------------------------------------------------------------------
    // 审核：驳回写意见 / 重新提交清意见
    // ------------------------------------------------------------------

    @Test
    @DisplayName("驳回写入审核意见与审核时间，并去掉首尾空白")
    void reject_writesReviewNote() {
        AdQualification pending = qualification(3L, USER_ID, AdQualificationStatus.PENDING);
        when(qualificationRepository.findById(3L)).thenReturn(Optional.of(pending));
        when(qualificationRepository.save(pending)).thenReturn(pending);

        AdQualificationResponse response = qualificationService.reject(3L, "  营业执照看不清  ");

        assertEquals(AdQualificationStatus.REJECTED, response.getStatus());
        assertEquals("营业执照看不清", response.getReviewNote());
        assertNotNull(response.getReviewedAt());
    }

    @Test
    @DisplayName("重新提交后回到 PENDING，并清空 reviewNote 与 reviewedAt")
    void update_clearsPreviousReview() {
        loginAs(USER_ID);
        AdQualification rejected = qualification(4L, USER_ID, AdQualificationStatus.REJECTED);
        rejected.setReviewNote("营业执照看不清");
        rejected.setReviewedAt(LocalDateTime.now().minusDays(1));
        when(qualificationRepository.findById(4L)).thenReturn(Optional.of(rejected));
        when(qualificationRepository.save(rejected)).thenReturn(rejected);

        AdQualificationResponse response = qualificationService.update(4L, request());

        assertEquals(AdQualificationStatus.PENDING, response.getStatus());
        // 旧意见是针对**旧材料**写的，留着会让用户以为新内容已经带上了上次的结论
        assertNull(response.getReviewNote());
        assertNull(response.getReviewedAt());
        assertEquals("日用百货", response.getCategory());
    }

    @Test
    @DisplayName("资质已通过后不允许再改：它是管理员的结论，不是用户能改的字段")
    void update_rejectsApprovedQualification() {
        loginAs(USER_ID);
        when(qualificationRepository.findById(5L))
                .thenReturn(Optional.of(qualification(5L, USER_ID, AdQualificationStatus.APPROVED)));

        assertEquals("资质已通过，无需修改",
                assertThrows(BizException.class, () -> qualificationService.update(5L, request())).getMessage());
        verify(qualificationRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // 越权：一律 404
    // ------------------------------------------------------------------

    @Test
    @DisplayName("改别人的资质返回 404 而不是 403")
    void update_rejectsOutsiderWith404() {
        loginAs(OTHER_ID);
        when(qualificationRepository.findById(6L))
                .thenReturn(Optional.of(qualification(6L, USER_ID, AdQualificationStatus.REJECTED)));

        BizException ex = assertThrows(BizException.class, () -> qualificationService.update(6L, request()));

        assertEquals(404, ex.getCode());
        assertEquals("资质申请不存在", ex.getMessage());
        verify(qualificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("撤回别人的资质同样是 404，且不删任何行")
    void withdraw_rejectsOutsiderWith404() {
        loginAs(OTHER_ID);
        when(qualificationRepository.findById(7L))
                .thenReturn(Optional.of(qualification(7L, USER_ID, AdQualificationStatus.PENDING)));

        assertEquals(404, assertThrows(BizException.class, () -> qualificationService.withdraw(7L)).getCode());
        verify(qualificationRepository, never()).delete(any());
    }

    @Test
    @DisplayName("只有待审的资质可撤回")
    void withdraw_onlyPending() {
        loginAs(USER_ID);
        when(qualificationRepository.findById(8L))
                .thenReturn(Optional.of(qualification(8L, USER_ID, AdQualificationStatus.APPROVED)));

        assertEquals("只有待审核的资质申请可以撤回",
                assertThrows(BizException.class, () -> qualificationService.withdraw(8L)).getMessage());
        verify(qualificationRepository, never()).delete(any());
    }

    @Test
    @DisplayName("撤回自己的待审申请会真的删掉那一行")
    void withdraw_deletesOwnPending() {
        loginAs(USER_ID);
        AdQualification pending = qualification(9L, USER_ID, AdQualificationStatus.PENDING);
        when(qualificationRepository.findById(9L)).thenReturn(Optional.of(pending));

        qualificationService.withdraw(9L);

        verify(qualificationRepository).delete(pending);
    }

    @Test
    @DisplayName("已审核过的资质不能再审一次")
    void approve_rejectsAlreadyReviewed() {
        when(qualificationRepository.findById(10L))
                .thenReturn(Optional.of(qualification(10L, USER_ID, AdQualificationStatus.APPROVED)));

        assertEquals("这条资质申请已经审核过了",
                assertThrows(BizException.class, () -> qualificationService.approve(10L, null)).getMessage());
        verify(qualificationRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // 管理端列表：批量补昵称与降级
    // ------------------------------------------------------------------

    @Test
    @DisplayName("整页昵称只发一次 Feign，不逐行查")
    void list_batchesNicknamesInOneCall() {
        when(qualificationRepository.findByStatusOrderByCreatedAtAsc(AdQualificationStatus.PENDING))
                .thenReturn(List.of(qualification(1L, USER_ID, AdQualificationStatus.PENDING),
                        qualification(2L, OTHER_ID, AdQualificationStatus.PENDING)));
        when(userApiClient.findBriefs(anyCollection())).thenReturn(ApiResponse.ok(List.of(
                new UserBrief(USER_ID, "邻居小王", null, null, null, null, null, null),
                new UserBrief(OTHER_ID, "隔壁老李", null, null, null, null, null, null))));

        List<AdQualificationResponse> list = qualificationService.list(AdQualificationStatus.PENDING);

        assertEquals(2, list.size());
        assertEquals("邻居小王", list.get(0).getUserNickname());
        assertEquals("隔壁老李", list.get(1).getUserNickname());
        // 关键断言：无论一页有多少条，批量查询只执行一次
        verify(userApiClient, times(1)).findBriefs(anyCollection());
    }

    @Test
    @DisplayName("auth-service 挂掉时列表照常返回，只是昵称为空")
    void list_degradesWhenAuthServiceUnavailable() {
        when(qualificationRepository.findByStatusOrderByCreatedAtAsc(AdQualificationStatus.PENDING))
                .thenReturn(List.of(qualification(1L, USER_ID, AdQualificationStatus.PENDING)));
        when(userApiClient.findBriefs(anyCollection())).thenThrow(new IllegalStateException("connection refused"));

        List<AdQualificationResponse> list = qualificationService.list(AdQualificationStatus.PENDING);

        assertEquals(1, list.size());
        assertNull(list.get(0).getUserNickname());
        // 兜底渲染靠这个 id，前端回落到「用户 {id}」而不是整页报错
        assertEquals(Long.valueOf(USER_ID), list.get(0).getUserId());
        assertEquals("张三", list.get(0).getApplicantName());
    }

    @Test
    @DisplayName("列表为空时不该白调一次 auth-service")
    void list_skipsFeignForEmptyPage() {
        when(qualificationRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of());

        assertEquals(List.of(), qualificationService.list(null));
        verifyNoInteractions(userApiClient);
    }
}
