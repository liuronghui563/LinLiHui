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
        // 第二个参数从 true 改成 false：这一个布尔值曾经是「绕过校园门禁的后门」。
        // 演示账号直接带着 student=true 落库，而这个账号在全新库上没有任何认证记录，
        // 于是「只有通过学生认证才能进入校园」这条规则在演示环境里形同不存在。
        // 现在演示账号需要走一遍真实链路：用 13900000000 提交认证，
        // 再用 13800000000（管理员）在管理台审核通过。
        createIfAbsent("13800000000", "Admin@123", "系统管理员", RoleType.ADMIN, false);
        createIfAbsent("13900000000", "User@123", "邻里同学", RoleType.USER, false);
        log.info("[初始化] MySQL 表 u_r_sys_user | 账号就绪：13800000000/Admin@123 , 13900000000/User@123");
        log.info("[初始化] 演示账号默认不是学生：进入「校园」需先提交学生认证，由 13800000000 在管理台审核");
        log.info("[初始化] 当前用户数={}", userRepository.count());
    }

    private void createIfAbsent(String phone, String rawPassword, String nickname, RoleType role, boolean student) {
        userRepository.findByPhone(phone).orElseGet(() -> {
            User created = userRepository.save(User.builder()
                    .phone(phone)
                    .passwordHash(passwordEncoder.encode(rawPassword))
                    .nickname(nickname)
                    .bio(role == RoleType.ADMIN ? "负责社区秩序与互助协调。" : "城区大学在读，热心邻里互助。")
                    .avatar(null)
                    .role(role)
                    .gender(Gender.UNKNOWN)
                    .presenceStatus(PresenceStatus.OFFLINE)
                    .city("城区")
                    .neighborhood("梧桐里")
                    // 学校/专业/年级不再由这里预置：它们是学生认证审核通过时
                    // 从申请里写回的资料，预置一份会让「资料里有学校但没通过认证」
                    // 这种自相矛盾的状态出现在演示环境里。
                    .student(student)
                    .enabled(true)
                    .build());
            log.info("[初始化] 创建用户 phone={}, role={}, id={}", phone, role, created.getId());
            return created;
        });
    }
}
