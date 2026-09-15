package com.chengqu.huzhu.community.repository;

import com.chengqu.huzhu.community.entity.CircleMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CircleMemberRepository extends JpaRepository<CircleMember, Long> {

    Optional<CircleMember> findByCircleIdAndUserId(Long circleId, Long userId);

    boolean existsByCircleIdAndUserId(Long circleId, Long userId);

    /**
     * 批量查询当前用户在给定圈子集合中的成员记录。
     *
     * <p>圈子列表要给每条圈子标 joined，逐条 exists 就是 N+1；
     * 这里一次查完，列表接口的跨表查询数固定为 1。
     */
    List<CircleMember> findByUserIdAndCircleIdIn(Long userId, Collection<Long> circleIds);

    /** 「我加入的圈子」分页，按加入时间倒序（排序由 Pageable 传入）。 */
    Page<CircleMember> findByUserId(Long userId, Pageable pageable);

    List<CircleMember> findByCircleIdOrderByJoinedAtAsc(Long circleId);
}
