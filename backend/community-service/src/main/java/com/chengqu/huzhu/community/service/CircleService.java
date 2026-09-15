package com.chengqu.huzhu.community.service;

import com.chengqu.huzhu.api.dto.UserBrief;
import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.common.file.LocalFileUrls;
import com.chengqu.huzhu.common.security.SecurityUtils;
import com.chengqu.huzhu.common.security.UserPrincipal;
import com.chengqu.huzhu.community.dto.CircleMemberResponse;
import com.chengqu.huzhu.community.dto.CircleMuteRequest;
import com.chengqu.huzhu.community.dto.CirclePostRequest;
import com.chengqu.huzhu.community.dto.CircleResponse;
import com.chengqu.huzhu.community.dto.CircleRoleRequest;
import com.chengqu.huzhu.community.dto.CreateCircleRequest;
import com.chengqu.huzhu.community.dto.CreatePostRequest;
import com.chengqu.huzhu.community.dto.PostResponse;
import com.chengqu.huzhu.community.entity.Circle;
import com.chengqu.huzhu.community.entity.CircleMember;
import com.chengqu.huzhu.community.entity.CircleMemberRole;
import com.chengqu.huzhu.community.entity.CirclePost;
import com.chengqu.huzhu.community.entity.CircleStatus;
import com.chengqu.huzhu.community.entity.Post;
import com.chengqu.huzhu.community.entity.PostChannel;
import com.chengqu.huzhu.community.entity.PostKind;
import com.chengqu.huzhu.community.entity.PostModule;
import com.chengqu.huzhu.community.repository.CircleMemberRepository;
import com.chengqu.huzhu.community.repository.CirclePostRepository;
import com.chengqu.huzhu.community.repository.CircleRepository;
import com.chengqu.huzhu.community.repository.PostCommentRepository;
import com.chengqu.huzhu.community.repository.PostLikeRepository;
import com.chengqu.huzhu.community.repository.PostRepository;
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

