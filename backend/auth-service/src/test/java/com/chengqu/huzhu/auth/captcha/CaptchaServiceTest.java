package com.chengqu.huzhu.auth.captcha;

import com.chengqu.huzhu.auth.dto.CaptchaPayload;
import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 图形验证码状态机测试。
 *
 * <p>迁移到 Redis 后，正确性依赖「Redis 里的字段状态」而不是进程内对象，
 * 因此这里直接以 Hash 内容作为输入来验证行为。
 */
@ExtendWith(MockitoExtension.class)
class CaptchaServiceTest {

    private static final String CAPTCHA_ID = "cid123";
    private static final String KEY = "cqh:captcha:" + CAPTCHA_ID;

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private HashOperations<String, Object, Object> hashOps;

    private CaptchaService captchaService;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getCaptcha().setExpireSeconds(120);
        properties.getCaptcha().setLength(4);
        // lenient：并非每个用例都会触碰 Hash 操作
        lenient().when(redis.opsForHash()).thenReturn(hashOps);
        captchaService = new CaptchaService(properties, redis);
    }

    private Map<Object, Object> hash(Object... keyValues) {
        Map<Object, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("生成验证码：写入 Hash 并设置 TTL")
    void createWritesHashWithTtl() {
        CaptchaPayload payload = captchaService.create();

        assertTrue(payload.getCaptchaId().length() >= 16);
        assertEquals(4, payload.getChallenge().length());
        assertEquals(120, payload.getExpireSeconds());
        verify(hashOps).putAll(eq("cqh:captcha:" + payload.getCaptchaId()), anyMap());
        verify(redis).expire(eq("cqh:captcha:" + payload.getCaptchaId()), eq(Duration.ofSeconds(120)));
    }

    @Test
    @DisplayName("校验通过：进入 60 秒冻结期")
    void verifyHoldAcceptsCorrectCode() {
        when(hashOps.entries(KEY)).thenReturn(hash("code", "abcd", "verified", "0"));

        int hold = captchaService.verifyHold(CAPTCHA_ID, "ABCD");

        assertEquals(60, hold);
        verify(hashOps).put(KEY, "verified", "1");
        verify(hashOps).put(eq(KEY), eq("verifiedUntil"), anyString());
    }

    @Test
    @DisplayName("校验失败：验证码错误")
    void verifyHoldRejectsWrongCode() {
        when(hashOps.entries(KEY)).thenReturn(hash("code", "abcd", "verified", "0"));

        BizException ex = assertThrows(BizException.class, () -> captchaService.verifyHold(CAPTCHA_ID, "zzzz"));
        assertEquals("验证码错误", ex.getMessage());
    }

    @Test
    @DisplayName("验证码已过期（key 不存在）时提示失效")
    void verifyHoldRejectsExpiredCaptcha() {
        when(hashOps.entries(KEY)).thenReturn(Map.of());

        BizException ex = assertThrows(BizException.class, () -> captchaService.verifyHold(CAPTCHA_ID, "abcd"));
        assertEquals("验证码失效", ex.getMessage());
    }

    @Test
    @DisplayName("处于冻结期内重复校验：不再比对答案，直接返回剩余秒数")
    void verifyHoldReturnsRemainingWhenAlreadyVerified() {
        long verifiedUntil = Instant.now().getEpochSecond() + 42;
        when(hashOps.entries(KEY)).thenReturn(
                hash("code", "abcd", "verified", "1", "verifiedUntil", String.valueOf(verifiedUntil)));

        int hold = captchaService.verifyHold(CAPTCHA_ID, "与答案完全无关");

        assertTrue(hold > 0 && hold <= 42, "应返回冻结剩余时间，实际=" + hold);
    }

    @Test
    @DisplayName("冻结期已过则重新比对答案")
    void verifyHoldRevalidatesAfterHoldExpired() {
        long verifiedUntil = Instant.now().getEpochSecond() - 10;
        when(hashOps.entries(KEY)).thenReturn(
                hash("code", "abcd", "verified", "1", "verifiedUntil", String.valueOf(verifiedUntil)));

        assertThrows(BizException.class, () -> captchaService.verifyHold(CAPTCHA_ID, "zzzz"));
    }

    @Test
    @DisplayName("requireVerified：冻结期内直接放行，不触碰答案")
    void requireVerifiedPassesWithinHold() {
        long verifiedUntil = Instant.now().getEpochSecond() + 30;
        when(hashOps.entries(KEY)).thenReturn(
                hash("verified", "1", "verifiedUntil", String.valueOf(verifiedUntil)));

        captchaService.requireVerified(CAPTCHA_ID, null);

        verify(hashOps, org.mockito.Mockito.never()).put(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("consume：删除 Redis 中的验证码，保证一次性")
    void consumeDeletesKey() {
        captchaService.consume(CAPTCHA_ID);

        verify(redis).delete(KEY);
    }

    @Test
    @DisplayName("verifyAndConsume：校验通过后立即删除")
    void verifyAndConsumeDeletesAfterSuccess() {
        when(hashOps.entries(KEY)).thenReturn(hash("code", "abcd", "verified", "0"));

        captchaService.verifyAndConsume(CAPTCHA_ID, "abcd");

        verify(redis).delete(KEY);
    }

    @Test
    @DisplayName("空参数直接拒绝，不访问 Redis")
    void rejectsBlankInput() {
        assertThrows(BizException.class, () -> captchaService.verifyHold("", "abcd"));
        assertThrows(BizException.class, () -> captchaService.verifyHold(CAPTCHA_ID, "  "));
        verify(hashOps, org.mockito.Mockito.never()).entries(anyString());
    }

    @Test
    @DisplayName("key 命名集中管理：验证码前缀正确")
    void captchaKeyPrefix() {
        assertEquals("cqh:captcha:" + CAPTCHA_ID,
                com.chengqu.huzhu.common.redis.RedisKeys.captcha(CAPTCHA_ID));
        assertFalse(com.chengqu.huzhu.common.redis.RedisKeys.captcha(CAPTCHA_ID).isEmpty());
    }
}
