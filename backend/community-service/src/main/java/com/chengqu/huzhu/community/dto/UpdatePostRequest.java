package com.chengqu.huzhu.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdatePostRequest {

    @NotBlank(message = "内容不能为空")
    @Size(max = 1000, message = "内容最多1000字")
    private String content;
}
