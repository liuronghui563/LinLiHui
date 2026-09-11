package com.chengqu.huzhu.auth.controller;

import com.chengqu.huzhu.auth.dto.*;
import com.chengqu.huzhu.auth.service.AuthService;
import com.chengqu.huzhu.common.api.ApiResponse;
import com.chengqu.huzhu.security.LoginUser;
import com.chengqu.huzhu.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/captcha")
    public ApiResponse<CaptchaPayload> captcha() {
        return ApiResponse.ok(authService.createCaptcha());
    }

    @PostMapping("/captcha/verify")
    public ApiResponse<Map<String, Object>> verifyCaptcha(@Valid @RequestBody CaptchaVerifyRequest request) {
        int holdSeconds = authService.verifyCaptcha(request);
        return ApiResponse.ok("验证码正确", Map.of("verified", true, "holdSeconds", holdSeconds));
    }

    @PostMapping("/sms/send")
    public ApiResponse<Map<String, Object>> sendSms(@Valid @RequestBody SendSmsRequest request) {
        authService.sendSms(request);
        return ApiResponse.ok("验证码已发送", Map.of("sent", true));
    }

    @PostMapping("/register")
    public ApiResponse<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok("注册成功", authService.register(request));
    }

    @PostMapping("/login/password")
    public ApiResponse<TokenResponse> loginPassword(@Valid @RequestBody PasswordLoginRequest request) {
        return ApiResponse.ok("登录成功", authService.loginByPassword(request));
    }

    @PostMapping("/login/sms")
    public ApiResponse<TokenResponse> loginSms(@Valid @RequestBody SmsLoginRequest request) {
        return ApiResponse.ok("登录成功", authService.loginBySms(request));
    }

    @PostMapping("/login/internal")
    public ApiResponse<TokenResponse> loginInternal(@Valid @RequestBody InternalLoginRequest request) {
        return ApiResponse.ok("登录成功", authService.loginByInternal(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authService.refresh(request));
    }

    @GetMapping("/me")
    public ApiResponse<TokenResponse.UserProfile> me() {
        LoginUser user = SecurityUtils.currentUser();
        return ApiResponse.ok(authService.me(user.getId()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        LoginUser user = SecurityUtils.currentUser();
        authService.logout(user.getId());
        return ApiResponse.okMessage("已退出登录");
    }
}
