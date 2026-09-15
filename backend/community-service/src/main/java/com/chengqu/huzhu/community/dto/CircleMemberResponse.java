package com.chengqu.huzhu.community.dto;

import com.chengqu.huzhu.community.entity.CircleMember;
import com.chengqu.huzhu.community.entity.CircleMemberRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CircleMemberResponse {

    private Long userId;
    private String nickname;
    private String avatar;
    private String role;
    private Boolean muted;
    private LocalDateTime joinedAt;

    public static CircleMemberResponse from(CircleMember member) {
        CircleMemberRole role = member.getRole() == null ? CircleMemberRole.MEMBER : member.getRole();
        return CircleMemberResponse.builder()
                .userId(member.getUserId())
                .role(role.name())
                .muted(Boolean.TRUE.equals(member.getMuted()))
                .joinedAt(member.getJoinedAt())
                .build();
    }
}
