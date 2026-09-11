package com.chengqu.huzhu.community.service;

import com.chengqu.huzhu.community.dto.PlazaHotResponse;
import com.chengqu.huzhu.community.entity.PlazaHotRank;
import com.chengqu.huzhu.community.entity.Post;
import com.chengqu.huzhu.community.entity.PostChannel;
import com.chengqu.huzhu.community.repository.PlazaHotRankRepository;
import com.chengqu.huzhu.community.repository.PostCommentRepository;
import com.chengqu.huzhu.community.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlazaHotService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_HOT = 16;

    private final PlazaHotRankRepository plazaHotRankRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostRepository postRepository;

    @Transactional(readOnly = true)
    public PlazaHotResponse currentBoard() {
        LocalDate date = plazaHotRankRepository.findLatestDate();
        if (date == null) {
            return PlazaHotResponse.builder().rankDate(LocalDate.now(ZONE)).items(List.of()).build();
        }
        List<PlazaHotResponse.Item> items = new ArrayList<>();
        for (PlazaHotRank rank : plazaHotRankRepository.findByRankDateOrderByRankNoAsc(date)) {
            postRepository.findById(rank.getPostId()).ifPresent(post -> items.add(PlazaHotResponse.Item.builder()
                    .rankNo(rank.getRankNo())
                    .postId(post.getId())
                    .content(post.getContent())
                    .authorId(post.getAuthorId())
                    .authorName(post.getAuthorName())
                    .authorAvatar("https://picsum.photos/seed/user-" + post.getAuthorId() + "/200/200")
                    .commentCount(rank.getCommentCount())
                    .likeCount(rank.getLikeCount())
                    .heatScore(post.getHeatScore() == null ? 0 : post.getHeatScore())
                    .createdAt(post.getCreatedAt())
                    .build()));
        }
        return PlazaHotResponse.builder().rankDate(date).items(items).build();
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
        Set<Long> used = new LinkedHashSet<>();
        int rankNo = 1;
        List<PlazaHotRank> saved = new ArrayList<>();
        for (Object[] row : rows) {
            if (rankNo > MAX_HOT) {
                break;
            }
            Long postId = (Long) row[0];
            long comments = ((Number) row[1]).longValue();
            Post post = postRepository.findById(postId).orElse(null);
            if (post == null) {
                continue;
            }
            used.add(postId);
            saved.add(buildRank(rankDate, rankNo++, post, comments));
        }
        if (rankNo <= MAX_HOT) {
            List<Post> fillers = postRepository.findByChannelOrderByLikeCountDescIdDesc(
                    PostChannel.PLAZA, PageRequest.of(0, MAX_HOT));
            for (Post post : fillers) {
                if (rankNo > MAX_HOT) {
                    break;
                }
                if (!used.add(post.getId())) {
                    continue;
                }
                saved.add(buildRank(rankDate, rankNo++, post, postCommentRepository.countByPostId(post.getId())));
            }
        }
        plazaHotRankRepository.saveAll(saved);
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
