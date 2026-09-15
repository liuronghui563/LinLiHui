package com.chengqu.huzhu.common.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 令牌吊销逻辑测试。
 *
 * <p>这是本轮最需要回归保护的一块：吊销判定写错会导致「登出后令牌仍然可用」，
 * 而这类问题在手工点测时极难发现。
 */
@ExtendWith(MockitoExtension.class)
class TokenRevocationServiceTest {

    private static final long USER_ID = 7L;
    private static final String SID = "sid-abc";

    @Mock
    private ObjectProvider<StringRedisTemplate> redisProvider;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private TokenRevocationService service;

    @BeforeEach
    void setUp() {
        service = new TokenRevocationService(redisProvider);
        // @Value 注入的字段在单元测试中保持默认值 false（fail-open）
    }

    private Claims claims(String sessionId, Long tokenVersion) {
        Claims claims = mock(Claims.class);
        lenient().when(claims.get(JwtSupport.CLAIM_SESSION_ID, String.class)).thenReturn(sessionId);
        lenient().when(claims.get(JwtSupport.CLAIM_TOKEN_VERSION)).thenReturn(tokenVersion);
        lenient().when(claims.getSubject()).thenReturn(String.valueOf(USER_ID));
        return claims;
    }

    private void stubValueOps() {
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    // ------------------------------------------------------------------
    // 会话级吊销
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Redis 未引入时不吊销（保持向后兼容，也不触碰 claim）")
    void notRevokedWhenRedisAbsent() {
        when(redisProvider.getIfAvailable()).thenReturn(null);

        assertFalse(service.isRevoked(mock(Claims.class)));
    }

    @Test
    @DisplayName("会话被吊销则判定为已吊销")
    void revokedWhenSessionRevoked() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.hasKey("cqh:token:revoked-sid:" + SID)).thenReturn(true);

        assertTrue(service.isRevoked(claims(SID, 0L)));
    }

    @Test
    @DisplayName("会话未吊销且版本一致时不吊销")
    void notRevokedWhenSessionActiveAndVersionMatches() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.hasKey(anyString())).thenReturn(false);
        stubValueOps();
        when(valueOps.get("cqh:token:user-version:" + USER_ID)).thenReturn("1");

        assertFalse(service.isRevoked(claims(SID, 1L)));
    }

    // ------------------------------------------------------------------
    // 用户级令牌版本
    // ------------------------------------------------------------------

    @Test
    @DisplayName("令牌版本落后于当前版本则判定为已吊销（退出所有设备）")
    void revokedWhenVersionOutdated() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.hasKey(anyString())).thenReturn(false);
        stubValueOps();
        when(valueOps.get("cqh:token:user-version:" + USER_ID)).thenReturn("2");

        assertTrue(service.isRevoked(claims(SID, 1L)));
    }

    @Test
    @DisplayName("版本号相同（含同一次登录内刚签发的令牌）不受影响")
    void notRevokedWhenVersionEqualsCurrent() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.hasKey(anyString())).thenReturn(false);
        stubValueOps();
        when(valueOps.get("cqh:token:user-version:" + USER_ID)).thenReturn("2");

        // 版本号是精确比对，不存在「签发时间同秒」这类精度歧义
        assertFalse(service.isRevoked(claims(SID, 2L)));
    }

    @Test
    @DisplayName("无版本记录时默认版本为 0，携带 ver=0 的令牌仍然有效")
    void notRevokedWhenNoStoredVersion() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.hasKey(anyString())).thenReturn(false);
        stubValueOps();
        when(valueOps.get("cqh:token:user-version:" + USER_ID)).thenReturn(null);

        assertFalse(service.isRevoked(claims(SID, 0L)));
    }

    @Test
    @DisplayName("历史令牌没有 ver 声明时跳过版本校验（向后兼容）")
    void notRevokedWhenVersionClaimAbsent() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.hasKey(anyString())).thenReturn(false);

        assertFalse(service.isRevoked(claims(SID, null)));
    }

    @Test
    @DisplayName("currentTokenVersion 无记录时返回初始版本 0")
    void currentVersionDefaultsToInitial() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        stubValueOps();
        when(valueOps.get("cqh:token:user-version:" + USER_ID)).thenReturn(null);

        assertEquals(TokenRevocationService.INITIAL_TOKEN_VERSION, service.currentTokenVersion(USER_ID));
    }

    @Test
    @DisplayName("自增版本号并设置过期时间")
    void bumpVersionIncrementsAndExpires() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        stubValueOps();
        when(valueOps.increment("cqh:token:user-version:" + USER_ID)).thenReturn(1L);

        service.bumpUserTokenVersion(USER_ID);

        verify(valueOps).increment("cqh:token:user-version:" + USER_ID);
        verify(redis).expire(eq("cqh:token:user-version:" + USER_ID), any(Duration.class));
    }

    // ------------------------------------------------------------------
    // 降级策略
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Redis 故障时默认放行，避免缓存故障演变成全站无法登录")
    void failOpenOnRedisFailureByDefault() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.hasKey(anyString())).thenThrow(new RuntimeException("connection refused"));

        assertFalse(service.isRevoked(claims(SID, 0L)));
    }

    @Test
    @DisplayName("配置 fail-closed 后，Redis 故障时拒绝令牌")
    void failClosedWhenConfigured() {
        ReflectionTestUtils.setField(service, "failClosed", true);
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.hasKey(anyString())).thenThrow(new RuntimeException("connection refused"));

        assertTrue(service.isRevoked(claims(SID, 0L)));
    }

    // ------------------------------------------------------------------
    // 写入
    // ------------------------------------------------------------------

    @Test
    @DisplayName("吊销会话：标记 TTL 至少覆盖刷新令牌有效期，否则刷新令牌会复活")
    void revokeSessionWritesKeyWithTtl() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        stubValueOps();

        service.revokeSession(SID);

        org.mockito.ArgumentCaptor<Duration> ttl = org.mockito.ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(eq("cqh:token:revoked-sid:" + SID), eq("1"), ttl.capture());
        assertTrue(ttl.getValue().toDays() >= 7,
                "TTL 必须 >= refresh token 有效期(7天)，实际=" + ttl.getValue());
    }

    @Test
    @DisplayName("空 sid 不写 Redis")
    void revokeSessionIgnoresBlankSid() {
        service.revokeSession("  ");
        service.revokeSession(null);

        org.mockito.Mockito.verifyNoInteractions(redis);
    }
}
