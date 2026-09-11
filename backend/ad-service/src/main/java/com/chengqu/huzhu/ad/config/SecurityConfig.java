package com.chengqu.huzhu.ad.config;

import com.chengqu.huzhu.common.security.JwtAuthFilter;
import com.chengqu.huzhu.common.security.ResourceSecuritySupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 广告服务安全配置：CORS 由网关统一处理，本服务关闭 CORS。
 * 鉴权规则参考 {@link ResourceSecuritySupport}。
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
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/ad/carousel").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/ad/*/click").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/ad/list").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/ad").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/ad/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/ad/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                ResourceSecuritySupport.writeJson(res, objectMapper, 401, "未登录或登录已过期"))
                        .accessDeniedHandler((req, res, e) ->
                                ResourceSecuritySupport.writeJson(res, objectMapper, 403, "无权限访问"))
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
