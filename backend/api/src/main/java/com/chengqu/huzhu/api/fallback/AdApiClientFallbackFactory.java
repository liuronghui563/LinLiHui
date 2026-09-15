package com.chengqu.huzhu.api.fallback;

import com.chengqu.huzhu.api.client.AdApiClient;
import com.chengqu.huzhu.api.dto.PlatformAdStats;
import com.chengqu.huzhu.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AdApiClientFallbackFactory implements FallbackFactory<AdApiClient> {

    @Override
    public AdApiClient create(Throwable cause) {
        return new AdApiClient() {
            @Override
            public ApiResponse<PlatformAdStats> platformStats() {
                log.warn("[熔断] AdApiClient.platformStats: {}", cause.getMessage());
                return ApiResponse.ok(null);
            }
        };
    }
}
