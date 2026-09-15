package com.chengqu.huzhu.api.fallback;

import com.chengqu.huzhu.api.client.UserApiClient;
import com.chengqu.huzhu.api.dto.UserBrief;
import com.chengqu.huzhu.common.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Slf4j
@Component
public class UserApiClientFallbackFactory implements FallbackFactory<UserApiClient> {

    @Override
    public UserApiClient create(Throwable cause) {
        return new UserApiClient() {
            @Override
            public ApiResponse<List<UserBrief>> findBriefs(Collection<Long> ids) {
                log.warn("[熔断] UserApiClient.findBriefs: {}", cause.getMessage());
                return ApiResponse.ok(List.of());
            }

            @Override
            public ApiResponse<UserBrief> findBrief(Long id) {
                log.warn("[熔断] UserApiClient.findBrief: {}", cause.getMessage());
                return ApiResponse.ok(null);
            }
        };
    }
}
