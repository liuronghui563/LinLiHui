package com.chengqu.huzhu.auth.sms;

import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.common.redis.RedisKeys;
import com.chengqu.huzhu.common.redis.RedisRateLimiter;
import com.chengqu.huzhu.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

/**
 * 短信验证码服务：频控 + 校验 + 一次性消费。
 *
 * <p>状态全部放 Redis，用 TTL 代替手写清理：
 * <ul>
 *   <li><b>发送间隔</b>：{@code SET NX PX} 占位，原子且自动解锁，替代原先的 {@code lastSendAt} Map。</li>
 *   <li><b>每日上限</b>：{@code INCR + PEXPIRE}（Lua 原子），key 带日期，跨天自然失效。</li>
 *   <li><b>验证码本体</b>：带 TTL 的字符串，校验通过即删除，保证一次性。</li>
 * </ul>
 *
 * <p>频控顺序刻意与原来保持一致：<b>先查日限（只读）→ 再抢间隔锁 → 最后累加日计数</b>。
 * 若反过来先抢锁再查日限，一次被日限拒绝的请求会白白吃掉间隔配额，
 * 用户会看到「明明没发出去，却被要求等待」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final AppProperties appProperties;
    private final SmsSender smsSender;
    private final StringRedisTemplate redis;
    private final RedisRateLimiter rateLimiter;

    public void sendCode(String phone, SmsScene scene) {
        validatePhone(phone);

        String codeKey = RedisKeys.smsCode(phone, scene.name());
        String intervalKey = RedisKeys.smsInterval(phone, scene.name());
        String dailyKey = RedisKeys.smsDaily(phone, LocalDate.now(ZONE));
        int dailyLimit = appProperties.getSms().getDailyLimit();

        // 1) 日限预检（只读，不产生副作用）
        if (rateLimiter.currentCount(dailyKey) >= dailyLimit) {
            throw new BizException("今日短信发送次数已达上限");
        }

        // 2) 发送间隔：SET NX PX，抢不到说明仍在冷却期
        int interval = appProperties.getSms().getSendIntervalSeconds();
        if (!rateLimiter.tryAcquireOnce(intervalKey, Duration.ofSeconds(interval))) {
            Long remain = redis.getExpire(intervalKey, TimeUnit.SECONDS);
            long wait = remain == null || remain < 0 ? interval : remain;
            throw new BizException("发送过于频繁，请 " + wait + " 秒后再试");
        }

        // 3) 累加日计数（Lua 内 INCR + 首次设置过期到当天结束）
        long remaining = rateLimiter.tryAcquire(dailyKey, dailyLimit, ttlUntilEndOfDay());
        if (remaining < 0) {
            // 并发下越过了日限：释放刚抢到的间隔锁，避免「被拒了还要等」
            redis.delete(intervalKey);
            throw new BizException("今日短信发送次数已达上限");
        }
        long used = dailyLimit - remaining;

        String code = generateCode(appProperties.getSms().getCodeLength());
        redis.opsForValue().set(codeKey, code, Duration.ofSeconds(appProperties.getSms().getExpireSeconds()));

        smsSender.send(phone, code, scene);
        log.info("[鉴权] 短信验证码已发送 phone={}, scene={}, 今日第 {} 次", phone, scene, used);
    }

    public void verifyAndConsume(String phone, SmsScene scene, String code) {
        validatePhone(phone);
        if (!StringUtils.hasText(code)) {
            throw new BizException("请输入短信验证码");
        }
        String key = RedisKeys.smsCode(phone, scene.name());
        String stored = redis.opsForValue().get(key);
        if (stored == null) {
            throw new BizException("短信验证码已失效，请重新获取");
        }
        if (!stored.equals(code.trim())) {
            throw new BizException("短信验证码错误");
        }
        // 一次性：校验通过立即删除，避免同码重复使用
        redis.delete(key);
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

    private Duration ttlUntilEndOfDay() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDateTime midnight = LocalDate.now(ZONE).plusDays(1).atTime(LocalTime.MIDNIGHT);
        Duration ttl = Duration.between(now, midnight);
        return ttl.isNegative() || ttl.isZero() ? Duration.ofMinutes(1) : ttl;
    }
}
