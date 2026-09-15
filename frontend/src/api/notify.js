import http from './http'

/**
 * 消息通知。
 *
 * 由独立的 notify-service 提供。该服务未部署时这些请求会失败，
 * 调用方（导航角标、消息页）都应静默降级，而不是让整个页面报错。
 */

export function fetchNotifications(params = {}) {
  return http.get('/notify/list', { params })
}

export function fetchUnreadCount() {
  return http.get('/notify/unread-count')
}

export function markNotificationRead(id) {
  return http.post(`/notify/${id}/read`)
}

export function markAllNotificationsRead() {
  return http.post('/notify/read-all')
}

export function deleteNotification(id) {
  return http.delete(`/notify/${id}`)
}
