package com.chengqu.huzhu.community.entity;

import com.chengqu.huzhu.common.exception.BizException;

import java.math.BigDecimal;
import java.util.Arrays;

/**
 * 可回收品类及其计价口径。
 *
 * <p>把单价放在枚举里而不是配置文件：它就是业务规则本身，改价属于一次代码变更，
 * 顺便让 /api/recycle/categories 与下单预估金额共用同一份数据，不会出现
 * 「页面写着 0.8 元/公斤、后端按 0.5 算」这种口径分叉。
 *
 * <p>{@code unitPrice} 为 null 表示该品类没有「按公斤 × 单价」的简单口径
 * （家电按台评估、其他需现场判断），此时预估金额留空而不是编一个数字出来。
 *
 * <p>数据库列用 MySQL 原生 ENUM（见 V8__recycle.sql），取值集合必须同步修改。
 */
public enum RecycleCategory {

    PAPER("废纸", new BigDecimal("0.80"), "公斤", "纸箱、报纸、书本，请压平捆好"),
    PLASTIC("塑料", new BigDecimal("0.60"), "公斤", "饮料瓶、塑料盆桶，请清空残留"),
    METAL("金属", new BigDecimal("1.60"), "公斤", "易拉罐、旧锅具、废铜烂铁"),
    CLOTHES("旧衣物", new BigDecimal("0.30"), "公斤", "干净衣物、床单被罩，请装袋"),
    APPLIANCE("旧家电", null, "台", "冰箱、洗衣机、空调等大件，上门评估后按台计价"),
    OTHER("其他", null, "公斤", "以上都不是？写在描述里，师傅上门评估");

    private final String label;
    private final BigDecimal unitPrice;
    private final String unit;
    private final String hint;

    RecycleCategory(String label, BigDecimal unitPrice, String unit, String hint) {
        this.label = label;
        this.unitPrice = unitPrice;
        this.unit = unit;
        this.hint = hint;
    }

    public String getLabel() {
        return label;
    }

    /** 参考单价（元/公斤），null 表示不做按重量的预估。 */
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public String getUnit() {
        return unit;
    }

    /** 计价说明，直接下发给前端展示。 */
    public String getHint() {
        return hint;
    }

    public static RecycleCategory parseOrNull(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(category -> category.name().equals(normalized))
                .findFirst()
                .orElse(null);
    }

    public static RecycleCategory require(String code) {
        RecycleCategory category = parseOrNull(code);
        if (category == null) {
            throw new BizException("回收品类无效");
        }
        return category;
    }
}
