package com.chengqu.huzhu.community;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 社区服务入口。扫描 {@code com.chengqu.huzhu} 以加载 common 中的
 * JwtAuthFilter、ApiAccessLogFilter、GlobalExceptionHandler、JwtSupport 等组件。
 */
@SpringBootApplication(scanBasePackages = "com.chengqu.huzhu")
@EnableDiscoveryClient
@EnableScheduling
public class CommunityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommunityServiceApplication.class, args);
    }
}
