package com.chengqu.huzhu;

import com.chengqu.huzhu.common.security.JwtAuthFilter;
import com.chengqu.huzhu.security.jwt.JwtAuthenticationFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;

/**
 * 鉴权服务入口。scanBasePackages 覆盖 com.chengqu.huzhu，
 * 以加载 common 中的 GlobalExceptionHandler、ApiAccessLogFilter、JwtSupport。
 * <p>
 * common 的 JwtAuthFilter 禁用 Servlet 注册；本服务 JWT 过滤器仅挂在 SecurityFilterChain。
 */
@SpringBootApplication(scanBasePackages = "com.chengqu.huzhu")
@EnableDiscoveryClient
@ConfigurationPropertiesScan
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> disableCommonJwtAuthFilter(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> disableServletJwtFilter(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
