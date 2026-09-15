package com.chengqu.huzhu.ad.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审核一条广告位申请时附带的说明。
 *
 * <p>整个请求体是可选的：通过时通常不需要写什么，
 * 驳回时则应该说明原因（用户会在「我的申请」里看到这句话）。
 */
@Data
public class ReviewAdRequest {

    @Size(max = 200, message = "审核意见最多200字")
    private String note;
}
