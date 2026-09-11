package com.chengqu.huzhu.config;

import com.chengqu.huzhu.user.entity.Gender;
import com.chengqu.huzhu.user.entity.PresenceStatus;
import com.chengqu.huzhu.user.entity.RoleType;
import com.chengqu.huzhu.user.entity.User;
import com.chengqu.huzhu.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        createIfAbsent("13800000000", "Admin@123", "系统管理员", RoleType.ADMIN, false);
        createIfAbsent("13900000000", "User@123", "邻里同学", RoleType.USER, true);
        log.info("[初始化] MySQL 表 u_r_sys_user | 账号就绪：13800000000/Admin@123 , 13900000000/User@123");
        log.info("[初始化] 当前用户数={}", userRepository.count());
    }

    private void createIfAbsent(String phone, String rawPassword, String nickname, RoleType role, boolean student) {
        userRepository.findByPhone(phone).orElseGet(() -> {
            User created = userRepository.save(User.builder()
                    .phone(phone)
                    .passwordHash(passwordEncoder.encode(rawPassword))
                    .nickname(nickname)
                    .bio(role == RoleType.ADMIN ? "负责社区秩序与互助协调。" : "城区大学在读，热心邻里互助。")
                    .avatar("https://picsum.photos/seed/user-" + phone + "/200/200")
                    .role(role)
                    .gender(Gender.UNKNOWN)
                    .presenceStatus(PresenceStatus.OFFLINE)
                    .city("城区")
                    .neighborhood("梧桐里")
                    .school(student ? "城区大学" : null)
                    .major(student ? "计算机科学" : null)
                    .grade(student ? "大三" : null)
                    .student(student)
                    .enabled(true)
                    .build());
            log.info("[初始化] 创建用户 phone={}, role={}, id={}", phone, role, created.getId());
            return created;
        });
    }
}
