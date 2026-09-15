package com.chengqu.huzhu.admin.controller;

import com.chengqu.huzhu.admin.dto.AdminDashboardResponse;
import com.chengqu.huzhu.admin.service.AdminDashboardService;
import com.chengqu.huzhu.common.api.ApiResponse;
import com.chengqu.huzhu.security.LoginUser;
import com.chengqu.huzhu.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员接口。
 *
 * <p>看板数据现在由 auth-service 通过 Feign 汇总四个域的统计：
 * 用户数取本地，求助/动态/广告取各自服务。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminDashboardResponse> dashboard() {
        LoginUser user = SecurityUtils.currentUser();
        log.info("[用户] 管理员访问看板 userId={}, phone={}", user.getId(), user.getPhone());
        return ApiResponse.ok(adminDashboardService.dashboard(user.getPhone()));
    }
}
