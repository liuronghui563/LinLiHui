package com.chengqu.huzhu.community.support;

import com.chengqu.huzhu.api.client.UserRelationApiClient;
import com.chengqu.huzhu.api.dto.UserExclusions;
import com.chengqu.huzhu.common.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 取当前用户的屏蔽作者集合，供信息流过滤。
 *
 * <p>两个必须守住的细节：
 * <ol>
 *   <li><b>返回值永不为空集合</b>。JPQL 的 {@code NOT IN ()} 是非法语法，
 *       没有要屏蔽的人时返回一个不存在的 id 作为哨兵。</li>
 *   <li><b>失败即降级为「不屏蔽」</b>。屏蔽是体验优化而非安全边界，
 *       因为 auth-service 抖动就让整个信息流打不开是不划算的。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserExclusionLookup {

    /** 没有需要屏蔽的人时使用的哨兵 id（用户 id 自增，永远不会是 -1）。 */
    public static final List<Long> NO_EXCLUSION = List.of(-1L);

    private final UserRelationApiClient relationApiClient;

    public List<Long> blockedAuthorIds() {
        try {
            ApiResponse<UserExclusions> response = relationApiClient.exclusions();
            if (response != null && response.getCode() == 0 && response.getData() != null) {
                List<Long> ids = response.getData().blockedAuthorIds();
                if (ids != null && !ids.isEmpty()) {
                    return ids;
                }
                return NO_EXCLUSION;
            }
            log.warn("[社区] 屏蔽名单查询返回异常，本次不过滤 code={}",
                    response == null ? "null" : response.getCode());
        } catch (Exception e) {
            // 跨服务调用失败不应影响主流程
            log.warn("[社区] 调用 auth-service 获取屏蔽名单失败，本次不过滤: {}", e.getMessage());
        }
        return NO_EXCLUSION;
    }
}
