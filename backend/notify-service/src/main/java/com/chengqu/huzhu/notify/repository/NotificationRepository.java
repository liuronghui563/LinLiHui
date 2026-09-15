package com.chengqu.huzhu.notify.repository;

import com.chengqu.huzhu.notify.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserId(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadAtIsNull(Long userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    /**
     * 按「id + 接收者」查询，而不是先 findById 再比对 userId。
     *
     * <p>这样查不到就统一按「通知不存在」返回，不区分「不存在」与「别人的」，
     * 避免用 403/404 的差异逐个探测其他用户收到过哪些通知。
     */
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /**
     * 全部标记已读。
     *
     * <p>用一条批量 UPDATE 而不是把未读列表拉回内存逐条改：未读可能有上千条，
     * 而且并发下逐条改容易产生大量无意义的 UPDATE。
     *
     * @return 实际被标记的条数
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.readAt = :readAt where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
}
