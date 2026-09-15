package com.chengqu.huzhu.community.dto;

import com.chengqu.huzhu.community.entity.Goods;
import com.chengqu.huzhu.community.entity.GoodsContactType;
import com.chengqu.huzhu.community.entity.GoodsStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class GoodsResponse {

    private Long id;
    private String title;
    private String description;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private String category;
    private List<String> images;
    private Long sellerId;
    private String sellerName;
    /** 真实头像由 Feign 从 auth-service 补全，取不到时留空由前端展示占位图 */
    private String sellerAvatar;
    private String status;
    /** 状态中文名，由后端下发，前端不必再维护映射 */
    private String statusLabel;
    private Integer viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 卖家联系方式，**只在详情接口返回**（列表 / 我发布的都是 null）。
     *
     * <p>为什么列表必须留空：列表一页就是几十个卖家，且是最容易被脚本批量抓取的地方，
     * 把联系方式放进去等于对外提供一份可枚举的通讯录。买家要看，就多点一次进详情。
     */
    private String contact;

    /** 联系方式类型编码（WECHAT / PHONE / QQ / OTHER），同样只在详情返回。 */
    private String contactType;

    /** 联系方式类型中文名，前端直接展示。 */
    private String contactTypeLabel;

    /** 列表 / 卡片装配：不带联系方式，见 {@link #contact} 的说明。 */
    public static GoodsResponse from(Goods goods) {
        return base(goods).build();
    }

    /**
     * 详情装配：在列表字段的基础上补上联系方式，是联系方式唯一的出口。
     *
     * <p>做成独立方法而不是给 {@code from} 加布尔参数，是为了让「哪里会泄露联系方式」
     * 在调用处一眼可查：搜索 fromDetail 就能列出所有出口。
     */
    public static GoodsResponse fromDetail(Goods goods) {
        GoodsContactType contactType = goods.getContactType();
        String contact = goods.getContact();
        // contact 为空时类型一并置空：库里不该出现「有类型没号码」的半截数据，
        // 真出现了也不让前端渲染出「微信：」这种空标签
        boolean hasContact = contact != null && !contact.isBlank();
        return base(goods)
                .contact(hasContact ? contact : null)
                .contactType(hasContact && contactType != null ? contactType.name() : null)
                .contactTypeLabel(hasContact && contactType != null ? contactType.getLabel() : null)
                .build();
    }

    private static GoodsResponseBuilder base(Goods goods) {
        GoodsStatus status = goods.getStatus() == null ? GoodsStatus.ON_SALE : goods.getStatus();
        return GoodsResponse.builder()
                .id(goods.getId())
                .title(goods.getTitle())
                .description(goods.getDescription())
                .price(goods.getPrice())
                .originalPrice(goods.getOriginalPrice())
                .category(goods.getCategory())
                .images(goods.getImages() == null ? List.of() : goods.getImages())
                .sellerId(goods.getSellerId())
                .sellerName(goods.getSellerName())
                .status(status.name())
                .statusLabel(status.getLabel())
                .viewCount(goods.getViewCount() == null ? 0 : goods.getViewCount())
                .createdAt(goods.getCreatedAt())
                .updatedAt(goods.getUpdatedAt());
    }
}
