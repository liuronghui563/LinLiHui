package com.chengqu.huzhu.common.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 缓存原语测试。缓存的核心约定是「永不参与正确性判断」，
 * 因此除了命中/未命中，重点验证降级与脏数据自愈。
 */
@ExtendWith(MockitoExtension.class)
class RedisCacheTest {

    /** 用 record 作为缓存值，顺带验证 record 能被正确序列化/反序列化。 */
    record Sample(Long id, String title) {
    }

    @Mock
    private ObjectProvider<StringRedisTemplate> redisProvider;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private RedisCache cache;

    @BeforeEach
    void setUp() {
        cache = new RedisCache(redisProvider, objectMapper);
    }

    @Test
    @DisplayName("Redis 未引入时 isAvailable 为 false，get 返回空")
    void degradesWhenRedisAbsent() {
        when(redisProvider.getIfAvailable()).thenReturn(null);

        assertFalse(cache.isAvailable());
        assertTrue(cache.get("k", new TypeReference<Sample>() {
        }).isEmpty());
    }

    @Test
    @DisplayName("写入后再读取可正确反序列化")
    void putThenGetRoundTrip() throws Exception {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.opsForValue()).thenReturn(valueOps);

        Sample sample = new Sample(1L, "首页广告");
        cache.put("k", sample, Duration.ofSeconds(60));

        // 捕获真正写进 Redis 的 JSON，再把它喂回 get，形成真实的往返验证
        org.mockito.ArgumentCaptor<String> jsonCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(org.mockito.ArgumentMatchers.eq("k"), jsonCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(60)));
        when(valueOps.get("k")).thenReturn(jsonCaptor.getValue());

        Optional<Sample> loaded = cache.get("k", new TypeReference<Sample>() {
        });

        assertTrue(loaded.isPresent());
        assertEquals(sample, loaded.get());
    }

    @Test
    @DisplayName("缓存内容损坏时删除该 key 并按未命中处理")
    void evictsCorruptPayload() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("k")).thenReturn("{ this is not json");

        assertTrue(cache.get("k", new TypeReference<Sample>() {
        }).isEmpty());
        verify(redis).delete("k");
    }

    @Test
    @DisplayName("读取报错时按未命中处理，不向上抛异常")
    void getSwallowsRedisFailure() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("connection refused"));

        assertTrue(cache.get("k", new TypeReference<Sample>() {
        }).isEmpty());
    }

    @Test
    @DisplayName("写入报错时只记日志，不影响主流程")
    void putSwallowsRedisFailure() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.opsForValue()).thenReturn(valueOps);
        doThrow(new RuntimeException("connection refused"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        cache.put("k", new Sample(1L, "x"), Duration.ofSeconds(60));

        verify(redis, never()).delete(anyString());
    }

    @Test
    @DisplayName("Redis 未引入时 put / evict 是安全空操作")
    void putAndEvictAreNoopWithoutRedis() {
        when(redisProvider.getIfAvailable()).thenReturn(null);

        cache.put("k", new Sample(1L, "x"), Duration.ofSeconds(60));
        cache.evict("k");

        verify(redis, never()).delete(anyString());
    }

    @Test
    @DisplayName("可以缓存 List 这类集合结构")
    void supportsCollectionPayloads() throws Exception {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.opsForValue()).thenReturn(valueOps);

        List<Sample> list = List.of(new Sample(1L, "a"), new Sample(2L, "b"));
        cache.put("k", list, Duration.ofSeconds(60));

        org.mockito.ArgumentCaptor<String> jsonCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(org.mockito.ArgumentMatchers.eq("k"), jsonCaptor.capture(), any(Duration.class));
        when(valueOps.get("k")).thenReturn(jsonCaptor.getValue());

        Optional<List<Sample>> loaded = cache.get("k", new TypeReference<List<Sample>>() {
        });

        assertTrue(loaded.isPresent());
        assertEquals(2, loaded.get().size());
        assertEquals("b", loaded.get().get(1).title());
    }
}
