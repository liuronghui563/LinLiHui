package com.chengqu.huzhu.security;

import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.user.entity.User;
import com.chengqu.huzhu.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByPhone(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
        return toLoginUser(user);
    }

    public LoginUser loadById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BizException(401, "用户不存在或已注销"));
        return toLoginUser(user);
    }

    private LoginUser toLoginUser(User user) {
        return new LoginUser(
                user.getId(),
                user.getPhone(),
                user.getNickname(),
                user.getPasswordHash(),
                user.getRole(),
                Boolean.TRUE.equals(user.getEnabled())
        );
    }
}
