package com.chengqu.huzhu.ad.controller;

import com.chengqu.huzhu.ad.dto.AdQualificationRequest;
import com.chengqu.huzhu.ad.dto.AdQualificationResponse;
import com.chengqu.huzhu.ad.dto.MyQualificationResponse;
import com.chengqu.huzhu.ad.service.AdQualificationService;
import com.chengqu.huzhu.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 广告位资质（用户侧）：所有登录用户都能提交与查看自己的资质。
 *
 * <p>注意路径是单数的 {@code /api/ad/qualification}，管理侧是复数的
 * {@code /api/ad/qualifications}，两者只差一个字母 {@code s}——
 * 规则顺序见 {@code SecurityConfig}，改任一条都要同时看一眼另一条。
 */
@RestController
@RequestMapping("/api/ad/qualification")
@RequiredArgsConstructor
public class AdQualificationController {

    private final AdQualificationService qualificationService;

    /** 提交资质申请：落库为 PENDING，管理员通过后才有资格提交广告位申请 */
    @PostMapping
    public ApiResponse<AdQualificationResponse> submit(@Valid @RequestBody AdQualificationRequest request) {
        return ApiResponse.ok("已提交资质申请，等待管理员审核", qualificationService.submit(request));
    }

    /** 我的资质：状态 + 最新一条明细 + 能否投广告的结论 */
    @GetMapping("/mine")
    public ApiResponse<MyQualificationResponse> mine() {
        return ApiResponse.ok(qualificationService.mine());
    }

    /**
     * 修改并重新提交。
     *
     * <p>待审和已驳回都能改（材料填错不必先撤回再重填），改完回到待审；
     * 已通过的不允许改——资质是管理员的结论，不是用户可以自己改的字段。
     */
    @PutMapping("/{id}")
    public ApiResponse<AdQualificationResponse> update(@PathVariable Long id,
                                                      @Valid @RequestBody AdQualificationRequest request) {
        return ApiResponse.ok("已重新提交，等待管理员审核", qualificationService.update(id, request));
    }

    /** 撤回自己的待审资质申请 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> withdraw(@PathVariable Long id) {
        qualificationService.withdraw(id);
        return ApiResponse.okMessage("已撤回资质申请");
    }
}
