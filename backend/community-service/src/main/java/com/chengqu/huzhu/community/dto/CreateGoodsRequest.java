package com.chengqu.huzhu.community.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateGoodsRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题最多100字")
    private String title;

    @Size(max = 1000, message = "描述最多1000字")
    private String description;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.00", message = "价格不能为负")
    @Digits(integer = 8, fraction = 2, message = "价格最多两位小数")
    private BigDecimal price;

    @DecimalMin(value = "0.00", message = "原价不能为负")
    @Digits(integer = 8, fraction = 2, message = "原价最多两位小数")
    private BigDecimal originalPrice;

    @Size(max = 50, message = "分类最多50字")
    private String category;

    /** 本站已上传的对象地址，最多 9 张 */
    @Size(max = 9, message = "最多上传 9 张图片")
    private List<String> images;

    /**
     * 联系方式（微信号 / 手机号 / QQ 号 / 其他），选填。
     *
     * <p>不设 @NotBlank：老商品与不想公开联系方式的卖家都应该能继续发布，
     * 为空时买家只能通过卖家主页判断，至少不会比改动前更差。
     */
    @Size(max = 100, message = "联系方式最多100字")
    private String contact;

    /**
     * 联系方式类型编码（WECHAT / PHONE / QQ / OTHER），选填。
     *
     * <p>这里用 String 而不是枚举类型：编码非法时由 MarketService 抛 BizException
     * 给出中文提示，而不是让 Jackson 在反序列化阶段抛出的英文异常直接冒到接口上。
     * 留空时按「其他」保存——不能因为没选类型就把唯一的联系方式丢掉。
     */
    @Size(max = 20, message = "联系方式类型最多20字")
    private String contactType;
}
