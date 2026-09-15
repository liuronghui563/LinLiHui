package com.chengqu.huzhu.file.domain;

import com.chengqu.huzhu.common.exception.BizException;

public enum FilePurpose {
    AVATAR,
    /** 个人主页封面图。与头像同域，但单独成目录，便于按用途清理与配额统计。 */
    COVER,
    POST,
    AID,
    /**
     * 用户**申请**广告位时上传的广告图。
     *
     * <p>与 {@link #AD} 分开是必须的：AD 只允许管理员上传（后台直接投放），
     * 而申请是普通用户行为。合并成一个用途就等于给所有人开了管理员上传通道；
     * 图片能不能上线由审核状态决定（见 ad-service 的 AdStatus），与上传权限无关。
     */
    ADAPPLY,
    /**
     * 学生认证的证明材料（学生证 / 校园卡照片）。
     *
     * <p>单独成目录而不是复用 AVATAR/COVER：这张图是**审核凭据**，
     * 与用户主动展示的头像封面性质不同 —— 它会被管理员在审核页看到，
     * 将来要按用途做「仅本人与管理员可读」时，能直接按目录收敛权限，
     * 不必把用户的所有头像也一起锁掉。
     */
    STUDENT,
    /**
     * 广告位资质的证明材料（营业执照 / 经营许可）。
     *
     * <p>与 {@link #ADAPPLY} 分开：ADAPPLY 是「这一条广告的图」，
     * 一条广告一张；资质是「这个人能不能投放」的凭据，一份资质
     * 对应之后提交的多条广告。混在同一目录会让「删除某条广告」的清理逻辑
     * 有可能误删还在生效的资质证明。
     */
    QUALIFICATION,
    AD;

    public static FilePurpose from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException("请指定上传用途");
        }
        try {
            return FilePurpose.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException("不支持的上传用途");
        }
    }

    public String folder() {
        return name().toLowerCase();
    }

    public boolean requiresAdmin() {
        return this == AD;
    }
}
