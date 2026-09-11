package com.chengqu.huzhu.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * 网关交互日志：方法、路径、路由、状态码、耗时。
 */
@Slf4j
@Component
public class GatewayAccessLogFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.currentTimeMillis();
        String method = exchange.getRequest().getMethod() == null
                ? "?"
                : exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getRawPath();
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
            String routeId = route == null ? "unmatched" : route.getId();
            HttpStatusCode status = exchange.getResponse().getStatusCode();
            long cost = System.currentTimeMillis() - start;
            log.info("[网关] {} {} -> {} status={} ({} ms)",
                    method, path, routeId, status == null ? "-" : status.value(), cost);
        }));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
