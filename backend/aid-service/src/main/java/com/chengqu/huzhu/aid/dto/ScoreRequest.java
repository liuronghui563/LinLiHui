package com.chengqu.huzhu.aid.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ScoreRequest {

    @NotNull(message = "请选择星级")
    @Min(value = 1, message = "最低 1 星")
    @Max(value = 5, message = "最高 5 星")
    private Integer score;
}
