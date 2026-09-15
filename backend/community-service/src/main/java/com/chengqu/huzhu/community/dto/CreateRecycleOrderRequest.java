package com.chengqu.huzhu.community.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 预约上门回收。
 *
 * <p>品类与时段用字符串接收、在 Service 里走枚举白名单解析：
 * 直接用枚举接收时非法值的报错信息是 Spring 的类型转换异常，
 * 前端拿到的是「Failed to convert」，不如「回收品类无效」可读。
 */
@Data
public class CreateRecycleOrderRequest {

    /** 见 RecycleCategory：PAPER / PLASTIC / METAL / CLOTHES / APPLIANCE / OTHER */
    @NotBlank(message = "请选择回收品类")
    private String category;

    /** 估重，可留空由师傅上门称重 */
    @DecimalMin(value = "0.01", message = "重量需大于 0")
    @Digits(integer = 6, fraction = 2, message = "重量最多两位小数")
    private BigDecimal weightKg;

    @Size(max = 500, message = "描述最多500字")
    private String description;

    @NotBlank(message = "地址不能为空")
    @Size(max = 200, message = "地址最多200字")
    private String address;

    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式错误")
    private String contactPhone;

    @NotNull(message = "请选择上门日期")
    @FutureOrPresent(message = "上门日期不能早于今天")
    private LocalDate appointDate;

    /** 见 RecycleSlot：MORNING / AFTERNOON / EVENING */
    @NotBlank(message = "请选择上门时段")
    private String appointSlot;

    @Size(max = 200, message = "备注最多200字")
    private String remark;
}
