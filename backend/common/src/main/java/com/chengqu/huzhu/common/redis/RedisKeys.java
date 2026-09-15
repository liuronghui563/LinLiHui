package com.chengqu.huzhu.common.redis;

import java.time.LocalDate;

/**
 * Redis key 统一命名。
 *
 * <p>集中管理的原因：key 是跨服务共享的状态契约——auth-service 写入的令牌吊销标记，
 * aid/community/ad 三个服务都要能查到同一个 key。散落在各处极易写错前缀导致
 * 「这边写、那边查不到」的静默故障。
 *
 * <p>统一前缀 {@code cqh:}（chengqu huzhu），便于在共享 Redis 实例上区分业务，
 * 也方便用 {@code SCAN cqh:*} 排查。
 */
public final class RedisKeys {

    private static final String PREFIX = "cqh:";

    private RedisKeys() {
    }

    /** 图形验证码。Hash：{@code code} / {@code verified} / {@code verifiedUntil}。 */
    public static String captcha(String captchaId) {
        return PREFIX + "captcha:" + captchaId;
    }

    /** 短信验证码明文。 */
    public static String smsCode(String phone, String scene) {
        return PREFIX + "sms:code:" + phone + ":" + scene;
    }

    /** 短信发送间隔锁，用 SET NX EX 实现，过期即自动解锁。 */
    public static String smsInterval(String phone, String scene) {
        return PREFIX + "sms:interval:" + phone + ":" + scene;
    }

    /** 短信每日发送计数。 */
    public static String smsDaily(String phone, LocalDate date) {
        return PREFIX + "sms:daily:" + phone + ":" + date;
    }

    /**
     * 会话级吊销标记（按 sid）。
     *
     * <p>一次登录会同时签发 access token 与 refresh token，两者带上同一个 sid。
     * 吊销 sid 即可让这一对令牌同时失效——这是唯一能拦住「登出后拿 refresh token 续期」的做法，
     * 因为客户端登出时只会上送 access token，服务端拿不到 refresh token 的 jti。
     */
    public static String revokedSession(String sessionId) {
        return PREFIX + "token:revoked-sid:" + sessionId;
    }

    /**
     * 用户级令牌版本号，用于「退出所有设备」。
     *
     * <p>签发令牌时把当时的版本号写进 {@code ver} 声明，校验时比对当前版本；
     * 版本号一旦自增，此前签发的全部令牌立刻失效。
     *
     * <p>之所以不用「签发时间水位线」：JWT 的 {@code iat} 只有秒级精度，
     * 与阈值同秒签发的令牌无法区分，会出现「退出所有设备后仍有会话存活」。
     */
    public static String userTokenVersion(Long userId) {
        return PREFIX + "token:user-version:" + userId;
    }

    /** 固定窗口限流计数。 */
    public static String rateLimit(String scope, String identity) {
        return PREFIX + "rate:" + scope + ":" + identity;
    }

    /** 广告轮播缓存。 */
    public static String adCarousel() {
        return PREFIX + "cache:ad:carousel";
    }

    /** 广场热度榜缓存。 */
    public static String plazaHot() {
        return PREFIX + "cache:community:plaza-hot";
    }
}
