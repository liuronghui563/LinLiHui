package com.chengqu.huzhu.student.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审核一条学生认证申请时附带的说明。
 *
 * <p>整个请求体可选：通过时通常不写什么，驳回时应当说明原因
 * （用户会在「我的认证状态」里看到这句话，也是他改完重投的依据）。
 */
@Data
public class ReviewStudentVerificationRequest {

    @Size(max = 200, message = "审核意见最多200字")
    private String note;
}
