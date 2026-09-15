package com.chengqu.huzhu.notify.config;

import com.chengqu.huzhu.common.security.JwtAuthFilter;
import com.chengqu.huzhu.common.security.ResourceSecuritySupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 消息服务安全配置：CORS 由网关统一处理，本服务关闭 CORS。
 *
 * <p>通知是本服务唯一的业务数据、且完全属于接收者个人，因此没有公开接口，
 * 也不存在管理员可读的列表——所有请求（含 {@code /internal/notify/**}）
 * 都要求携带合法 JWT；服务间调用由 api 模块的 FeignAuthForwardConfig 原样透传调用方令牌。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return ResourceSecuritySupport.buildDefaultChain(http, jwtAuthFilter, objectMapper, auth -> auth
                .requestMatchers("/api/notify/**").authenticated()
                .anyRequest().authenticated());
    }
}
