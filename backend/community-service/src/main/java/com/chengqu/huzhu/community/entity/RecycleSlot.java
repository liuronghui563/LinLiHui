package com.chengqu.huzhu.community.entity;

import com.chengqu.huzhu.common.exception.BizException;

import java.util.Arrays;

/**
 * 上门时段。
 *
 * <p>只约到半天，不做精确到分钟的排班：回收端是按半天派单的，
 * 逼居民选一个整点，只会让双方在电话里再确认一次。
 */
public enum RecycleSlot {

    MORNING("上午", "09:00-12:00"),
    AFTERNOON("下午", "14:00-17:00"),
    EVENING("晚间", "18:00-21:00");

    private final String label;
    private final String window;

    RecycleSlot(String label, String window) {
        this.label = label;
        this.window = window;
    }

    public String getLabel() {
        return label;
    }

    /** 时段区间文案，供前端直接展示。 */
    public String getWindow() {
        return window;
    }

    public static RecycleSlot parseOrNull(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(slot -> slot.name().equals(normalized))
                .findFirst()
                .orElse(null);
    }

    /** 写操作解析：必须显式给出合法时段。 */
    public static RecycleSlot require(String code) {
        RecycleSlot slot = parseOrNull(code);
        if (slot == null) {
            throw new BizException("上门时段无效");
        }
        return slot;
    }
}
