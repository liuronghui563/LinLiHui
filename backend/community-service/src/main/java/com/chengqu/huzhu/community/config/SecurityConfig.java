package com.chengqu.huzhu.community.config;

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
                // 这里曾经为 GET /api/recycle/categories 开过 permitAll，现已删除。
                // 原因：前端 /recycle 路由是 requiresAuth（见 router/index.js），
                // 登录墙挡在页面层，匿名请求根本走不到这个接口——白名单是不可达的死代码，
                // 只会让「哪些接口需要登录」这件事多出一条例外而难以推理。
                // 将来若真要做成「先看价目表再决定登录」，必须同时放开前端路由，
                // 并让页面在无令牌时隐藏「我的预约」分区（该分区依赖 /recycle/orders，仍然必须登录）。
                .requestMatchers("/api/community/**").authenticated()
                .anyRequest().authenticated());
    }
}
