package com.chengqu.huzhu.ad.repository;

import com.chengqu.huzhu.ad.entity.AdQualification;
import com.chengqu.huzhu.ad.entity.AdQualificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdQualificationRepository extends JpaRepository<AdQualification, Long> {

    /**
     * 闸门查询：这个用户有没有通过的资质。
     *
     * <p>用 {@code exists} 而不是取最新一条再判状态：一个用户正常只会有
     * 一条 APPROVED（通过后 {@code submit} 就被拒了），但历史 REJECTED 会累积多条，
     * 「最新一条」在不同排序下说法不一致，而「有没有通过过」是个明确的事实。
     */
    boolean existsByUserIdAndStatus(Long userId, AdQualificationStatus status);

    /** 我的资质：取最新一条，同毫秒提交时用 id 兜底排序，避免返回哪条看运气 */
    Optional<AdQualification> findFirstByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /** 管理端：按状态筛选，先来先审（status 为空时用下面那个方法取全部） */
    List<AdQualification> findByStatusOrderByCreatedAtAsc(AdQualificationStatus status);

    /** 管理端：全部资质 */
    List<AdQualification> findAllByOrderByCreatedAtAsc();
}
