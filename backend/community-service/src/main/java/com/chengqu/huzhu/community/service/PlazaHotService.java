package com.chengqu.huzhu.community.service;

import com.chengqu.huzhu.api.dto.UserBrief;
import com.chengqu.huzhu.common.redis.RedisCache;
import com.chengqu.huzhu.common.redis.RedisKeys;
import com.chengqu.huzhu.community.dto.PlazaHotResponse;
import com.chengqu.huzhu.community.entity.PlazaHotRank;
import com.chengqu.huzhu.community.entity.Post;
import com.chengqu.huzhu.community.entity.PostChannel;
import com.chengqu.huzhu.community.repository.PlazaHotRankRepository;
import com.chengqu.huzhu.community.repository.PostCommentRepository;
import com.chengqu.huzhu.community.repository.PostRepository;
import com.chengqu.huzhu.community.support.UserLookup;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlazaHotService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_HOT = 16;

    private final PlazaHotRankRepository plazaHotRankRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostRepository postRepository;
    private final UserLookup userLookup;
    private final RedisCache redisCache;

    /**
     * 热度榜缓存时长。榜单每天 6:00 才重算一次，却会被首页高频拉取，
     * 是最典型的「读极多、写极少」数据。定时刷新与手动刷新都会主动失效缓存。
     *
     * <p>空榜单不写缓存：控制器在榜单为空时会触发一次同步重算，
     * 若把空结果也缓存下来，这个兜底逻辑会被压制 10 分钟。
     */
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    @Transactional(readOnly = true)
    public PlazaHotResponse currentBoard() {
        Optional<PlazaHotResponse> cached =
                redisCache.get(RedisKeys.plazaHot(), new TypeReference<PlazaHotResponse>() {
                });
        if (cached.isPresent()) {
            List<PlazaHotResponse.Item> items = cached.get().getItems();
            log.info("[广场] 热度榜命中缓存 items={}", items == null ? 0 : items.size());
            return cached.get();
        }

        PlazaHotResponse board = loadFromDatabase();
        if (board.getItems() != null && !board.getItems().isEmpty()) {
            redisCache.put(RedisKeys.plazaHot(), board, CACHE_TTL);
            log.info("[广场] 热度榜查库 items={}（已写入缓存 {}s）",
                    board.getItems().size(), CACHE_TTL.toSeconds());
        }
        return board;
    }

    private PlazaHotResponse loadFromDatabase() {
        LocalDate date = plazaHotRankRepository.findLatestDate();
        if (date == null) {
            return PlazaHotResponse.builder().rankDate(LocalDate.now(ZONE)).items(List.of()).build();
        }
        List<PlazaHotRank> ranks = plazaHotRankRepository.findByRankDateOrderByRankNoAsc(date);
        if (ranks.isEmpty()) {
            return PlazaHotResponse.builder().rankDate(date).items(List.of()).build();
        }
        // 批量取帖，替代原先逐条 findById（16 条榜单 = 16 次查询）
        Map<Long, Post> posts = postRepository.findAllById(
                        ranks.stream().map(PlazaHotRank::getPostId).toList())
                .stream()
                .collect(Collectors.toMap(Post::getId, post -> post));

        List<PlazaHotResponse.Item> items = new ArrayList<>();
        for (PlazaHotRank rank : ranks) {
            Post post = posts.get(rank.getPostId());
            if (post == null) {
                continue;
            }
            items.add(PlazaHotResponse.Item.builder()
                    .rankNo(rank.getRankNo())
                    .postId(post.getId())
                    .content(post.getContent())
                    .authorId(post.getAuthorId())
                    .authorName(post.getAuthorName())
                    .commentCount(rank.getCommentCount())
                    .likeCount(rank.getLikeCount())
                    .heatScore(post.getHeatScore() == null ? 0 : post.getHeatScore())
                    .createdAt(post.getCreatedAt())
                    .build());
        }
        fillAuthorInfo(items);
        return PlazaHotResponse.builder().rankDate(date).items(items).build();
    }

    /**
     * 通过 Feign 批量补全榜单作者的头像与昵称（1 次调用，非逐条）。
     * 定时任务路径没有请求上下文，Feign 会降级为空结果，此时头像留空由前端兜底。
     */
    private void fillAuthorInfo(List<PlazaHotResponse.Item> items) {
        if (items.isEmpty()) {
            return;
        }
        Set<Long> authorIds = items.stream()
                .map(PlazaHotResponse.Item::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, UserBrief> users = userLookup.byIds(authorIds);
        if (users.isEmpty()) {
            return;
        }
        for (PlazaHotResponse.Item item : items) {
            UserBrief author = users.get(item.getAuthorId());
            if (author == null) {
                continue;
            }
            if (StringUtils.hasText(author.nickname())) {
                item.setAuthorName(author.nickname());
            }
            item.setAuthorAvatar(author.avatar());
        }
    }

    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Shanghai")
    @Transactional
    public void refreshDaily() {
        refresh(LocalDate.now(ZONE));
    }

    @Transactional
    public PlazaHotResponse refresh(LocalDate rankDate) {
        plazaHotRankRepository.deleteByRankDate(rankDate);
        LocalDateTime since = rankDate.minusDays(1).atTime(6, 0);
        List<Object[]> rows = postCommentRepository.countPlazaCommentsSince(
                PostChannel.PLAZA, since, PageRequest.of(0, MAX_HOT));

        // 一次把上榜帖全部取回，而不是在循环里逐条 findById：
        // 最多 16 条，逐条查就是 16 次 SELECT，而这里只需要 1 次。
        List<Long> rankedIds = rows.stream()
                .limit(MAX_HOT)
                .map(row -> (Long) row[0])
                .toList();
        Map<Long, Post> rankedPosts = new HashMap<>();
        if (!rankedIds.isEmpty()) {
            postRepository.findAllById(rankedIds).forEach(post -> rankedPosts.put(post.getId(), post));
        }

        Set<Long> used = new LinkedHashSet<>();
        int rankNo = 1;
        List<PlazaHotRank> saved = new ArrayList<>();
        for (Object[] row : rows) {
            if (rankNo > MAX_HOT) {
                break;
            }
            Long postId = (Long) row[0];
            long comments = ((Number) row[1]).longValue();
            Post post = rankedPosts.get(postId);
            if (post == null) {
                continue;
            }
            used.add(postId);
            saved.add(buildRank(rankDate, rankNo++, post, comments));
        }
        if (rankNo <= MAX_HOT) {
            List<Post> fillers = postRepository.findByChannelOrderByLikeCountDescIdDesc(
                    PostChannel.PLAZA, PageRequest.of(0, MAX_HOT));
            // 同样把「近 24 小时评论数」一次问清（与主榜保持同一时间口径），
            // 而不是在循环里逐条 COUNT。多算几条被跳过的帖子不影响结果，
            // 但省掉了最多 16 次往返。
            List<Long> fillerIds = fillers.stream().map(Post::getId).toList();
            Map<Long, Long> fillerCommentCounts = new HashMap<>();
            if (!fillerIds.isEmpty()) {
                for (Object[] row : postCommentRepository.countByPostIdsAndCreatedAtAfter(fillerIds, since)) {
                    fillerCommentCounts.put((Long) row[0], ((Number) row[1]).longValue());
                }
            }
            for (Post post : fillers) {
                if (rankNo > MAX_HOT) {
                    break;
                }
                if (!used.add(post.getId())) {
                    continue;
                }
                saved.add(buildRank(rankDate, rankNo++, post,
                        fillerCommentCounts.getOrDefault(post.getId(), 0L)));
            }
        }
        plazaHotRankRepository.saveAll(saved);
        redisCache.evict(RedisKeys.plazaHot());
        log.info("[广场] 热点榜刷新 date={}, count={}", rankDate, saved.size());
        return currentBoard();
    }

    private PlazaHotRank buildRank(LocalDate date, int rankNo, Post post, long comments) {
        PlazaHotRank rank = new PlazaHotRank();
        rank.setRankDate(date);
        rank.setRankNo(rankNo);
        rank.setPostId(post.getId());
        rank.setCommentCount(comments);
        rank.setLikeCount(post.getLikeCount() == null ? 0 : post.getLikeCount());
        return rank;
    }
}
