package com.chengqu.huzhu.student.controller;

import com.chengqu.huzhu.common.api.ApiResponse;
import com.chengqu.huzhu.student.dto.StudentVerificationRequest;
import com.chengqu.huzhu.student.dto.StudentVerificationResponse;
import com.chengqu.huzhu.student.dto.StudentVerificationStatusResponse;
import com.chengqu.huzhu.student.service.StudentVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 学生认证（用户侧）。
 *
 * <p>四个接口都只要求登录，鉴权由 SecurityConfig 的 {@code anyRequest().authenticated()} 兜住：
 * 它们操作的全是「自己的」数据，越权在服务层按 404 挡掉，不需要额外的角色规则。
 */
@Slf4j
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentVerificationController {

    private final StudentVerificationService studentVerificationService;

    /** 提交认证申请：落库为 PENDING，管理员通过后 user.student 才为 true */
    @PostMapping("/verification")
    public ApiResponse<StudentVerificationResponse> submit(@Valid @RequestBody StudentVerificationRequest request) {
        return ApiResponse.ok("已提交认证申请，等待管理员审核", studentVerificationService.submit(request));
    }

    /**
     * 我的认证状态：含最近一条申请（可能为 null）与当前是否已认证。
     *
     * <p>{@code status} 可能是 {@code NONE}（从未提交过），见响应 DTO 的说明。
     */
    @GetMapping("/verification/mine")
    public ApiResponse<StudentVerificationStatusResponse> mine() {
        return ApiResponse.ok(studentVerificationService.mine());
    }

    /** 修改并重新提交：被驳回的改完重投，原来的审核意见会被清空 */
    @PutMapping("/verification/{id}")
    public ApiResponse<StudentVerificationResponse> resubmit(@PathVariable Long id,
                                                             @Valid @RequestBody StudentVerificationRequest request) {
        return ApiResponse.ok("已重新提交，等待管理员审核", studentVerificationService.update(id, request));
    }

    /** 撤回自己的待审申请 */
    @DeleteMapping("/verification/{id}")
    public ApiResponse<Void> withdraw(@PathVariable Long id) {
        studentVerificationService.withdraw(id);
        return ApiResponse.okMessage("已撤回认证申请");
    }
}
