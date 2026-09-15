package com.chengqu.huzhu.api.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 跨服务调用时的身份透传。
 *
 * <p>各服务的 {@code /internal/**} 接口同样受 Spring Security 保护（需要合法 JWT）。
 * 这里把当前请求的 {@code Authorization} 头原样带给下游服务，
 * 使得下游可以用调用方的真实身份做鉴权，无需另造一套服务间口令。
 *
 * <p>四个服务都会扫描到本类（{@code scanBasePackages = "com.chengqu.huzhu"}），
 * 因此注册的拦截器对该应用内的所有 Feign 客户端生效。
 * 无请求上下文的场景（定时任务、启动期预热）会直接跳过，不影响调用本身。
 */
@Configuration(proxyBeanMethods = false)
public class FeignAuthForwardConfig {

    public static final String AUTHORIZATION_HEADER = "Authorization";

    @Bean
    public RequestInterceptor authorizationForwardingInterceptor() {
        return template -> {
            if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
                return;
            }
            String authorization = attributes.getRequest().getHeader(AUTHORIZATION_HEADER);
            if (StringUtils.hasText(authorization)) {
                template.header(AUTHORIZATION_HEADER, authorization);
            }
        };
    }
}
