package com.chengqu.huzhu;

import com.chengqu.huzhu.api.client.AdApiClient;
import com.chengqu.huzhu.api.client.AidApiClient;
import com.chengqu.huzhu.api.client.CommunityApiClient;
import com.chengqu.huzhu.api.client.NotifyApiClient;
import com.chengqu.huzhu.common.security.JwtAuthFilter;
import com.chengqu.huzhu.security.jwt.JwtAuthenticationFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

/**
 * 鉴权服务入口。scanBasePackages 覆盖 com.chengqu.huzhu，
 * 以加载 common 中的 GlobalExceptionHandler、ApiAccessLogFilter、JwtSupport。
 * <p>
 * common 的 JwtAuthFilter 禁用 Servlet 注册；本服务 JWT 过滤器仅挂在 SecurityFilterChain。
 * <p>
 * 本服务同时是「用户信息的提供方」与「聚合数据的消费方」：
 * 只启用自己需要的 Feign 客户端，避免误引入对自身的调用。
 * <p>
 * NotifyApiClient 用于关注成功后写入站内通知（被关注者在本服务域内）。
 */
@SpringBootApplication(scanBasePackages = "com.chengqu.huzhu")
@EnableDiscoveryClient
@EnableFeignClients(clients = {AidApiClient.class, CommunityApiClient.class, AdApiClient.class, NotifyApiClient.class})
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
