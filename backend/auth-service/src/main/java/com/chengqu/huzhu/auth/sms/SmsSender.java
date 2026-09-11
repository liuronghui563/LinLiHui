package com.chengqu.huzhu.auth.sms;

public interface SmsSender {

    /**
     * 发送短信验证码。
     *
     * @param phone 手机号
     * @param code  验证码
     * @param scene 业务场景
     */
    void send(String phone, String code, SmsScene scene);
}
