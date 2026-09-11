package com.chengqu.huzhu.aid.service;

import com.chengqu.huzhu.aid.dto.AidResponse;
import com.chengqu.huzhu.aid.dto.AidStatsResponse;
import com.chengqu.huzhu.aid.dto.CreateAidRequest;
import com.chengqu.huzhu.aid.dto.HelperReviewRequest;
import com.chengqu.huzhu.aid.dto.ScoreRequest;
import com.chengqu.huzhu.aid.dto.UpdateAidRequest;
import com.chengqu.huzhu.aid.entity.AidBoard;
import com.chengqu.huzhu.aid.entity.AidHelperReview;
import com.chengqu.huzhu.aid.entity.AidRating;
import com.chengqu.huzhu.aid.entity.AidRequest;
import com.chengqu.huzhu.aid.entity.AidStatus;
import com.chengqu.huzhu.aid.repository.AidHelperReviewRepository;
import com.chengqu.huzhu.aid.repository.AidRatingRepository;
import com.chengqu.huzhu.aid.repository.AidRequestRepository;
import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.common.security.SecurityUtils;
import com.chengqu.huzhu.common.security.UserPrincipal;
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
public class AidService {

    private final AidRequestRepository aidRequestRepository;
    private final AidRatingRepository aidRatingRepository;
    private final AidHelperReviewRepository aidHelperReviewRepository;

    private static final java.util.List<String> CAMPUS_CATEGORIES = java.util.List.of("失物招领");

    @Transactional(readOnly = true)
    public Page<AidResponse> list(AidStatus status, String category, Boolean student, String board, Pageable pageable) {
        AidBoard parsedBoard = parseBoard(board, Boolean.TRUE.equals(student));
        boolean hasCategory = StringUtils.hasText(category);
        Page<AidRequest> page;
        if (parsedBoard == AidBoard.CAMPUS) {
            if (hasCategory && status != null) {
                page = aidRequestRepository.findByBoardAndStatusAndCategory(AidBoard.CAMPUS, status, category.trim(), pageable);
            } else if (hasCategory) {
                page = aidRequestRepository.findByBoardAndCategory(AidBoard.CAMPUS, category.trim(), pageable);
            } else if (status != null) {
                page = aidRequestRepository.findByBoardAndStatus(AidBoard.CAMPUS, status, pageable);
            } else {
                page = aidRequestRepository.findByBoard(AidBoard.CAMPUS, pageable);
            }
        } else if (hasCategory && status != null) {
            page = aidRequestRepository.findNeighborhoodByStatusAndCategory(AidBoard.NEIGHBORHOOD, status, category.trim(), pageable);
        } else if (hasCategory) {
            page = aidRequestRepository.findNeighborhoodByCategory(AidBoard.NEIGHBORHOOD, category.trim(), pageable);
        } else if (status != null) {
            page = aidRequestRepository.findNeighborhoodByStatus(AidBoard.NEIGHBORHOOD, status, pageable);
        } else {
            page = aidRequestRepository.findNeighborhood(AidBoard.NEIGHBORHOOD, pageable);
        }
        log.info("[求助] 查询列表 board={}, status={}, category={}, page={}, size={}, total={}",
                parsedBoard, status, category, pageable.getPageNumber(), page.getNumberOfElements(), page.getTotalElements());
        return page.map(this::withRating);
    }

    @Transactional(readOnly = true)
    public AidResponse detail(Long id) {
        AidResponse resp = withRating(requireAid(id));
        log.info("[求助] 查询详情 id={}, title={}, status={}", id, resp.getTitle(), resp.getStatus());
        return resp;
    }

    @Transactional
    public AidResponse create(CreateAidRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        AidRequest aid = new AidRequest();
        aid.setTitle(request.getTitle().trim());
        aid.setContent(request.getContent().trim());
        aid.setCategory(request.getCategory().trim());
        AidBoard board = parseBoard(request.getBoard(), false);
        if (board == AidBoard.CAMPUS) {
            if (!CAMPUS_CATEGORIES.contains(aid.getCategory())) {
                throw new BizException("校园专区分类无效");
            }
            aid.setAddress(StringUtils.hasText(request.getAddress()) ? request.getAddress().trim() : "校园");
        } else if (!StringUtils.hasText(request.getAddress())) {
            throw new BizException("地址不能为空");
        } else {
            aid.setAddress(request.getAddress().trim());
        }
        aid.setBoard(board);
        aid.setStatus(AidStatus.OPEN);
        aid.setPublisherId(user.getId());
        aid.setPublisherName(displayName(user));
        AidRequest saved = aidRequestRepository.save(aid);
        log.info("[求助] 新建成功 id={}, publisherId={}, title={}", saved.getId(), user.getId(), saved.getTitle());
        return withRating(saved);
    }

