package com.chengqu.huzhu.notify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 消息通知服务入口。扫描 {@code com.chengqu.huzhu} 以加载 common 中的
 * JwtAuthFilter、ApiAccessLogFilter、GlobalExceptionHandler、JwtSupport 等组件——
 * 少写这一层扫描，JWT 过滤器与全局异常处理都不会生效。
 *
 * <p>本服务只被其他服务调用（{@code /internal/notify/**}），不主动调用下游，
 * 因此不需要 {@code @EnableFeignClients}。
 */
@SpringBootApplication(scanBasePackages = "com.chengqu.huzhu")
@EnableDiscoveryClient
public class NotifyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotifyServiceApplication.class, args);
    }
}
