package com.chengqu.huzhu.api.client;

import com.chengqu.huzhu.api.dto.PlatformAidStats;
import com.chengqu.huzhu.api.dto.UserAidStats;
import com.chengqu.huzhu.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 求助服务（aid-service）的内部契约。
 *
 * <p>路径前缀为 {@code /internal/**}，不在网关的路由规则内，仅限集群内部调用。
 */
@FeignClient(
        name = "aid-service",
        path = "/internal/aid",
        contextId = "aidApiClient",
        fallbackFactory = com.chengqu.huzhu.api.fallback.AidApiClientFallbackFactory.class)
public interface AidApiClient {

    /** 全平台求助统计，供管理台看板使用。 */
    @GetMapping("/platform-stats")
    ApiResponse<PlatformAidStats> platformStats();

    /** 指定用户在求助域的统计，供用户主页聚合使用。 */
    @GetMapping("/user-stats/{userId}")
    ApiResponse<UserAidStats> userStats(@PathVariable("userId") Long userId);
}
