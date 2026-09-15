package com.chengqu.huzhu.community.dto;

import com.chengqu.huzhu.community.entity.RecycleCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** 单个可回收品类的展示项，含计价说明。 */
@Data
@Builder
public class RecycleCategoryResponse {

    private String code;
    private String label;
    /** 计价单位：公斤 / 台 */
    private String unit;
    /** 参考单价，null 表示该品类不做按重量的预估 */
    private BigDecimal unitPrice;
    /** 计价或交付说明，直接展示给居民 */
    private String hint;

    public static RecycleCategoryResponse from(RecycleCategory category) {
        return RecycleCategoryResponse.builder()
                .code(category.name())
                .label(category.getLabel())
                .unit(category.getUnit())
                .unitPrice(category.getUnitPrice())
                .hint(category.getHint())
                .build();
    }
}
