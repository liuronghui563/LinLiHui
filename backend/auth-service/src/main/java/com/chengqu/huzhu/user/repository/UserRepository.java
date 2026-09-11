package com.chengqu.huzhu.user.repository;

import com.chengqu.huzhu.user.entity.RoleType;
import com.chengqu.huzhu.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPhone(String phone);

    Optional<User> findFirstByRoleAndEnabledTrue(RoleType role);

    boolean existsByPhone(String phone);
}
