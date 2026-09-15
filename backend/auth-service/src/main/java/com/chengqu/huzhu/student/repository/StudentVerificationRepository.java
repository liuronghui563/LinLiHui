package com.chengqu.huzhu.student.repository;

import com.chengqu.huzhu.student.entity.StudentVerification;
import com.chengqu.huzhu.student.entity.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentVerificationRepository extends JpaRepository<StudentVerification, Long> {

    /**
     * 取某人最近的一条申请，用于「我的认证状态」。
     *
     * <p>按 id 倒序而不是 created_at 倒序：同一次请求内写入的两行时间戳可能相同
     * （DATETIME(6) 也可能撞上），id 是唯一能确定「哪条更晚」的排序键。
     */
    Optional<StudentVerification> findFirstByUserIdOrderByIdDesc(Long userId);

    /** 「同一用户至多一条 PENDING」的判据，提交前先问一次 */
    boolean existsByUserIdAndStatus(Long userId, VerificationStatus status);

    /**
     * 用 id 查「我的某条申请」。
     *
     * <p>查询条件里带上 userId，而不是查出来再比对：这样「不是你的」与「不存在」
     * 在数据层就是同一个结果（空），服务层统一抛 404，不必区分——见
     * {@code StudentVerificationService.requireOwn}。
     */
    Optional<StudentVerification> findByIdAndUserId(Long id, Long userId);

    /** 管理端列表：按提交时间升序，先来先审 */
    List<StudentVerification> findByStatusOrderByCreatedAtAsc(VerificationStatus status);

    List<StudentVerification> findAllByOrderByCreatedAtAsc();
}
