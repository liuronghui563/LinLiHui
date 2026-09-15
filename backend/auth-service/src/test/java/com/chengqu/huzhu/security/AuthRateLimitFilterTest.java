package com.chengqu.huzhu.security;

import com.chengqu.huzhu.common.redis.RedisRateLimiter;
import com.chengqu.huzhu.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 限流用的客户端 IP 提取测试。
 *
 * <p>这一段直接决定限流能否被绕过，因此单独回归：
 * 网关对 {@code X-Forwarded-For} 是「追加」语义，客户端可以自带伪造值，
 * 只有最后一段是可信网关写入的真实地址。
 */
@ExtendWith(MockitoExtension.class)
class AuthRateLimitFilterTest {

    @Mock
    private RedisRateLimiter rateLimiter;
    @Mock
    private AppProperties appProperties;

    private AuthRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuthRateLimitFilter(rateLimiter, appProperties, new ObjectMapper());
    }

    private String resolveIp(MockHttpServletRequest request) {
        return (String) ReflectionTestUtils.invokeMethod(filter, "clientIp", request);
    }

    @Test
    @DisplayName("客户端伪造 X-Forwarded-For 时取最后一段（网关写入的真实 IP）")
    void takesLastSegmentOfForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // 网关注入真实地址时是追加：客户端伪造值在前，真实值在后
        request.addHeader("X-Forwarded-For", "1.2.3.4, 5.6.7.8, 192.168.198.1");

        assertEquals("192.168.198.1", resolveIp(request),
                "取第一段会让攻击者靠轮换伪造值绕过限流");
    }

    @Test
    @DisplayName("只有一段时直接使用")
    void usesSingleSegment() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.9");

        assertEquals("10.0.0.9", resolveIp(request));
    }

    @Test
    @DisplayName("末尾有空段时向前取最后一个非空值")
    void skipsBlankTrailingSegment() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.9, ");

        assertEquals("10.0.0.9", resolveIp(request));
    }

    @Test
    @DisplayName("没有 X-Forwarded-For 时回退到 X-Real-IP")
    void fallsBackToRealIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "172.16.0.5");

        assertEquals("172.16.0.5", resolveIp(request));
    }

    @Test
    @DisplayName("两个头都没有时使用 remoteAddr")
    void fallsBackToRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        assertEquals("127.0.0.1", resolveIp(request));
    }
}
