package com.chengqu.huzhu.ad.controller;

import com.chengqu.huzhu.ad.service.AdService;
import com.chengqu.huzhu.api.dto.PlatformAdStats;
import com.chengqu.huzhu.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 广告统计的内部接口，供 auth-service 通过 Feign 聚合。
 *
 * <p>路径不带 {@code /api} 前缀，网关不路由，外部无法访问。
 */
@Slf4j
@RestController
@RequestMapping("/internal/ad")
@RequiredArgsConstructor
public class InternalAdController {

    private final AdService adService;

    /** 全平台广告统计，供管理台看板使用。 */
    @GetMapping("/platform-stats")
    public ApiResponse<PlatformAdStats> platformStats() {
        return ApiResponse.ok(adService.platformStats());
    }
}
