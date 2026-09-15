package com.chengqu.huzhu.community.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 上门回收预约单。
 *
 * <p>为什么不复用求助（u_r_aid_request）：求助是「我缺什么、谁来帮」，
 * 回收是「我有废品、谁来收」，字段完全不同（品类、估重、上门时段、预估金额），
 * 硬塞进求助表会让那张表的语义彻底糊掉。
 */
@Getter
@Setter
@Entity
@Table(name = "u_r_recycle_order")
public class RecycleOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** 冗余昵称快照：Feign 取不到用户信息时兜底渲染。 */
    @Column(nullable = false, length = 50)
    private String userName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecycleCategory category;

    /** 估重，允许为空：居民常常只有「大概一麻袋」的概念，由师傅上门现场称重。 */
    @Column(precision = 8, scale = 2)
    private BigDecimal weightKg;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(nullable = false, length = 20)
    private String contactPhone;

    /** 只约到天，具体时段看 appointSlot。 */
    @Column(nullable = false)
    private LocalDate appointDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecycleSlot appointSlot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecycleStatus status;

    /** 下单时按「估重 × 品类单价」算出的参考金额，不是结算依据。 */
    @Column(precision = 10, scale = 2)
    private BigDecimal estimatedAmount;

    @Column(length = 200)
    private String remark;

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
            status = RecycleStatus.PENDING;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
