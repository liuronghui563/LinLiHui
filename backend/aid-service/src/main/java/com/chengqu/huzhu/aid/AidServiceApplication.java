package com.chengqu.huzhu.aid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 求助服务入口。扫描 {@code com.chengqu.huzhu} 以加载 common 中的
 * JwtAuthFilter、ApiAccessLogFilter、GlobalExceptionHandler、JwtSupport 等组件。
 */
@SpringBootApplication(scanBasePackages = "com.chengqu.huzhu")
@EnableDiscoveryClient
public class AidServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AidServiceApplication.class, args);
    }
}
