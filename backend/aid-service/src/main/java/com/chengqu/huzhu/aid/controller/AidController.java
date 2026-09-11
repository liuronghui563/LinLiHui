package com.chengqu.huzhu.aid.controller;

import com.chengqu.huzhu.aid.dto.AidResponse;
import com.chengqu.huzhu.aid.dto.AidStatsResponse;
import com.chengqu.huzhu.aid.dto.CreateAidRequest;
import com.chengqu.huzhu.aid.dto.HelperReviewRequest;
import com.chengqu.huzhu.aid.dto.ScoreRequest;
import com.chengqu.huzhu.aid.dto.UpdateAidRequest;
import com.chengqu.huzhu.aid.entity.AidStatus;
import com.chengqu.huzhu.aid.service.AidService;
import com.chengqu.huzhu.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/aid")
@RequiredArgsConstructor
public class AidController {

    private final AidService aidService;

    @GetMapping("/list")
    public ApiResponse<Page<AidResponse>> list(
            @RequestParam(required = false) AidStatus status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean student,
            @RequestParam(required = false) String board,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(aidService.list(status, category, student, board, pageable));
    }

    @GetMapping("/stats")
    public ApiResponse<AidStatsResponse> stats() {
        return ApiResponse.ok(aidService.stats());
    }

    @GetMapping("/mine/published")
    public ApiResponse<Page<AidResponse>> myPublished(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(aidService.myPublished(pageable));
    }

    @GetMapping("/mine/helping")
    public ApiResponse<Page<AidResponse>> myHelping(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(aidService.myHelping(pageable));
    }

    @GetMapping("/user/{userId}/published")
    public ApiResponse<Page<AidResponse>> publishedByUser(
            @PathVariable Long userId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(aidService.publishedByUser(userId, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<AidResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(aidService.detail(id));
    }

    @PostMapping
    public ApiResponse<AidResponse> create(@Valid @RequestBody CreateAidRequest request) {
        return ApiResponse.ok("发布成功", aidService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AidResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateAidRequest request) {
        return ApiResponse.ok("更新成功", aidService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        aidService.delete(id);
        return ApiResponse.okMessage("删除成功");
    }

    @PostMapping("/{id}/accept")
    public ApiResponse<AidResponse> accept(@PathVariable Long id) {
        return ApiResponse.ok("接单成功", aidService.accept(id));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<AidResponse> complete(@PathVariable Long id) {
        return ApiResponse.ok("已完成", aidService.complete(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<AidResponse> cancel(@PathVariable Long id) {
        return ApiResponse.ok("已取消", aidService.cancel(id));
    }

    @PutMapping("/{id}/rating")
    public ApiResponse<AidResponse> rate(@PathVariable Long id, @Valid @RequestBody ScoreRequest request) {
        return ApiResponse.ok("评价已保存", aidService.rate(id, request));
    }

    @PutMapping("/{id}/helper-review")
    public ApiResponse<AidResponse> reviewHelper(@PathVariable Long id, @Valid @RequestBody HelperReviewRequest request) {
        return ApiResponse.ok("对帮助方的评价已保存", aidService.reviewHelper(id, request));
    }
}
