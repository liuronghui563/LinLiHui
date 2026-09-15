package com.chengqu.huzhu.community.dto;

import com.chengqu.huzhu.community.entity.RecycleOrder;
import com.chengqu.huzhu.community.entity.RecycleSlot;
import com.chengqu.huzhu.community.entity.RecycleStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class RecycleOrderResponse {

    private Long id;
    private Long userId;
    private String userName;
    /** 真实头像由 Feign 从 auth-service 补全，取不到时留空由前端展示占位图 */
    private String userAvatar;
    private String category;
    private String categoryLabel;
    private BigDecimal weightKg;
    private String description;
    private String address;
    private String contactPhone;
    private LocalDate appointDate;
    private String appointSlot;
    private String appointSlotLabel;
    private String appointSlotWindow;
    private String status;
    private String statusLabel;
    private BigDecimal estimatedAmount;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static RecycleOrderResponse from(RecycleOrder order) {
        RecycleStatus status = order.getStatus() == null ? RecycleStatus.PENDING : order.getStatus();
        RecycleSlot slot = order.getAppointSlot();
        return RecycleOrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .userName(order.getUserName())
                .category(order.getCategory() == null ? null : order.getCategory().name())
                .categoryLabel(order.getCategory() == null ? null : order.getCategory().getLabel())
                .weightKg(order.getWeightKg())
                .description(order.getDescription())
                .address(order.getAddress())
                .contactPhone(order.getContactPhone())
                .appointDate(order.getAppointDate())
                .appointSlot(slot == null ? null : slot.name())
                .appointSlotLabel(slot == null ? null : slot.getLabel())
                .appointSlotWindow(slot == null ? null : slot.getWindow())
                .status(status.name())
                .statusLabel(status.getLabel())
                .estimatedAmount(order.getEstimatedAmount())
                .remark(order.getRemark())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
