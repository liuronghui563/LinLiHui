package com.chengqu.huzhu.community.controller;

import com.chengqu.huzhu.common.api.ApiResponse;
import com.chengqu.huzhu.community.dto.RecycleStatsResponse;
import com.chengqu.huzhu.community.service.RecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 回收统计的内部接口，供管理台通过 Feign 聚合。
 *
 * <p>单独一个 Controller 而不是往 InternalCommunityController 里加方法：
 * 那个类正在被并行改动（平台统计、用户统计），新业务自己一个入口，
 * 合并时的冲突面最小。
 *
 * <p>路径同样不带 {@code /api} 前缀，网关不路由，外部无法访问。
 */
@Slf4j
@RestController
@RequestMapping("/internal/community/recycle")
@RequiredArgsConstructor
public class InternalRecycleController {

    private final RecycleService recycleService;

    /** 回收预约统计：{@code {pending, done}}。 */
    @GetMapping("/stats")
    public ApiResponse<RecycleStatsResponse> stats() {
        return ApiResponse.ok(recycleService.stats());
    }
}
