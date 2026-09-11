package com.chengqu.huzhu.aid.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AidStatsResponse {
    private long openCount;
    private long acceptedCount;
    private long doneCount;
    private long myPublishedCount;
    private long myHelpingCount;
}
