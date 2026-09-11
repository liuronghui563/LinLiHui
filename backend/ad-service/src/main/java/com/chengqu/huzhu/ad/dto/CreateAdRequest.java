package com.chengqu.huzhu.ad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAdRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题最长100字")
    private String title;

    @Size(max = 200, message = "副标题最长200字")
    private String subtitle;

    @Size(max = 500, message = "图片地址最长500字")
    private String imageUrl;

    @Size(max = 500, message = "链接地址最长500字")
    private String linkUrl;

    private Integer sortOrder;

    private Boolean enabled;
}
