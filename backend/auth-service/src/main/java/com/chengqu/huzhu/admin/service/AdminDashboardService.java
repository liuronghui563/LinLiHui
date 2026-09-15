package com.chengqu.huzhu.admin.service;

import com.chengqu.huzhu.admin.dto.AdminDashboardResponse;
import com.chengqu.huzhu.api.client.AdApiClient;
import com.chengqu.huzhu.api.client.AidApiClient;
import com.chengqu.huzhu.api.client.CommunityApiClient;
import com.chengqu.huzhu.api.dto.PlatformAdStats;
import com.chengqu.huzhu.api.dto.PlatformAidStats;
import com.chengqu.huzhu.api.dto.PlatformPostStats;
import com.chengqu.huzhu.common.api.ApiResponse;
import com.chengqu.huzhu.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * 管理台看板聚合：本地用户数 + 三个业务域的远程统计。
 *
 * <p>三个远程调用相互独立，任一失败只影响自己那一块（返回 null），不阻塞看板整体渲染。
 * 这里刻意不加 {@code @Transactional}，避免跨服务调用期间持有数据库事务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final AidApiClient aidApiClient;
    private final CommunityApiClient communityApiClient;
    private final AdApiClient adApiClient;

    public AdminDashboardResponse dashboard(String adminPhone) {
        AdminDashboardResponse response = new AdminDashboardResponse(
                "欢迎管理员 " + adminPhone,
                userRepository.count(),
                fetch("aid-service", () -> aidApiClient.platformStats()),
                fetch("community-service", () -> communityApiClient.platformStats()),
                fetch("ad-service", () -> adApiClient.platformStats()));
        log.info("[用户] 管理台看板聚合 userCount={}, aid={}, post={}, ad={}",
                response.userCount(),
                response.aidStats() != null,
                response.postStats() != null,
                response.adStats() != null);
        return response;
    }

    /**
     * 统一处理跨服务取数的异常与业务错误码：失败一律降级为 null。
     */
    private <T> T fetch(String serviceName, Supplier<ApiResponse<T>> call) {
        try {
            ApiResponse<T> response = call.get();
            if (response != null && response.getCode() == 0 && response.getData() != null) {
                return response.getData();
            }
            log.warn("[用户] {} 统计返回异常 code={}", serviceName,
                    response == null ? "null" : response.getCode());
        } catch (Exception e) {
            log.warn("[用户] 调用 {} 获取统计失败: {}", serviceName, e.getMessage());
        }
        return null;
    }
}
