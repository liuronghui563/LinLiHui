package com.chengqu.huzhu.security;

import com.chengqu.huzhu.common.api.ApiResponse;
import com.chengqu.huzhu.common.redis.RedisKeys;
import com.chengqu.huzhu.common.redis.RedisRateLimiter;
import com.chengqu.huzhu.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 敏感接口的 IP 维度限流。
 *
 * <p>覆盖登录（含内部码登录）、注册、短信下发四类入口。这些接口是撞库、短信轰炸、
 * 管理员口令爆破的主要目标，放在控制器之前拦截，避免无效请求打到数据库和验证码逻辑。
 *
 * <p>手机号维度的限流在 {@code AuthService} 内单独做一层——攻击者换 IP 就能绕过 IP 限流，
 * 两层叠加才有意义。
 *
 * <p>Redis 不可用时 {@link RedisRateLimiter} 会放行全部请求，只打一次 WARN，
 * 不会因为缓存故障把用户挡在登录页外。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String SCOPE_LOGIN = "login-ip";
    private static final String SCOPE_REGISTER = "register-ip";
    private static final String SCOPE_SMS = "sms-ip";

    private final RedisRateLimiter rateLimiter;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!appProperties.getRateLimit().isEnabled() || !"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        AppProperties.RateLimit config = appProperties.getRateLimit();
        String scope;
        int limit;
        Duration window;
        if (path.equals("/api/auth/login/password")
                || path.equals("/api/auth/login/sms")
                || path.equals("/api/auth/login/internal")) {
            scope = SCOPE_LOGIN;
            limit = config.getLoginPerMinutePerIp();
            window = Duration.ofMinutes(1);
        } else if (path.equals("/api/auth/register")) {
            scope = SCOPE_REGISTER;
            limit = config.getRegisterPerHourPerIp();
            window = Duration.ofHours(1);
        } else if (path.equals("/api/auth/sms/send")) {
            scope = SCOPE_SMS;
            limit = config.getSmsPerMinutePerIp();
            window = Duration.ofMinutes(1);
        } else {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = clientIp(request);
        long remaining = rateLimiter.tryAcquire(RedisKeys.rateLimit(scope, clientIp), limit, window);
        if (remaining < 0) {
            log.warn("[鉴权] 触发接口限流 scope={}, ip={}, path={}", scope, clientIp, path);
            writeTooManyRequests(response, scope, window);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 取真实客户端 IP。
     *
     * <p><b>取 {@code X-Forwarded-For} 的最后一段，而不是第一段。</b>
     * 网关是「追加」而非「替换」该头：客户端发来的
     * {@code X-Forwarded-For: 伪造IP} 到达网关后会变成 {@code 伪造IP, 真实IP}。
     * 若取第一段，攻击者只要每次换一个伪造值就能完全绕过限流；
     * 取最后一段才是可信网关写入的真实地址（本项目里网关是唯一入口）。
     *
     * <p>若将来在网关之前再加一层代理，需要改为「从右往左跳过已知代理」的策略。
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String[] parts = forwarded.split(",");
            for (int i = parts.length - 1; i >= 0; i--) {
                String candidate = parts[i].trim();
                if (!candidate.isEmpty()) {
                    return candidate;
                }
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        return StringUtils.hasText(realIp) ? realIp.trim() : request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response, String scope, Duration window) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String hint = SCOPE_REGISTER.equals(scope) ? "请稍后再试" : "请 1 分钟后再试";
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.fail(429, "操作过于频繁，" + hint));
    }
}
