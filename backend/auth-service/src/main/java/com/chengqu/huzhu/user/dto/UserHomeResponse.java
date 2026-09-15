package com.chengqu.huzhu.user.dto;

import com.chengqu.huzhu.api.dto.UserAidStats;
import com.chengqu.huzhu.api.dto.UserPostStats;
import lombok.Builder;
import lombok.Data;

/**
 * 用户主页聚合结果。
 *
 * <p>原先前端打开一个主页要分别调用「用户资料」「某人的动态」「某人发布的求助」三个接口，
 * 现在由 auth-service 通过 Feign 向 aid-service / community-service 取统计后一次返回。
 *
 * <p>统计字段在下游服务不可用时为 {@code null}，前端应隐藏对应区块而不是显示 0。
 *
 * @param profile  公开资料
 * @param aidStats 求助域统计，可能为 null
 * @param postStats 社区域统计，可能为 null
 */
@Data
@Builder
public class UserHomeResponse {

    private PublicUserProfile profile;
    private UserAidStats aidStats;
    private UserPostStats postStats;
}
