package com.chengqu.huzhu.community.entity;

import com.chengqu.huzhu.common.jpa.StringListJsonConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 集市商品（二手闲置）。
 *
 * <p>与 u_r_post 的关系：动态是「说点什么」，商品是「卖点什么」。
 * 二者字段诉求差别很大（价格、状态流转、浏览量），因此独立成表，
 * 而不是在动态表上加一堆只有集市才用的列。
 */
@Getter
@Setter
@Entity
@Table(name = "u_r_goods")
public class Goods {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** 原价，可选。用于前端展示划线价，不参与任何计算。 */
    @Column(precision = 10, scale = 2)
    private BigDecimal originalPrice;

    /** 自由文本分类（数码、母婴、家具……），与帖子 category 一样不做白名单。 */
    @Column(length = 50)
    private String category;

    /** 本站对象地址（/api/file/objects/...），最多 9 张，存 JSON 文本。 */
    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> images;

    @Column(nullable = false)
    private Long sellerId;

    /** 冗余昵称快照：Feign 取不到用户信息时兜底渲染，不作为权威数据。 */
    @Column(nullable = false, length = 50)
    private String sellerName;

    /**
     * 卖家留下的联系方式（微信号 / 手机号 / QQ / 其他），可空。
     *
     * <p>为什么需要它：站内没有私信功能，在此之前买家看完商品没有任何办法联系卖家。
     * 联系方式只在**详情接口**返回，列表接口刻意不带——列表是最容易被批量抓取的地方，
     * 把全站卖家的手机号铺在一页 24 条里等于白送一份通讯录。
     */
    @Column(length = 100)
    private String contact;

    /** 联系方式类型，与 contact 同生共死：没有 contact 时这里必须是 null。 */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private GoodsContactType contactType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoodsStatus status;

    @Column(nullable = false)
    private Integer viewCount = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = GoodsStatus.ON_SALE;
        }
        if (viewCount == null) {
            viewCount = 0;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
