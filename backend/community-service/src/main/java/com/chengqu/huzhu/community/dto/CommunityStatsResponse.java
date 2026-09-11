package com.chengqu.huzhu.community.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommunityStatsResponse {
    private long postCount;
    private long myPostCount;
}
