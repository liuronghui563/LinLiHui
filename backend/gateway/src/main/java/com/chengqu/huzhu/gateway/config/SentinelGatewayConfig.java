package com.chengqu.huzhu.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayParamFlowItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Set;

/**
 * 网关限流：登录防刷、上传收口、默认宽松挡突发。
 * Dashboard 改规则后重启会被本类覆盖，持久化到 Nacos 后再去掉这里的硬编码。
 */
@Configuration
public class SentinelGatewayConfig {

    @PostConstruct
    public void init() {
        Set<ApiDefinition> definitions = new HashSet<>();

        ApiDefinition login = new ApiDefinition("auth-login");
        Set<ApiPredicateItem> loginItems = new HashSet<>();
        loginItems.add(new ApiPathPredicateItem().setPattern("/api/auth/login/**"));
        login.setPredicateItems(loginItems);
        definitions.add(login);

        ApiDefinition upload = new ApiDefinition("file-upload");
        Set<ApiPredicateItem> uploadItems = new HashSet<>();
        uploadItems.add(new ApiPathPredicateItem().setPattern("/api/file/upload"));
        upload.setPredicateItems(uploadItems);
        definitions.add(upload);

        ApiDefinition apiDefault = new ApiDefinition("api-default");
        Set<ApiPredicateItem> defaultItems = new HashSet<>();
        defaultItems.add(new ApiPathPredicateItem()
                .setPattern("/api/**")
                .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX));
        apiDefault.setPredicateItems(defaultItems);
        definitions.add(apiDefault);

        GatewayApiDefinitionManager.loadApiDefinitions(definitions);

        Set<GatewayFlowRule> rules = new HashSet<>();
        rules.add(ipRule("auth-login", 5));
        rules.add(ipRule("file-upload", 2));
        rules.add(ipRule("api-default", 80));
        GatewayRuleManager.loadRules(rules);

        BlockRequestHandler handler = (exchange, t) -> ServerResponse
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}"), String.class);
        GatewayCallbackManager.setBlockHandler(handler);
    }

    private static GatewayFlowRule ipRule(String resource, int qps) {
        return new GatewayFlowRule(resource)
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(qps)
                .setIntervalSec(1)
                .setParamItem(new GatewayParamFlowItem()
                        .setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_CLIENT_IP));
    }
}
