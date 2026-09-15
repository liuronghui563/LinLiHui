package com.chengqu.huzhu.community.entity;

import com.chengqu.huzhu.common.exception.BizException;

import java.util.Arrays;

/**
 * 回收订单状态。
 *
 * <p>PENDING/CONFIRMED 是可取消的（还没上门，取消不影响任何人），
 * DONE/CANCELLED 是终态。约束写在 Service 里而不是枚举上，
 * 因为「能不能从这个状态走过去」还取决于操作人是谁。
 */
public enum RecycleStatus {

    PENDING("待确认"),
    CONFIRMED("已确认"),
    DONE("已完成"),
    CANCELLED("已取消");

    private final String label;

    RecycleStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static RecycleStatus parseOrNull(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(status -> status.name().equals(normalized))
                .findFirst()
                .orElse(null);
    }

    /** 列表筛选解析：不传表示全部状态；传了非法值直接报错，避免前端筛选静默失效。 */
    public static RecycleStatus parseFilter(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        RecycleStatus status = parseOrNull(code);
        if (status == null) {
            throw new BizException("订单状态无效");
        }
        return status;
    }
}
