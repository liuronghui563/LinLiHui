package com.chengqu.huzhu.common.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 基于 Redis 的固定窗口限流。
 *
 * <p>计数与「首次设置过期」放在同一段 Lua 里执行。若拆成 INCR + EXPIRE 两条命令，
 * 进程在两条命令之间挂掉会留下永不过期的计数器，把该身份永久锁死——
 * 这正是限流实现里最常见的坑，用脚本保证原子性可以彻底避免。
 *
 * <p><b>降级语义：Redis 不可用时一律放行</b>。限流是保护措施而非业务规则，
 * 不应因为缓存故障把正常用户挡在门外；不可用时会打 WARN 日志提示。
 */
@Slf4j
@Component
@ConditionalOnClass(StringRedisTemplate.class)
@RequiredArgsConstructor
public class RedisRateLimiter {

    private static final RedisScript<Long> FIXED_WINDOW = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    private static final RedisScript<Long> SET_IF_ABSENT_WITH_TTL = new DefaultRedisScript<>("""
            if redis.call('SET', KEYS[1], '1', 'NX', 'PX', ARGV[1]) then
                return 1
            end
            return 0
            """, Long.class);

    private static volatile boolean degradedLogged = false;

    private final ObjectProvider<StringRedisTemplate> redisProvider;

    /**
     * 固定窗口计数限流。
     *
     * @param key    RedisKeys.rateLimit(...) 生成的 key
     * @param limit  窗口内允许的最大次数
     * @param window 窗口长度
     * @return 允许放行的剩余额度；返回 {@code -1} 表示 Redis 不可用（降级放行）
     */
    public long tryAcquire(String key, int limit, Duration window) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            logDegradedOnce("Redis 未配置");
            return -1;
        }
        try {
            Long current = redis.execute(FIXED_WINDOW, List.of(key), String.valueOf(window.toMillis()));
            if (current == null) {
                return -1;
            }
            return current <= limit ? limit - current : -1;
        } catch (Exception e) {
            logDegradedOnce("Redis 不可用: " + e.getMessage());
            return -1;
        }
    }

    /** 窗口内已发生的次数，仅用于日志与提示，不参与判断。 */
    public long currentCount(String key) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return 0;
        }
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? 0 : Long.parseLong(value);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 基于 {@code SET NX PX} 的「占用一次配额」原语，用于「N 秒内只允许一次」这类间隔限制。
     *
     * @return true 表示本次获取成功（此前不存在）；false 表示仍在冷却期内；
     *         Redis 不可用时返回 true（降级放行）
     */
    public boolean tryAcquireOnce(String key, Duration ttl) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            logDegradedOnce("Redis 未配置");
            return true;
        }
        try {
            Long acquired = redis.execute(SET_IF_ABSENT_WITH_TTL, List.of(key), String.valueOf(ttl.toMillis()));
            return acquired != null && acquired == 1L;
        } catch (Exception e) {
            logDegradedOnce("Redis 不可用: " + e.getMessage());
            return true;
        }
    }

    private static void logDegradedOnce(String reason) {
        if (!degradedLogged) {
            degradedLogged = true;
            log.warn("[限流] 已降级为「一律放行」，限流暂时失效：{}", reason);
        }
    }
}
