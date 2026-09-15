package com.chengqu.huzhu.ad.controller;

import com.chengqu.huzhu.ad.dto.AdQualificationResponse;
import com.chengqu.huzhu.ad.dto.ReviewAdRequest;
import com.chengqu.huzhu.ad.entity.AdQualificationStatus;
import com.chengqu.huzhu.ad.service.AdQualificationService;
import com.chengqu.huzhu.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 广告位资质（管理侧）：审核谁有资格投广告。
 *
 * <p>单独一个 Controller 而不是塞进 {@code AdQualificationController}：
 * 两边的路径前缀（单数 / 复数）与权限（登录 / ADMIN）都不同，
 * 合成一个类就得在每个方法上写全路径、还容易把权限看串行。
 */
@RestController
@RequestMapping("/api/ad/qualifications")
@RequiredArgsConstructor
public class AdQualificationAdminController {

    private final AdQualificationService qualificationService;

    /** 资质列表：status 省略即全部，按提交时间正序（先来先审），带账号昵称 */
    @GetMapping
    public ApiResponse<List<AdQualificationResponse>> list(
            @RequestParam(required = false) AdQualificationStatus status) {
        return ApiResponse.ok(qualificationService.list(status));
    }

    /**
     * 通过：该用户随即获得提交广告位申请的资格。
     *
     * <p>只改资质这一行，不回头动任何一条广告：已上线的广告本来就审过了，
     * 不该因为一次资质复核而上下架。
     */
    @PostMapping("/{id}/approve")
    public ApiResponse<AdQualificationResponse> approve(@PathVariable Long id,
                                                       @RequestBody(required = false) ReviewAdRequest request) {
        return ApiResponse.ok("已通过，该用户现在可以提交广告位申请",
                qualificationService.approve(id, noteOf(request)));
    }

    /** 驳回：可附原因，用户会在「我的资质」里看到，并按此修改后重新提交 */
    @PostMapping("/{id}/reject")
    public ApiResponse<AdQualificationResponse> reject(@PathVariable Long id,
                                                      @RequestBody(required = false) ReviewAdRequest request) {
        return ApiResponse.ok("已驳回", qualificationService.reject(id, noteOf(request)));
    }

    private static String noteOf(ReviewAdRequest request) {
        return request == null ? null : request.getNote();
    }
}
