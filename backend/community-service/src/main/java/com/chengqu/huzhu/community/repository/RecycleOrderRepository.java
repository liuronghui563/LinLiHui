package com.chengqu.huzhu.community.repository;

import com.chengqu.huzhu.community.entity.RecycleOrder;
import com.chengqu.huzhu.community.entity.RecycleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecycleOrderRepository extends JpaRepository<RecycleOrder, Long> {

    /** 我的回收订单（全部状态）。 */
    Page<RecycleOrder> findByUserId(Long userId, Pageable pageable);

    /** 我的回收订单（按状态筛选），命中 (user_id, created_at) 索引后的等值过滤。 */
    Page<RecycleOrder> findByUserIdAndStatus(Long userId, RecycleStatus status, Pageable pageable);

    /** 管理台看板统计，命中 (status, appoint_date) 索引前缀。 */
    long countByStatus(RecycleStatus status);
}
