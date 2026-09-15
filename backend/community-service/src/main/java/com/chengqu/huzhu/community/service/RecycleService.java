package com.chengqu.huzhu.community.service;

import com.chengqu.huzhu.api.dto.UserBrief;
import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.common.security.SecurityUtils;
import com.chengqu.huzhu.common.security.UserPrincipal;
import com.chengqu.huzhu.community.dto.CreateRecycleOrderRequest;
import com.chengqu.huzhu.community.dto.RecycleCategoryResponse;
import com.chengqu.huzhu.community.dto.RecycleGuideResponse;
import com.chengqu.huzhu.community.dto.RecycleOrderResponse;
import com.chengqu.huzhu.community.dto.RecycleStatsResponse;
import com.chengqu.huzhu.community.entity.RecycleCategory;
import com.chengqu.huzhu.community.entity.RecycleOrder;
import com.chengqu.huzhu.community.entity.RecycleSlot;
import com.chengqu.huzhu.community.entity.RecycleStatus;
import com.chengqu.huzhu.community.repository.RecycleOrderRepository;
import com.chengqu.huzhu.community.support.UserLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 上门回收业务逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecycleService {

    /** 计价口径说明。预估金额只是预期，不是结算依据，这句话必须跟着品类一起下发。 */
    private static final String PRICING_NOTE =
            "参考单价为基准回收价，实际金额以师傅上门称重、现场确认为准；旧家电按台评估。";

    private final RecycleOrderRepository recycleOrderRepository;
    private final UserLookup userLookup;

    /**
     * 可回收品类与计价说明。
     *
     * <p>需要登录：前端 /recycle 路由本身就是 requiresAuth，匿名请求到不了这里；
     * 做成真实匿名入口要同时放开路由并隐藏「我的预约」分区，不属于当前范围。
     * 见 {@link com.chengqu.huzhu.community.config.SecurityConfig}。
     */
    @Transactional(readOnly = true)
    public RecycleGuideResponse guide() {
        List<RecycleCategoryResponse> categories = Arrays.stream(RecycleCategory.values())
                .map(RecycleCategoryResponse::from)
                .toList();
        List<RecycleGuideResponse.SlotOption> slots = Arrays.stream(RecycleSlot.values())
                .map(RecycleGuideResponse::slotOf)
                .toList();
        log.info("[回收] 下发回收指南 品类={} 项, 时段={} 项", categories.size(), slots.size());
        return RecycleGuideResponse.builder()
                .pricingNote(PRICING_NOTE)
                .categories(categories)
                .slots(slots)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<RecycleOrderResponse> myOrders(String status, Pageable pageable) {
        Long userId = SecurityUtils.currentUser().getId();
        RecycleStatus parsedStatus = RecycleStatus.parseFilter(status);
        Page<RecycleOrder> page = parsedStatus == null
                ? recycleOrderRepository.findByUserId(userId, pageable)
                : recycleOrderRepository.findByUserIdAndStatus(userId, parsedStatus, pageable);
        Page<RecycleOrderResponse> result = toPage(page);
        log.info("[回收] 我的回收订单 userId={}, status={}, page={}, size={}, total={}",
                userId, parsedStatus, pageable.getPageNumber(), result.getNumberOfElements(), result.getTotalElements());
        return result;
    }

    @Transactional
    public RecycleOrderResponse create(CreateRecycleOrderRequest request) {
        UserPrincipal user = SecurityUtils.currentUser();
        RecycleCategory category = RecycleCategory.require(request.getCategory());
        RecycleSlot slot = RecycleSlot.require(request.getAppointSlot());

        RecycleOrder order = new RecycleOrder();
        order.setUserId(user.getId());
        order.setUserName(displayName(user));
        order.setCategory(category);
        order.setWeightKg(request.getWeightKg());
        order.setDescription(trimToNull(request.getDescription()));
        order.setAddress(request.getAddress().trim());
        order.setContactPhone(request.getContactPhone().trim());
        order.setAppointDate(request.getAppointDate());
        order.setAppointSlot(slot);
        order.setStatus(RecycleStatus.PENDING);
        // 预估金额在落库时算好并冻结：之后调价不应该让历史订单的预期金额发生变化
        order.setEstimatedAmount(estimate(category, request.getWeightKg()));
        order.setRemark(trimToNull(request.getRemark()));

        RecycleOrder saved = recycleOrderRepository.save(order);
        log.info("[回收] 预约成功 id={}, userId={}, category={}, appointDate={}, slot={}, estimatedAmount={}",
                saved.getId(), user.getId(), category, saved.getAppointDate(), slot, saved.getEstimatedAmount());
        return toResponse(saved);
    }

    @Transactional
    public RecycleOrderResponse cancel(Long id) {
        UserPrincipal user = SecurityUtils.currentUser();
        RecycleOrder order = requireOrder(id);
        if (!order.getUserId().equals(user.getId())) {
            throw new BizException(403, "仅本人可取消回收预约");
        }
        RecycleStatus status = order.getStatus();
        // 已上门完成的单不能取消，已取消的单不必再取消
        if (status != RecycleStatus.PENDING && status != RecycleStatus.CONFIRMED) {
            throw new BizException("当前状态不可取消（仅待确认或已确认可取消）");
        }
        order.setStatus(RecycleStatus.CANCELLED);
        RecycleOrder saved = recycleOrderRepository.save(order);
        log.info("[回收] 取消预约 id={}, userId={}, {} -> CANCELLED", id, user.getId(), status);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public RecycleOrderResponse detail(Long id) {
        UserPrincipal user = SecurityUtils.currentUser();
        RecycleOrder order = requireOrder(id);
        // 订单里有详细地址与手机号，只能本人或管理员看
        if (!order.getUserId().equals(user.getId()) && !isAdmin(user)) {
            throw new BizException(403, "无权查看该回收预约");
        }
        log.info("[回收] 预约详情 id={}, userId={}, viewerId={}", id, order.getUserId(), user.getId());
        return toResponse(order);
    }

    /** 管理台看板聚合用：待处理与已完成两个数。 */
    @Transactional(readOnly = true)
    public RecycleStatsResponse stats() {
        RecycleStatsResponse stats = RecycleStatsResponse.builder()
                .pending(recycleOrderRepository.countByStatus(RecycleStatus.PENDING))
                .done(recycleOrderRepository.countByStatus(RecycleStatus.DONE))
                .build();
        log.info("[回收] 平台统计 pending={}, done={}", stats.getPending(), stats.getDone());
        return stats;
    }

    // ------------------------------------------------------------------
    // 装配层
    // ------------------------------------------------------------------

    private Page<RecycleOrderResponse> toPage(Page<RecycleOrder> page) {
        return new PageImpl<>(toResponses(page.getContent()), page.getPageable(), page.getTotalElements());
    }

    private RecycleOrderResponse toResponse(RecycleOrder order) {
        return toResponses(List.of(order)).get(0);
    }

    private List<RecycleOrderResponse> toResponses(List<RecycleOrder> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<RecycleOrderResponse> result = new ArrayList<>(orders.size());
        for (RecycleOrder order : orders) {
            result.add(RecycleOrderResponse.from(order));
        }
        fillUserInfo(result);
        return result;
    }

    /** 通过 Feign 批量补全用户昵称与头像；无论一页多少条只调用 1 次。 */
    private void fillUserInfo(List<RecycleOrderResponse> responses) {
        Set<Long> userIds = new HashSet<>();
        for (RecycleOrderResponse response : responses) {
            if (response.getUserId() != null) {
                userIds.add(response.getUserId());
            }
        }
        Map<Long, UserBrief> users = userLookup.byIds(userIds);
        if (users.isEmpty()) {
            return;
        }
        for (RecycleOrderResponse response : responses) {
            UserBrief brief = users.get(response.getUserId());
            if (brief == null) {
                continue;
            }
            if (StringUtils.hasText(brief.nickname())) {
                response.setUserName(brief.nickname());
            }
            response.setUserAvatar(brief.avatar());
        }
    }

    /** 预估金额 = 估重 × 品类参考单价。任一缺失就留空，不编数字。 */
    private BigDecimal estimate(RecycleCategory category, BigDecimal weightKg) {
        BigDecimal unitPrice = category.getUnitPrice();
        if (weightKg == null || unitPrice == null) {
            return null;
        }
        return weightKg.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    private RecycleOrder requireOrder(Long id) {
        return recycleOrderRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "回收预约不存在"));
    }

    private String displayName(UserPrincipal user) {
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname();
        }
        if (StringUtils.hasText(user.getPhone())) {
            return user.getPhone();
        }
        return "用户" + user.getId();
    }

    private boolean isAdmin(UserPrincipal user) {
        String role = user.getRole();
        if (role == null) {
            return false;
        }
        if (role.startsWith("ROLE_")) {
            role = role.substring(5);
        }
        return "ADMIN".equals(role);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
