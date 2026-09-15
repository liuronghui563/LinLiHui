package com.chengqu.huzhu.aid;

import com.chengqu.huzhu.api.client.NotifyApiClient;
import com.chengqu.huzhu.api.client.UserApiClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 求助服务入口。扫描 {@code com.chengqu.huzhu} 以加载 common 中的
 * JwtAuthFilter、ApiAccessLogFilter、GlobalExceptionHandler、JwtSupport 等组件。
 * <p>
 * 通过 Feign 调用 auth-service 补全发布者/帮助者的昵称与头像。
 *
 * <p>同时通过 NotifyApiClient 在接单、完成、评价等动作成功后写入站内通知。
 */
@SpringBootApplication(scanBasePackages = "com.chengqu.huzhu")
@EnableDiscoveryClient
@EnableFeignClients(clients = {UserApiClient.class, NotifyApiClient.class})
public class AidServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AidServiceApplication.class, args);
    }
}
