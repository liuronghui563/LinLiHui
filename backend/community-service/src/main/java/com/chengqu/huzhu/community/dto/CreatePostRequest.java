package com.chengqu.huzhu.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreatePostRequest {

    @NotBlank(message = "内容不能为空")
    @Size(max = 1000, message = "内容最多1000字")
    private String content;

    /** DISCOVER 发现模块；CAMPUS 校园模块 */
    private String channel;

    /**
     * 帖子种类编码，取值见 {@link com.chengqu.huzhu.community.entity.PostKind}。
     * 不传时按频道取默认种类。
     */
    private String kind;

    /** 本站已上传的对象地址，最多 9 张 */
    @Size(max = 9, message = "最多上传 9 张图片")
    private List<String> images;
}
