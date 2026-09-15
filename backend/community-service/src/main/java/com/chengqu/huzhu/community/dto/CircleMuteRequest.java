package com.chengqu.huzhu.community.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 限制 / 解除本圈发帖。 */
@Data
public class CircleMuteRequest {

    @NotNull(message = "请指定是否限制发帖")
    private Boolean muted;
}
