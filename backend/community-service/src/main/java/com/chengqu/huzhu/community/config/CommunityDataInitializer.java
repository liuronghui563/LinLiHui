package com.chengqu.huzhu.community.config;

import com.chengqu.huzhu.community.entity.Post;
import com.chengqu.huzhu.community.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 空表时写入示例动态。
 * <p>
 * 作者 id 与 auth-service 种子一致：1=系统管理员，2=邻里用户。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommunityDataInitializer implements CommandLineRunner {

    private static final long SEED_ADMIN_ID = 1L;
    private static final String SEED_ADMIN_NAME = "系统管理员";
    private static final long SEED_USER_ID = 2L;
    private static final String SEED_USER_NAME = "邻里用户";

    private final PostRepository postRepository;

    @Override
    public void run(String... args) {
        if (postRepository.count() > 0) {
            log.info("[社区] 表 u_r_post 已有数据 count={}，跳过种子", postRepository.count());
            return;
        }
        Post p1 = new Post();
        p1.setContent("周末小区有义诊，欢迎邻里来坐坐。");
        p1.setAuthorId(SEED_ADMIN_ID);
        p1.setAuthorName(SEED_ADMIN_NAME);
        p1.setLikeCount(0);
        postRepository.save(p1);

        Post p2 = new Post();
        p2.setContent("有人一起拼车去市图书馆吗？周六上午出发。");
        p2.setAuthorId(SEED_USER_ID);
        p2.setAuthorName(SEED_USER_NAME);
        p2.setLikeCount(0);
        postRepository.save(p2);

        log.info("[社区] 已写入示例动态 2 条 -> 表 u_r_post");
    }
}
