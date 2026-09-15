package com.chengqu.huzhu.notify.controller;

import com.chengqu.huzhu.common.api.ApiResponse;
import com.chengqu.huzhu.notify.dto.NotificationResponse;
import com.chengqu.huzhu.notify.dto.UnreadCountResponse;
import com.chengqu.huzhu.notify.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内消息的对外接口，经网关 {@code /api/notify/**} 暴露。
 *
 * <p>所有接口都只操作「当前登录用户自己的通知」，用户身份一律取自 JWT，
 * 不从请求参数读取 userId（否则等于允许读别人的通知）。
 */
@RestController
@RequestMapping("/api/notify")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/list")
    public ApiResponse<Page<NotificationResponse>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(notificationService.list(unreadOnly, pageable));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount() {
        return ApiResponse.ok(notificationService.unreadCount());
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return ApiResponse.okMessage("已标记为已读");
    }

    @PostMapping("/read-all")
    public ApiResponse<Long> markAllRead() {
        return ApiResponse.ok("已全部标记为已读", notificationService.markAllRead());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        notificationService.delete(id);
        return ApiResponse.okMessage("删除成功");
    }
}
