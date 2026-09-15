package com.chengqu.huzhu.ad.dto;

import com.chengqu.huzhu.ad.entity.AdQualification;
import com.chengqu.huzhu.ad.entity.AdQualificationStatus;
import lombok.Builder;
import lombok.Data;

/**
 * 「我的资质」页面一次性要的全部信息。
 *
 * <p>为什么不是直接返回 {@link AdQualificationResponse}（没提交过就返回 null）：
 * 前端要区分的其实是三件事——「没提交过」「提交了在审」「已开通」，
 * 只有 qualification 的话，第一种是 null、第二种是有值但状态在 qualification.status 里，
 * 前端得写两处判断。这里把结论（{@code status}）摊平到顶层，
 * 前端的按钮状态直接读它就行。
 *
 * <p>{@code status} 用 String 而不是 {@link AdQualificationStatus} 的原因：
 * 它可能是 {@link #STATUS_NONE}，而那是**响应层补的哨兵值**，
 * 数据库里没有这个取值、也不该往枚举里塞——枚举一旦带上 NONE，
 * 所有写库路径都得先把它排除，等于给每个写入点埋一个坑。
 */
@Data
@Builder
public class MyQualificationResponse {

    /** 从未提交过资质。不是数据库取值，只在响应里出现 */
    public static final String STATUS_NONE = "NONE";

    /** PENDING / APPROVED / REJECTED / NONE */
    private String status;

    /** 状态的中文名，含 NONE 的「未申请」，前端不维护映射表 */
    private String statusLabel;

    /** 最新一条资质（含被驳回的历史）；从未提交过时为 null */
    private AdQualificationResponse qualification;

    /** 闸门结论：能不能提交广告位申请。等价于 status == APPROVED，由后端算好下发 */
    private boolean qualified;

    public static MyQualificationResponse of(AdQualification latest) {
        if (latest == null) {
            return MyQualificationResponse.builder()
                    .status(STATUS_NONE)
                    .statusLabel("未申请")
                    .qualification(null)
                    .qualified(false)
                    .build();
        }
        AdQualificationStatus status = latest.getStatus() == null
                ? AdQualificationStatus.PENDING : latest.getStatus();
        return MyQualificationResponse.builder()
                .status(status.name())
                .statusLabel(status.getLabel())
                .qualification(AdQualificationResponse.from(latest))
                .qualified(status == AdQualificationStatus.APPROVED)
                .build();
    }
}
