package com.chengqu.huzhu.aid.support;

import com.chengqu.huzhu.common.api.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 固化 NotifyApiClient 的响应绑定行为。
 *
 * <p>契约两侧的类型并不对称：api 模块的 {@code NotifyApiClient#create} 声明
 * {@code ApiResponse<Void>}，而 notify-service 的 {@code InternalNotifyController#create}
 * 实际返回 {@code ApiResponse<NotificationResponse>}（data 非 null）。
 * 这依赖 Jackson「把对象绑定到 Void 时直接丢弃」的行为才能成功反序列化。
 *
 * <p>一旦绑定失败（例如升级 Jackson 或把 Void 换成别的类型），Feign 调用会在反序列化阶段抛异常，
 * 而按本项目的降级策略只会留下一条 WARN 日志，通知会「静默」全部丢失。
 * 因此这里把这个隐含依赖写成测试，避免它被无声破坏。
 */
class NotifyApiResponseContractTest {

    /** 与 Spring Boot/Feign 实际使用的 ObjectMapper 一致：未知字段不报错。 */
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    @Test
    @DisplayName("notify-service 返回带 data 对象的 JSON，可以绑定到 ApiResponse<Void>")
    void voidDataBindingToleratesPopulatedData() throws Exception {
        String body = """
                {"code":0,"message":"通知已创建","data":{"id":1,"userId":2,"type":"AID_ACCEPTED",\
                "title":"有人接下了你的求助","link":"/aids/1","read":false}}""";

        ApiResponse<Void> response = objectMapper.readValue(body, new TypeReference<ApiResponse<Void>>() {
        });

        assertEquals(0, response.getCode(), "调用方只判断 code，非 0 即视为投递失败");
        assertNull(response.getData(), "data 被丢弃，因此调用方拿不到通知 id（当前不需要）");
    }

    @Test
    @DisplayName("没有 data 字段（NON_NULL 省略）时同样可绑定")
    void voidDataBindingToleratesMissingData() throws Exception {
        ApiResponse<Void> response = objectMapper.readValue(
                "{\"code\":400,\"message\":\"标题最长100字\"}", new TypeReference<ApiResponse<Void>>() {
                });

        assertEquals(400, response.getCode());
    }
}
