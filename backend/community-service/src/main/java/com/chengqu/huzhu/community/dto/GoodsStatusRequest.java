package com.chengqu.huzhu.community.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 修改商品状态。取值见 {@link com.chengqu.huzhu.community.entity.GoodsStatus}：
 * ON_SALE 在售、RESERVED 已预定、SOLD 已售出、OFF 已下架。
 */
@Data
public class GoodsStatusRequest {

    @NotBlank(message = "状态不能为空")
    private String status;
}
