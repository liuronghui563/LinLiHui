package com.chengqu.huzhu.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 分页参数防护测试。
 *
 * <p>各业务接口都用 {@code @PageableDefault(size = 10)} 设默认值，
 * 但客户端可用 {@code ?size=100000} 覆盖默认值一次性拉全表。
 * 这里确认自定义器确实把上限压到了 {@link PageableConfig#MAX_PAGE_SIZE}。
 */
class PageableConfigTest {

    /** 暴露父类 protected 的 getMaxPageSize() 以便断言。 */
    private static final class ExposedPageableResolver extends PageableHandlerMethodArgumentResolver {
        int maxPageSizeValue() {
            return getMaxPageSize();
        }
    }

    @Test
    @DisplayName("自定义器应把单页上限设为 50")
    void customizerCapsMaxPageSize() {
        ExposedPageableResolver resolver = new ExposedPageableResolver();
        assertEquals(2000, resolver.maxPageSizeValue(), "Spring Data 默认为 2000，用于确认本测试确实改变了它");

        new PageableConfig().pageableCustomizer().customize(resolver);

        assertEquals(PageableConfig.MAX_PAGE_SIZE, resolver.maxPageSizeValue());
        assertEquals(50, PageableConfig.MAX_PAGE_SIZE);
    }
}
