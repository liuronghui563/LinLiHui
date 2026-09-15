package com.chengqu.huzhu.common.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 基于 Redis 的 JSON 缓存原语。
 *
 * <p>用字符串 + 显式 ObjectMapper 反序列化，而不是 {@code RedisTemplate<String,Object>} +
 * {@code GenericJackson2JsonRedisSerializer}：后者把 {@code @class} 写进缓存，
 * 一旦 DTO 改名或换包，反序列化会在运行期炸掉；显式 {@link TypeReference} 让类型契约留在代码里。
 *
 * <p><b>缓存永远不参与正确性判断</b>：Redis 未引入、连不上、序列化失败，
 * 一律按「未命中」处理并只打日志，绝不向上抛异常。
 */
@Slf4j
@Component
@ConditionalOnClass(StringRedisTemplate.class)
@RequiredArgsConstructor
public class RedisCache {

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ObjectMapper objectMapper;

    /** Redis 是否可用。不可用时调用方应直接走数据库。 */
    public boolean isAvailable() {
        return redisProvider.getIfAvailable() != null;
    }

    public <T> Optional<T> get(String key, TypeReference<T> type) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return Optional.empty();
        }
        try {
            String json = redis.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.ofNullable(objectMapper.readValue(json, type));
        } catch (Exception e) {
            // 缓存内容损坏时主动删除，避免每次请求都解析失败
            log.warn("[缓存] 读取失败，按未命中处理 key={}: {}", key, e.getMessage());
            evict(key);
            return Optional.empty();
        }
    }

    public void put(String key, Object value, Duration ttl) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null || value == null) {
            return;
        }
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("[缓存] 写入失败 key={}: {}", key, e.getMessage());
        }
    }

    public void evict(String key) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("[缓存] 失效失败 key={}: {}", key, e.getMessage());
        }
    }
}
