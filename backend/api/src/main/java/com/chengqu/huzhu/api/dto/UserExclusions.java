package com.chengqu.huzhu.api.dto;

import java.util.List;

/**
 * 需要从信息流中屏蔽的作者 id 集合。
 *
 * <p>由 auth-service 通过 {@code /internal/user/exclusions} 提供，取的是
 * <b>双向并集</b>：我拉黑的人 + 拉黑我的人。只过滤前者的话，被拉黑方仍会看到
 * 我的内容并继续评论，隔离是失效的。
 *
 * @param blockedAuthorIds 需要屏蔽的作者 id；调用方需注意空集合不能直接用于 SQL 的 IN 条件
 */
public record UserExclusions(List<Long> blockedAuthorIds) {

    public static UserExclusions empty() {
        return new UserExclusions(List.of());
    }
}
