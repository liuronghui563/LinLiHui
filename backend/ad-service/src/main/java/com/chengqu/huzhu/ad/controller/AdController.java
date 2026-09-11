package com.chengqu.huzhu.ad.controller;

import com.chengqu.huzhu.ad.dto.AdBannerResponse;
import com.chengqu.huzhu.ad.dto.CreateAdRequest;
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
}
