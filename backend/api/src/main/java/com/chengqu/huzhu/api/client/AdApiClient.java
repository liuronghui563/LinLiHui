package com.chengqu.huzhu.api.client;

import com.chengqu.huzhu.api.dto.PlatformAdStats;
import com.chengqu.huzhu.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 广告服务（ad-service）的内部契约。
 *
 * <p>路径前缀为 {@code /internal/**}，不在网关的路由规则内，仅限集群内部调用。
 */
@FeignClient(
        name = "ad-service",
        path = "/internal/ad",
        contextId = "adApiClient",
        fallbackFactory = com.chengqu.huzhu.api.fallback.AdApiClientFallbackFactory.class)
public interface AdApiClient {

    /** 全平台广告统计，供管理台看板使用。 */
    @GetMapping("/platform-stats")
    ApiResponse<PlatformAdStats> platformStats();
}
