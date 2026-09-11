package com.chengqu.huzhu.auth.sms;

import com.chengqu.huzhu.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 真实短信通道预留位（如阿里云）。当前未配置密钥时仅抛出提示。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "aliyun")
public class AliyunSmsSender implements SmsSender {

    @Override
    public void send(String phone, String code, SmsScene scene) {
        log.warn("[鉴权] 阿里云短信未完整配置，拒绝发送。phone={}, scene={}", phone, scene);
        throw new BizException("短信通道未配置，请将 app.sms.provider 设为 mock 或完成阿里云配置");
    }
}
