package com.chengqu.huzhu.community.controller;

import com.chengqu.huzhu.api.dto.PlatformPostStats;
import com.chengqu.huzhu.api.dto.UserPostStats;
import com.chengqu.huzhu.common.api.ApiResponse;
import com.chengqu.huzhu.community.service.CommunityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 社区统计的内部接口，供 auth-service 通过 Feign 聚合。
 *
 * <p>路径不带 {@code /api} 前缀，网关不路由，外部无法访问。
 */
@Slf4j
@RestController
@RequestMapping("/internal/community")
@RequiredArgsConstructor
public class InternalCommunityController {

    private final CommunityService communityService;

    /** 全平台动态统计，供管理台看板使用。 */
    @GetMapping("/platform-stats")
    public ApiResponse<PlatformPostStats> platformStats() {
        return ApiResponse.ok(communityService.platformStats());
    }

    /** 指定用户在社区域的统计，供用户主页聚合使用。 */
    @GetMapping("/user-stats/{userId}")
    public ApiResponse<UserPostStats> userStats(@PathVariable("userId") Long userId) {
        return ApiResponse.ok(communityService.userStats(userId));
    }
}
