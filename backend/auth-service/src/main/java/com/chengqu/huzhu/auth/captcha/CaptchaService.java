package com.chengqu.huzhu.auth.captcha;

import com.chengqu.huzhu.auth.dto.CaptchaPayload;
import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图形验证码：生成后可先校验并冻结 60 秒，登录失败不必反复刷新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final char[] CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int HOLD_SECONDS = 60;

    private final AppProperties appProperties;
    private final Map<String, CaptchaRecord> store = new ConcurrentHashMap<>();

    public CaptchaPayload create() {
        cleanup();
        int length = appProperties.getCaptcha().getLength();
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(CHARS[RANDOM.nextInt(CHARS.length)]);
        }

        String captchaId = UUID.randomUUID().toString().replace("-", "");
        String challenge = code.toString();
        long expireAt = Instant.now().getEpochSecond() + appProperties.getCaptcha().getExpireSeconds();
        store.put(captchaId, new CaptchaRecord(challenge.toLowerCase(), expireAt, false, 0));
        log.info("[鉴权] 图形验证码 captchaId={}, challenge={}", captchaId, challenge);

        return new CaptchaPayload(captchaId, challenge, appProperties.getCaptcha().getExpireSeconds());
    }

    public int verifyHold(String captchaId, String captchaCode) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw new BizException("请输入图形验证码");
        }
        CaptchaRecord record = store.get(captchaId);
        long now = Instant.now().getEpochSecond();
        if (record == null || record.expireAt() < now) {
            store.remove(captchaId);
            throw new BizException("验证码失效");
        }
        if (record.verified() && record.verifiedUntil() >= now) {
            return (int) Math.max(1, record.verifiedUntil() - now);
        }
        if (!record.code().equalsIgnoreCase(captchaCode.trim())) {
            log.warn("[鉴权] 图形验证码校验失败 captchaId={}", captchaId);
            throw new BizException("验证码错误");
        }
        long verifiedUntil = now + HOLD_SECONDS;
        store.put(captchaId, new CaptchaRecord(record.code(), Math.max(record.expireAt(), verifiedUntil), true, verifiedUntil));
        log.info("[鉴权] 图形验证码校验通过并冻结 captchaId={}, hold={}s", captchaId, HOLD_SECONDS);
        return HOLD_SECONDS;
    }

    public void requireVerified(String captchaId, String captchaCode) {
        CaptchaRecord record = store.get(captchaId);
        long now = Instant.now().getEpochSecond();
        if (record != null && record.verified() && record.verifiedUntil() >= now) {
            return;
        }
        verifyHold(captchaId, captchaCode);
    }

    public void consume(String captchaId) {
        if (StringUtils.hasText(captchaId)) {
            store.remove(captchaId);
        }
    }

    public void verifyAndConsume(String captchaId, String captchaCode) {
        requireVerified(captchaId, captchaCode);
        consume(captchaId);
    }

    private void cleanup() {
        long now = Instant.now().getEpochSecond();
        store.entrySet().removeIf(e -> e.getValue().expireAt() < now
                && (!e.getValue().verified() || e.getValue().verifiedUntil() < now));
    }

    private record CaptchaRecord(String code, long expireAt, boolean verified, long verifiedUntil) {
    }
}
