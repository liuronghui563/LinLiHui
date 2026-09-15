package com.chengqu.huzhu.user.service;

import com.chengqu.huzhu.api.client.AidApiClient;
import com.chengqu.huzhu.api.client.CommunityApiClient;
import com.chengqu.huzhu.api.dto.UserAidStats;
import com.chengqu.huzhu.api.dto.UserPostStats;
import com.chengqu.huzhu.common.api.ApiResponse;
import com.chengqu.huzhu.user.dto.PublicUserProfile;
import com.chengqu.huzhu.user.dto.UserHomeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户主页聚合：把「用户资料 + 求助统计 + 动态统计」合并为一次返回。
 *
 * <p>资料读取走本地数据库，两个统计通过 Feign 向对应服务获取。
 * <b>刻意不加 {@code @Transactional}</b>：不应在持有数据库事务的情况下发起跨服务调用，
 * 否则下游抖动会长时间占用连接。
 *
 * <p>任一统计服务不可用时对应字段为 null，让前端隐藏该区块，
 * 而不是用 0 冒充「没有求助/没有动态」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserHomeService {

    private final UserService userService;
    private final AidApiClient aidApiClient;
    private final CommunityApiClient communityApiClient;

    public UserHomeResponse home(Long userId) {
        PublicUserProfile profile = userService.publicProfile(userId);
        UserHomeResponse response = UserHomeResponse.builder()
                .profile(profile)
                .aidStats(loadAidStats(userId))
                .postStats(loadPostStats(userId))
                .build();
        log.info("[用户] 主页聚合 userId={}, aidStats={}, postStats={}",
                userId, response.getAidStats() != null, response.getPostStats() != null);
        return response;
    }

    private UserAidStats loadAidStats(Long userId) {
        try {
            ApiResponse<UserAidStats> response = aidApiClient.userStats(userId);
            if (response != null && response.getCode() == 0 && response.getData() != null) {
                return response.getData();
            }
            log.warn("[用户] 求助统计获取异常 userId={}, code={}", userId,
                    response == null ? "null" : response.getCode());
        } catch (Exception e) {
            log.warn("[用户] 调用 aid-service 获取求助统计失败 userId={}: {}", userId, e.getMessage());
        }
        return null;
    }

    private UserPostStats loadPostStats(Long userId) {
        try {
            ApiResponse<UserPostStats> response = communityApiClient.userStats(userId);
            if (response != null && response.getCode() == 0 && response.getData() != null) {
                return response.getData();
            }
            log.warn("[用户] 动态统计获取异常 userId={}, code={}", userId,
                    response == null ? "null" : response.getCode());
        } catch (Exception e) {
            log.warn("[用户] 调用 community-service 获取动态统计失败 userId={}: {}", userId, e.getMessage());
        }
        return null;
    }
}
