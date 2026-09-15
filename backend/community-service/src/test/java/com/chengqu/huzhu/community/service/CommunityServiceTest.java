package com.chengqu.huzhu.community.service;

import com.chengqu.huzhu.api.dto.NotifyCreateCommand;
import com.chengqu.huzhu.api.dto.UserBrief;
import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.common.security.UserPrincipal;
import com.chengqu.huzhu.community.dto.CreateCommentRequest;
import com.chengqu.huzhu.community.dto.PostResponse;
import com.chengqu.huzhu.community.entity.Post;
import com.chengqu.huzhu.community.entity.PostChannel;
import com.chengqu.huzhu.community.entity.PostKind;
import com.chengqu.huzhu.community.repository.PostCommentRepository;
import com.chengqu.huzhu.community.repository.PostLikeRepository;
import com.chengqu.huzhu.community.repository.PostRepository;
import com.chengqu.huzhu.community.repository.PostViewRepository;
import com.chengqu.huzhu.community.support.NotifySender;
import com.chengqu.huzhu.community.support.UserExclusionLookup;
import com.chengqu.huzhu.community.support.UserLookup;
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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 社区服务单元测试。
 *
 * <p>覆盖两块高风险逻辑：
 * <ol>
 *   <li>列表装配的批量查询（N+1 回归保护）</li>
 *   <li>点赞状态翻转与浏览去重</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    private static final long VIEWER_ID = 9L;
    private static final long OTHER_ID = 100L;

    @Mock
    private PostRepository postRepository;
    @Mock
    private PostLikeRepository postLikeRepository;
    @Mock
    private PostCommentRepository postCommentRepository;
    @Mock
    private PostViewRepository postViewRepository;
    @Mock
    private UserLookup userLookup;
    @Mock
    private UserExclusionLookup exclusionLookup;
    @Mock
    private NotifySender notifySender;

    private CommunityService communityService;

    @BeforeEach
    void setUp() {
        communityService = new CommunityService(postRepository, postLikeRepository, postCommentRepository,
                postViewRepository, userLookup, exclusionLookup, notifySender);
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

    private Post post(long id, PostChannel channel, int likeCount) {
        Post post = new Post();
        post.setId(id);
        post.setContent("动态内容");
        post.setAuthorId(OTHER_ID);
        post.setAuthorName("作者");
        post.setChannel(channel);
        post.setLikeCount(likeCount);
        post.setHeatScore(0);
        post.setViewCount(0);
        return post;
    }

    // ------------------------------------------------------------------
    // N+1 回归保护
    // ------------------------------------------------------------------

    @Test
    @DisplayName("列表装配应使用批量查询：条数增加不会增加查询次数")
    void listPosts_assemblesLikesAndCommentCountsInBatch() {
        loginAs(VIEWER_ID);

        Post liked = post(1L, PostChannel.PLAZA, 3);
        Post notLiked = post(2L, PostChannel.PLAZA, 0);

        when(postRepository.searchByChannels(anyCollection(), anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(liked, notLiked), PageRequest.of(0, 10), 2));
        when(postLikeRepository.findLikedPostIds(eq(VIEWER_ID), anyCollection())).thenReturn(List.of(1L));
        when(postCommentRepository.countByPostIds(anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 5L}));

        Page<PostResponse> page = communityService.listPosts("PLAZA", null, PageRequest.of(0, 10));

        assertEquals(2, page.getTotalElements());

        PostResponse first = page.getContent().get(0);
        assertTrue(first.getLiked());
        assertEquals(Long.valueOf(5L), first.getCommentCount());
        assertEquals(Integer.valueOf(3), first.getLikeCount());

        PostResponse second = page.getContent().get(1);
        assertFalse(second.getLiked());
        assertEquals(Long.valueOf(0L), second.getCommentCount());

        // 关键断言：批量查询各只执行一次，且不再逐条查询
        verify(postLikeRepository, times(1)).findLikedPostIds(any(), anyCollection());
        verify(postCommentRepository, times(1)).countByPostIds(anyCollection());
        verify(postLikeRepository, never()).existsByPostIdAndUserId(any(), any());
        verify(postCommentRepository, never()).countByPostId(any());
    }

    // ------------------------------------------------------------------
    // 跨服务补全作者信息（Feign）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("列表应通过 Feign 批量补全作者信息，且只调用 1 次")
    void listPosts_fillsAuthorInfoViaFeignInOneCall() {
        loginAs(VIEWER_ID);
        Post post = post(1L, PostChannel.PLAZA, 0);
        when(postRepository.searchByChannels(anyCollection(), anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 10), 1));
        when(postLikeRepository.findLikedPostIds(any(), anyCollection())).thenReturn(List.of());
        when(postCommentRepository.countByPostIds(anyCollection())).thenReturn(List.of());
        when(userLookup.byIds(anyCollection())).thenReturn(Map.of(
                OTHER_ID, new UserBrief(OTHER_ID, "作者新昵称", "https://cdn.example.com/author.png",
                        "ONLINE", false, null, 4.0, 1L)));

        PostResponse response = communityService.listPosts("PLAZA", null, PageRequest.of(0, 10))
                .getContent().get(0);

        // 实体里存的是过期快照「作者」，应被权威数据覆盖
        assertEquals("作者新昵称", response.getAuthorName());
        assertEquals("https://cdn.example.com/author.png", response.getAuthorAvatar());
        verify(userLookup, times(1)).byIds(anyCollection());
    }

    @Test
    @DisplayName("用户服务不可用时降级：保留本地冗余昵称，头像留空")
    void listPosts_degradesGracefullyWhenUserServiceUnavailable() {
        loginAs(VIEWER_ID);
        Post post = post(1L, PostChannel.PLAZA, 0);
        when(postRepository.searchByChannels(anyCollection(), anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 10), 1));
        when(postLikeRepository.findLikedPostIds(any(), anyCollection())).thenReturn(List.of());
        when(postCommentRepository.countByPostIds(anyCollection())).thenReturn(List.of());
        when(userLookup.byIds(anyCollection())).thenReturn(Map.of());

        PostResponse response = communityService.listPosts("PLAZA", null, PageRequest.of(0, 10))
                .getContent().get(0);

        assertEquals("作者", response.getAuthorName(), "应回退到实体上的冗余昵称");
        assertNull(response.getAuthorAvatar(), "取不到头像时应留空，由前端兜底");
    }

    @Test
    @DisplayName("按作者查询同样使用批量装配")
    void listByAuthor_usesBatchAssembly() {
        loginAs(VIEWER_ID);
        when(postRepository.searchByAuthor(eq(OTHER_ID), anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post(1L, PostChannel.COMMUNITY, 1)), PageRequest.of(0, 10), 1));
        when(postLikeRepository.findLikedPostIds(any(), anyCollection())).thenReturn(List.of());
        when(postCommentRepository.countByPostIds(anyCollection())).thenReturn(List.of());

        assertEquals(1, communityService.listByAuthor(OTHER_ID, PageRequest.of(0, 10)).getTotalElements());
        verify(postCommentRepository, times(1)).countByPostIds(anyCollection());
        verify(postCommentRepository, never()).countByPostId(any());
    }

    @Test
    @DisplayName("空页面不应触发批量查询")
    void listPosts_skipsBatchQueriesForEmptyPage() {
        // 空页面时装配层在取当前用户之前就返回，因此这里不需要（也不应）stub 登录用户
        when(postRepository.searchByChannels(anyCollection(), anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        assertEquals(0, communityService.listPosts("PLAZA", null, PageRequest.of(0, 10)).getTotalElements());
        verifyNoInteractions(postLikeRepository, postCommentRepository);
    }

    // ------------------------------------------------------------------
    // 模块与种类筛选语义
    // ------------------------------------------------------------------

    @Test
    @DisplayName("按种类筛选：命中 searchByChannelsAndKind，不再走无种类查询")
    void listPosts_filtersByKind() {
        loginAs(VIEWER_ID);
        when(postRepository.searchByChannelsAndKind(
                anyCollection(), eq(PostKind.RANT), anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        communityService.listPosts("CAMPUS", "RANT", PageRequest.of(0, 10));

        verify(postRepository).searchByChannelsAndKind(
                anyCollection(), eq(PostKind.RANT), anyCollection(), any(Pageable.class));
        verify(postRepository, never()).searchByChannels(anyCollection(), anyCollection(), any(Pageable.class));
    }

    @Test
    @DisplayName("非法种类编码按「不筛选」处理，而不是报错")
    void listPosts_ignoresInvalidKind() {
        loginAs(VIEWER_ID);
        when(postRepository.searchByChannels(anyCollection(), anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        communityService.listPosts("CAMPUS", "不存在的种类", PageRequest.of(0, 10));

        verify(postRepository).searchByChannels(anyCollection(), anyCollection(), any(Pageable.class));
    }

    @Test
    @DisplayName("「发现」模块聚合邻里动态与生活广场两个频道")
    void listPosts_discoverModuleAggregatesTwoChannels() {
        loginAs(VIEWER_ID);
        when(postRepository.searchByChannels(anyCollection(), anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        communityService.listPosts("DISCOVER", null, PageRequest.of(0, 10));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<PostChannel>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(postRepository).searchByChannels(captor.capture(), anyCollection(), any(Pageable.class));
        assertTrue(captor.getValue().containsAll(List.of(PostChannel.COMMUNITY, PostChannel.PLAZA)),
                "发现模块应同时包含 COMMUNITY 与 PLAZA 两个频道");
    }

    // ------------------------------------------------------------------
    // 点赞与浏览
    // ------------------------------------------------------------------

    @Test
    @DisplayName("未点赞时点赞：新增记录并累加计数与热度")
    void toggleLike_addsLike() {
        loginAs(VIEWER_ID);
        Post target = post(1L, PostChannel.PLAZA, 0);
        when(postRepository.findById(1L)).thenReturn(Optional.of(target));
        when(postLikeRepository.findByPostIdAndUserId(1L, VIEWER_ID)).thenReturn(Optional.empty());
        when(postRepository.save(target)).thenReturn(target);
        when(postCommentRepository.countByPostId(1L)).thenReturn(0L);

        PostResponse response = communityService.toggleLike(1L);

        assertTrue(response.getLiked());
        assertEquals(Integer.valueOf(1), response.getLikeCount());
        assertEquals(Integer.valueOf(5), response.getHeatScore());
        verify(postLikeRepository).save(any());
    }

    @Test
    @DisplayName("已点赞时再点赞：取消点赞并回退计数与热度")
    void toggleLike_removesLike() {
        loginAs(VIEWER_ID);
        Post target = post(1L, PostChannel.PLAZA, 1);
        target.setHeatScore(5);
        when(postRepository.findById(1L)).thenReturn(Optional.of(target));
        when(postLikeRepository.findByPostIdAndUserId(1L, VIEWER_ID))
                .thenReturn(Optional.of(new com.chengqu.huzhu.community.entity.PostLike()));
        when(postRepository.save(target)).thenReturn(target);
        when(postCommentRepository.countByPostId(1L)).thenReturn(0L);

        PostResponse response = communityService.toggleLike(1L);

        assertFalse(response.getLiked());
        assertEquals(Integer.valueOf(0), response.getLikeCount());
        assertEquals(Integer.valueOf(0), response.getHeatScore());
        verify(postLikeRepository).delete(any());
    }

    @Test
    @DisplayName("重复浏览只计一次")
    void recordView_countsOnlyFirstTime() {
        loginAs(VIEWER_ID);
        Post target = post(1L, PostChannel.PLAZA, 0);
        when(postRepository.findById(1L)).thenReturn(Optional.of(target));
        when(postViewRepository.existsByPostIdAndUserId(1L, VIEWER_ID)).thenReturn(true);
        when(postLikeRepository.existsByPostIdAndUserId(1L, VIEWER_ID)).thenReturn(false);
        when(postCommentRepository.countByPostId(1L)).thenReturn(0L);

        PostResponse response = communityService.recordView(1L);

        assertFalse(response.getViewCounted());
        assertEquals(Integer.valueOf(0), response.getViewCount());
        verify(postViewRepository, never()).save(any());
    }

    @Test
    @DisplayName("非作者不能删除他人动态")
    void deletePost_rejectsNonAuthor() {
        loginAs(VIEWER_ID);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post(1L, PostChannel.PLAZA, 0)));

        assertEquals(403, assertThrows(BizException.class, () -> communityService.deletePost(1L)).getCode());
        verify(postRepository, never()).delete(any());
    }

    // ------------------------------------------------------------------
    // 站内通知：评论 / 点赞成功后写入，且不阻塞主流程
    // ------------------------------------------------------------------

    @Test
    @DisplayName("评论他人动态：通知作者，正文是评论内容")
    void addComment_notifiesAuthor() {
        loginAs(VIEWER_ID);
        Post target = post(1L, PostChannel.PLAZA, 0);
        stubCommentWrite(target);

        communityService.addComment(1L, commentRequest("一楼说得对"));

        NotifyCreateCommand command = capturedNotification();
        assertEquals(Long.valueOf(OTHER_ID), command.userId(), "接收者应是动态作者");
        assertEquals("POST_COMMENTED", command.type());
        assertEquals("POST", command.refType());
        assertEquals(Long.valueOf(1L), command.refId());
        assertEquals("一楼说得对", command.content());
        assertEquals(Long.valueOf(VIEWER_ID), command.actorId());
    }

    @Test
    @DisplayName("评论自己的动态：不发通知")
    void addComment_skipsSelfComment() {
        loginAs(OTHER_ID);
        Post target = post(1L, PostChannel.PLAZA, 0);
        stubCommentWrite(target);

        communityService.addComment(1L, commentRequest("自问自答"));

        verifyNoInteractions(notifySender);
    }

    @Test
    @DisplayName("超长评论：通知正文截断到 100 字以内")
    void addComment_truncatesLongContent() {
        loginAs(VIEWER_ID);
        Post target = post(1L, PostChannel.PLAZA, 0);
        stubCommentWrite(target);

        communityService.addComment(1L, commentRequest("很".repeat(300)));

        String content = capturedNotification().content();
        // 通知服务对正文有长度上限，超长会让整条通知写入失败；宁可截断也不能丢提醒
        assertTrue(content.length() <= 100, "截断后长度应不超过 100，实际 " + content.length());
    }

    @Test
    @DisplayName("点赞他人动态：通知作者")
    void toggleLike_notifiesAuthor() {
        loginAs(VIEWER_ID);
        Post target = post(1L, PostChannel.PLAZA, 0);
        stubLikeWrite(target, false);

        communityService.toggleLike(1L);

        NotifyCreateCommand command = capturedNotification();
        assertEquals(Long.valueOf(OTHER_ID), command.userId());
        assertEquals("POST_LIKED", command.type());
        assertEquals(Long.valueOf(1L), command.refId());
    }

    @Test
    @DisplayName("取消点赞：不发通知")
    void toggleLike_doesNotNotifyOnUnlike() {
        loginAs(VIEWER_ID);
        Post target = post(1L, PostChannel.PLAZA, 1);
        stubLikeWrite(target, true);

        communityService.toggleLike(1L);

        verifyNoInteractions(notifySender);
    }

    @Test
    @DisplayName("自己给自己点赞：不发通知")
    void toggleLike_skipsSelfLike() {
        loginAs(VIEWER_ID);
        Post target = post(1L, PostChannel.PLAZA, 0);
        target.setAuthorId(VIEWER_ID);
        stubLikeWrite(target, false);

        communityService.toggleLike(1L);

        verifyNoInteractions(notifySender);
    }

    /** 评论写入的固定桩：查动态、保存评论、保存动态。 */
    private void stubCommentWrite(Post post) {
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(postCommentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(postRepository.save(post)).thenReturn(post);
    }

    /** 点赞写入的固定桩；{@code alreadyLiked} 决定走「取消点赞」还是「新增点赞」分支。 */
    private void stubLikeWrite(Post post, boolean alreadyLiked) {
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(postLikeRepository.findByPostIdAndUserId(post.getId(), VIEWER_ID)).thenReturn(
                alreadyLiked
                        ? Optional.of(new com.chengqu.huzhu.community.entity.PostLike())
                        : Optional.empty());
        when(postRepository.save(post)).thenReturn(post);
        when(postCommentRepository.countByPostId(post.getId())).thenReturn(0L);
    }

    private CreateCommentRequest commentRequest(String content) {
        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent(content);
        return request;
    }

    /** 取出唯一一次通知投递的命令对象，同时断言「确实发了通知且只发了一条」。 */
    private NotifyCreateCommand capturedNotification() {
        ArgumentCaptor<NotifyCreateCommand> captor = ArgumentCaptor.forClass(NotifyCreateCommand.class);
        verify(notifySender).sendAfterCommit(captor.capture());
        return captor.getValue();
    }
}
