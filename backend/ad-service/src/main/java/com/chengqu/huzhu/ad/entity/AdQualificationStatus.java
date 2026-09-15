package com.chengqu.huzhu.ad.entity;

/**
 * 广告位资质的审核状态。
 *
 * <p>与 {@link AdStatus} 形状相同但**刻意不复用**：广告的状态说的是「这条广告能不能上台」，
 * 资质的状态说的是「这个人能不能提交广告」。两者的取值虽然目前都是三档，
 * 但资质将来很可能多出「已过期」「已停用」这类只属于人的状态，
 * 共用一个枚举会让广告侧被迫认识一批与它无关的取值。
 *
 * <p>对外响应里还会出现第四个值 {@code NONE}（从未提交过），
 * 它不是数据库取值，因此不放进本枚举——枚举一旦包含它，
 * 每个写入路径都得先把它排除掉，反而容易漏。
 */
public enum AdQualificationStatus {

    /** 待审核：用户已提交，管理员还没处理 */
    PENDING("待审核"),

    /** 已通过：闸门打开，可以提交广告位申请 */
    APPROVED("已通过"),

    /** 已驳回：附审核意见，用户可修改后重新提交 */
    REJECTED("已驳回");

    private final String label;

    AdQualificationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
