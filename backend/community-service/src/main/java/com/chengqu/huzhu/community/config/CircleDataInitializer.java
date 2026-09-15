package com.chengqu.huzhu.community.config;

import com.chengqu.huzhu.community.entity.Circle;
import com.chengqu.huzhu.community.entity.CircleMember;
import com.chengqu.huzhu.community.entity.CircleMemberRole;
import com.chengqu.huzhu.community.entity.CirclePost;
import com.chengqu.huzhu.community.entity.CircleStatus;
import com.chengqu.huzhu.community.entity.Post;
import com.chengqu.huzhu.community.repository.CircleMemberRepository;
import com.chengqu.huzhu.community.repository.CirclePostRepository;
import com.chengqu.huzhu.community.repository.CircleRepository;
import com.chengqu.huzhu.community.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 表为空时写入几个示例圈子。
 *
 * <p>圈主 id 与 auth-service 种子一致：1=系统管理员，2=邻里用户；
 * 示例圈主同时写入 u_r_circle_member 的 OWNER 记录，
 * 否则成员表和成员数会对不上（示例数据也要自洽，否则排查问题时会被误导）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CircleDataInitializer implements CommandLineRunner {

    private static final long SEED_ADMIN_ID = 1L;
    private static final String SEED_ADMIN_NAME = "系统管理员";
    private static final long SEED_USER_ID = 2L;
    private static final String SEED_USER_NAME = "邻里用户";

    private final CircleRepository circleRepository;
    private final CircleMemberRepository circleMemberRepository;
    private final CirclePostRepository circlePostRepository;
    private final PostRepository postRepository;

    @Override
    public void run(String... args) {
        if (circleRepository.count() > 0) {
            log.info("[圈子] 表 u_r_circle 已有数据 count={}，跳过种子", circleRepository.count());
            return;
        }

        Circle badminton = createCircle("周末羽毛球", "每周六上午在社区体育馆打球，新手也欢迎。",
                SEED_USER_ID, SEED_USER_NAME);
        createCircle("阳台种菜交流", "分享种子、肥料和踩过的坑，欢迎晒图。",
                SEED_ADMIN_ID, SEED_ADMIN_NAME);

        linkSamplePosts(badminton);
        log.info("[圈子] 已写入示例圈子 2 个 -> 表 u_r_circle / u_r_circle_member");
    }

    private Circle createCircle(String name, String description, long ownerId, String ownerName) {
        Circle circle = new Circle();
        circle.setName(name);
        circle.setDescription(description);
        circle.setOwnerId(ownerId);
        circle.setOwnerName(ownerName);
        circle.setMemberCount(1);
        circle.setPostCount(0);
        circle.setStatus(CircleStatus.ACTIVE);
        Circle saved = circleRepository.save(circle);

        CircleMember owner = new CircleMember();
        owner.setCircleId(saved.getId());
        owner.setUserId(ownerId);
        owner.setRole(CircleMemberRole.OWNER);
        circleMemberRepository.save(owner);
        return saved;
    }

    /**
     * 收录两条既有动态，让示例圈子不是空壳。
     *
     * <p>CommunityDataInitializer 与本类同为 CommandLineRunner，谁先执行没有保证：
     * 此刻若还没有动态就跳过——圈子仍然可用，之后可以通过
     * {@code POST /api/circle/{id}/posts} 收录。
     */
    private void linkSamplePosts(Circle circle) {
        List<Post> samples = postRepository.findAll(PageRequest.of(0, 2)).getContent();
        if (samples.isEmpty()) {
            log.info("[圈子] 当前没有可收录的动态，示例圈子 circleId={} 暂为空", circle.getId());
            return;
        }
        for (Post post : samples) {
            CirclePost relation = new CirclePost();
            relation.setCircleId(circle.getId());
            relation.setPostId(post.getId());
            circlePostRepository.save(relation);
        }
        circle.setPostCount((int) circlePostRepository.countByCircleId(circle.getId()));
        circleRepository.save(circle);
        log.info("[圈子] 示例圈子 circleId={} 已收录动态 {} 条", circle.getId(), circle.getPostCount());
    }
}
