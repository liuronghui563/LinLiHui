package com.chengqu.huzhu.common.security;

import com.chengqu.huzhu.common.redis.RedisKeys;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 令牌吊销服务。
 *
 * <p>JWT 是无状态的，签发后在有效期内天然无法撤回——这正是原来 {@code logout()}
 * 只把用户状态改成 OFFLINE、却拦不住已发出的令牌的原因。这里用 Redis 补上这一环。
 *
 * <p>两级吊销，各解决一个具体问题：
 * <ol>
 *   <li><b>会话级（sid）</b>：一次登录签发的 access + refresh 共用同一个 sid。
 *       登出时吊销 sid，两个令牌同时失效。这是必须的——客户端登出只上送 access token，
 *       服务端拿不到 refresh token，只吊销 access 会留下「登出后仍能用 refresh 续期」的口子。</li>
 *   <li><b>用户级（令牌版本号）</b>：签发时把当前版本写入 {@code ver} 声明，校验时比对。
 *       「退出所有设备」只需自增版本号，O(1) 且精确——不像时间水位线那样受
 *       JWT {@code iat} 秒级精度限制，能覆盖同秒签发的其他会话。</li>
 * </ol>
 *
 * <p>由于四个业务服务都各自校验 JWT，本组件放在 {@code common} 中由所有服务共享，
 * 检查的是同一份 Redis 数据——否则吊销在 aid-service 生效、在 community-service 不生效，
 * 只给了「已经下线」的错觉。
 *
 * <p><b>降级策略</b>：Redis 不可用时默认 <b>放行</b>（{@code fail-open}）。
 * 理由是令牌本身仍有 30 分钟自然过期兜底，让全站登录一起挂掉的代价更大。
 * 需要更严格语义时把 {@code app.jwt.revocation-fail-closed} 设为 true。
 */
@Slf4j
@Component
@ConditionalOnClass(StringRedisTemplate.class)
@RequiredArgsConstructor
public class TokenRevocationService {

    /** 无版本号记录时的默认版本。首个「退出所有设备」会把版本自增到 1。 */
    public static final long INITIAL_TOKEN_VERSION = 0L;

    /** 会话吊销标记至少要覆盖 refresh token 的有效期，否则刷新令牌会在标记过期后复活。 */
    private static final Duration SESSION_REVOKE_TTL = Duration.ofDays(7);
    private static final Duration VERSION_TTL = Duration.ofDays(7);

    private final ObjectProvider<StringRedisTemplate> redisProvider;

    @Value("${app.jwt.revocation-fail-closed:false}")
    private boolean failClosed;

    /** 吊销一个会话（该次登录的 access + refresh 同时失效）。 */
    public void revokeSession(String sessionId) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null || sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            redis.opsForValue().set(RedisKeys.revokedSession(sessionId), "1", SESSION_REVOKE_TTL);
            log.info("[鉴权] 会话已吊销 sid={}", sessionId);
        } catch (Exception e) {
            log.warn("[鉴权] 写入会话吊销标记失败 sid={}: {}", sessionId, e.getMessage());
        }
    }

    /** 读取用户当前令牌版本；无记录时返回 {@link #INITIAL_TOKEN_VERSION}。 */
    public long currentTokenVersion(Long userId) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null || userId == null) {
            return INITIAL_TOKEN_VERSION;
        }
        try {
            String value = redis.opsForValue().get(RedisKeys.userTokenVersion(userId));
            return value == null ? INITIAL_TOKEN_VERSION : Long.parseLong(value);
        } catch (Exception e) {
            log.warn("[鉴权] 读取令牌版本失败 userId={}: {}", userId, e.getMessage());
            return INITIAL_TOKEN_VERSION;
        }
    }

    /** 自增用户令牌版本，使其此前签发的全部令牌立即失效（退出所有设备）。 */
    public void bumpUserTokenVersion(Long userId) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null || userId == null) {
            return;
        }
        try {
            String key = RedisKeys.userTokenVersion(userId);
            Long version = redis.opsForValue().increment(key);
            redis.expire(key, VERSION_TTL);
            log.info("[鉴权] 已吊销用户全部会话 userId={}, 新版本={}", userId, version);
        } catch (Exception e) {
            log.warn("[鉴权] 自增令牌版本失败 userId={}: {}", userId, e.getMessage());
        }
    }

    /**
     * 判断令牌是否已被吊销。正常路径 1~2 次 Redis 查询。
     */
    public boolean isRevoked(Claims claims) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null || claims == null) {
            return false;
        }
        try {
            String sessionId = claims.get(JwtSupport.CLAIM_SESSION_ID, String.class);
            if (sessionId != null && !sessionId.isBlank()
                    && Boolean.TRUE.equals(redis.hasKey(RedisKeys.revokedSession(sessionId)))) {
                return true;
            }

            // 没有 ver 声明的历史令牌跳过版本校验，保持向后兼容
            Object versionClaim = claims.get(JwtSupport.CLAIM_TOKEN_VERSION);
            Long userId = parseUserId(claims);
            if (versionClaim != null && userId != null) {
                String current = redis.opsForValue().get(RedisKeys.userTokenVersion(userId));
                if (current != null && parseLong(versionClaim) != Long.parseLong(current)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            if (failClosed) {
                log.error("[鉴权] 吊销状态查询失败且配置为 fail-closed，拒绝该令牌: {}", e.getMessage());
                return true;
            }
            log.warn("[鉴权] 吊销状态查询失败，按未吊销处理: {}", e.getMessage());
            return false;
        }
    }

    private Long parseUserId(Claims claims) {
        try {
            return claims.getSubject() == null ? null : Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return Long.MIN_VALUE;
        }
    }
}
