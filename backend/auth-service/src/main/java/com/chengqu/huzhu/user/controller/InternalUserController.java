package com.chengqu.huzhu.user.controller;

import com.chengqu.huzhu.api.dto.UserBrief;
import com.chengqu.huzhu.common.api.ApiResponse;
import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户信息的内部接口，供 aid-service / community-service 通过 Feign 调用。
 *
 * <p>路径刻意不带 {@code /api} 前缀：网关只路由 {@code /api/**}，
 * 因此这些接口无法从外部经网关访问，只能由集群内部直连。
 * 它们仍受 Spring Security 保护，需要合法的 JWT（由 Feign 拦截器透传调用方令牌）。
 */
@Slf4j
@RestController
@RequestMapping("/internal/user")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    /** 批量查询用户精简信息。列表场景使用，固定 2 次查询。 */
    @GetMapping("/brief")
    public ApiResponse<List<UserBrief>> briefs(@RequestParam("ids") List<Long> ids) {
        return ApiResponse.ok(userService.briefs(ids));
    }

    /** 查询单个用户精简信息。 */
    @GetMapping("/{id}/brief")
    public ApiResponse<UserBrief> brief(@PathVariable("id") Long id) {
        List<UserBrief> found = userService.briefs(List.of(id));
        if (found.isEmpty()) {
            throw new BizException(404, "用户不存在");
        }
        return ApiResponse.ok(found.get(0));
    }
}
