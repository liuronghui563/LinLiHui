package com.chengqu.huzhu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Captcha captcha = new Captcha();
    private Sms sms = new Sms();
    private Cors cors = new Cors();

    /**
     * 内部码登录口令。原实现将 "8461" 硬编码在 AuthService 中，
     * 任何拿到源码的人都能用它换取管理员令牌。
     * 现改为配置项，生产环境必须通过 INTERNAL_LOGIN_CODE 覆盖；
     * 留空表示关闭该登录方式。
     */
    private String internalLoginCode;

    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class Jwt {
        private String secret;
        private long accessTokenExpireMinutes = 30;
        private long refreshTokenExpireDays = 7;
    }

    /**
     * 接口限流配置。基于 Redis 固定窗口实现，Redis 不可用时自动降级为放行。
     */
    @Data
    public static class RateLimit {
        private boolean enabled = true;
        /** 同一 IP 每分钟允许的登录尝试次数。 */
        private int loginPerMinutePerIp = 20;
        /** 同一手机号每分钟允许的登录尝试次数（用于抵挡换 IP 的撞库）。 */
        private int loginPerMinutePerPhone = 10;
        /** 同一 IP 每小时允许的注册次数。 */
        private int registerPerHourPerIp = 10;
        /** 同一 IP 每分钟允许的短信发送次数（手机号维度的频控在 SmsCodeService 内）。 */
        private int smsPerMinutePerIp = 10;
    }

    @Data
    public static class Captcha {
        private int expireSeconds = 120;
        private int length = 4;
    }

    @Data
    public static class Sms {
        private String provider = "mock";
        private int codeLength = 6;
        private int expireSeconds = 300;
        private int sendIntervalSeconds = 60;
        private int dailyLimit = 20;
    }

    @Data
    public static class Cors {
        private String allowedOrigins = "http://localhost:5173";
    }
}
