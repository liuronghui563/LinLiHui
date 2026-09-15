package com.chengqu.huzhu.api.fallback;

import com.chengqu.huzhu.api.client.AidApiClient;
import com.chengqu.huzhu.api.dto.PlatformAidStats;
import com.chengqu.huzhu.api.dto.UserAidStats;
import com.chengqu.huzhu.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AidApiClientFallbackFactory implements FallbackFactory<AidApiClient> {

    @Override
    public AidApiClient create(Throwable cause) {
        return new AidApiClient() {
            @Override
            public ApiResponse<PlatformAidStats> platformStats() {
                log.warn("[熔断] AidApiClient.platformStats: {}", cause.getMessage());
                return ApiResponse.ok(null);
            }

            @Override
            public ApiResponse<UserAidStats> userStats(Long userId) {
                log.warn("[熔断] AidApiClient.userStats: {}", cause.getMessage());
                return ApiResponse.ok(null);
            }
        };
    }
}
