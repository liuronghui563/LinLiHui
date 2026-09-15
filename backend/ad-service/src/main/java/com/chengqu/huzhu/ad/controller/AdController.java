package com.chengqu.huzhu.ad.controller;

import com.chengqu.huzhu.ad.dto.AdApplicationRequest;
import com.chengqu.huzhu.ad.dto.AdApplicationResponse;
import com.chengqu.huzhu.ad.dto.AdBannerResponse;
import com.chengqu.huzhu.ad.dto.CreateAdRequest;
import com.chengqu.huzhu.ad.dto.ReviewAdRequest;
import com.chengqu.huzhu.ad.dto.UpdateAdRequest;
import com.chengqu.huzhu.ad.service.AdService;
import com.chengqu.huzhu.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ad")
@RequiredArgsConstructor
public class AdController {

    private final AdService adService;

    @GetMapping("/carousel")
    public ApiResponse<List<AdBannerResponse>> carousel() {
        return ApiResponse.ok(adService.carousel());
    }

    @GetMapping("/list")
    public ApiResponse<List<AdBannerResponse>> list() {
        return ApiResponse.ok(adService.listAll());
    }

    @PostMapping
    public ApiResponse<AdBannerResponse> create(@Valid @RequestBody CreateAdRequest request) {
        return ApiResponse.ok("创建成功", adService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdBannerResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody UpdateAdRequest request) {
        return ApiResponse.ok("更新成功", adService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adService.delete(id);
        return ApiResponse.okMessage("删除成功");
    }

    @PostMapping("/{id}/click")
    public ApiResponse<AdBannerResponse> click(@PathVariable Long id) {
        return ApiResponse.ok(adService.click(id));
    }

    // ------------------------------------------------------------------
    // 广告位申请与审核
    //
    // 申请开放给所有登录用户；审核只有管理员能做（规则见 SecurityConfig，
    // /api/ad/applications/** 除 mine 与提交本身外都要求 ADMIN）。
    // ------------------------------------------------------------------

    /**
     * 提交广告位申请：落库为 PENDING，管理员通过后才进首页轮播。
     *
     * <p>进来之前先过「广告位资质」这道闸门：没有 APPROVED 的资质会拿到 403
     * （判定在 AdService.apply 里，因为那是业务数据的结论，不是静态的 URL 权限）。
     */
    @PostMapping("/applications")
    public ApiResponse<AdApplicationResponse> apply(@Valid @RequestBody AdApplicationRequest request) {
        return ApiResponse.ok("已提交申请，等待管理员审核", adService.apply(request));
    }

    /** 我的申请：含待审 / 已通过 / 已驳回与审核意见 */
    @GetMapping("/applications/mine")
    public ApiResponse<List<AdApplicationResponse>> myApplications() {
        return ApiResponse.ok(adService.myApplications());
    }

    /**
     * 修改自己的广告。
     *
     * <p>「改了就要重新审」：已通过的会退回待审并**立刻从首页轮播撤下**，
     * 通过后再上；被驳回的改完重试。
     */
    @PutMapping("/applications/{id}")
    public ApiResponse<AdApplicationResponse> updateApplication(@PathVariable Long id,
                                                               @Valid @RequestBody AdApplicationRequest request) {
        return ApiResponse.ok("已提交修改，等待管理员重新审核", adService.updateApplication(id, request));
    }

    /** 撤回自己的待审申请 */
    @DeleteMapping("/applications/{id}")
    public ApiResponse<Void> withdraw(@PathVariable Long id) {
        adService.withdraw(id);
        return ApiResponse.okMessage("已撤回申请");
    }

    /** 管理端：待审列表（带申请人昵称） */
    @GetMapping("/applications/pending")
    public ApiResponse<List<AdApplicationResponse>> pendingApplications() {
        return ApiResponse.ok(adService.pendingApplications());
    }

    /** 管理端：全部广告，用于审核通过之后的日常管理（上下架 / 排序 / 改文案 / 删除） */
    @GetMapping("/applications/all")
    public ApiResponse<List<AdApplicationResponse>> allApplications() {
        return ApiResponse.ok(adService.allApplications());
    }

    /** 管理端：通过。通过即上线，首页轮播缓存同时失效 */
    @PostMapping("/applications/{id}/approve")
    public ApiResponse<AdApplicationResponse> approve(@PathVariable Long id,
                                                     @RequestBody(required = false) ReviewAdRequest request) {
        return ApiResponse.ok("已通过，广告将出现在首页轮播", adService.approve(id, noteOf(request)));
    }

    /** 管理端：驳回，可附原因 */
    @PostMapping("/applications/{id}/reject")
    public ApiResponse<AdApplicationResponse> reject(@PathVariable Long id,
                                                    @RequestBody(required = false) ReviewAdRequest request) {
        return ApiResponse.ok("已驳回", adService.reject(id, noteOf(request)));
    }

    private static String noteOf(ReviewAdRequest request) {
        return request == null ? null : request.getNote();
    }
}
