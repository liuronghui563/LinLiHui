package com.chengqu.huzhu.community.controller;

import com.chengqu.huzhu.common.api.ApiResponse;
import com.chengqu.huzhu.community.dto.CreateRecycleOrderRequest;
import com.chengqu.huzhu.community.dto.RecycleGuideResponse;
import com.chengqu.huzhu.community.dto.RecycleOrderResponse;
import com.chengqu.huzhu.community.service.RecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

/**
 * 上门回收接口。
 *
 * <p>全部接口都要求登录（见 SecurityConfig）：回收涉及用户的地址与手机号，
 * 前端 /recycle 路由同样是 requiresAuth，不做匿名例外。
 */
@RestController
@RequestMapping("/api/recycle")
@RequiredArgsConstructor
public class RecycleController {

    private final RecycleService recycleService;

    /** 可回收品类与计价说明。 */
    @GetMapping("/categories")
    public ApiResponse<RecycleGuideResponse> categories() {
        return ApiResponse.ok(recycleService.guide());
    }

    /** 我的回收订单。status 不传表示全部状态。 */
    @GetMapping("/orders")
    public ApiResponse<Page<RecycleOrderResponse>> myOrders(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(recycleService.myOrders(status, pageable));
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<RecycleOrderResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(recycleService.detail(id));
    }

    @PostMapping("/orders")
    public ApiResponse<RecycleOrderResponse> create(@Valid @RequestBody CreateRecycleOrderRequest request) {
        return ApiResponse.ok("预约成功", recycleService.create(request));
    }

    /** 取消预约：仅本人，且状态为待确认或已确认。 */
    @PostMapping("/orders/{id}/cancel")
    public ApiResponse<RecycleOrderResponse> cancel(@PathVariable Long id) {
        return ApiResponse.ok("已取消预约", recycleService.cancel(id));
    }
}
