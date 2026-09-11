package com.chengqu.huzhu.admin.controller;

import com.chengqu.huzhu.common.api.ApiResponse;
import com.chengqu.huzhu.security.LoginUser;
import com.chengqu.huzhu.security.SecurityUtils;
import com.chengqu.huzhu.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理员接口示例，用于验证 RBAC。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> dashboard() {
        LoginUser user = SecurityUtils.currentUser();
        log.info("[用户] 管理员仪表盘 userId={}, phone={}", user.getId(), user.getPhone());
        return ApiResponse.ok(Map.of(
                "greeting", "欢迎管理员 " + user.getPhone(),
                "userCount", userRepository.count()
        ));
    }
}
