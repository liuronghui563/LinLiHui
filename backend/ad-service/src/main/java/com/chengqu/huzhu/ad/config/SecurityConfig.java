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
                        // —— 广告位申请：所有登录用户都能提交与查看自己的申请 ——
                        // 顺序要紧：这几条必须排在下面 "/api/ad/**" 的 ADMIN 规则之前，
                        // 否则用户提交申请会被判成「无权限」。
                        .requestMatchers(HttpMethod.POST, "/api/ad/applications").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/ad/applications/mine").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/ad/applications/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/ad/applications/*").authenticated()
                        // —— 广告位资质（用户侧）：登录即可提交与查看自己的资质 ——
                        // 同样是「必须排在 /api/ad/** 的 ADMIN 通配规则之前」，漏了这条，
                        // PUT/DELETE /api/ad/qualification/{id} 会先撞上下面的
                        // "/api/ad/**" → hasRole("ADMIN")，用户提交资质直接被判 403。
                        //
                        // 易错点：用户侧是单数 qualification，管理侧是复数 qualifications，
                        // 只差一个 s 却是两套权限。Ant 模式下 "/api/ad/qualification/**"
                        // 不会匹配 "/api/ad/qualifications"，两条规则互不覆盖，都要写。
                        .requestMatchers("/api/ad/qualification", "/api/ad/qualification/**").authenticated()
                        // —— 广告位资质（管理侧）：列表 / 通过 / 驳回只有管理员能做 ——
                        // 这条不能省：POST /api/ad/qualifications/{id}/approve 既不匹配
                        // 下面的 "/api/ad/applications/**"，也不匹配 POST "/api/ad"
                        // （那条只匹配 /api/ad 本身），于是会一路落到 anyRequest().authenticated()，
                        // 变成任何登录用户都能给自己开通资质。
                        .requestMatchers("/api/ad/qualifications", "/api/ad/qualifications/**").hasRole("ADMIN")
                        // —— 审核相关（待审列表 / 通过 / 驳回）只有管理员 ——
                        .requestMatchers("/api/ad/applications/**").hasRole("ADMIN")
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
