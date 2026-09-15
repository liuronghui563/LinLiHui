package com.chengqu.huzhu.notify.entity;

/**
 * 通知类型。
 *
 * <p>取值同时是数据库 {@code u_r_notification.type} 的 ENUM 成员，
 * <b>新增类型必须同时改实体与迁移脚本</b>，否则 Hibernate 校验或写入会失败。
 */
public enum NotificationType {

    AID_ACCEPTED("求助被接单"),
    AID_COMPLETED("求助已完成"),
    AID_RATED("收到评价"),
    POST_COMMENTED("动态被评论"),
    POST_LIKED("动态被点赞"),
    FOLLOWED("被关注"),
    SYSTEM("系统通知");

    private final String label;

    NotificationType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 生成前端点击该通知后的跳转路径。
     *
     * <p>路径放在后端生成：调用方（aid-service、community-service 等）只需写入
     * refId/actorId，不必各自拼一遍 URL；将来前端路由调整只改这一处。
     *
     * <p>关联信息缺失（如 refId 为 null）时返回 null，表示「不可点击」，
     * 由前端决定是置灰还是不响应点击——比返回一个会 404 的路径更安全。
     */
    public String link(Long refId, Long actorId) {
        return switch (this) {
            case AID_ACCEPTED, AID_COMPLETED, AID_RATED -> refId == null ? null : "/aids/" + refId;
            case POST_COMMENTED, POST_LIKED -> refId == null ? null : "/discover?post=" + refId;
            case FOLLOWED -> actorId == null ? null : "/users/" + actorId;
            // 系统通知没有可跳转的实体，前端只做展示
            case SYSTEM -> null;
        };
    }
}
