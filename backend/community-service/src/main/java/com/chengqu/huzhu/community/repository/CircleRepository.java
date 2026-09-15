package com.chengqu.huzhu.community.repository;

import com.chengqu.huzhu.community.entity.Circle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CircleRepository extends JpaRepository<Circle, Long> {

    /** 按圈子名模糊搜索。中文没有大小写问题，不做 IgnoreCase。 */
    Page<Circle> findByNameContaining(String keyword, Pageable pageable);

    /** 创建前的友好查重；真正的兜底是 u_r_circle 上的唯一键。 */
    boolean existsByName(String name);
}
