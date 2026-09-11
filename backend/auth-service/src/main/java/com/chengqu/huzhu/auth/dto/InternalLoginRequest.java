package com.chengqu.huzhu.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InternalLoginRequest {

    @NotBlank(message = "请输入内部码")
    private String code;
}
