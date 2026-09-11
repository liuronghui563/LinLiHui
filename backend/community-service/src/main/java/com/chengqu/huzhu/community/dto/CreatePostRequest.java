package com.chengqu.huzhu.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreatePostRequest {

    @NotBlank(message = "内容不能为空")
    @Size(max = 1000, message = "内容最多1000字")
    private String content;

    /** COMMUNITY 邻里动态；PLAZA 生活广场；CAMPUS 校园互动 */
    private String channel;

    @Size(max = 50, message = "分类最多50字")
    private String category;
}
