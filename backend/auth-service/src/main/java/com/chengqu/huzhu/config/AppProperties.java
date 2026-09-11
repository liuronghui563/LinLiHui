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

    @Data
    public static class Jwt {
        private String secret;
        private long accessTokenExpireMinutes = 30;
        private long refreshTokenExpireDays = 7;
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
