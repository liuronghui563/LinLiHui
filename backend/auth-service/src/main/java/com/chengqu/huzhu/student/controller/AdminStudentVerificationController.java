package com.chengqu.huzhu.student.controller;

import com.chengqu.huzhu.common.api.ApiResponse;
import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.student.dto.ReviewStudentVerificationRequest;
import com.chengqu.huzhu.student.dto.StudentVerificationResponse;
import com.chengqu.huzhu.student.entity.VerificationStatus;
import com.chengqu.huzhu.student.service.StudentVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

/**
 * 学生认证（管理侧）。
 *
 * <p>路径落在 {@code /api/admin/**} 下，已被 SecurityConfig 的
 * {@code hasRole("ADMIN")} 覆盖；类上的 {@code @PreAuthorize} 是第二道，
 * 与 AdminController 同一做法——将来有人调整路径规则时，权限不会跟着一起松掉。
 *
 * <p>三个接口都不再传申请人 id：审核对象就是申请本身，申请人从申请里带出来，
 * 免得出现「申请是 A 的、却按 B 的账号去授权」这种参数错配。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/student")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminStudentVerificationController {

    private final StudentVerificationService studentVerificationService;

    /**
     * 认证申请列表，按提交时间升序（先来先审）。
     *
     * @param status PENDING / APPROVED / REJECTED，省略或留空表示全部
     */
    @GetMapping("/verifications")
    public ApiResponse<List<StudentVerificationResponse>> list(
            @RequestParam(value = "status", required = false) String status) {
        return ApiResponse.ok(studentVerificationService.list(parseStatus(status)));
    }

    /** 通过：置 user.student = true，并把学校 / 专业 / 年级写回用户资料 */
    @PostMapping("/verifications/{id}/approve")
    public ApiResponse<StudentVerificationResponse> approve(@PathVariable Long id,
                                                            @RequestBody(required = false) ReviewStudentVerificationRequest request) {
        return ApiResponse.ok("已通过学生认证", studentVerificationService.approve(id, noteOf(request)));
    }

    /** 驳回：可附原因，用户会在「我的认证状态」里看到并据此修改 */
    @PostMapping("/verifications/{id}/reject")
    public ApiResponse<StudentVerificationResponse> reject(@PathVariable Long id,
                                                           @RequestBody(required = false) ReviewStudentVerificationRequest request) {
        return ApiResponse.ok("已驳回", studentVerificationService.reject(id, noteOf(request)));
    }

    /**
     * 手写解析而不是把参数声明成 {@link VerificationStatus}。
     *
     * <p>枚举参数遇到 {@code status=FOO} 会抛 MethodArgumentTypeMismatchException，
     * 落到兜底处理器变成 500「系统繁忙，请稍后重试」——一个拼错的状态值被报成服务端故障，
     * 管理员只会反复重试。这里显式转成 400 加一句能看懂的话。
     */
    private static VerificationStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return VerificationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException("状态取值不合法，只能是 PENDING / APPROVED / REJECTED");
        }
    }

    private static String noteOf(ReviewStudentVerificationRequest request) {
        return request == null ? null : request.getNote();
    }
}
