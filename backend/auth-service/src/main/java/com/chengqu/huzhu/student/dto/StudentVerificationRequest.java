package com.chengqu.huzhu.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 提交 / 修改学生认证申请的请求体。
 *
 * <p>姓名、学校、学号三项必填：没有它们，管理员拿到的东西无法核对，
 * 审核就退化成「点一下通过」。专业与年级是选填——学生证上未必有，
 * 而且它们只影响资料展示，不影响能不能进校园模块。
 */
@Data
public class StudentVerificationRequest {

    @NotBlank(message = "请填写真实姓名")
    @Size(max = 30, message = "姓名最多30字")
    private String realName;

    @NotBlank(message = "请填写学校")
    @Size(max = 80, message = "学校最多80字")
    private String school;

    @Size(max = 80, message = "专业最多80字")
    private String major;

    @Size(max = 30, message = "年级最多30字")
    private String grade;

    @NotBlank(message = "请填写学号")
    @Size(max = 30, message = "学号最多30字")
    private String studentNo;

    /** 学生证 / 校园卡照片的相对路径，选填；填了就必须是本站地址 */
    @Size(max = 255, message = "证件照地址过长")
    private String proofImage;
}
