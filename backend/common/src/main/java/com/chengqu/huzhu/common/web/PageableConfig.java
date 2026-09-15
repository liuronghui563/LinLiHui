package com.chengqu.huzhu.common.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

/**
 * 分页参数防护。
 *
 * <p>各业务接口均使用 {@code @PageableDefault(size = 10)}，但客户端仍可显式传入
 * {@code ?size=100000} 绕过默认值，一次性拉取全表。此处统一限制单页上限，
 * 使所有分页接口（求助列表、动态列表、我的求助等）自动生效。
 *
 * <p>仅作用于 Servlet 应用且类路径存在 Spring Data Web 时激活，
 * 因此响应式的 WebFlux 网关不受影响。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(PageableHandlerMethodArgumentResolverCustomizer.class)
public class PageableConfig {

    /** 单页最大条数上限。超过该值的 size 参数会被截断为此值。 */
    public static final int MAX_PAGE_SIZE = 50;

    @Bean
    PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer() {
        return resolver -> {
            resolver.setMaxPageSize(MAX_PAGE_SIZE);
            resolver.setOneIndexedParameters(false);
        };
    }
}
