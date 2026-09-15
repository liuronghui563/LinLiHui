package com.chengqu.huzhu.ad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户提交广告位申请。
 *
 * <p>比管理员创建（{@link CreateAdRequest}）少两个字段，而且是刻意的：
 * <ul>
 *   <li>没有 {@code enabled}——启用与否是审核的结果，不是申请人能决定的；</li>
 *   <li>没有 {@code sortOrder}——排序是运营位，申请人不该也不能自己插队。</li>
 * </ul>
 */
@Data
public class AdApplicationRequest {

    @NotBlank(message = "请填写广告标题")
    @Size(max = 100, message = "标题最多100字")
    private String title;

    @Size(max = 200, message = "说明最多200字")
    private String subtitle;

    @NotBlank(message = "请上传广告图")
    @Size(max = 500, message = "图片地址过长")
    private String imageUrl;

    @Size(max = 500, message = "链接过长")
    private String linkUrl;
}
