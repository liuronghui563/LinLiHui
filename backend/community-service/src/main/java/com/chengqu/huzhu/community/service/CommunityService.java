package com.chengqu.huzhu.community.service;

import com.chengqu.huzhu.api.dto.NotifyCreateCommand;
import com.chengqu.huzhu.api.dto.PlatformPostStats;
import com.chengqu.huzhu.api.dto.UserBrief;
import com.chengqu.huzhu.api.dto.UserPostStats;
import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.common.file.LocalFileUrls;
import com.chengqu.huzhu.common.security.SecurityUtils;
import com.chengqu.huzhu.common.security.UserPrincipal;
import com.chengqu.huzhu.community.dto.CommentResponse;
import com.chengqu.huzhu.community.dto.CommunityStatsResponse;
import com.chengqu.huzhu.community.dto.CreateCommentRequest;
import com.chengqu.huzhu.community.dto.CreatePostRequest;
import com.chengqu.huzhu.community.dto.PostResponse;
import com.chengqu.huzhu.community.dto.UpdatePostRequest;
import com.chengqu.huzhu.community.entity.Post;
import com.chengqu.huzhu.community.entity.PostChannel;
import com.chengqu.huzhu.community.entity.PostComment;
import com.chengqu.huzhu.community.entity.PostKind;
import com.chengqu.huzhu.community.entity.PostLike;
import com.chengqu.huzhu.community.entity.PostModule;
import com.chengqu.huzhu.community.entity.PostView;
import com.chengqu.huzhu.community.repository.PostCommentRepository;
import com.chengqu.huzhu.community.repository.PostLikeRepository;
import com.chengqu.huzhu.community.repository.PostRepository;
import com.chengqu.huzhu.community.repository.PostViewRepository;
import com.chengqu.huzhu.community.support.NotifySender;
import com.chengqu.huzhu.community.support.UserExclusionLookup;
import com.chengqu.huzhu.community.support.UserLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityService {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostViewRepository postViewRepository;
    private final UserLookup userLookup;
    private final UserExclusionLookup exclusionLookup;
    private final NotifySender notifySender;

    private static final int HEAT_VIEW = 1;
    private static final int HEAT_LIKE = 5;
    private static final int HEAT_COMMENT = 10;

    /** 通知里的关联对象类型，与 notify-service 生成跳转链接的取值保持一致。 */
    private static final String REF_TYPE_POST = "POST";

    /**
     * 动态通知的跳转前缀。
     *
     * <p>为什么要按频道分开：社区互动帖（COMMUNITY）与生活广场（PLAZA）同属
     * 前端的「发现」模块，校园帖（CAMPUS）才是校园专区。notify-service 按通知类型
     * 生成的链接只认 type，会把校园帖也指向 /discover，点进去定位不到那条帖子；
     * 帖子频道只有本服务知道，因此链接在本服务拼好后随命令一起发给通知服务。
     * 前缀与前端路由（/discover?post=、/campus?post=）必须同步修改。
     */
    private static final String POST_LINK_DISCOVER = "/discover?post=";
    private static final String POST_LINK_CAMPUS = "/campus?post=";

    /** 评论通知的正文长度上限：通知只是提醒，完整内容留在动态详情里。 */
    private static final int COMMENT_NOTIFY_MAX = 100;

    @Transactional(readOnly = true)
    public Page<PostResponse> listPosts(String channel, String kind, Pageable pageable) {
        PostModule module = PostModule.parse(channel, PostModule.DISCOVER);
        PostKind parsedKind = PostKind.parseOrNull(kind);
        List<PostChannel> channels = module.channels();
        // 拉黑关系在 SQL 层过滤：取回后再剔除会让分页条数忽多忽少
        List<Long> excluded = exclusionLookup.blockedAuthorIds();
        Page<Post> source = parsedKind == null
                ? postRepository.searchByChannels(channels, excluded, pageable)
                : postRepository.searchByChannelsAndKind(channels, parsedKind, excluded, pageable);
        Page<PostResponse> page = toPage(source);
        log.info("[社区] 查询动态 module={}, kind={}, channels={}, excluded={}, page={}, size={}, total={}",
                module, parsedKind, channels, excluded.size(), pageable.getPageNumber(),
                page.getNumberOfElements(), page.getTotalElements());
        return page;
    }

    /** 某模块可用的帖子种类，供前端渲染发布器的种类选择。 */
    @Transactional(readOnly = true)
    public List<PostKind> listKinds(String module) {
        return PostKind.of(PostModule.parse(module, PostModule.DISCOVER));
    }

    @Transactional(readOnly = true)
    public PostResponse detail(Long id) {
        Long userId = SecurityUtils.currentUser().getId();
        Post post = requirePost(id);
        // 详情同样校验屏蔽关系：只把帖子从信息流里摘掉、却允许凭 id 直接读，
        // 等于屏蔽只对「随便看看」有效，对「知道 id 的人」无效
        requireInteractable(post, userId);
        boolean liked = postLikeRepository.existsByPostIdAndUserId(id, userId);
        log.info("[社区] 动态详情 id={}, authorId={}, liked={}", id, post.getAuthorId(), liked);
        return toResponse(post, liked);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> listByAuthor(Long authorId, Pageable pageable) {
        Long viewerId = SecurityUtils.currentUser().getId();
        // 被屏蔽的作者，其主页内容同样不可见
        Page<PostResponse> page = toPage(
                postRepository.searchByAuthor(authorId, exclusionLookup.blockedAuthorIds(), pageable));
        log.info("[社区] 查看用户动态 authorId={}, viewerId={}, total={}",
                authorId, viewerId, page.getTotalElements());
        return page;
    }

    @Transactional
    public PostResponse createPost(CreatePostRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        Post post = new Post();
        post.setContent(request.getContent().trim());
        post.setAuthorId(user.getId());
        post.setAuthorName(displayName(user));
        post.setLikeCount(0);
        post.setHeatScore(0);
        post.setViewCount(0);
        PostChannel channel = parseChannel(request.getChannel(), PostChannel.COMMUNITY);
        post.setChannel(channel);
        // 种类由后端白名单校验，并校验它是否属于该模块——客户端塞任意字符串会被直接拒绝
        post.setKind(PostKind.require(request.getKind(), PostModule.of(channel)));
        post.setImages(LocalFileUrls.normalizeImages(request.getImages()));
        Post saved = postRepository.save(post);
        log.info("[社区] 发帖成功 id={}, authorId={}", saved.getId(), user.getId());
        return toResponse(saved, false);
    }

    @Transactional
    public PostResponse updatePost(Long id, UpdatePostRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        Post post = requirePost(id);
        if (!post.getAuthorId().equals(user.getId())) {
            throw new BizException(403, "仅作者可编辑动态");
        }
        post.setContent(request.getContent().trim());
        if (request.getImages() != null) {
            post.setImages(LocalFileUrls.normalizeImages(request.getImages()));
        }
        Post saved = postRepository.save(post);
        boolean liked = postLikeRepository.existsByPostIdAndUserId(id, user.getId());
        log.info("[社区] 更新动态 id={}, authorId={}", id, user.getId());
        return toResponse(saved, liked);
    }

    @Transactional
    public void deletePost(Long id) {
        UserPrincipal user = SecurityUtils.currentUser();
        Post post = requirePost(id);
        boolean isAuthor = post.getAuthorId().equals(user.getId());
        boolean isAdmin = isAdmin(user);
        if (!isAuthor && !isAdmin) {
            throw new BizException(403, "仅作者或管理员可删除动态");
        }
        postCommentRepository.deleteByPostId(id);
        postLikeRepository.deleteByPostId(id);
        postViewRepository.deleteByPostId(id);
        postRepository.delete(post);
        log.info("[社区] 删除动态 id={}, operatorId={}", id, user.getId());
    }

    @Transactional
    public PostResponse toggleLike(Long postId) {
        UserPrincipal user = SecurityUtils.currentUser();
        Post post = requirePost(postId);
        requireInteractable(post, user.getId());

        return postLikeRepository.findByPostIdAndUserId(postId, user.getId())
                .map(existing -> {
                    postLikeRepository.delete(existing);
                    post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
                    post.addHeat(-HEAT_LIKE);
                    Post saved = postRepository.save(post);
                    log.info("[社区] 取消点赞 postId={}, userId={}, likeCount={}", postId, user.getId(), saved.getLikeCount());
                    return toResponse(saved, false);
                })
                .orElseGet(() -> {
                    PostLike like = new PostLike();
                    like.setPostId(postId);
                    like.setUserId(user.getId());
                    postLikeRepository.save(like);
                    post.setLikeCount(post.getLikeCount() + 1);
                    post.addHeat(HEAT_LIKE);
                    Post saved = postRepository.save(post);
                    log.info("[社区] 点赞成功 postId={}, userId={}, likeCount={}", postId, user.getId(), saved.getLikeCount());
                    // 只有「新增点赞」才通知作者：取消点赞是撤回动作，再发提醒就是骚扰
                    notifyPostLiked(saved, user);
                    return toResponse(saved, true);
                });
    }

    @Transactional(readOnly = true)
    public CommunityStatsResponse stats() {
        Long userId = SecurityUtils.currentUser().getId();
        CommunityStatsResponse stats = CommunityStatsResponse.builder()
                .postCount(postRepository.count())
                .myPostCount(postRepository.countByAuthorId(userId))
                .build();
        log.info("[社区] 统计 postCount={}, myPostCount={}", stats.getPostCount(), stats.getMyPostCount());
        return stats;
    }

    @Transactional(readOnly = true)
    public java.util.List<CommentResponse> listComments(Long postId) {
        requirePost(postId);
        // 被屏蔽者的评论同样不可见：只屏蔽帖子而不屏蔽评论，
        // 会出现「拉黑了对方，却在自己的帖子下面看到对方的留言」
        List<CommentResponse> comments = postCommentRepository
                .findByPostIdExcludingAuthors(postId, exclusionLookup.blockedAuthorIds()).stream()
                .map(CommentResponse::from)
                .toList();
        fillCommentAuthors(comments);
        return comments;
    }

    @Transactional
    public CommentResponse addComment(Long postId, CreateCommentRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        Post post = requirePost(postId);
        requireInteractable(post, user.getId());
        PostComment comment = new PostComment();
        comment.setPostId(postId);
        comment.setAuthorId(user.getId());
        comment.setAuthorName(displayName(user));
        comment.setContent(request.getContent().trim());
        PostComment saved = postCommentRepository.save(comment);
        post.addHeat(HEAT_COMMENT);
        postRepository.save(post);
        log.info("[社区] 评论 postId={}, commentId={}, authorId={}, heat={}", postId, saved.getId(), user.getId(), post.getHeatScore());
        // 评论成功 → 通知动态作者；评论自己的动态不通知（等于给自己发提醒）
        if (!post.getAuthorId().equals(user.getId())) {
            notifySender.sendAfterCommit(new NotifyCreateCommand(
                    post.getAuthorId(),
                    "POST_COMMENTED",
                    "有人评论了你的动态",
                    truncate(saved.getContent(), COMMENT_NOTIFY_MAX),
                    REF_TYPE_POST,
                    postId,
                    user.getId(),
                    displayName(user),
                    postLink(post)));
        }
        CommentResponse response = CommentResponse.from(saved);
        fillCommentAuthors(List.of(response));
        return response;
    }

    @Transactional
    public void deleteComment(Long commentId) {
        UserPrincipal user = SecurityUtils.currentUser();
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new BizException(404, "评论不存在"));
        boolean isAuthor = comment.getAuthorId().equals(user.getId());
        boolean isAdmin = isAdmin(user);
        if (!isAuthor && !isAdmin) {
            throw new BizException(403, "仅作者或管理员可删除评论");
        }
        postCommentRepository.delete(comment);
        postRepository.findById(comment.getPostId()).ifPresent(post -> {
            post.addHeat(-HEAT_COMMENT);
            postRepository.save(post);
        });
        log.info("[社区] 删除评论 commentId={}, operatorId={}", commentId, user.getId());
    }

    @Transactional
    public PostResponse recordView(Long postId) {
        UserPrincipal user = SecurityUtils.currentUser();
        Post post = requirePost(postId);
        boolean counted = false;
        if (!postViewRepository.existsByPostIdAndUserId(postId, user.getId())) {
            PostView view = new PostView();
            view.setPostId(postId);
            view.setUserId(user.getId());
            postViewRepository.save(view);
            post.setViewCount((post.getViewCount() == null ? 0 : post.getViewCount()) + 1);
            post.addHeat(HEAT_VIEW);
            post = postRepository.save(post);
            counted = true;
            log.info("[社区] 刷到帖子 postId={}, userId={}, heat={}", postId, user.getId(), post.getHeatScore());
        }
        boolean liked = postLikeRepository.existsByPostIdAndUserId(postId, user.getId());
        PostResponse response = PostResponse.from(post, liked, postCommentRepository.countByPostId(postId), counted);
        fillUserInfo(List.of(response));
        return response;
    }

    // ------------------------------------------------------------------
    // 内部接口：供 auth-service 通过 Feign 聚合（管理台看板 / 用户主页）
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PlatformPostStats platformStats() {
        PlatformPostStats stats = new PlatformPostStats(
                postRepository.count(),
                postCommentRepository.count(),
                postLikeRepository.count());
        log.info("[社区] 平台统计 posts={}, comments={}, likes={}",
                stats.total(), stats.commentTotal(), stats.likeTotal());
        return stats;
    }

    @Transactional(readOnly = true)
    public UserPostStats userStats(Long userId) {
        long postCount = postRepository.countByAuthorId(userId);
        log.info("[社区] 用户统计 userId={}, posts={}", userId, postCount);
        return new UserPostStats(userId, postCount);
    }

    // ------------------------------------------------------------------
    // 装配层：改批量查询以消除列表场景的 N+1（原实现每条动态 2 次查询）
    // ------------------------------------------------------------------

    private Page<PostResponse> toPage(Page<Post> page) {
        return new PageImpl<>(toResponses(page.getContent()), page.getPageable(), page.getTotalElements());
    }

    /**
     * 批量装配点赞状态与评论数。无论传入多少条，固定 2 次查询。
     */
    private List<PostResponse> toResponses(List<Post> posts) {
        if (posts.isEmpty()) {
            return List.of();
        }
        Long userId = SecurityUtils.currentUser().getId();
        List<Long> postIds = posts.stream().map(Post::getId).toList();

        Set<Long> likedIds = new HashSet<>(postLikeRepository.findLikedPostIds(userId, postIds));

        Map<Long, Long> commentCounts = new HashMap<>();
        for (Object[] row : postCommentRepository.countByPostIds(postIds)) {
            commentCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }

        List<PostResponse> result = new ArrayList<>(posts.size());
        for (Post post : posts) {
            result.add(PostResponse.from(post,
                    likedIds.contains(post.getId()),
                    commentCounts.getOrDefault(post.getId(), 0L)));
        }
        fillUserInfo(result);
        return result;
    }

    /**
     * 通过 Feign 批量补全作者昵称与头像。
     *
     * <p>无论一页多少条，只发起 1 次跨服务调用（N+1 修复的前提不能被破坏）。
     * 调用失败或作者已不存在时保留实体上的冗余昵称、头像留空，由前端兜底。
     */
    private void fillUserInfo(List<PostResponse> responses) {
        Set<Long> authorIds = new HashSet<>();
        for (PostResponse response : responses) {
            if (response.getAuthorId() != null) {
                authorIds.add(response.getAuthorId());
            }
        }
        Map<Long, UserBrief> users = userLookup.byIds(authorIds);
        if (users.isEmpty()) {
            return;
        }
        for (PostResponse response : responses) {
            UserBrief author = users.get(response.getAuthorId());
            if (author == null) {
                continue;
            }
            if (StringUtils.hasText(author.nickname())) {
                response.setAuthorName(author.nickname());
            }
            response.setAuthorAvatar(author.avatar());
        }
    }

    private void fillCommentAuthors(List<CommentResponse> comments) {
        Set<Long> authorIds = new HashSet<>();
        for (CommentResponse comment : comments) {
            if (comment.getAuthorId() != null) {
                authorIds.add(comment.getAuthorId());
            }
        }
        Map<Long, UserBrief> users = userLookup.byIds(authorIds);
        if (users.isEmpty()) {
            return;
        }
        for (CommentResponse comment : comments) {
            UserBrief author = users.get(comment.getAuthorId());
            if (author == null) {
                continue;
            }
            if (StringUtils.hasText(author.nickname())) {
                comment.setAuthorName(author.nickname());
            }
            comment.setAuthorAvatar(author.avatar());
        }
    }

    private PostResponse toResponse(Post post, boolean liked) {
        // 这里显式接收 liked，而不是交给批量装配去查：
        // 点赞/取消点赞刚写完数据，结果状态是已知的，重新查询会依赖 flush 时序。
        PostResponse response = PostResponse.from(post, liked,
                postCommentRepository.countByPostId(post.getId()));
        fillUserInfo(List.of(response));
        return response;
    }

    private Post requirePost(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "动态不存在"));
    }

    /**
     * 互动前的屏蔽校验：与作者存在拉黑关系（无论方向）时不允许点赞或评论。
     *
     * <p>为什么列表已经过滤掉了还要在写操作上再挡一次：信息流过滤只作用于「看」，
     * 客户端拿到 id 之后完全可以绕过界面直接调接口。屏蔽是关系状态，
     * 必须由服务端在读与写两侧同时守住，否则「拉黑」只是一个装饰。
     *
     * <p>提示语刻意不区分「你拉黑了对方」与「对方拉黑了你」：告诉被拉黑者
     * 「你被对方拉黑了」等于把对方的操作暴露出去，是一种隐私泄漏。
     */
    private void requireInteractable(Post post, Long viewerId) {
        if (post.getAuthorId().equals(viewerId)) {
            return;
        }
        if (exclusionLookup.blockedAuthorIds().contains(post.getAuthorId())) {
            throw new BizException(403, "无法对该动态进行操作");
        }
    }

    /**
     * 点赞成功后的通知：作者本人点赞不发通知。
     *
     * <p>抽成独立方法是为了让「哪种情况才发通知」的判断集中在一处，
     * 避免以后新增点赞入口（如圈子、市场）时各写一份。
     */
    private void notifyPostLiked(Post post, UserPrincipal actor) {
        if (post.getAuthorId().equals(actor.getId())) {
            return;
        }
        notifySender.sendAfterCommit(new NotifyCreateCommand(
                post.getAuthorId(),
                "POST_LIKED",
                "有人赞了你的动态",
                null,
                REF_TYPE_POST,
                post.getId(),
                actor.getId(),
                displayName(actor),
                postLink(post)));
    }

    /**
     * 帖子通知的跳转路径。
     *
     * <p>校园帖落在校园专区，其余频道（COMMUNITY / PLAZA）都在「发现」模块，
     * 与前端 PostModule 的聚合口径一致。链接在这里生成而不是交给通知服务推导，
     * 是因为只有本服务知道帖子存在哪个频道。
     */
    private static String postLink(Post post) {
        String prefix = post.getChannel() == PostChannel.CAMPUS ? POST_LINK_CAMPUS : POST_LINK_DISCOVER;
        return prefix + post.getId();
    }

    /**
     * 截断到 {@code max} 个字符（含省略号），用于通知正文。
     * 通知服务对正文有长度上限，超长会整条写入失败——宁可截断也不能丢提醒。
     */
    private static String truncate(String text, int max) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max - 1) + "…";
    }

    private String displayName(UserPrincipal user) {
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname();
        }
        if (StringUtils.hasText(user.getPhone())) {
            return user.getPhone();
        }
        return "用户" + user.getId();
    }

    private boolean isAdmin(UserPrincipal user) {
        String role = user.getRole();
        if (role == null) {
            return false;
        }
        if (role.startsWith("ROLE_")) {
            role = role.substring(5);
        }
        return "ADMIN".equals(role);
    }

    private PostChannel parseChannel(String channel, PostChannel fallback) {
        if (!StringUtils.hasText(channel)) {
            return fallback;
        }
        try {
            return PostChannel.valueOf(channel.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
