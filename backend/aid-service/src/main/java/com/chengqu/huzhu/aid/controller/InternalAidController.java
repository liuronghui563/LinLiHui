package com.chengqu.huzhu.aid.controller;

import com.chengqu.huzhu.aid.service.AidService;
import com.chengqu.huzhu.api.dto.PlatformAidStats;
import com.chengqu.huzhu.api.dto.UserAidStats;
import com.chengqu.huzhu.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 求助统计的内部接口，供 auth-service 通过 Feign 聚合。
 *
 * <p>路径不带 {@code /api} 前缀，网关不路由，外部无法访问。
 */
@Slf4j
@RestController
@RequestMapping("/internal/aid")
@RequiredArgsConstructor
public class InternalAidController {

    private final AidService aidService;

    /** 全平台求助统计，供管理台看板使用。 */
    @GetMapping("/platform-stats")
    public ApiResponse<PlatformAidStats> platformStats() {
        return ApiResponse.ok(aidService.platformStats());
    }

    /** 指定用户在求助域的统计，供用户主页聚合使用。 */
    @GetMapping("/user-stats/{userId}")
    public ApiResponse<UserAidStats> userStats(@PathVariable("userId") Long userId) {
        return ApiResponse.ok(aidService.userStats(userId));
    }
}
