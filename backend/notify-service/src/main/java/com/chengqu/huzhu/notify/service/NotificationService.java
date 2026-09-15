package com.chengqu.huzhu.notify.service;

import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.common.security.SecurityUtils;
import com.chengqu.huzhu.notify.dto.CreateNotificationRequest;
import com.chengqu.huzhu.notify.dto.NotificationResponse;
import com.chengqu.huzhu.notify.dto.UnreadCountResponse;
import com.chengqu.huzhu.notify.entity.Notification;
import com.chengqu.huzhu.notify.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * 我的通知列表。
     *
     * <p>排序由 Controller 的 {@code @PageableDefault} 固定为 createdAt 倒序，
     * 与 (user_id, created_at) 索引的顺序一致，避免文件排序。
     */
    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(boolean unreadOnly, Pageable pageable) {
        Long userId = SecurityUtils.currentUser().getId();
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByUserIdAndReadAtIsNull(userId, pageable)
                : notificationRepository.findByUserId(userId, pageable);
        log.info("[消息] 列表查询 userId={} unreadOnly={} page={} size={} total={}",
                userId, unreadOnly, pageable.getPageNumber(), pageable.getPageSize(), page.getTotalElements());
        return page.map(NotificationResponse::from);
    }

    /** 当前登录用户的未读数。 */
    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount() {
        return unreadCount(SecurityUtils.currentUser().getId());
    }

    /**
     * 指定用户的未读数（内部接口复用）。
     *
     * <p>只走 count 查询，不把列表拉回来数——角标可能被前端高频轮询。
     */
    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(Long userId) {
        long count = notificationRepository.countByUserIdAndReadAtIsNull(userId);
        log.info("[消息] 未读统计 userId={} count={}", userId, count);
        return new UnreadCountResponse(count);
    }

    /**
     * 创建一条通知，供各业务服务在关键动作后调用。
     *
     * <p>刻意不做「同一对象短时间内去重」之类的合并：通知是用户可见的事实记录，
     * 少发一条比多发一条更难被发现和排查。
     */
    @Transactional
    public NotificationResponse create(CreateNotificationRequest request) {
        Notification notification = new Notification();
        notification.setUserId(request.getUserId());
        notification.setType(request.getType());
        notification.setTitle(request.getTitle().trim());
        notification.setContent(blankToNull(request.getContent()));
        notification.setRefType(blankToNull(request.getRefType()));
        notification.setRefId(request.getRefId());
        notification.setActorId(request.getActorId());
        notification.setActorName(blankToNull(request.getActorName()));
        notification.setLink(normalizeLink(request.getLink()));
        Notification saved = notificationRepository.save(notification);

        NotificationResponse response = NotificationResponse.from(saved);
        if (response.getLink() == null && request.getType() != null) {
            // 不阻断写入：通知本身是有效的事实，缺的只是跳转目标；
            // 但调用方大概率漏传了 refId/actorId，留一条 warn 便于定位。
            log.warn("[消息] 无法生成跳转链接 type={} userId={} refId={} actorId={}",
                    request.getType(), request.getUserId(), request.getRefId(), request.getActorId());
        }
        log.info("[消息] 创建成功 id={} userId={} type={}", saved.getId(), saved.getUserId(), saved.getType());
        return response;
    }

    /**
     * 校验调用方指定的跳转路径，只接受站内相对路径。
     *
     * <p>为什么在服务端收口：这个字符串由上游透传、由前端直接当路由目标使用，
     * 若允许 http(s):// 或协议相对的 //host，通知就成了一个可以把用户带出站外的跳转口子。
     * 本项目所有落地页都在同一前端应用内，因此以单个 "/" 开头即可覆盖全部合法用法。
     *
     * <p>不合规的链接按「没传」处理（返回 null，由 NotificationType 兜底生成），
     * 而不是抛异常让整条通知写入失败：提醒本身比链接更重要，且兜底路径通常也能用。
     */
    private static String normalizeLink(String link) {
        String trimmed = blankToNull(link);
        if (trimmed == null) {
            return null;
        }
        if (!trimmed.startsWith("/") || trimmed.startsWith("//")) {
            log.warn("[消息] 跳转链接不是站内相对路径，按未传处理 link={}", trimmed);
            return null;
        }
        return trimmed;
    }

    /**
     * 标记单条已读。重复请求按幂等处理：前端可能因为网络重试而提交两次，
     * 第二次不应该报错，也不应该刷新已读时间（否则「已读时间」会被最后一次点击污染）。
     */
    @Transactional
    public void markRead(Long id) {
        Long userId = SecurityUtils.currentUser().getId();
        Notification notification = requireMine(id, userId);
        if (notification.getReadAt() != null) {
            log.info("[消息] 重复标记已读，忽略 id={} userId={}", id, userId);
            return;
        }
        notification.setReadAt(LocalDateTime.now());
        notificationRepository.save(notification);
        log.info("[消息] 标记已读 id={} userId={}", id, userId);
    }

    /** 全部标记已读，返回本次真正被标记的条数（0 表示本来就没有未读）。 */
    @Transactional
    public long markAllRead() {
        Long userId = SecurityUtils.currentUser().getId();
        int updated = notificationRepository.markAllRead(userId, LocalDateTime.now());
        log.info("[消息] 全部标记已读 userId={} updated={}", userId, updated);
        return updated;
    }

    /** 删除自己的某条通知（不区分已读未读）。 */
    @Transactional
    public void delete(Long id) {
        Long userId = SecurityUtils.currentUser().getId();
        Notification notification = requireMine(id, userId);
        notificationRepository.delete(notification);
        log.info("[消息] 删除 id={} userId={} type={}", id, userId, notification.getType());
    }

    private Notification requireMine(Long id, Long userId) {
        return notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BizException(404, "通知不存在"));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
