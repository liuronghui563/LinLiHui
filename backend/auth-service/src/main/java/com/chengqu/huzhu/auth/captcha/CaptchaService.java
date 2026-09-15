package com.chengqu.huzhu.auth.captcha;

import com.chengqu.huzhu.auth.dto.CaptchaPayload;
import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.common.redis.RedisKeys;
import com.chengqu.huzhu.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 图形验证码：生成后可先校验并冻结 60 秒，登录失败不必反复刷新。
 *
 * <p><b>状态存 Redis 而非进程内存</b>。原来的 {@code ConcurrentHashMap} 有两个问题：
 * 一是多实例部署时验证码大概率落在另一个实例上而校验失败；
 * 二是要靠手写的 {@code cleanup()} 扫过期数据，重启即全丢。
 * 改用 Redis Hash + TTL 后，过期由 Redis 自动回收，且多实例天然共享。
 *
 * <p>字段用 Hash 而不是序列化对象：校验通过时只需原子更新 {@code verified} 两个字段，
 * 不必读出整个对象再写回，避免并发下的读改写竞争。
 *
 * <p>注意：验证码流程现在**强依赖 Redis**——Redis 不可用时无法登录。
 * 这是刻意的取舍：验证码必须跨实例共享才有意义，退回内存会在多实例下静默出错。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final char[] CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int HOLD_SECONDS = 60;

    private static final String FIELD_CODE = "code";
    private static final String FIELD_VERIFIED = "verified";
    private static final String FIELD_VERIFIED_UNTIL = "verifiedUntil";

    private final AppProperties appProperties;
    private final StringRedisTemplate redis;

    public CaptchaPayload create() {
        int length = appProperties.getCaptcha().getLength();
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(CHARS[RANDOM.nextInt(CHARS.length)]);
        }

        String captchaId = UUID.randomUUID().toString().replace("-", "");
        String challenge = code.toString();
        Duration ttl = Duration.ofSeconds(appProperties.getCaptcha().getExpireSeconds());
        String key = RedisKeys.captcha(captchaId);

        Map<String, String> fields = new HashMap<>();
        fields.put(FIELD_CODE, challenge.toLowerCase());
        fields.put(FIELD_VERIFIED, "0");
        redis.opsForHash().putAll(key, fields);
        redis.expire(key, ttl);

        log.info("[鉴权] 图形验证码已写入 Redis captchaId={}, ttl={}s", captchaId, ttl.toSeconds());
        return new CaptchaPayload(captchaId, challenge, appProperties.getCaptcha().getExpireSeconds());
    }

    /**
     * 校验验证码并进入冻结期。
     *
     * @return 冻结剩余秒数
     */
    public int verifyHold(String captchaId, String captchaCode) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw new BizException("请输入图形验证码");
        }
        String key = RedisKeys.captcha(captchaId);
        Map<Object, Object> fields = redis.opsForHash().entries(key);
        if (fields.isEmpty()) {
            throw new BizException("验证码失效");
        }

        long now = Instant.now().getEpochSecond();
        if ("1".equals(text(fields.get(FIELD_VERIFIED)))) {
            long verifiedUntil = parseLong(fields.get(FIELD_VERIFIED_UNTIL));
            if (verifiedUntil >= now) {
                return (int) Math.max(1, verifiedUntil - now);
            }
        }

        String stored = text(fields.get(FIELD_CODE));
        if (stored == null || !stored.equalsIgnoreCase(captchaCode.trim())) {
            log.warn("[鉴权] 图形验证码校验失败 captchaId={}", captchaId);
            throw new BizException("验证码错误");
        }

        long verifiedUntil = now + HOLD_SECONDS;
        redis.opsForHash().put(key, FIELD_VERIFIED, "1");
        redis.opsForHash().put(key, FIELD_VERIFIED_UNTIL, String.valueOf(verifiedUntil));
        // 冻结期内必须保持存活，避免验证码本体先过期导致冻结失效
        redis.expire(key, Duration.ofSeconds(Math.max(appProperties.getCaptcha().getExpireSeconds(), HOLD_SECONDS + 1)));

        log.info("[鉴权] 图形验证码校验通过并冻结 captchaId={}, hold={}s", captchaId, HOLD_SECONDS);
        return HOLD_SECONDS;
    }

    /** 已验证（处于冻结期）则直接放行，否则重新校验。 */
    public void requireVerified(String captchaId, String captchaCode) {
        if (StringUtils.hasText(captchaId)) {
            Map<Object, Object> fields = redis.opsForHash().entries(RedisKeys.captcha(captchaId));
            if (!fields.isEmpty() && "1".equals(text(fields.get(FIELD_VERIFIED)))) {
                long verifiedUntil = parseLong(fields.get(FIELD_VERIFIED_UNTIL));
                if (verifiedUntil >= Instant.now().getEpochSecond()) {
                    return;
                }
            }
        }
        verifyHold(captchaId, captchaCode);
    }

    public void consume(String captchaId) {
        if (StringUtils.hasText(captchaId)) {
            redis.delete(RedisKeys.captcha(captchaId));
        }
    }

    public void verifyAndConsume(String captchaId, String captchaCode) {
        requireVerified(captchaId, captchaCode);
        consume(captchaId);
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static long parseLong(Object value) {
        try {
            return value == null ? 0L : Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
