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
                .requestMatchers("/api/community/**").authenticated()
                .anyRequest().authenticated());
    }
}