    @Transactional
    public AidResponse update(Long id, UpdateAidRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        AidRequest aid = requireAid(id);
        if (!aid.getPublisherId().equals(user.getId())) {
            throw new BizException(403, "仅发布者可编辑求助");
        }
        if (aid.getStatus() != AidStatus.OPEN) {
            throw new BizException("仅待接单状态可编辑");
        }
        aid.setTitle(request.getTitle().trim());
        aid.setContent(request.getContent().trim());
        aid.setCategory(request.getCategory().trim());
        aid.setAddress(request.getAddress().trim());
        AidRequest saved = aidRequestRepository.save(aid);
        log.info("[求助] 更新成功 id={}, operatorId={}", saved.getId(), user.getId());
        return withRating(saved);
    }

    @Transactional
    public void delete(Long id) {
        UserPrincipal user = SecurityUtils.currentUser();
        AidRequest aid = requireAid(id);
        boolean isPublisher = aid.getPublisherId().equals(user.getId());
        boolean isAdmin = isAdmin(user);
        if (!isPublisher && !isAdmin) {
            throw new BizException(403, "仅发布者或管理员可删除求助");
        }
        if (isPublisher && aid.getStatus() == AidStatus.ACCEPTED) {
            throw new BizException("进行中的求助不可直接删除，请先联系帮助者完成或取消");
        }
        aidRatingRepository.deleteByAidId(id);
        aidHelperReviewRepository.deleteByAidId(id);
        aidRequestRepository.delete(aid);
        log.info("[求助] 删除成功 id={}, operatorId={}, wasStatus={}", id, user.getId(), aid.getStatus());
    }

    @Transactional
    public AidResponse accept(Long id) {
        UserPrincipal user = SecurityUtils.currentUser();
        AidRequest aid = requireAid(id);
        if (aid.getStatus() != AidStatus.OPEN) {
            throw new BizException("仅开放中的求助可被接单");
        }
        if (aid.getPublisherId().equals(user.getId())) {
            throw new BizException("不能接自己发布的求助");
        }
        aid.setStatus(AidStatus.ACCEPTED);
        aid.setHelperId(user.getId());
        aid.setHelperName(displayName(user));
        AidRequest saved = aidRequestRepository.save(aid);
        log.info("[求助] 接单成功 id={}, helperId={}, publisherId={}", id, user.getId(), aid.getPublisherId());
        return withRating(saved);
    }

    @Transactional
    public AidResponse complete(Long id) {
        UserPrincipal user = SecurityUtils.currentUser();
        AidRequest aid = requireAid(id);
        if (aid.getStatus() != AidStatus.ACCEPTED) {
            throw new BizException("仅已接单的求助可标记完成");
        }
        boolean isPublisher = aid.getPublisherId().equals(user.getId());
        boolean isHelper = aid.getHelperId() != null && aid.getHelperId().equals(user.getId());
        if (!isPublisher && !isHelper) {
            throw new BizException(403, "仅发布者或帮助者可标记完成");
        }
        aid.setStatus(AidStatus.DONE);
        AidRequest saved = aidRequestRepository.save(aid);
        log.info("[求助] 标记完成 id={}, operatorId={}", id, user.getId());
        return withRating(saved);
    }

    @Transactional
    public AidResponse cancel(Long id) {
        UserPrincipal user = SecurityUtils.currentUser();
        AidRequest aid = requireAid(id);
        if (!aid.getPublisherId().equals(user.getId())) {
            throw new BizException(403, "仅发布者可取消求助");
        }
        if (aid.getStatus() != AidStatus.OPEN) {
            throw new BizException("仅开放中的求助可取消");
        }
        aid.setStatus(AidStatus.CANCELLED);
        AidRequest saved = aidRequestRepository.save(aid);
        log.info("[求助] 取消成功 id={}, publisherId={}", id, user.getId());
        return withRating(saved);
    }

    @Transactional(readOnly = true)
    public Page<AidResponse> myPublished(Pageable pageable) {
        Long userId = SecurityUtils.currentUser().getId();
        Page<AidResponse> page = aidRequestRepository.findByPublisherId(userId, pageable).map(this::withRating);
        log.info("[求助] 我发布的 userId={}, total={}", userId, page.getTotalElements());
        return page;
    }

    @Transactional(readOnly = true)
    public Page<AidResponse> publishedByUser(Long userId, Pageable pageable) {
        Page<AidResponse> page = aidRequestRepository.findByPublisherId(userId, pageable).map(this::withRating);
        log.info("[求助] 查看用户发布 userId={}, viewerId={}, total={}",
                userId, SecurityUtils.currentUser().getId(), page.getTotalElements());
        return page;
    }

