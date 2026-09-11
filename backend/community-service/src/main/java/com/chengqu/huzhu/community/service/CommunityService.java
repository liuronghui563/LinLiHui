package com.chengqu.huzhu.community.service;

import com.chengqu.huzhu.common.exception.BizException;
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
import com.chengqu.huzhu.community.entity.PostLike;
import com.chengqu.huzhu.community.entity.PostView;
import com.chengqu.huzhu.community.repository.PostCommentRepository;
import com.chengqu.huzhu.community.repository.PostLikeRepository;
import com.chengqu.huzhu.community.repository.PostRepository;
import com.chengqu.huzhu.community.repository.PostViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityService {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostViewRepository postViewRepository;

    private static final int HEAT_VIEW = 1;
    private static final int HEAT_LIKE = 5;
    private static final int HEAT_COMMENT = 10;
    private static final java.util.List<String> CAMPUS_SOCIAL_CATEGORIES = java.util.List.of("吐槽", "表白", "唠嗑", "分享日常");

    @Transactional(readOnly = true)
    public Page<PostResponse> listPosts(String channel, String category, Pageable pageable) {
        PostChannel parsed = parseChannel(channel, PostChannel.COMMUNITY);
        boolean hasCategory = StringUtils.hasText(category);
        Page<Post> source;
        if (parsed == PostChannel.CAMPUS) {
            source = hasCategory
                    ? postRepository.findByChannelAndCategory(PostChannel.CAMPUS, category.trim(), pageable)
                    : postRepository.findByChannel(PostChannel.CAMPUS, pageable);
        } else if (parsed == PostChannel.PLAZA) {
            source = postRepository.findByChannel(PostChannel.PLAZA, pageable);
        } else {
            source = postRepository.findByChannelIsNullOrChannel(PostChannel.COMMUNITY, pageable);
        }
        Page<PostResponse> page = source.map(this::toResponse);
        log.info("[社区] 查询动态 channel={}, category={}, page={}, size={}, total={}",
                parsed, category, pageable.getPageNumber(), page.getNumberOfElements(), page.getTotalElements());
        return page;
    }

    @Transactional(readOnly = true)
    public PostResponse detail(Long id) {
        Long userId = SecurityUtils.currentUser().getId();
        Post post = requirePost(id);
        boolean liked = postLikeRepository.existsByPostIdAndUserId(id, userId);
        log.info("[社区] 动态详情 id={}, authorId={}, liked={}", id, post.getAuthorId(), liked);
        return toResponse(post, liked);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> listByAuthor(Long authorId, Pageable pageable) {
        Long viewerId = SecurityUtils.currentUser().getId();
        Page<PostResponse> page = postRepository.findByAuthorId(authorId, pageable).map(this::toResponse);
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
        if (channel == PostChannel.CAMPUS) {
            String category = request.getCategory() == null ? "" : request.getCategory().trim();
            if (!CAMPUS_SOCIAL_CATEGORIES.contains(category)) {
                throw new BizException("校园互动帖分类无效");
            }
            post.setCategory(category);
        }
        post.setChannel(channel);
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
        return postCommentRepository.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .map(CommentResponse::from)
                .toList();
    }

    @Transactional
    public CommentResponse addComment(Long postId, CreateCommentRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        Post post = requirePost(postId);
        PostComment comment = new PostComment();
        comment.setPostId(postId);
        comment.setAuthorId(user.getId());
        comment.setAuthorName(displayName(user));
        comment.setContent(request.getContent().trim());
        PostComment saved = postCommentRepository.save(comment);
        post.addHeat(HEAT_COMMENT);
        postRepository.save(post);
        log.info("[社区] 评论 postId={}, commentId={}, authorId={}, heat={}", postId, saved.getId(), user.getId(), post.getHeatScore());
        return CommentResponse.from(saved);
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
        return PostResponse.from(post, liked, postCommentRepository.countByPostId(postId), counted);
    }

    private PostResponse toResponse(Post post) {
        Long userId = SecurityUtils.currentUser().getId();
        boolean liked = postLikeRepository.existsByPostIdAndUserId(post.getId(), userId);
        return toResponse(post, liked);
    }

    private PostResponse toResponse(Post post, boolean liked) {
        return PostResponse.from(post, liked, postCommentRepository.countByPostId(post.getId()));
    }

    private Post requirePost(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "动态不存在"));
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
