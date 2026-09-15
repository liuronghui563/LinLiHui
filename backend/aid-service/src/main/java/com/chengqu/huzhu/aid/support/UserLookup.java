package com.chengqu.huzhu.aid.support;

import com.chengqu.huzhu.api.client.UserApiClient;
import com.chengqu.huzhu.api.dto.UserBrief;
import com.chengqu.huzhu.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 跨服务查询用户信息的统一入口。
 *
 * <p>求助表里虽然冗余了 {@code publisher_name} / {@code helper_name}，
 * 但用户改昵称后快照就会过期。这里通过 Feign 实时向 auth-service 取权威数据，
 * 冗余字段退化为「下游不可用时的兜底」。
 *
 * <p>降级策略：任何异常（服务未注册、超时、下游返回业务错误）都返回空 Map，
 * 由调用方保留本地冗余字段继续渲染，避免下游抖动导致整个求助列表不可用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserLookup {

    private final UserApiClient userApiClient;

    public Map<Long, UserBrief> byIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            ApiResponse<List<UserBrief>> response = userApiClient.findBriefs(ids);
            if (response == null || response.getCode() != 0 || response.getData() == null) {
                log.warn("[求助] 用户信息查询返回异常，降级使用本地冗余字段 code={}",
                        response == null ? "null" : response.getCode());
                return Map.of();
            }
            Map<Long, UserBrief> result = new HashMap<>();
            for (UserBrief brief : response.getData()) {
                if (brief != null && brief.id() != null) {
                    result.put(brief.id(), brief);
                }
            }
            return result;
        } catch (Exception e) {
            // 故意捕获宽泛异常：跨服务调用失败不应影响主流程
            log.warn("[求助] 调用 auth-service 查询用户失败，降级使用本地冗余字段: {}", e.getMessage());
            return Map.of();
        }
    }
}
