package com.chengqu.huzhu.api.client;

import com.chengqu.huzhu.api.dto.UserBrief;
import com.chengqu.huzhu.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

/**
 * 用户服务（auth-service）的内部契约。
 *
 * <p>路径前缀为 {@code /internal/**}，不在网关的路由规则内，
 * 只能由集群内部的服务之间调用，不对外暴露。
 */
@FeignClient(
        name = "auth-service",
        path = "/internal/user",
        contextId = "userApiClient",
        fallbackFactory = com.chengqu.huzhu.api.fallback.UserApiClientFallbackFactory.class)
public interface UserApiClient {

    /**
     * 批量查询用户精简信息。列表场景请使用本方法，避免逐条调用造成 N+1。
     *
     * @param ids 用户 ID 集合；调用方需保证非空
     */
    @GetMapping("/brief")
    ApiResponse<List<UserBrief>> findBriefs(@RequestParam("ids") Collection<Long> ids);

    /** 查询单个用户精简信息。 */
    @GetMapping("/{id}/brief")
    ApiResponse<UserBrief> findBrief(@PathVariable("id") Long id);
}
