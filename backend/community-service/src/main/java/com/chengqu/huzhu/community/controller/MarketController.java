package com.chengqu.huzhu.community.controller;

import com.chengqu.huzhu.common.api.ApiResponse;
import com.chengqu.huzhu.community.dto.CreateGoodsRequest;
import com.chengqu.huzhu.community.dto.GoodsResponse;
import com.chengqu.huzhu.community.dto.GoodsStatusRequest;
import com.chengqu.huzhu.community.dto.UpdateGoodsRequest;
import com.chengqu.huzhu.community.service.MarketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

/**
 * 集市（二手闲置）接口。
 *
 * <p>全部需要登录：浏览也要求登录是本项目的统一策略（见 SecurityConfig），
 * 集市不做例外，避免出现匿名可枚举全站用户的入口。
 */
@RestController
@RequestMapping("/api/market/goods")
@RequiredArgsConstructor
public class MarketController {

    private final MarketService marketService;

    /**
     * 商品列表。status 不传时只返回在售商品。
     *
     * <p>刻意不返回卖家联系方式（contact / contactType 为 null）：
     * 列表可分页、可筛选，是最容易被脚本批量抓取的地方，
     * 联系方式只在 {@link #detail(Long)} 里返回。
     */
    @GetMapping("/list")
    public ApiResponse<Page<GoodsResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(marketService.list(status, category, keyword, pageable));
    }

    /**
     * 我发布的商品（含已售出、已下架）。必须声明在 /{id} 之前语义上更清晰，路径本身不冲突。
     *
     * <p>与列表一样不返回联系方式。
     */
    @GetMapping("/mine")
    public ApiResponse<Page<GoodsResponse>> mine(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(marketService.mine(pageable));
    }

    /** 商品详情，是联系方式唯一的出口。 */
    @GetMapping("/{id}")
    public ApiResponse<GoodsResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(marketService.detail(id));
    }

    @PostMapping
    public ApiResponse<GoodsResponse> create(@Valid @RequestBody CreateGoodsRequest request) {
        return ApiResponse.ok("发布成功", marketService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<GoodsResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateGoodsRequest request) {
        return ApiResponse.ok("更新成功", marketService.update(id, request));
    }

    /** 改状态：SOLD 表示已售出。 */
    @PostMapping("/{id}/status")
    public ApiResponse<GoodsResponse> changeStatus(@PathVariable Long id,
                                                   @Valid @RequestBody GoodsStatusRequest request) {
        return ApiResponse.ok("状态已更新", marketService.changeStatus(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        marketService.delete(id);
        return ApiResponse.okMessage("删除成功");
    }
}
