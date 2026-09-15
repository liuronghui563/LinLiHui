package com.chengqu.huzhu.common.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * 限流原语测试。重点不是 Lua 本身，而是两条边界：
 * 额度用尽时拒绝、Redis 故障时放行。
 */
@ExtendWith(MockitoExtension.class)
class RedisRateLimiterTest {

    @Mock
    private ObjectProvider<StringRedisTemplate> redisProvider;
    @Mock
    private StringRedisTemplate redis;

    private RedisRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new RedisRateLimiter(redisProvider);
    }

    @SuppressWarnings("unchecked")
    private void stubExecute(Long result) {
        doReturn(result).when(redis).execute(any(RedisScript.class), anyList(), any());
    }

    @SuppressWarnings("unchecked")
    private void stubExecuteThrows() {
        doThrow(new RuntimeException("connection refused"))
                .when(redis).execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    @DisplayName("Redis 未引入时降级放行")
    void tryAcquireDegradesWhenRedisAbsent() {
        when(redisProvider.getIfAvailable()).thenReturn(null);

        assertEquals(-1, limiter.tryAcquire("k", 5, Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("窗口内未超限时返回剩余额度")
    void tryAcquireReturnsRemainingQuota() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        stubExecute(3L);

        assertEquals(2, limiter.tryAcquire("k", 5, Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("刚好用满时剩余额度为 0（仍放行）")
    void tryAcquireAllowsExactlyAtLimit() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        stubExecute(5L);

        assertEquals(0, limiter.tryAcquire("k", 5, Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("超出上限时返回 -1（拒绝）")
    void tryAcquireRejectsWhenExceeded() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        stubExecute(6L);

        assertEquals(-1, limiter.tryAcquire("k", 5, Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("Redis 异常时降级放行")
    void tryAcquireDegradesOnFailure() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        stubExecuteThrows();

        assertEquals(-1, limiter.tryAcquire("k", 5, Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("间隔锁：抢到返回 true")
    void tryAcquireOnceSucceeds() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        stubExecute(1L);

        assertTrue(limiter.tryAcquireOnce("k", Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("间隔锁：仍在冷却期返回 false")
    void tryAcquireOnceRejectsWhenHeld() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        stubExecute(0L);

        assertFalse(limiter.tryAcquireOnce("k", Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("间隔锁：Redis 未引入时放行")
    void tryAcquireOnceDegradesWhenRedisAbsent() {
        when(redisProvider.getIfAvailable()).thenReturn(null);

        assertTrue(limiter.tryAcquireOnce("k", Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("key 命名带统一前缀")
    void rateLimitKeyHasPrefix() {
        assertEquals("cqh:rate:login-ip:10.0.0.1", RedisKeys.rateLimit("login-ip", "10.0.0.1"));
    }

    @Test
    @DisplayName("脚本入参为窗口毫秒数")
    void passesWindowMillisAsArgument() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        stubExecute(1L);

        limiter.tryAcquire("k", 5, Duration.ofSeconds(30));

        org.mockito.Mockito.verify(redis).execute(any(RedisScript.class), any(List.class), any());
    }
}