/**
 * 圈子（兴趣小组）业务逻辑。
 *
 * <p>圈内帖子复用 {@link PostRepository}，通过 u_r_circle_post 关联表取：
 * 帖子本身仍然是普通动态（可被点赞、评论、在发现流里看到），
 * 圈子只是给它加了一个「聚合入口」。这样动态模块的既有链路一行都不用改。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CircleService {

    private final CircleRepository circleRepository;
    private final CircleMemberRepository circleMemberRepository;
    private final CirclePostRepository circlePostRepository;
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostCommentRepository postCommentRepository;
    private final UserLookup userLookup;

    @Transactional(readOnly = true)
    public Page<CircleResponse> list(String keyword, Pageable pageable) {
        String parsedKeyword = trimToNull(keyword);
        Page<Circle> page = parsedKeyword == null
                ? circleRepository.findAll(pageable)
                : circleRepository.findByNameContaining(parsedKeyword, pageable);
        Page<CircleResponse> result = toPage(page);
        log.info("[圈子] 查询圈子列表 keyword={}, page={}, size={}, total={}",
                parsedKeyword, pageable.getPageNumber(), result.getNumberOfElements(), result.getTotalElements());
        return result;
    }

    @Transactional(readOnly = true)
    public CircleResponse detail(Long id) {
        Circle circle = requireCircle(id);
        CircleMember me = currentMembership(id);
        log.info("[圈子] 圈子详情 id={}, ownerId={}, memberCount={}, joined={}",
                id, circle.getOwnerId(), circle.getMemberCount(), me != null);
        return toResponse(circle, me);
    }

    @Transactional
    public CircleResponse create(CreateCircleRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        String name = request.getName().trim();
        if (circleRepository.existsByName(name)) {
            throw new BizException("圈子名称已存在，换一个吧");
        }
        Circle circle = new Circle();
        circle.setName(name);
        circle.setDescription(trimToNull(request.getDescription()));
        circle.setCover(coverUrl(request.getCover()));
        circle.setOwnerId(user.getId());
        circle.setOwnerName(displayName(user));
        // 创建者即圈主，成员数从 1 起算，避免出现「有圈主但没有成员」的空圈子
        circle.setMemberCount(1);
        circle.setPostCount(0);
        circle.setStatus(CircleStatus.ACTIVE);
        Circle saved = circleRepository.save(circle);

        CircleMember owner = new CircleMember();
        owner.setCircleId(saved.getId());
        owner.setUserId(user.getId());
        owner.setRole(CircleMemberRole.OWNER);
        owner.setMuted(false);
        circleMemberRepository.save(owner);

        log.info("[圈子] 创建圈子成功 id={}, name={}, ownerId={}", saved.getId(), saved.getName(), user.getId());
        return toResponse(saved, owner);
    }

    @Transactional
    public CircleResponse join(Long id) {
        UserPrincipal user = SecurityUtils.currentUser();
        Circle circle = requireCircle(id);
        if (circle.getStatus() == CircleStatus.CLOSED) {
            throw new BizException("圈子已关闭，无法加入");
        }
        if (circleMemberRepository.existsByCircleIdAndUserId(id, user.getId())) {
            throw new BizException("你已经在这个圈子里了");
        }
        CircleMember member = new CircleMember();
        member.setCircleId(id);
        member.setUserId(user.getId());
        member.setRole(CircleMemberRole.MEMBER);
        member.setMuted(false);
        circleMemberRepository.save(member);

        circle.setMemberCount(count(circle.getMemberCount()) + 1);
        Circle saved = circleRepository.save(circle);
        log.info("[圈子] 加入圈子 id={}, userId={}, memberCount={}", id, user.getId(), saved.getMemberCount());
        return toResponse(saved, member);
    }

    @Transactional
    public CircleResponse leave(Long id) {
        UserPrincipal user = SecurityUtils.currentUser();
        Circle circle = requireCircle(id);
        CircleMember member = circleMemberRepository.findByCircleIdAndUserId(id, user.getId())
                .orElseThrow(() -> new BizException("你还没有加入这个圈子"));
        // 圈主退出会让圈子没有维护者：只能关闭圈子，成员记录保留
        if (member.getRole() == CircleMemberRole.OWNER) {
            throw new BizException("圈主不能退出圈子，如果需要停止维护请关闭圈子");
        }
        circleMemberRepository.delete(member);

        circle.setMemberCount(Math.max(0, count(circle.getMemberCount()) - 1));
        Circle saved = circleRepository.save(circle);
        log.info("[圈子] 退出圈子 id={}, userId={}, memberCount={}", id, user.getId(), saved.getMemberCount());
        return toResponse(saved, null);
    }

    /**
     * 关闭圈子（圈主或平台管理员）。
     *
     * <p>需求里只写了「圈主不能退出，只能关闭圈子」，但没有给关闭的入口，
     * 状态枚举里的 CLOSED 就会永远不可达，因此补上这个动作。
     */
    @Transactional
    public CircleResponse close(Long id) {
        UserPrincipal user = SecurityUtils.currentUser();
        Circle circle = requireCircle(id);
        if (!isCircleOwner(circle, user) && !isPlatformAdmin(user)) {
            throw new BizException(403, "仅圈主或管理员可关闭圈子");
        }
        if (circle.getStatus() == CircleStatus.CLOSED) {
            throw new BizException("圈子已经关闭了");
        }
        circle.setStatus(CircleStatus.CLOSED);
        Circle saved = circleRepository.save(circle);
        log.info("[圈子] 关闭圈子 id={}, operatorId={}", id, user.getId());
        return toResponse(saved, currentMembership(id));
    }

    @Transactional(readOnly = true)
    public Page<CircleResponse> mine(Pageable pageable) {
        Long userId = SecurityUtils.currentUser().getId();
        Page<CircleMember> members = circleMemberRepository.findByUserId(userId, pageable);
        List<Long> circleIds = members.getContent().stream().map(CircleMember::getCircleId).toList();
        List<Circle> circles = orderedByIds(circleIds);
        Map<Long, CircleMember> memberships = new HashMap<>();
        for (CircleMember member : members.getContent()) {
            memberships.put(member.getCircleId(), member);
        }
        List<CircleResponse> responses = toResponses(circles, memberships);
        log.info("[圈子] 我加入的圈子 userId={}, total={}", userId, members.getTotalElements());
        return new PageImpl<>(responses, pageable, members.getTotalElements());
    }

    // ------------------------------------------------------------------
    // 圈内帖子：复用 PostRepository，通过关联表取
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<PostResponse> listPosts(Long id, Pageable pageable) {
        requireCircle(id);
        Page<CirclePost> relations = circlePostRepository.findByCircleId(id, pageable);
        Page<PostResponse> result = toPostPage(relations, pageable);
        log.info("[圈子] 查询圈内动态 circleId={}, page={}, size={}, total={}",
                id, pageable.getPageNumber(), result.getNumberOfElements(), result.getTotalElements());
        return result;
    }

    @Transactional
    public PostResponse compose(Long id, CreatePostRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        Circle circle = requireCircle(id);
        if (circle.getStatus() == CircleStatus.CLOSED) {
            throw new BizException("圈子已关闭，无法发帖");
        }
        CircleMember me = circleMemberRepository.findByCircleIdAndUserId(id, user.getId())
                .orElseThrow(() -> new BizException(403, "加入圈子后才能发帖"));
        if (Boolean.TRUE.equals(me.getMuted())) {
            throw new BizException(403, "你已被限制在本圈发帖");
        }

        Post post = new Post();
        post.setContent(request.getContent().trim());
        post.setAuthorId(user.getId());
        post.setAuthorName(displayName(user));
        post.setLikeCount(0);
        post.setHeatScore(0);
        post.setViewCount(0);
        post.setChannel(PostChannel.COMMUNITY);
        post.setKind(PostKind.require(request.getKind(), PostModule.DISCOVER));
        post.setImages(LocalFileUrls.normalizeImages(request.getImages()));
        Post saved = postRepository.save(post);

        CirclePost relation = new CirclePost();
        relation.setCircleId(id);
        relation.setPostId(saved.getId());
        circlePostRepository.save(relation);
        circle.setPostCount((int) circlePostRepository.countByCircleId(id));
        circleRepository.save(circle);

        PostResponse response = PostResponse.from(saved, false, 0L);
        fillPostAuthors(List.of(response));
        log.info("[圈子] 圈内发帖 circleId={}, postId={}, authorId={}", id, saved.getId(), user.getId());
        return response;
    }

    @Transactional
    public void addPost(Long id, CirclePostRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        Circle circle = requireCircle(id);
        if (!isCircleOwner(circle, user) && !isPlatformAdmin(user)) {
            throw new BizException(403, "仅圈主或管理员可把动态收录进圈子");
        }
        Long postId = request.getPostId();
        if (!postRepository.existsById(postId)) {
            throw new BizException(404, "动态不存在");
        }
        if (circlePostRepository.existsByCircleIdAndPostId(id, postId)) {
            throw new BizException("这条动态已经在圈子里了");
        }
        CirclePost relation = new CirclePost();
        relation.setCircleId(id);
        relation.setPostId(postId);
        circlePostRepository.save(relation);

        // 计数以关联表为准重新数一遍：并发收录时不会像 +1 那样漂移
        circle.setPostCount((int) circlePostRepository.countByCircleId(id));
        circleRepository.save(circle);
        log.info("[圈子] 收录动态 circleId={}, postId={}, operatorId={}, postCount={}",
                id, postId, user.getId(), circle.getPostCount());
    }

    /** 圈内下线：从圈子里拿掉，原动态仍留在发现流。 */
    @Transactional
    public void removePost(Long circleId, Long postId) {
        UserPrincipal user = SecurityUtils.currentUser();
        Circle circle = requireCircle(circleId);
        requireStaff(circle, user, currentMembership(circleId));
        CirclePost relation = circlePostRepository.findByCircleIdAndPostId(circleId, postId)
                .orElseThrow(() -> new BizException(404, "这条动态不在圈子里"));
        circlePostRepository.delete(relation);
        circle.setPostCount((int) circlePostRepository.countByCircleId(circleId));
        circleRepository.save(circle);
        log.info("[圈子] 下线动态 circleId={}, postId={}, operatorId={}", circleId, postId, user.getId());
    }

    @Transactional(readOnly = true)
    public List<CircleMemberResponse> listMembers(Long circleId) {
        UserPrincipal user = SecurityUtils.currentUser();
        Circle circle = requireCircle(circleId);
        CircleMember me = currentMembership(circleId);
        if (me == null && !isPlatformAdmin(user)) {
            throw new BizException(403, "加入圈子后才能查看成员");
        }
        List<CircleMember> members = circleMemberRepository.findByCircleIdOrderByJoinedAtAsc(circleId);
        List<CircleMemberResponse> responses = members.stream().map(CircleMemberResponse::from).toList();
        fillMemberProfiles(responses);
        log.info("[圈子] 成员列表 circleId={}, count={}, operatorId={}", circleId, responses.size(), user.getId());
        return responses;
    }

    @Transactional
    public void kick(Long circleId, Long targetUserId) {
        UserPrincipal user = SecurityUtils.currentUser();
        Circle circle = requireCircle(circleId);
        CircleMember actor = currentMembership(circleId);
        requireStaff(circle, user, actor);
        if (user.getId().equals(targetUserId)) {
            throw new BizException("不能把自己踢出圈子");
        }
        CircleMember target = requireMember(circleId, targetUserId);
        if (target.getRole() == CircleMemberRole.OWNER) {
            throw new BizException("不能踢出圈主");
        }
        if (isCircleAdminOnly(circle, user, actor) && target.getRole() != CircleMemberRole.MEMBER) {
            throw new BizException(403, "管理员只能踢出普通成员");
        }
        circleMemberRepository.delete(target);
        circle.setMemberCount(Math.max(0, count(circle.getMemberCount()) - 1));
        circleRepository.save(circle);
        log.info("[圈子] 踢出成员 circleId={}, targetUserId={}, operatorId={}",
                circleId, targetUserId, user.getId());
    }

    @Transactional
    public void mute(Long circleId, Long targetUserId, CircleMuteRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        Circle circle = requireCircle(circleId);
        CircleMember actor = currentMembership(circleId);
        requireStaff(circle, user, actor);
        CircleMember target = requireMember(circleId, targetUserId);
        if (target.getRole() == CircleMemberRole.OWNER) {
            throw new BizException("不能限制圈主发帖");
        }
        if (isCircleAdminOnly(circle, user, actor) && target.getRole() != CircleMemberRole.MEMBER) {
            throw new BizException(403, "管理员只能限制普通成员发帖");
        }
        target.setMuted(Boolean.TRUE.equals(request.getMuted()));
        circleMemberRepository.save(target);
        log.info("[圈子] {}发帖 circleId={}, targetUserId={}, operatorId={}",
                target.getMuted() ? "限制" : "解除限制", circleId, targetUserId, user.getId());
    }

    @Transactional
    public void setRole(Long circleId, Long targetUserId, CircleRoleRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        Circle circle = requireCircle(circleId);
        if (!isCircleOwner(circle, user) && !isPlatformAdmin(user)) {
            throw new BizException(403, "仅圈主可增设管理员");
        }
        CircleMember target = requireMember(circleId, targetUserId);
        if (target.getRole() == CircleMemberRole.OWNER) {
            throw new BizException("圈主身份不能更改");
        }
        CircleMemberRole next = parseAssignableRole(request.getRole());
        target.setRole(next);
        circleMemberRepository.save(target);
        log.info("[圈子] 调整身份 circleId={}, targetUserId={}, role={}, operatorId={}",
                circleId, targetUserId, next, user.getId());
    }

    // ------------------------------------------------------------------
    // 装配层
    // ------------------------------------------------------------------

    private Page<CircleResponse> toPage(Page<Circle> page) {
        if (page.isEmpty()) {
            return new PageImpl<>(List.of(), page.getPageable(), page.getTotalElements());
        }
        Long userId = SecurityUtils.currentUser().getId();
        List<Long> circleIds = page.getContent().stream().map(Circle::getId).toList();
        Map<Long, CircleMember> memberships = new HashMap<>();
        for (CircleMember member : circleMemberRepository.findByUserIdAndCircleIdIn(userId, circleIds)) {
            memberships.put(member.getCircleId(), member);
        }
        return new PageImpl<>(toResponses(page.getContent(), memberships), page.getPageable(), page.getTotalElements());
    }

    private CircleResponse toResponse(Circle circle, CircleMember membership) {
        return toResponses(List.of(circle), membership == null
                ? Map.of()
                : Map.of(circle.getId(), membership)).get(0);
    }

    private List<CircleResponse> toResponses(List<Circle> circles, Map<Long, CircleMember> memberships) {
        if (circles.isEmpty()) {
            return List.of();
        }
        List<CircleResponse> result = new ArrayList<>(circles.size());
        for (Circle circle : circles) {
            CircleMember membership = memberships.get(circle.getId());
            boolean joined = membership != null;
            String myRole = membership == null || membership.getRole() == null ? null : membership.getRole().name();
            boolean muted = membership != null && Boolean.TRUE.equals(membership.getMuted());
            result.add(CircleResponse.from(circle, joined, myRole, muted));
        }
        fillOwnerInfo(result);
        return result;
    }

    /**
     * 把关联表里的 circleId 还原成圈子列表并保持原有顺序。
     * findAllById 不保证返回顺序，而分页顺序（加入时间倒序）必须保留。
     */
    private List<Circle> orderedByIds(List<Long> circleIds) {
        if (circleIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Circle> found = new HashMap<>();
        for (Circle circle : circleRepository.findAllById(circleIds)) {
            found.put(circle.getId(), circle);
        }
        List<Circle> ordered = new ArrayList<>(circleIds.size());
        for (Long circleId : circleIds) {
            Circle circle = found.get(circleId);
            if (circle != null) {
                ordered.add(circle);
            }
        }
        return ordered;
    }

    /**
     * 装配圈内帖子。
     *
     * <p>与其他列表接口同样的原则：无论一页多少条，跨表查询次数固定
     * （关联表 1 次 + 帖子 1 次 + 点赞 1 次 + 评论数 1 次 + 用户信息 1 次）。
     */
    private Page<PostResponse> toPostPage(Page<CirclePost> relations, Pageable pageable) {
        long total = relations.getTotalElements();
        if (relations.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, total);
        }
        Long userId = SecurityUtils.currentUser().getId();
        List<Long> postIds = relations.getContent().stream().map(CirclePost::getPostId).toList();

        Map<Long, Post> found = new HashMap<>();
        for (Post post : postRepository.findAllById(postIds)) {
            found.put(post.getId(), post);
        }
        // 帖子被作者删除后关联会留下悬空 id，这里直接跳过（不在分页里制造 null 元素）
        List<Post> posts = new ArrayList<>(postIds.size());
        for (Long postId : postIds) {
            Post post = found.get(postId);
            if (post != null) {
                posts.add(post);
            }
        }
        if (posts.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, total);
        }

        List<Long> visibleIds = posts.stream().map(Post::getId).toList();
        Set<Long> likedIds = new HashSet<>(postLikeRepository.findLikedPostIds(userId, visibleIds));
        Map<Long, Long> commentCounts = new HashMap<>();
        for (Object[] row : postCommentRepository.countByPostIds(visibleIds)) {
            commentCounts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }

        List<PostResponse> responses = new ArrayList<>(posts.size());
        for (Post post : posts) {
            responses.add(PostResponse.from(post,
                    likedIds.contains(post.getId()),
                    commentCounts.getOrDefault(post.getId(), 0L)));
        }
        fillPostAuthors(responses);
        return new PageImpl<>(responses, pageable, total);
    }

    /** 通过 Feign 批量补全圈主昵称与头像；失败时保留冗余昵称、头像留空。 */
    private void fillOwnerInfo(List<CircleResponse> responses) {
        Set<Long> ownerIds = new HashSet<>();
        for (CircleResponse response : responses) {
            if (response.getOwnerId() != null) {
                ownerIds.add(response.getOwnerId());
            }
        }
        Map<Long, UserBrief> users = userLookup.byIds(ownerIds);
        if (users.isEmpty()) {
            return;
        }
        for (CircleResponse response : responses) {
            UserBrief owner = users.get(response.getOwnerId());
            if (owner == null) {
                continue;
            }
            if (StringUtils.hasText(owner.nickname())) {
                response.setOwnerName(owner.nickname());
            }
            response.setOwnerAvatar(owner.avatar());
        }
    }

    /** 圈内帖子的作者信息同样只发起 1 次跨服务调用。 */
    private void fillPostAuthors(List<PostResponse> responses) {
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

    private void fillMemberProfiles(List<CircleMemberResponse> responses) {
        Set<Long> userIds = new HashSet<>();
        for (CircleMemberResponse response : responses) {
            if (response.getUserId() != null) {
                userIds.add(response.getUserId());
            }
        }
        Map<Long, UserBrief> users = userLookup.byIds(userIds);
        for (CircleMemberResponse response : responses) {
            UserBrief brief = users.get(response.getUserId());
            if (brief == null) {
                if (!StringUtils.hasText(response.getNickname())) {
                    response.setNickname("用户" + response.getUserId());
                }
                continue;
            }
            response.setNickname(StringUtils.hasText(brief.nickname())
                    ? brief.nickname()
                    : "用户" + response.getUserId());
            response.setAvatar(brief.avatar());
        }
    }

    private Circle requireCircle(Long id) {
        return circleRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "圈子不存在"));
    }

    private CircleMember requireMember(Long circleId, Long userId) {
        return circleMemberRepository.findByCircleIdAndUserId(circleId, userId)
                .orElseThrow(() -> new BizException(404, "对方不在这个圈子里"));
    }

    private CircleMember currentMembership(Long circleId) {
        return circleMemberRepository.findByCircleIdAndUserId(circleId, SecurityUtils.currentUser().getId())
                .orElse(null);
    }

    private void requireStaff(Circle circle, UserPrincipal user, CircleMember actor) {
        if (isCircleOwner(circle, user) || isPlatformAdmin(user)) {
            return;
        }
        if (actor != null && actor.getRole() == CircleMemberRole.ADMIN) {
            return;
        }
        throw new BizException(403, "仅圈主或管理员可执行此操作");
    }

    /** 圈子管理员（不含圈主、不含平台管理员） */
    private boolean isCircleAdminOnly(Circle circle, UserPrincipal user, CircleMember actor) {
        return !isCircleOwner(circle, user)
                && !isPlatformAdmin(user)
                && actor != null
                && actor.getRole() == CircleMemberRole.ADMIN;
    }

    private boolean isCircleOwner(Circle circle, UserPrincipal user) {
        return circle.getOwnerId() != null && circle.getOwnerId().equals(user.getId());
    }

    private CircleMemberRole parseAssignableRole(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException("请指定成员身份");
        }
        String normalized = raw.trim().toUpperCase();
        if ("ADMIN".equals(normalized)) {
            return CircleMemberRole.ADMIN;
        }
        if ("MEMBER".equals(normalized)) {
            return CircleMemberRole.MEMBER;
        }
        throw new BizException("只能设置为管理员或普通成员");
    }

    private int count(Integer value) {
        return value == null ? 0 : value;
    }

    /** 封面必须来自本站：外链图床失效后圈子列表会变成一片空白。 */
    private String coverUrl(String cover) {
        if (!StringUtils.hasText(cover)) {
            return null;
        }
        return LocalFileUrls.requireLocal(cover.trim(), "封面必须先上传到本站");
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

    private boolean isPlatformAdmin(UserPrincipal user) {
        String role = user.getRole();
        if (role == null) {
            return false;
        }
        if (role.startsWith("ROLE_")) {
            role = role.substring(5);
        }
        return "ADMIN".equals(role);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
