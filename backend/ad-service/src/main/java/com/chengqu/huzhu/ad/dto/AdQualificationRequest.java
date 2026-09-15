package com.chengqu.huzhu.ad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户提交 / 修改广告位资质申请。
 *
 * <p>只有姓名与联系方式是必填的：资质审核看的是「这个人找不找得到、材料真不真」，
 * 主体名称、品类、简介可以让管理员在驳回时补问，但**联系人填不了就没法审**
 * ——审核员需要打电话核实，所以卡在入口。
 *
 * <p>长度上限与 {@code u_r_ad_qualification} 的列宽严格一致：
 * 校验放在这一层，数据库就不会因为超长在写库时才抛 DataTruncation（500）。
 */
@Data
public class AdQualificationRequest {

    @NotBlank(message = "请填写联系人姓名")
    @Size(max = 50, message = "联系人姓名最多50字")
    private String applicantName;

    @NotBlank(message = "请填写联系电话或微信")
    @Size(max = 50, message = "联系方式最多50字")
    private String contact;

    @Size(max = 100, message = "主体名称最多100字")
    private String company;

    @Size(max = 100, message = "经营范围最多100字")
    private String category;

    @Size(max = 500, message = "简介最多500字")
    private String intro;

    /** 资质证明图相对路径；可空（个人主体常常没有营业执照） */
    @Size(max = 255, message = "图片地址过长")
    private String licenseImage;
}
