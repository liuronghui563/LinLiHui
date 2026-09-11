package com.chengqu.huzhu.auth.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 开发环境短信 Mock：验证码打印到控制台，不真实下发。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "mock", matchIfMissing = true)
public class MockSmsSender implements SmsSender {

    @Override
    public void send(String phone, String code, SmsScene scene) {
        log.info("[鉴权] 短信Mock phone={}, scene={}, code={}", phone, scene, code);
    }
}
