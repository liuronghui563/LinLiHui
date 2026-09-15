package com.chengqu.huzhu.auth.service;

import com.chengqu.huzhu.auth.captcha.CaptchaService;
import com.chengqu.huzhu.auth.dto.*;
import com.chengqu.huzhu.auth.sms.SmsCodeService;
import com.chengqu.huzhu.auth.sms.SmsScene;
import com.chengqu.huzhu.common.exception.BizException;
import com.chengqu.huzhu.common.redis.RedisKeys;
import com.chengqu.huzhu.common.redis.RedisRateLimiter;
import com.chengqu.huzhu.common.security.JwtSupport;
import com.chengqu.huzhu.common.security.TokenRevocationService;
import com.chengqu.huzhu.config.AppProperties;
import com.chengqu.huzhu.security.LoginUser;
import com.chengqu.huzhu.security.SecurityUtils;
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

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final CaptchaService captchaService;
    private final SmsCodeService smsCodeService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtSupport jwtSupport;
    private final AppProperties appProperties;
    private final RedisRateLimiter rateLimiter;
    private final TokenRevocationService tokenRevocationService;

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
        requireLoginAttemptAllowed(request.getPhone());
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

    /**
     * 手机号维度的登录频控。
     *
     * <p>网关侧的限流按 IP 计，攻击者换 IP 即可绕过；这里以手机号为维度再限一层，
     * 让撞库在单个账号上无法提速。Redis 不可用时自动放行（限流是保护措施，不应阻断正常登录）。
     */
    private void requireLoginAttemptAllowed(String phone) {
        if (!appProperties.getRateLimit().isEnabled() || !StringUtils.hasText(phone)) {
            return;
        }
        long remaining = rateLimiter.tryAcquire(
                RedisKeys.rateLimit("login-phone", phone),
                appProperties.getRateLimit().getLoginPerMinutePerPhone(),
                Duration.ofMinutes(1));
        if (remaining < 0) {
            log.warn("[鉴权] 登录尝试过于频繁，已限流 phone={}", phone);
            throw new BizException(429, "尝试过于频繁，请 1 分钟后再试");
        }
    }

    @Transactional
    public TokenResponse loginByInternal(InternalLoginRequest request) {
        String expected = appProperties.getInternalLoginCode();
        if (!StringUtils.hasText(expected)) {
            // 未配置内部码时直接关闭该登录方式，避免源码中的固定口令成为后门
            throw new BizException(403, "内部码登录未启用");
        }
        if (!expected.equals(request.getCode().trim())) {
            log.warn("[鉴权] 内部码登录失败");
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
            // 刷新令牌同样要被吊销机制拦住，否则「登出」之后还能靠它换回可用的访问令牌
            if (tokenRevocationService.isRevoked(claims)) {
                log.warn("[鉴权] 刷新令牌已被吊销，拒绝续期 sub={}", claims.getSubject());
                throw new BizException(401, "登录状态已失效，请重新登录");
            }
            Long userId = Long.valueOf(claims.getSubject());
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BizException(401, "用户不存在"));
            if (!Boolean.TRUE.equals(user.getEnabled())) {
                throw new BizException(403, "账号已被禁用");
            }
            String sessionId = claims.get(JwtSupport.CLAIM_SESSION_ID, String.class);
            log.info("[鉴权] 刷新令牌成功 userId={}, sid={}", userId, sessionId);
            return buildTokens(markOnline(user), sessionId);
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

    /**
     * 退出登录。
     *
     * <p>原实现只是把在线状态改成 OFFLINE，令牌本身仍然有效——JWT 无状态，
     * 签发后到过期前无法收回。现在按会话 ID（sid）吊销：一次登录签发的
     * access token 与 refresh token 共用同一个 sid，吊销后两者同时失效，
     * 因此既不会留下可续期的刷新令牌，四个业务服务也会立即拒绝该访问令牌。
     *
     * @param authorizationHeader 当前请求的 Authorization 头，用于取出待吊销会话
     * @param allDevices          为 true 时自增用户令牌版本，吊销该用户全部会话（退出所有设备）
     */
    @Transactional
    public void logout(String authorizationHeader, boolean allDevices) {
        LoginUser user = SecurityUtils.currentUser();
        String sessionId = resolveSessionId(authorizationHeader);
        if (sessionId != null) {
            tokenRevocationService.revokeSession(sessionId);
        }
        if (allDevices) {
            tokenRevocationService.bumpUserTokenVersion(user.getId());
        }

        userRepository.findById(user.getId()).ifPresent(entity -> {
            entity.setPresenceStatus(PresenceStatus.OFFLINE);
            userRepository.save(entity);
        });
        log.info("[鉴权] 用户退出登录 userId={}, sid={}, allDevices={}", user.getId(), sessionId, allDevices);
    }

    /** 从当前请求的令牌中取出会话 ID。 */
    private String resolveSessionId(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            log.warn("[鉴权] 退出登录未携带可解析的令牌，仅更新在线状态");
            return null;
        }
        try {
            return jwtSupport.parse(authorizationHeader.substring(7))
                    .get(JwtSupport.CLAIM_SESSION_ID, String.class);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("[鉴权] 退出登录解析令牌失败: {}", e.getMessage());
            return null;
        }
    }

    private TokenResponse issueTokens(User user) {
        return buildTokens(markOnline(user), newSessionId());
    }

    private String newSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private User markOnline(User user) {
        PresenceStatus current = user.getPresenceStatus();
        if (current == null || current == PresenceStatus.OFFLINE) {
            user.setPresenceStatus(PresenceStatus.ONLINE);
            return userRepository.save(user);
        }
        return user;
    }

    /**
     * 签发一对令牌，两者共享同一个会话 ID 与当前令牌版本号。
     * 刷新时复用原 sid，使该会话在刷新后仍可被单独吊销。
     */
    private TokenResponse buildTokens(User user, String sessionId) {
        long version = tokenRevocationService.currentTokenVersion(user.getId());
        String access = jwtSupport.createAccessToken(user.getId(), user.getPhone(), user.getNickname(),
                user.getRole().name(), version, sessionId);
        String refresh = jwtSupport.createRefreshToken(user.getId(), version, sessionId);
        log.info("[鉴权] 已签发令牌 userId={}, sid={}, ver={}", user.getId(), sessionId, version);
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
