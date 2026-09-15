package com.chengqu.huzhu.aid.service;

import com.chengqu.huzhu.aid.dto.AidResponse;
import com.chengqu.huzhu.aid.dto.HelperReviewRequest;
import com.chengqu.huzhu.aid.entity.AidBoard;
import com.chengqu.huzhu.aid.entity.AidRequest;
import com.chengqu.huzhu.aid.entity.AidStatus;
import com.chengqu.huzhu.aid.repository.AidHelperReviewRepository;
import com.chengqu.huzhu.aid.repository.AidRatingRepository;
import com.chengqu.huzhu.aid.repository.AidRequestRepository;
import com.chengqu.huzhu.aid.support.NotifySender;
import com.chengqu.huzhu.aid.support.UserLookup;
import com.chengqu.huzhu.api.dto.NotifyCreateCommand;
import com.chengqu.huzhu.api.dto.UserBrief;
import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.common.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 求助服务单元测试。
 *
 * <p>覆盖两块高风险逻辑：
 * <ol>
 *   <li>求助状态机与越权校验（OPEN → ACCEPTED → DONE / CANCELLED）</li>
 *   <li>列表装配的批量查询（N+1 回归保护）</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class AidServiceTest {

    private static final long VIEWER_ID = 9L;
    private static final long OTHER_ID = 100L;

    @Mock
    private AidRequestRepository aidRequestRepository;
    @Mock
    private AidRatingRepository aidRatingRepository;
    @Mock
    private AidHelperReviewRepository aidHelperReviewRepository;
    @Mock
    private UserLookup userLookup;
    @Mock
    private NotifySender notifySender;

    private AidService aidService;

    @BeforeEach
    void setUp() {
        aidService = new AidService(aidRequestRepository, aidRatingRepository, aidHelperReviewRepository,
                userLookup, notifySender);
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

    private AidRequest aid(long id, long publisherId, Long helperId, AidStatus status) {
        AidRequest aid = new AidRequest();
        aid.setId(id);
        aid.setTitle("测试求助");
        aid.setContent("内容");
        aid.setCategory("代取快递");
        aid.setBoard(AidBoard.NEIGHBORHOOD);
        aid.setAddress("某小区");
        aid.setStatus(status);
        aid.setPublisherId(publisherId);
        aid.setPublisherName("发布者");
        aid.setHelperId(helperId);
        aid.setHelperName(helperId == null ? null : "帮助者");
        return aid;
    }

    // ------------------------------------------------------------------
    // 状态机与越权校验
    // ------------------------------------------------------------------

    @Test
    @DisplayName("不能接自己发布的求助")
    void accept_rejectsSelfAccept() {
        loginAs(VIEWER_ID);
        when(aidRequestRepository.findById(1L)).thenReturn(Optional.of(aid(1L, VIEWER_ID, null, AidStatus.OPEN)));

        BizException ex = assertThrows(BizException.class, () -> aidService.accept(1L));
        assertEquals("不能接自己发布的求助", ex.getMessage());
        verify(aidRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("仅 OPEN 状态可被接单")
    void accept_rejectsNonOpen() {
        loginAs(VIEWER_ID);
        when(aidRequestRepository.findById(1L)).thenReturn(Optional.of(aid(1L, OTHER_ID, OTHER_ID, AidStatus.ACCEPTED)));

        BizException ex = assertThrows(BizException.class, () -> aidService.accept(1L));
        assertEquals("仅开放中的求助可被接单", ex.getMessage());
    }

    @Test
    @DisplayName("接单成功后写入帮助者并置为 ACCEPTED")
    void accept_setsHelperAndStatus() {
        loginAs(VIEWER_ID);
        AidRequest aid = aid(1L, OTHER_ID, null, AidStatus.OPEN);
        when(aidRequestRepository.findById(1L)).thenReturn(Optional.of(aid));
        when(aidRequestRepository.save(aid)).thenReturn(aid);
        when(aidRatingRepository.aggregateByAidIds(anyCollection())).thenReturn(List.of());
        when(aidRatingRepository.findScoresByRaterAndAidIds(any(), anyCollection())).thenReturn(List.of());
        when(aidHelperReviewRepository.findByAidIdIn(anyCollection())).thenReturn(List.of());

        AidResponse response = aidService.accept(1L);

        assertEquals(AidStatus.ACCEPTED, response.getStatus());
        assertEquals(Long.valueOf(VIEWER_ID), response.getHelperId());
        assertEquals("测试用户", response.getHelperName());
    }

    @Test
    @DisplayName("无关用户不能标记完成")
    void complete_rejectsOutsider() {
        loginAs(555L);
        when(aidRequestRepository.findById(1L)).thenReturn(Optional.of(aid(1L, OTHER_ID, 777L, AidStatus.ACCEPTED)));

        BizException ex = assertThrows(BizException.class, () -> aidService.complete(1L));
        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("帮助者可标记完成")
    void complete_allowsHelper() {
        loginAs(VIEWER_ID);
        AidRequest aid = aid(1L, OTHER_ID, VIEWER_ID, AidStatus.ACCEPTED);
        when(aidRequestRepository.findById(1L)).thenReturn(Optional.of(aid));
        when(aidRequestRepository.save(aid)).thenReturn(aid);
        when(aidRatingRepository.aggregateByAidIds(anyCollection())).thenReturn(List.of());
        when(aidRatingRepository.findScoresByRaterAndAidIds(any(), anyCollection())).thenReturn(List.of());
        when(aidHelperReviewRepository.findByAidIdIn(anyCollection())).thenReturn(List.of());

        assertEquals(AidStatus.DONE, aidService.complete(1L).getStatus());
    }

    @Test
    @DisplayName("仅发布者可取消，且仅限 OPEN")
    void cancel_rejectsNonPublisherAndAccepted() {
        loginAs(VIEWER_ID);
        when(aidRequestRepository.findById(1L)).thenReturn(Optional.of(aid(1L, OTHER_ID, null, AidStatus.OPEN)));
        assertEquals(403, assertThrows(BizException.class, () -> aidService.cancel(1L)).getCode());

        when(aidRequestRepository.findById(2L)).thenReturn(Optional.of(aid(2L, VIEWER_ID, OTHER_ID, AidStatus.ACCEPTED)));
        assertEquals("仅开放中的求助可取消",
                assertThrows(BizException.class, () -> aidService.cancel(2L)).getMessage());
    }

    @Test
    @DisplayName("仅发布者可编辑，且仅限 OPEN")
    void update_rejectsNonPublisherAndNonOpen() {
        loginAs(VIEWER_ID);
        when(aidRequestRepository.findById(1L)).thenReturn(Optional.of(aid(1L, OTHER_ID, null, AidStatus.OPEN)));
        assertEquals(403, assertThrows(BizException.class,
                () -> aidService.update(1L, updateRequest())).getCode());

        when(aidRequestRepository.findById(2L)).thenReturn(Optional.of(aid(2L, VIEWER_ID, OTHER_ID, AidStatus.DONE)));
        assertEquals("仅待接单状态可编辑", assertThrows(BizException.class,
                () -> aidService.update(2L, updateRequest())).getMessage());
    }

    @Test
    @DisplayName("进行中的求助，发布者不可直接删除")
    void delete_rejectsAcceptedForPublisher() {
        loginAs(VIEWER_ID);
        when(aidRequestRepository.findById(1L)).thenReturn(Optional.of(aid(1L, VIEWER_ID, OTHER_ID, AidStatus.ACCEPTED)));

        BizException ex = assertThrows(BizException.class, () -> aidService.delete(1L));
        assertTrue(ex.getMessage().contains("进行中的求助不可直接删除"));
        verify(aidRequestRepository, never()).delete(any());
    }

    @Test
    @DisplayName("不能评价自己发布的求助")
    void rate_rejectsPublisher() {
        loginAs(VIEWER_ID);
        when(aidRequestRepository.findById(1L)).thenReturn(Optional.of(aid(1L, VIEWER_ID, OTHER_ID, AidStatus.DONE)));

        assertEquals("不能评价自己发布的求助",
                assertThrows(BizException.class, () -> aidService.rate(1L, scoreRequest(5))).getMessage());
    }

    @Test
    @DisplayName("帮助方评价仅限发布者、且必须在完成后")
    void reviewHelper_enforcesPublisherAndDoneStatus() {
        loginAs(VIEWER_ID);
        when(aidRequestRepository.findById(1L)).thenReturn(Optional.of(aid(1L, VIEWER_ID, OTHER_ID, AidStatus.ACCEPTED)));
        assertEquals("互助完成后才能评价帮助方",
                assertThrows(BizException.class, () -> aidService.reviewHelper(1L, reviewRequest(5))).getMessage());

        loginAs(OTHER_ID);
        when(aidRequestRepository.findById(2L)).thenReturn(Optional.of(aid(2L, VIEWER_ID, OTHER_ID, AidStatus.DONE)));
        assertEquals(403, assertThrows(BizException.class,
                () -> aidService.reviewHelper(2L, reviewRequest(5))).getCode());
    }

    // ------------------------------------------------------------------
    // N+1 回归保护
    // ------------------------------------------------------------------

    @Test
    @DisplayName("列表装配应使用批量查询：条数增加不会增加查询次数")
    void list_assemblesRatingsWithFixedNumberOfQueries() {
        loginAs(VIEWER_ID);

        // 第 1 条：当前用户是发布者、已完成、有评分与我的评分 → 可评价帮助方
        AidRequest rated = aid(1L, VIEWER_ID, OTHER_ID, AidStatus.DONE);
        // 第 2 条：他人发布、进行中、无评分
        AidRequest unrated = aid(2L, OTHER_ID, null, AidStatus.OPEN);

        Page<AidRequest> source = new PageImpl<>(List.of(rated, unrated), PageRequest.of(0, 10), 2);
        when(aidRequestRepository.findByBoard(eq(AidBoard.NEIGHBORHOOD), any(Pageable.class))).thenReturn(source);
        when(aidRatingRepository.aggregateByAidIds(anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 4.5d, 2L}));
        when(aidRatingRepository.findScoresByRaterAndAidIds(eq(VIEWER_ID), anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 5}));
        when(aidHelperReviewRepository.findByAidIdIn(anyCollection())).thenReturn(List.of());

        Page<AidResponse> result = aidService.list(null, null, false, null, PageRequest.of(0, 10));

        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());

        AidResponse first = result.getContent().get(0);
        assertEquals(Double.valueOf(4.5d), first.getRatingAvg());
        assertEquals(Long.valueOf(2L), first.getRatingCount());
        assertEquals(Integer.valueOf(5), first.getMyScore());
        assertTrue(first.isCanReviewHelper());

        AidResponse second = result.getContent().get(1);
        assertEquals(Double.valueOf(0d), second.getRatingAvg());
        assertEquals(Long.valueOf(0L), second.getRatingCount());
        assertNull(second.getMyScore());
        assertFalse(second.isCanReviewHelper());

        // 关键断言：无论页面里有多少条求助，批量查询各只执行一次
        verify(aidRatingRepository, times(1)).aggregateByAidIds(anyCollection());
        verify(aidRatingRepository, times(1)).findScoresByRaterAndAidIds(any(), anyCollection());
        verify(aidHelperReviewRepository, times(1)).findByAidIdIn(anyCollection());
        // 且不应再退回逐条查询
        verify(aidRatingRepository, never()).findByAidIdAndRaterId(any(), any());
        verify(aidHelperReviewRepository, never()).findByAidId(any());
    }

    @Test
    @DisplayName("空页面不应触发批量查询")
    void list_skipsBatchQueriesForEmptyPage() {
        // 空页面时装配层在取当前用户之前就返回，因此这里不需要（也不应）stub 登录用户
        when(aidRequestRepository.findByBoard(eq(AidBoard.NEIGHBORHOOD), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        Page<AidResponse> result = aidService.list(null, null, false, null, PageRequest.of(0, 10));

        assertEquals(0, result.getTotalElements());
        verifyNoInteractions(aidRatingRepository, aidHelperReviewRepository);
    }

    // ------------------------------------------------------------------
    // 站内通知：动作成功后才写入，且不阻塞主流程
    // ------------------------------------------------------------------

    @Test
    @DisplayName("接单成功：通知发布者，触发者是接单的帮助者")
    void accept_notifiesPublisher() {
        loginAs(VIEWER_ID);
        AidRequest aid = aid(1L, OTHER_ID, null, AidStatus.OPEN);
        stubAssembly(aid);

        aidService.accept(1L);

        NotifyCreateCommand command = capturedNotification();
        assertEquals(Long.valueOf(OTHER_ID), command.userId(), "接收者应是发布者");
        assertEquals("AID_ACCEPTED", command.type());
        assertEquals("AID", command.refType());
        assertEquals(Long.valueOf(1L), command.refId());
        assertEquals(Long.valueOf(VIEWER_ID), command.actorId(), "触发者应是接单的帮助者");
        assertEquals("测试用户", command.actorName());
    }

    @Test
    @DisplayName("帮助者标记完成：通知发布者")
    void complete_notifiesPublisherWhenHelperOperates() {
        loginAs(VIEWER_ID);
        AidRequest aid = aid(1L, OTHER_ID, VIEWER_ID, AidStatus.ACCEPTED);
        stubAssembly(aid);

        aidService.complete(1L);

        NotifyCreateCommand command = capturedNotification();
        assertEquals(Long.valueOf(OTHER_ID), command.userId());
        assertEquals("AID_COMPLETED", command.type());
    }

    @Test
    @DisplayName("发布者标记完成：通知帮助者（两方互不重复）")
    void complete_notifiesHelperWhenPublisherOperates() {
        loginAs(OTHER_ID);
        AidRequest aid = aid(1L, OTHER_ID, VIEWER_ID, AidStatus.ACCEPTED);
        stubAssembly(aid);

        aidService.complete(1L);

        NotifyCreateCommand command = capturedNotification();
        assertEquals(Long.valueOf(VIEWER_ID), command.userId());
        assertEquals("AID_COMPLETED", command.type());
    }

    @Test
    @DisplayName("求助方评价：通知帮助者，正文带评分")
    void reviewHelper_notifiesHelperWithScore() {
        loginAs(OTHER_ID);
        AidRequest aid = aid(1L, OTHER_ID, VIEWER_ID, AidStatus.DONE);
        when(aidRequestRepository.findById(1L)).thenReturn(Optional.of(aid));
        when(aidHelperReviewRepository.findByAidId(1L)).thenReturn(Optional.empty());
        when(aidHelperReviewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(aidRatingRepository.aggregateByAidIds(anyCollection())).thenReturn(List.of());
        when(aidRatingRepository.findScoresByRaterAndAidIds(any(), anyCollection())).thenReturn(List.of());
        when(aidHelperReviewRepository.findByAidIdIn(anyCollection())).thenReturn(List.of());

        aidService.reviewHelper(1L, reviewRequest(5));

        NotifyCreateCommand command = capturedNotification();
        assertEquals(Long.valueOf(VIEWER_ID), command.userId(), "接收者应是帮助者");
        assertEquals("AID_RATED", command.type());
        assertEquals(Long.valueOf(1L), command.refId());
        assertTrue(command.content().contains("5"), "正文应带上评分，便于不点开也能看到");
    }

    // ------------------------------------------------------------------
    // 跨服务补全用户信息（Feign）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("列表应通过 Feign 批量补全发布者与帮助者信息，且只调用 1 次")
    void list_fillsUserInfoViaFeignInOneCall() {
        loginAs(VIEWER_ID);
        AidRequest aid = aid(1L, OTHER_ID, VIEWER_ID, AidStatus.DONE);
        when(aidRequestRepository.findByBoard(eq(AidBoard.NEIGHBORHOOD), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(aid), PageRequest.of(0, 10), 1));
        when(aidRatingRepository.aggregateByAidIds(anyCollection())).thenReturn(List.of());
        when(aidRatingRepository.findScoresByRaterAndAidIds(any(), anyCollection())).thenReturn(List.of());
        when(aidHelperReviewRepository.findByAidIdIn(anyCollection())).thenReturn(List.of());

        Map<Long, UserBrief> users = Map.of(
                OTHER_ID, new UserBrief(OTHER_ID, "发布者新昵称", "https://cdn.example.com/pub.png",
                        "ONLINE", false, null, 4.5, 2L),
                VIEWER_ID, new UserBrief(VIEWER_ID, "帮助者新昵称", "https://cdn.example.com/helper.png",
                        "BUSY", false, null, 5.0, 1L));
        when(userLookup.byIds(anyCollection())).thenReturn(users);

        AidResponse response = aidService.list(null, null, false, null, PageRequest.of(0, 10))
                .getContent().get(0);

        // 实体里存的是过期的快照「发布者」「帮助者」，这里应被权威数据覆盖
        assertEquals("发布者新昵称", response.getPublisherName());
        assertEquals("https://cdn.example.com/pub.png", response.getPublisherAvatar());
        assertEquals("帮助者新昵称", response.getHelperName());
        assertEquals("https://cdn.example.com/helper.png", response.getHelperAvatar());

        // 关键：一页数据只发起一次跨服务调用
        verify(userLookup, times(1)).byIds(anyCollection());
    }

    @Test
    @DisplayName("用户服务不可用时降级：保留本地冗余昵称，头像留空")
    void list_degradesGracefullyWhenUserServiceUnavailable() {
        loginAs(VIEWER_ID);
        AidRequest aid = aid(1L, OTHER_ID, null, AidStatus.OPEN);
        when(aidRequestRepository.findByBoard(eq(AidBoard.NEIGHBORHOOD), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(aid), PageRequest.of(0, 10), 1));
        when(aidRatingRepository.aggregateByAidIds(anyCollection())).thenReturn(List.of());
        when(aidRatingRepository.findScoresByRaterAndAidIds(any(), anyCollection())).thenReturn(List.of());
        when(aidHelperReviewRepository.findByAidIdIn(anyCollection())).thenReturn(List.of());
        // UserLookup 内部已捕获异常并返回空 Map，这里模拟降级结果
        when(userLookup.byIds(anyCollection())).thenReturn(Map.of());

        AidResponse response = aidService.list(null, null, false, null, PageRequest.of(0, 10))
                .getContent().get(0);

        assertEquals("发布者", response.getPublisherName(), "应回退到实体上的冗余昵称");
        assertNull(response.getPublisherAvatar(), "取不到头像时应留空，由前端兜底");
    }

    @Test
    @DisplayName("我的求助列表同样使用批量装配")
    void myPublished_usesBatchAssembly() {        loginAs(VIEWER_ID);
        AidRequest mine = aid(1L, VIEWER_ID, null, AidStatus.OPEN);
        when(aidRequestRepository.findByPublisherId(eq(VIEWER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(mine), PageRequest.of(0, 10), 1));
        when(aidRatingRepository.aggregateByAidIds(anyCollection())).thenReturn(List.of());
        when(aidRatingRepository.findScoresByRaterAndAidIds(any(), anyCollection())).thenReturn(List.of());
        when(aidHelperReviewRepository.findByAidIdIn(anyCollection())).thenReturn(List.of());

        assertEquals(1, aidService.myPublished(PageRequest.of(0, 10)).getTotalElements());
        verify(aidRatingRepository, times(1)).aggregateByAidIds(anyCollection());
    }

    private com.chengqu.huzhu.aid.dto.UpdateAidRequest updateRequest() {
        com.chengqu.huzhu.aid.dto.UpdateAidRequest request = new com.chengqu.huzhu.aid.dto.UpdateAidRequest();
        request.setTitle("新标题");
        request.setContent("新内容");
        request.setCategory("代取快递");
        request.setAddress("新地址");
        return request;
    }

    private com.chengqu.huzhu.aid.dto.ScoreRequest scoreRequest(int score) {
        com.chengqu.huzhu.aid.dto.ScoreRequest request = new com.chengqu.huzhu.aid.dto.ScoreRequest();
        request.setScore(score);
        return request;
    }

    private HelperReviewRequest reviewRequest(int score) {
        HelperReviewRequest request = new HelperReviewRequest();
        request.setScore(score);
        request.setContent("很好");
        return request;
    }

    /** 成功路径下装配 AidResponse 的固定桩：查实体、保存实体、三次批量查询。 */
    private void stubAssembly(AidRequest aid) {
        when(aidRequestRepository.findById(aid.getId())).thenReturn(Optional.of(aid));
        when(aidRequestRepository.save(aid)).thenReturn(aid);
        when(aidRatingRepository.aggregateByAidIds(anyCollection())).thenReturn(List.of());
        when(aidRatingRepository.findScoresByRaterAndAidIds(any(), anyCollection())).thenReturn(List.of());
        when(aidHelperReviewRepository.findByAidIdIn(anyCollection())).thenReturn(List.of());
    }

    /** 取出唯一一次通知投递的命令对象，同时断言「确实发了通知且只发了一条」。 */
    private NotifyCreateCommand capturedNotification() {
        ArgumentCaptor<NotifyCreateCommand> captor = ArgumentCaptor.forClass(NotifyCreateCommand.class);
        verify(notifySender).sendAfterCommit(captor.capture());
        return captor.getValue();
    }
}
