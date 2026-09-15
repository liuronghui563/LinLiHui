package com.chengqu.huzhu.api.fallback;

import com.chengqu.huzhu.api.client.CommunityApiClient;
import com.chengqu.huzhu.api.dto.PlatformPostStats;
import com.chengqu.huzhu.api.dto.UserPostStats;
import com.chengqu.huzhu.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CommunityApiClientFallbackFactory implements FallbackFactory<CommunityApiClient> {

    @Override
    public CommunityApiClient create(Throwable cause) {
        return new CommunityApiClient() {
            @Override
            public ApiResponse<PlatformPostStats> platformStats() {
                log.warn("[熔断] CommunityApiClient.platformStats: {}", cause.getMessage());
                return ApiResponse.ok(null);
            }

            @Override
            public ApiResponse<UserPostStats> userStats(Long userId) {
                log.warn("[熔断] CommunityApiClient.userStats: {}", cause.getMessage());
                return ApiResponse.ok(null);
            }
        };
    }
}
