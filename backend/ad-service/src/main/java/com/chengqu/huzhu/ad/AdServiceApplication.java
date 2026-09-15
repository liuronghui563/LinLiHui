package com.chengqu.huzhu.ad;

import com.chengqu.huzhu.api.client.UserApiClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 广告服务。
 *
 * <p>需要 UserApiClient 的原因：管理端待审列表要显示「谁申请的」，
 * 而昵称的权威数据在 auth-service；一次批量调用取整页昵称，不逐行查。
 */
@EnableDiscoveryClient
@EnableFeignClients(clients = UserApiClient.class)
@SpringBootApplication(scanBasePackages = "com.chengqu.huzhu")
public class AdServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdServiceApplication.class, args);
    }
}
