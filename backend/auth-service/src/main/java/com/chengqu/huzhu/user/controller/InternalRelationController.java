package com.chengqu.huzhu.user.controller;

import com.chengqu.huzhu.api.dto.UserExclusions;
import com.chengqu.huzhu.common.api.ApiResponse;
import com.chengqu.huzhu.user.service.UserRelationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户关系内部接口，供内容服务做信息流过滤。
 *
 * <p>无参数：返回的是「当前令牌持有者」的屏蔽集合。Feign 会把调用方的
 * {@code Authorization} 透传过来（见 api 模块的 FeignAuthForwardConfig），
 * 因此这里天然是请求用户视角，不需要也不应该接收 userId 参数——
 * 那样会变成一个可以查询任意用户黑名单的越权接口。
 *
 * <p>路径不带 {@code /api} 前缀，网关不路由，外部无法访问。
 */
@Slf4j
@RestController
@RequestMapping("/internal/user")
@RequiredArgsConstructor
public class InternalRelationController {

    private final UserRelationService relationService;

    @GetMapping("/exclusions")
    public ApiResponse<UserExclusions> exclusions() {
        return ApiResponse.ok(relationService.exclusionsForCurrentUser());
    }
}
