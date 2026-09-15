package com.chengqu.huzhu.api.client;

import com.chengqu.huzhu.api.dto.PlatformPostStats;
import com.chengqu.huzhu.api.dto.UserPostStats;
import com.chengqu.huzhu.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 社区服务（community-service）的内部契约。
 *
 * <p>路径前缀为 {@code /internal/**}，不在网关的路由规则内，仅限集群内部调用。
 */
@FeignClient(
        name = "community-service",
        path = "/internal/community",
        contextId = "communityApiClient",
        fallbackFactory = com.chengqu.huzhu.api.fallback.CommunityApiClientFallbackFactory.class)
public interface CommunityApiClient {

    /** 全平台动态统计，供管理台看板使用。 */
    @GetMapping("/platform-stats")
    ApiResponse<PlatformPostStats> platformStats();

    /** 指定用户在社区域的统计，供用户主页聚合使用。 */
    @GetMapping("/user-stats/{userId}")
    ApiResponse<UserPostStats> userStats(@PathVariable("userId") Long userId);
}
