package com.chengqu.huzhu.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCommentRequest {

    @NotBlank(message = "评论不能为空")
    @Size(max = 500, message = "评论最多500字")
    private String content;
}
