package com.chengqu.huzhu.community.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 编辑商品。字段与 {@link CreateGoodsRequest} 一致（PUT 是全量更新）。
 *
 * <p>独立成类而不是复用 Create：两者的必填约束将来可能分叉
 * （例如允许老商品不带价格改描述），共用会让约束只能往松的方向妥协。
 * 注意状态不在这里改，走 {@code POST /{id}/status}。
 */
@Data
public class UpdateGoodsRequest {

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

    @Size(max = 9, message = "最多上传 9 张图片")
    private List<String> images;

    /**
     * 联系方式（微信号 / 手机号 / QQ 号 / 其他）。
     *
     * <p>与 images 同一约定：传 null 表示「本次不改联系方式」，
     * 传空串表示清空——这样只改价格的调用方不必先读一遍当前值再回写。
     */
    @Size(max = 100, message = "联系方式最多100字")
    private String contact;

    /** 联系方式类型编码（WECHAT / PHONE / QQ / OTHER）；contact 非空时留空按「其他」保存。 */
    @Size(max = 20, message = "联系方式类型最多20字")
    private String contactType;
}
