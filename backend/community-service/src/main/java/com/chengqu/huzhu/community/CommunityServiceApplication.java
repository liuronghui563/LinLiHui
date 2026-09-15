package com.chengqu.huzhu.community;

import com.chengqu.huzhu.api.client.NotifyApiClient;
import com.chengqu.huzhu.api.client.UserApiClient;
import com.chengqu.huzhu.api.client.UserRelationApiClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 社区服务入口。扫描 {@code com.chengqu.huzhu} 以加载 common 中的
 * JwtAuthFilter、ApiAccessLogFilter、GlobalExceptionHandler、JwtSupport 等组件。
 * <p>
 * 通过 Feign 调用 auth-service 补全动态作者的昵称与头像。
 *
 * <p>同时通过 NotifyApiClient 在评论、点赞成功后写入站内通知。
 */
@SpringBootApplication(scanBasePackages = "com.chengqu.huzhu")
@EnableDiscoveryClient
@EnableFeignClients(clients = {UserApiClient.class, UserRelationApiClient.class, NotifyApiClient.class})
@EnableScheduling
public class CommunityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommunityServiceApplication.class, args);
    }
}
