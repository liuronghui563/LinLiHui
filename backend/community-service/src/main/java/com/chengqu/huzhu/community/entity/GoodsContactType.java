package com.chengqu.huzhu.community.entity;

import java.util.Arrays;

/**
 * 集市商品的联系方式类型。
 *
 * <p>为什么要有类型而不是直接存一串文本：买家用微信、电话还是 QQ 联系，
 * 决定了前端怎么渲染（电话可以直接拨打、微信号只能复制），
 * 也让「联系方式为空」这种「有类型没号码」的半截数据可以被识别出来。
 *
 * <p>数据库列用 MySQL 原生 ENUM（见 V9__goods_contact.sql），
 * 因此这里的取值集合与迁移脚本必须同步修改。
 */
public enum GoodsContactType {

    WECHAT("微信"),
    PHONE("手机"),
    QQ("QQ"),
    OTHER("其他");

    private final String label;

    GoodsContactType(String label) {
        this.label = label;
    }

    /** 中文名由后端下发，前端不必再维护一份枚举映射。 */
    public String getLabel() {
        return label;
    }

    /** 解析：非法值返回 null，由调用方决定是报错还是回退。 */
    public static GoodsContactType parseOrNull(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(type -> type.name().equals(normalized))
                .findFirst()
                .orElse(null);
    }
}
