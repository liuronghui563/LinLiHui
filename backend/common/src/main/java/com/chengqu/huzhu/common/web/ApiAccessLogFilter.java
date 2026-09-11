package com.chengqu.huzhu.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 将关键 API 访问打印到后端控制台，便于联调观察交互。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiAccessLogFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (uri.startsWith("/api/")) {
                long cost = System.currentTimeMillis() - start;
                log.info("[API] {} {}{} -> {} ({} ms)",
                        method,
                        uri,
                        query == null ? "" : "?" + query,
                        response.getStatus(),
                        cost);
            }
        }
    }
}
