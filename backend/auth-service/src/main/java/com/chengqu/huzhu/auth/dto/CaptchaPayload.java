package com.chengqu.huzhu.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CaptchaPayload {

    /** 验证码会话 ID，提交校验时回传 */
    private String captchaId;

    /**
     * 挑战原文，仅供前端绘制展示。
     * 校验仍以服务端内存中存储的值为准。
     */
    private String challenge;

    private int expireSeconds;
}
