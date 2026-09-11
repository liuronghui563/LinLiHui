package com.chengqu.huzhu.auth.service;

import com.chengqu.huzhu.auth.captcha.CaptchaService;
import com.chengqu.huzhu.auth.dto.*;
import com.chengqu.huzhu.auth.sms.SmsCodeService;
import com.chengqu.huzhu.auth.sms.SmsScene;
import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.common.security.JwtSupport;
import com.chengqu.huzhu.user.entity.Gender;
import com.chengqu.huzhu.user.entity.PresenceStatus;
import com.chengqu.huzhu.user.entity.RoleType;
import com.chengqu.huzhu.user.entity.User;
import com.chengqu.huzhu.user.repository.UserRepository;
import com.chengqu.huzhu.user.support.UserProfiles;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final CaptchaService captchaService;
    private final SmsCodeService smsCodeService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtSupport jwtSupport;

    public CaptchaPayload createCaptcha() {
        CaptchaPayload payload = captchaService.create();
        log.info("[鉴权] 获取验证码 captchaId={}", payload.getCaptchaId());
        return payload;
    }

    public int verifyCaptcha(CaptchaVerifyRequest request) {
        return captchaService.verifyHold(request.getCaptchaId(), request.getCaptchaCode());
    }

    public void sendSms(SendSmsRequest request) {
        captchaService.requireVerified(request.getCaptchaId(), request.getCaptchaCode());
        SmsScene scene = parseScene(request.getScene());

        if (scene == SmsScene.REGISTER && userRepository.existsByPhone(request.getPhone())) {
            throw new BizException("该手机号已注册");
        }
        if (scene == SmsScene.LOGIN && !userRepository.existsByPhone(request.getPhone())) {
            throw new BizException("该手机号尚未注册");
        }
        smsCodeService.sendCode(request.getPhone(), scene);
        log.info("[鉴权] 发送短信验证码 phone={}, scene={}", request.getPhone(), scene);
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        smsCodeService.verifyAndConsume(request.getPhone(), SmsScene.REGISTER, request.getSmsCode());
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new BizException("该手机号已注册");
        }
        String nickname = StringUtils.hasText(request.getNickname())
                ? request.getNickname().trim()
                : "邻里" + request.getPhone().substring(7);

        User user = User.builder()
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .nickname(nickname)
                .role(RoleType.USER)
                .gender(Gender.UNKNOWN)
                .presenceStatus(PresenceStatus.ONLINE)
                .student(false)
                .enabled(true)
                .build();
        userRepository.save(user);
        log.info("[鉴权] 注册成功 phone={}, userId={}, nickname={}", user.getPhone(), user.getId(), nickname);
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse loginByPassword(PasswordLoginRequest request) {
        captchaService.requireVerified(request.getCaptchaId(), request.getCaptchaCode());
        User user = userRepository.findByPhone(request.getPhone())
                .orElseThrow(() -> new BizException(401, "账号或密码错误"));
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BizException(403, "账号已被禁用");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("[鉴权] 密码登录失败 phone={}", request.getPhone());
            throw new BizException(401, "账号或密码错误");
        }
        captchaService.consume(request.getCaptchaId());
        log.info("[鉴权] 密码登录成功 phone={}, userId={}, role={}", user.getPhone(), user.getId(), user.getRole());
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse loginByInternal(InternalLoginRequest request) {
        if (!"8461".equals(request.getCode().trim())) {
            throw new BizException(403, "内部码错误");
        }
        User admin = userRepository.findFirstByRoleAndEnabledTrue(RoleType.ADMIN)
                .orElseThrow(() -> new BizException(404, "未找到可用的管理员账号"));
        log.info("[鉴权] 内部码登录成功 userId={}, phone={}", admin.getId(), admin.getPhone());
        return issueTokens(admin);
    }

    @Transactional
    public TokenResponse loginBySms(SmsLoginRequest request) {
        smsCodeService.verifyAndConsume(request.getPhone(), SmsScene.LOGIN, request.getSmsCode());
        User user = userRepository.findByPhone(request.getPhone())
                .orElseThrow(() -> new BizException(401, "该手机号尚未注册"));
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BizException(403, "账号已被禁用");
        }
        log.info("[鉴权] 短信登录成功 phone={}, userId={}", user.getPhone(), user.getId());
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request) {
        try {
            Claims claims = jwtSupport.parse(request.getRefreshToken());
            if (!jwtSupport.isRefreshToken(claims)) {
                throw new BizException(401, "无效的刷新令牌");
            }
            Long userId = Long.valueOf(claims.getSubject());
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BizException(401, "用户不存在"));
            if (!Boolean.TRUE.equals(user.getEnabled())) {
                throw new BizException(403, "账号已被禁用");
            }
            log.info("[鉴权] 刷新令牌成功 userId={}", userId);
            return issueTokens(user);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(401, "刷新令牌无效或已过期");
        }
    }

    public TokenResponse.UserProfile me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(401, "用户不存在"));
        log.info("[鉴权] 查询当前用户 userId={}, nickname={}", user.getId(), user.getNickname());
        return UserProfiles.of(user);
    }

    @Transactional
    public void logout(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setPresenceStatus(PresenceStatus.OFFLINE);
            userRepository.save(user);
        });
        log.info("[鉴权] 用户退出登录 userId={}", userId);
    }

    private TokenResponse issueTokens(User user) {
        PresenceStatus current = user.getPresenceStatus();
        if (current == null || current == PresenceStatus.OFFLINE) {
            user.setPresenceStatus(PresenceStatus.ONLINE);
            user = userRepository.save(user);
        }
        String access = jwtSupport.createAccessToken(
                user.getId(), user.getPhone(), user.getNickname(), user.getRole().name());
        String refresh = jwtSupport.createRefreshToken(user.getId());
        return TokenResponse.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .tokenType("Bearer")
                .expiresIn(jwtSupport.getAccessExpireSeconds())
                .user(UserProfiles.of(user))
                .build();
    }

    private SmsScene parseScene(String scene) {
        try {
            return SmsScene.valueOf(scene.trim().toUpperCase());
        } catch (Exception e) {
            throw new BizException("不支持的短信场景");
        }
    }
}
