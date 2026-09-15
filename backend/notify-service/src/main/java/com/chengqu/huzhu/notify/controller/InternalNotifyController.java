package com.chengqu.huzhu.notify.controller;

import com.chengqu.huzhu.common.api.ApiResponse;
import com.chengqu.huzhu.notify.dto.CreateNotificationRequest;
import com.chengqu.huzhu.notify.dto.NotificationResponse;
import com.chengqu.huzhu.notify.dto.UnreadCountResponse;
import com.chengqu.huzhu.notify.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息通知的内部接口，供其他服务通过 {@code NotifyApiClient}（Feign）调用。
 *
 * <p>路径不带 {@code /api} 前缀，网关不路由，外部无法访问；
 * 本服务不主动调用下游，因此这里是被调用方，只读当前令牌的合法性。
 */
@Slf4j
@RestController
@RequestMapping("/internal/notify")
@RequiredArgsConstructor
public class InternalNotifyController {

    private final NotificationService notificationService;

    /** 创建一条通知，返回落库后的通知（含 id、createdAt 与跳转链接）。 */
    @PostMapping
    public ApiResponse<NotificationResponse> create(@Valid @RequestBody CreateNotificationRequest request) {
        return ApiResponse.ok("通知已创建", notificationService.create(request));
    }

    /** 指定用户的未读数，供后续聚合场景（如个人主页角标）使用。 */
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount(@RequestParam("userId") Long userId) {
        return ApiResponse.ok(notificationService.unreadCount(userId));
    }
}
