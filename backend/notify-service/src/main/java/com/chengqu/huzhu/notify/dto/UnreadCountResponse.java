package com.chengqu.huzhu.notify.dto;

/**
 * 未读通知数，用于首页/导航栏角标。
 *
 * @param count 未读条数
 */
public record UnreadCountResponse(long count) {
}
