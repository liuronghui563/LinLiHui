package com.chengqu.huzhu.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class JwtSupport {

    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_PHONE = "phone";
    public static final String CLAIM_NICKNAME = "nickname";
    /** 令牌版本号：与 Redis 中的当前版本比对，不一致即视为已吊销。 */
    public static final String CLAIM_TOKEN_VERSION = "ver";
    /** 会话 ID：同一次登录签发的 access / refresh 共用，用于按会话吊销。 */
    public static final String CLAIM_SESSION_ID = "sid";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    /** 源码中内置的开发用默认密钥，一旦线上仍在用它，任何人都可伪造任意用户的令牌。 */
    private static final String INSECURE_DEFAULT_SECRET =
            "chengqu-huzhu-jwt-secret-key-change-me-in-production-2026";

    private final SecretKey key;
    private final long accessTokenExpireMinutes;
    private final long refreshTokenExpireDays;

    public JwtSupport(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expire-minutes:30}") long accessTokenExpireMinutes,
            @Value("${app.jwt.refresh-token-expire-days:7}") long refreshTokenExpireDays) {
        if (INSECURE_DEFAULT_SECRET.equals(secret)) {
            log.warn("========================================================================");
            log.warn("[安全告警] 正在使用源码内置的默认 JWT 密钥，仅可用于本地开发。");
            log.warn("[安全告警] 部署到任何可被外部访问的环境前，必须通过环境变量 JWT_SECRET 覆盖。");
            log.warn("========================================================================");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        //临时局部变量，须this.xxx=赋值
        this.key = Keys.hmacShaKeyFor(bytes.length >= 32 ? bytes : pad(bytes));
        this.accessTokenExpireMinutes = accessTokenExpireMinutes;
        this.refreshTokenExpireDays = refreshTokenExpireDays;
    }

    private static byte[] pad(byte[] bytes) {
        byte[] padded = new byte[32];
        System.arraycopy(bytes, 0, padded, 0, Math.min(bytes.length, 32));
        return padded;
    }

    /**
     * 签发访问令牌。
     *
     * @param tokenVersion 签发时的用户令牌版本号，用于支持「退出所有设备」
     * @param sessionId    会话 ID，与同批签发的刷新令牌共用，用于支持按会话登出
     */
    public String createAccessToken(Long userId, String phone, String nickname, String role,
                                    long tokenVersion, String sessionId) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(accessTokenExpireMinutes * 60);
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TYPE, TYPE_ACCESS);
        claims.put(CLAIM_PHONE, phone);
        claims.put(CLAIM_NICKNAME, nickname == null ? "" : nickname);
        claims.put(CLAIM_ROLE, role);
        claims.put(CLAIM_TOKEN_VERSION, tokenVersion);
        if (sessionId != null) {
            claims.put(CLAIM_SESSION_ID, sessionId);
        }
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    /**
     * 签发刷新令牌。携带与访问令牌相同的 {@code ver} 与 {@code sid}，
     * 这样登出（按会话）或强制下线（按版本）时刷新令牌会一并失效，
     * 否则会出现「登出后仍能用刷新令牌换回新访问令牌」的漏洞。
     */
    public String createRefreshToken(Long userId, long tokenVersion, String sessionId) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(refreshTokenExpireDays * 24 * 3600);
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_TYPE, TYPE_REFRESH);
        claims.put(CLAIM_TOKEN_VERSION, tokenVersion);
        if (sessionId != null) {
            claims.put(CLAIM_SESSION_ID, sessionId);
        }
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public long getAccessExpireSeconds() {
        return accessTokenExpireMinutes * 60;
    }
}
