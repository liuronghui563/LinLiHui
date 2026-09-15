package com.chengqu.huzhu.admin.dto;

import com.chengqu.huzhu.api.dto.PlatformAdStats;
import com.chengqu.huzhu.api.dto.PlatformAidStats;
import com.chengqu.huzhu.api.dto.PlatformPostStats;

/**
 * 管理台看板聚合结果。
 *
 * <p>各业务域的统计由对应服务提供，auth-service 通过 Feign 汇总。
 * 某个下游不可用时对应字段为 null，前端应标注「暂不可用」而不是显示 0。
 */
public record AdminDashboardResponse(
        String greeting,
        long userCount,
        PlatformAidStats aidStats,
        PlatformPostStats postStats,
        PlatformAdStats adStats) {
}
