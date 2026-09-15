package com.chengqu.huzhu.community.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 圈主增设 / 取消管理员。role 仅允许 ADMIN、MEMBER。 */
@Data
public class CircleRoleRequest {

    @NotBlank(message = "请指定成员身份")
    private String role;
}
