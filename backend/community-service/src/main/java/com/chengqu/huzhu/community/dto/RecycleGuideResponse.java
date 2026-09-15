package com.chengqu.huzhu.community.dto;

import com.chengqu.huzhu.community.entity.RecycleSlot;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 回收品类与计价说明。
 *
 * <p>当前这个接口需要登录，和 {@code /api/recycle} 下其余接口一致（见 SecurityConfig）。
 * 内容本身不含任何用户数据，做成匿名可访问是合理的下一步——但前端 /recycle 是
 * requiresAuth 路由，单放开后端白名单并为不可达路径，要放就得连路由与页面分区一起改。
 */
@Data
@Builder
public class RecycleGuideResponse {

    /** 总体计价口径说明 */
    private String pricingNote;
    private List<RecycleCategoryResponse> categories;
    private List<SlotOption> slots;

    @Data
    @Builder
    public static class SlotOption {
        private String code;
        private String label;
        private String window;
    }

    public static SlotOption slotOf(RecycleSlot slot) {
        return SlotOption.builder()
                .code(slot.name())
                .label(slot.getLabel())
                .window(slot.getWindow())
                .build();
    }
}
