package com.chengqu.huzhu.community.dto;

import com.chengqu.huzhu.community.entity.Circle;
import com.chengqu.huzhu.community.entity.CircleStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CircleResponse {

    private Long id;
    private String name;
    private String description;
    private String cover;
    private Long ownerId;
    private String ownerName;
    /** 真实头像由 Feign 从 auth-service 补全，取不到时留空由前端展示占位图 */
    private String ownerAvatar;
    private Integer memberCount;
    private Integer postCount;
    private String status;
    /** 当前用户是否已加入。列表/详情都返回，前端据此决定按钮是「加入」还是「已加入」 */
    private Boolean joined;
    /** 当前用户在本圈的身份：OWNER / ADMIN / MEMBER；未加入时为空 */
    private String myRole;
    /** 当前用户是否被限制在本圈发帖 */
    private Boolean muted;
    /** 当前用户是否可以在本圈发帖（已加入、未被禁言、圈子未关闭） */
    private Boolean canPost;
    private LocalDateTime createdAt;

    public static CircleResponse from(Circle circle, boolean joined) {
        return from(circle, joined, null, false);
    }

    public static CircleResponse from(Circle circle, boolean joined, String myRole, boolean muted) {
        CircleStatus status = circle.getStatus() == null ? CircleStatus.ACTIVE : circle.getStatus();
        boolean postingAllowed = joined && !muted && status != CircleStatus.CLOSED;
        return CircleResponse.builder()
                .id(circle.getId())
                .name(circle.getName())
                .description(circle.getDescription())
                .cover(circle.getCover())
                .ownerId(circle.getOwnerId())
                .ownerName(circle.getOwnerName())
                .memberCount(circle.getMemberCount() == null ? 0 : circle.getMemberCount())
                .postCount(circle.getPostCount() == null ? 0 : circle.getPostCount())
                .status(status.name())
                .joined(joined)
                .myRole(myRole)
                .muted(muted)
                .canPost(postingAllowed)
                .createdAt(circle.getCreatedAt())
                .build();
    }
}
