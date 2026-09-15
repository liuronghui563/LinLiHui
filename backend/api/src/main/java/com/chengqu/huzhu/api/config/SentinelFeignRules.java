package com.chengqu.huzhu.api.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Feign 熔断规则。资源名与 Spring Cloud Alibaba Sentinel 对 Feign 的默认命名一致：
 * {@code HTTP方法:http://服务名/路径}。
 *
 * <p>异常比例 ≥50% 且 5 秒窗口内至少 5 次调用后熔断 10 秒，避免下游持续故障时每次都等满超时。
 */
@Slf4j
@Configuration
public class SentinelFeignRules {

    @PostConstruct
    public void load() {
        List<DegradeRule> rules = new ArrayList<>();
        for (String resource : List.of(
                "GET:http://auth-service/internal/user/brief",
                "GET:http://auth-service/internal/user/{id}/brief",
                "GET:http://aid-service/internal/aid/platform-stats",
                "GET:http://aid-service/internal/aid/user-stats/{userId}",
                "GET:http://community-service/internal/community/platform-stats",
                "GET:http://community-service/internal/community/user-stats/{userId}",
                "GET:http://ad-service/internal/ad/platform-stats"
        )) {
            DegradeRule rule = new DegradeRule(resource)
                    .setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO)
                    .setCount(0.5)
                    .setTimeWindow(10)
                    .setStatIntervalMs(5000)
                    .setMinRequestAmount(5);
            rules.add(rule);
        }
        DegradeRuleManager.loadRules(rules);
        log.info("[Sentinel] 已加载 Feign 熔断规则 count={}", rules.size());
    }
}
