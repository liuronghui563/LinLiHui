package com.chengqu.huzhu.community.entity;

import com.chengqu.huzhu.common.exception.BizException;

import java.util.Arrays;

/**
 * 集市商品的流转状态。
 *
 * <p>为什么不复用帖子的那套表达：闲置转让以前只是动态正文里的一句话，
 * 卖没卖掉只能靠卖家自己再发一条「已出」。状态是集市的核心字段，
 * 它决定了列表默认能看到什么（只在售）、卖家能不能改价（已售出就不必了）。
 *
 * <p>数据库列用 MySQL 原生 ENUM（见 V6__market_goods.sql），
 * 因此这里的取值集合与迁移脚本必须同步修改。
 */
public enum GoodsStatus {

    ON_SALE("在售"),
    RESERVED("已预定"),
    SOLD("已售出"),
    OFF("已下架");

    private final String label;

    GoodsStatus(String label) {
        this.label = label;
    }

    /** 中文名由后端下发，前端不必再维护一份枚举映射。 */
    public String getLabel() {
        return label;
    }

    /** 筛选条件解析：非法值返回 null，表示该条件无效（调用方决定回退策略）。 */
    public static GoodsStatus parseOrNull(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(status -> status.name().equals(normalized))
                .findFirst()
                .orElse(null);
    }

    /**
     * 列表筛选解析：不传时默认只看在售（集市首页的默认视角），
     * 传了非法值直接报错而不是静默返回全部——静默回退会让前端筛选项写错也看不出来。
     */
    public static GoodsStatus requireFilter(String code) {
        if (code == null || code.isBlank()) {
            return ON_SALE;
        }
        GoodsStatus status = parseOrNull(code);
        if (status == null) {
            throw new BizException("商品状态无效");
        }
        return status;
    }

    /** 写操作解析：必须显式给出合法状态。 */
    public static GoodsStatus require(String code) {
        GoodsStatus status = parseOrNull(code);
        if (status == null) {
            throw new BizException("商品状态无效");
        }
        return status;
    }
}
