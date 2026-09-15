package com.chengqu.huzhu.community.config;

import com.chengqu.huzhu.community.entity.RecycleCategory;
import com.chengqu.huzhu.community.entity.RecycleOrder;
import com.chengqu.huzhu.community.entity.RecycleSlot;
import com.chengqu.huzhu.community.entity.RecycleStatus;
import com.chengqu.huzhu.community.repository.RecycleOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * 表为空时写入两条示例回收预约。
 *
 * <p>用户 id 与 auth-service 种子一致：2=邻里用户。
 * 上门日期取「明天/后天」而不是写死某个日期：写死的日期过几天就变成过去时间，
 * 与下单接口上的「上门日期不能早于今天」自相矛盾，会让人以为校验没生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecycleDataInitializer implements CommandLineRunner {

    private static final long SEED_USER_ID = 2L;
    private static final String SEED_USER_NAME = "邻里用户";

    private final RecycleOrderRepository recycleOrderRepository;

    @Override
    public void run(String... args) {
        if (recycleOrderRepository.count() > 0) {
            log.info("[回收] 表 u_r_recycle_order 已有数据 count={}，跳过种子", recycleOrderRepository.count());
            return;
        }

        RecycleCategory paper = RecycleCategory.PAPER;
        saveOrder(paper, new BigDecimal("12.50"), "攒了两周的纸箱和报纸，已压平捆好。",
                "城南花园 3 栋 502", LocalDate.now().plusDays(1), RecycleSlot.MORNING,
                RecycleStatus.PENDING, "单元门需要按门禁，到了打电话。");

        RecycleCategory clothes = RecycleCategory.CLOTHES;
        saveOrder(clothes, new BigDecimal("8.00"), "换季整理出来的旧衣物，已装袋。",
                "阳光里小区东门", LocalDate.now().plusDays(2), RecycleSlot.AFTERNOON,
                RecycleStatus.CONFIRMED, "放在东门门卫处也可以。");

        log.info("[回收] 已写入示例回收预约 2 条 -> 表 u_r_recycle_order, userId={}", SEED_USER_ID);
    }

    private void saveOrder(RecycleCategory category, BigDecimal weightKg, String description,
                           String address, LocalDate appointDate, RecycleSlot slot,
                           RecycleStatus status, String remark) {
        RecycleOrder order = new RecycleOrder();
        order.setUserId(SEED_USER_ID);
        order.setUserName(SEED_USER_NAME);
        order.setCategory(category);
        order.setWeightKg(weightKg);
        order.setDescription(description);
        order.setAddress(address);
        // 种子数据的手机号用测试号段，避免看起来像真实用户的号码
        order.setContactPhone("13800000002");
        order.setAppointDate(appointDate);
        order.setAppointSlot(slot);
        order.setStatus(status);
        // 与 RecycleService 同一套口径：估重 × 品类参考单价
        order.setEstimatedAmount(category.getUnitPrice() == null
                ? null
                : weightKg.multiply(category.getUnitPrice()).setScale(2, RoundingMode.HALF_UP));
        order.setRemark(remark);
        recycleOrderRepository.save(order);
    }
}
