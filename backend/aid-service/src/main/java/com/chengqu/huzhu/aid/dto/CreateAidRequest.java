package com.chengqu.huzhu.aid.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAidRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题最多100字")
    private String title;

    @NotBlank(message = "内容不能为空")
    @Size(max = 2000, message = "内容最多2000字")
    private String content;

    @NotBlank(message = "分类不能为空")
    @Size(max = 50, message = "分类最多50字")
    private String category;

    private String board;

    @Size(max = 200, message = "地址最多200字")
    private String address;

    /** 本站已上传的对象地址，最多 9 张 */
    private java.util.List<String> images;
}
