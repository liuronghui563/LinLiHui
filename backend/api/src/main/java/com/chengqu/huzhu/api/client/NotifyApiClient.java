package com.chengqu.huzhu.api.client;

import com.chengqu.huzhu.api.dto.NotifyCreateCommand;
import com.chengqu.huzhu.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 消息通知服务（notify-service）的内部契约。
 *
 * <p>路径前缀为 {@code /internal/**}，不在网关的路由规则内，仅限集群内部调用。
 *
 * <p>调用方需要在自己的启动类上显式声明：
 * {@code @EnableFeignClients(clients = NotifyApiClient.class)}。
 * 身份由 api 模块的 {@code FeignAuthForwardConfig} 透传当前请求的 Authorization 头，
 * 因此本接口通常要在「有登录用户」的请求线程里调用。
 */
@FeignClient(
        name = "notify-service",
        path = "/internal/notify",
        contextId = "notifyApiClient")
public interface NotifyApiClient {

    /**
     * 创建一条通知。
     *
     * <p>返回 {@code ApiResponse<Void>}：调用方只关心是否成功，不需要通知 id；
     * 业务错误（如参数不合法）同样以 {@code code != 0} 返回，HTTP 状态是 200。
     */
    @PostMapping
    ApiResponse<Void> create(@RequestBody NotifyCreateCommand command);
}
