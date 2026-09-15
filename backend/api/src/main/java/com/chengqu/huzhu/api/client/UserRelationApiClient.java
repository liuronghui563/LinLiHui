package com.chengqu.huzhu.api.client;

import com.chengqu.huzhu.api.dto.UserExclusions;
import com.chengqu.huzhu.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 用户关系内部契约（关注 / 拉黑）。
 *
 * <p>无参数：返回的是当前令牌持有者的屏蔽集合。调用方的 Authorization 头由
 * {@code FeignAuthForwardConfig} 自动透传，因此下游能识别请求用户。
 *
 * <p>刻意不提供「按 userId 查询他人黑名单」的入口——那会变成越权接口。
 */
@FeignClient(name = "auth-service", path = "/internal/user", contextId = "userRelationApiClient")
public interface UserRelationApiClient {

    /** 当前用户需要屏蔽的作者集合（我拉黑的 ∪ 拉黑我的）。 */
    @GetMapping("/exclusions")
    ApiResponse<UserExclusions> exclusions();
}
