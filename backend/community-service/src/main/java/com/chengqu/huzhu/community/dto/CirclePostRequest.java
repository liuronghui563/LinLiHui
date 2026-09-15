package com.chengqu.huzhu.community.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 把一条已有动态收录进圈子。 */
@Data
public class CirclePostRequest {

    @NotNull(message = "帖子 id 不能为空")
    private Long postId;
}
