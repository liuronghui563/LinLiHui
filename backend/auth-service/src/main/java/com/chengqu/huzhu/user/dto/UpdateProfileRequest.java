package com.chengqu.huzhu.user.dto;

import com.chengqu.huzhu.user.entity.Gender;
import com.chengqu.huzhu.user.entity.PresenceStatus;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(max = 50, message = "昵称最多50字")
    private String nickname;

    @Size(max = 30, message = "姓名最多30字")
    private String realName;

    @Size(max = 255, message = "头像地址过长")
    private String avatar;

    @Size(max = 200, message = "简介最多200字")
    private String bio;

    private PresenceStatus presenceStatus;

    private Gender gender;

    @Size(max = 50, message = "城市最多50字")
    private String city;

    @Size(max = 80, message = "小区最多80字")
    private String neighborhood;

    @Size(max = 80, message = "学校最多80字")
    private String school;

    @Size(max = 80, message = "专业最多80字")
    private String major;

    @Size(max = 30, message = "年级最多30字")
    private String grade;

    private Boolean student;

    private Boolean privateAccount;

    @Size(max = 50, message = "微信号最多50字")
    private String wechat;
}
