package com.chengqu.huzhu.common.security;

import com.chengqu.huzhu.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * 资源服务构建 {@link SecurityFilterChain} 的参考模板。
 * <p>
 * 各微服务可复制此模式，按业务调整 {@code authorize} 白名单与角色规则，例如：
 * <pre>{@code
 * @Bean
 * SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter, ObjectMapper mapper)
 *         throws Exception {
 *     return ResourceSecuritySupport.buildDefaultChain(http, jwtAuthFilter, mapper, auth -> auth
 *             .requestMatchers("/api/aid/public/**").permitAll()
 *             .requestMatchers("/api/admin/**").hasRole("ADMIN")
 *             .anyRequest().authenticated());
 * }
 * }</pre>
 */
public final class ResourceSecuritySupport {

    private ResourceSecuritySupport() {
    }

    public static SecurityFilterChain buildDefaultChain(
            HttpSecurity http,
            JwtAuthFilter jwtAuthFilter,
            ObjectMapper objectMapper,
            Consumer<org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer
                    <HttpSecurity>.AuthorizationManagerRequestMatcherRegistry> authorizeCustomizer) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                // CORS 由网关统一处理，资源服务关闭以免重复加头
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/error", "/actuator/health").permitAll();
                    authorizeCustomizer.accept(auth);
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> writeJson(res, objectMapper, 401, "未登录或登录已过期"))
                        .accessDeniedHandler((req, res, e) -> writeJson(res, objectMapper, 403, "无权限访问"))
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    public static void writeJson(HttpServletResponse response, ObjectMapper objectMapper, int code, String message)
            throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(code, message));
    }
}