    @Transactional(readOnly = true)
    public Page<AidResponse> myHelping(Pageable pageable) {
        Long userId = SecurityUtils.currentUser().getId();
        Page<AidResponse> page = aidRequestRepository.findByHelperId(userId, pageable).map(this::withRating);
        log.info("[求助] 我帮忙的 userId={}, total={}", userId, page.getTotalElements());
        return page;
    }

    @Transactional(readOnly = true)
    public AidStatsResponse stats() {
        Long userId = SecurityUtils.currentUser().getId();
        AidStatsResponse stats = AidStatsResponse.builder()
                .openCount(aidRequestRepository.countByStatus(AidStatus.OPEN))
                .acceptedCount(aidRequestRepository.countByStatus(AidStatus.ACCEPTED))
                .doneCount(aidRequestRepository.countByStatus(AidStatus.DONE))
                .myPublishedCount(aidRequestRepository.countByPublisherId(userId))
                .myHelpingCount(aidRequestRepository.countByHelperId(userId))
                .build();
        log.info("[求助] 统计 open={}, accepted={}, done={}",
                stats.getOpenCount(), stats.getAcceptedCount(), stats.getDoneCount());
        return stats;
    }

    @Transactional
    public AidResponse rate(Long id, ScoreRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        AidRequest aid = requireAid(id);
        if (aid.getPublisherId().equals(user.getId())) {
            throw new BizException("不能评价自己发布的求助");
        }
        AidRating rating = aidRatingRepository.findByAidIdAndRaterId(id, user.getId())
                .orElseGet(() -> {
                    AidRating created = new AidRating();
                    created.setAidId(id);
                    created.setRaterId(user.getId());
                    return created;
                });
        rating.setScore(request.getScore());
        aidRatingRepository.save(rating);
        log.info("[求助] 评价 id={}, raterId={}, score={}", id, user.getId(), request.getScore());
        return withRating(aid);
    }

    @Transactional
    public AidResponse reviewHelper(Long id, HelperReviewRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        AidRequest aid = requireAid(id);
        if (!aid.getPublisherId().equals(user.getId())) {
            throw new BizException(403, "仅求助方可评价帮助方");
        }
        if (aid.getStatus() != AidStatus.DONE) {
            throw new BizException("互助完成后才能评价帮助方");
        }
        if (aid.getHelperId() == null) {
            throw new BizException("该求助没有帮助方");
        }
        AidHelperReview review = aidHelperReviewRepository.findByAidId(id)
                .orElseGet(() -> {
                    AidHelperReview created = new AidHelperReview();
                    created.setAidId(id);
                    created.setPublisherId(user.getId());
                    created.setHelperId(aid.getHelperId());
                    return created;
                });
        review.setScore(request.getScore());
        review.setContent(StringUtils.hasText(request.getContent()) ? request.getContent().trim() : null);
        aidHelperReviewRepository.save(review);
        log.info("[求助] 求助方评价帮助方 aidId={}, helperId={}, score={}", id, aid.getHelperId(), request.getScore());
        return withRating(aid);
    }

    private AidResponse withRating(AidRequest aid) {
        AidResponse resp = AidResponse.from(aid);
        Long viewerId = SecurityUtils.currentUser().getId();
        Double avg = aidRatingRepository.averageByAidId(aid.getId());
        resp.setRatingAvg(avg == null ? 0D : Math.round(avg * 10.0) / 10.0);
        resp.setRatingCount(aidRatingRepository.countByAidId(aid.getId()));
        resp.setMyScore(aidRatingRepository.findByAidIdAndRaterId(aid.getId(), viewerId)
                .map(AidRating::getScore)
                .orElse(null));
        aidHelperReviewRepository.findByAidId(aid.getId()).ifPresent(review -> {
            resp.setHelperReviewScore(review.getScore());
            resp.setHelperReviewContent(review.getContent());
            resp.setHelperReviewAt(review.getCreatedAt());
        });
        resp.setCanReviewHelper(aid.getPublisherId().equals(viewerId)
                && aid.getStatus() == AidStatus.DONE
                && aid.getHelperId() != null);
        return resp;
    }

    private AidRequest requireAid(Long id) {
        return aidRequestRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "求助不存在"));
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

    private AidBoard parseBoard(String board, boolean campusFallback) {
        if (StringUtils.hasText(board)) {
            try {
                return AidBoard.valueOf(board.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        if (campusFallback) {
            return AidBoard.CAMPUS;
        }
        return AidBoard.NEIGHBORHOOD;
    }
}
