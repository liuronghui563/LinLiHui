package com.chengqu.huzhu.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCircleRequest {

    @NotBlank(message = "圈子名称不能为空")
    @Size(max = 50, message = "圈子名称最多50字")
    private String name;

    @Size(max = 200, message = "圈子简介最多200字")
    private String description;

    /** 本站已上传的对象地址；不传表示暂不设置封面 */
    @Size(max = 500, message = "封面地址过长")
    private String cover;
}
