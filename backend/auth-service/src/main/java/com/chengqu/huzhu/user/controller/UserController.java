package com.chengqu.huzhu.user.controller;

import com.chengqu.huzhu.auth.dto.TokenResponse;
import com.chengqu.huzhu.common.api.ApiResponse;
import com.chengqu.huzhu.security.LoginUser;
import com.chengqu.huzhu.security.SecurityUtils;
import com.chengqu.huzhu.user.dto.PublicUserProfile;
import com.chengqu.huzhu.user.dto.UpdateProfileRequest;
import com.chengqu.huzhu.user.dto.UserHomeResponse;
import com.chengqu.huzhu.user.service.UserHomeService;
import com.chengqu.huzhu.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserHomeService userHomeService;

    @GetMapping("/home")
    public ApiResponse<Map<String, Object>> home() {
        LoginUser user = SecurityUtils.currentUser();
        log.info("[用户] 访问首页 userId={}, role={}", user.getId(), user.getRole());
        return ApiResponse.ok(Map.of(
                "message", "欢迎来到邻里汇",
                "userId", user.getId(),
                "role", user.getRole().name()
        ));
    }

    @GetMapping("/profile")
    public ApiResponse<TokenResponse.UserProfile> profile() {
        return ApiResponse.ok(userService.profile());
    }

    @GetMapping("/{id}")
    public ApiResponse<PublicUserProfile> publicProfile(@PathVariable Long id) {
        return ApiResponse.ok(userService.publicProfile(id));
    }

    /**
     * 用户主页聚合：资料 + 求助统计 + 动态统计，一次返回。
     *
     * <p>统计部分由 auth-service 通过 Feign 向 aid-service / community-service 获取，
     * 下游不可用时对应字段为 null。
     */
    @GetMapping("/{id}/home")
    public ApiResponse<UserHomeResponse> home(@PathVariable Long id) {
        return ApiResponse.ok(userHomeService.home(id));
    }

    @PutMapping("/{id}/rating")
    public ApiResponse<PublicUserProfile> rateUser(@PathVariable Long id, @Valid @RequestBody com.chengqu.huzhu.user.dto.ScoreRequest request) {
        return ApiResponse.ok("评价已保存", userService.rateUser(id, request));
    }

    @PutMapping("/profile")
    public ApiResponse<TokenResponse.UserProfile> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok("资料已更新", userService.updateProfile(request));
    }
}
