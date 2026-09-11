package com.chengqu.huzhu.aid.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HelperReviewRequest {

    @NotNull(message = "请选择星级")
    @Min(value = 1, message = "最低 1 星")
    @Max(value = 5, message = "最高 5 星")
    private Integer score;

    @Size(max = 200, message = "评价最多200字")
    private String content;
}
