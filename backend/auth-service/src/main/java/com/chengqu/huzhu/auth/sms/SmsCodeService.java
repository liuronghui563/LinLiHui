package com.chengqu.huzhu.auth.sms;

import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 短信验证码服务：限流 + 校验 + 一次性消费。
 * 生产建议将 store 换为 Redis。
 */
@Service
@RequiredArgsConstructor
public class SmsCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppProperties appProperties;
    private final SmsSender smsSender;

    private final Map<String, CodeRecord> codeStore = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSendAt = new ConcurrentHashMap<>();
    private final Map<String, DailyCounter> dailyCounter = new ConcurrentHashMap<>();

    public void sendCode(String phone, SmsScene scene) {
        validatePhone(phone);
        cleanup();

        String rateKey = phone + ":" + scene.name();
        long now = Instant.now().getEpochSecond();
        Long last = lastSendAt.get(rateKey);
        int interval = appProperties.getSms().getSendIntervalSeconds();
        if (last != null && now - last < interval) {
            throw new BizException("发送过于频繁，请 " + (interval - (now - last)) + " 秒后再试");
        }

        String dayKey = phone + ":" + LocalDate.now();
        DailyCounter counter = dailyCounter.computeIfAbsent(dayKey, k -> new DailyCounter(0, LocalDate.now()));
        if (!counter.day.equals(LocalDate.now())) {
            counter = new DailyCounter(0, LocalDate.now());
            dailyCounter.put(dayKey, counter);
        }
        if (counter.count >= appProperties.getSms().getDailyLimit()) {
            throw new BizException("今日短信发送次数已达上限");
        }

        String code = generateCode(appProperties.getSms().getCodeLength());
        long expireAt = now + appProperties.getSms().getExpireSeconds();
        codeStore.put(rateKey, new CodeRecord(code, expireAt));
        lastSendAt.put(rateKey, now);
        dailyCounter.put(dayKey, new DailyCounter(counter.count + 1, LocalDate.now()));

        smsSender.send(phone, code, scene);
    }

    public void verifyAndConsume(String phone, SmsScene scene, String code) {
        validatePhone(phone);
        if (!StringUtils.hasText(code)) {
            throw new BizException("请输入短信验证码");
        }
        String key = phone + ":" + scene.name();
        CodeRecord record = codeStore.get(key);
        if (record == null || record.expireAt() < Instant.now().getEpochSecond()) {
            codeStore.remove(key);
            throw new BizException("短信验证码已失效，请重新获取");
        }
        if (!record.code().equals(code.trim())) {
            throw new BizException("短信验证码错误");
        }
        codeStore.remove(key);
    }

    private String generateCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private void validatePhone(String phone) {
        if (!StringUtils.hasText(phone) || !phone.matches("^1\\d{10}$")) {
            throw new BizException("手机号格式错误");
        }
    }

    private void cleanup() {
        long now = Instant.now().getEpochSecond();
        codeStore.entrySet().removeIf(e -> e.getValue().expireAt() < now);
        LocalDate today = LocalDate.now();
        dailyCounter.entrySet().removeIf(e -> !e.getValue().day.equals(today));
    }

    private record CodeRecord(String code, long expireAt) {
    }

    private record DailyCounter(int count, LocalDate day) {
    }
}
