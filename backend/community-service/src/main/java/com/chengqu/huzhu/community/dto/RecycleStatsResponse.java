package com.chengqu.huzhu.community.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 回收业务统计，供管理台看板聚合。
 *
 * <p>字段按需求固定为 {@code {pending, done}}：管理台只关心「还有多少单没处理」
 * 和「已经完成多少单」，其余状态可以由前端用列表接口自查。
 */
@Data
@Builder
public class RecycleStatsResponse {

    /** 待确认的预约单数（PENDING） */
    private long pending;
    /** 已完成的回收单数（DONE） */
    private long done;
}
