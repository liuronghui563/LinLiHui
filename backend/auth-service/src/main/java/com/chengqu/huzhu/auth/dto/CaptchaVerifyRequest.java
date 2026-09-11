package com.chengqu.huzhu.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CaptchaVerifyRequest {

    @NotBlank(message = "图形验证码ID不能为空")
    private String captchaId;

    @NotBlank(message = "请输入图形验证码")
    private String captchaCode;
}
