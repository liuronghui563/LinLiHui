import http from './http'

export function fetchUserProfile() {
  return http.get('/user/profile')
}

export function fetchPublicProfile(userId) {
  return http.get(`/user/${userId}`)
}

/**
 * 用户主页聚合接口：一次返回资料 + 求助统计 + 动态统计。
 * 统计由后端跨服务聚合，下游不可用时对应字段为 null。
 */
export function fetchUserHome(userId) {
  return http.get(`/user/${userId}/home`)
}

export function rateUser(userId, score) {
  return http.put(`/user/${userId}/rating`, { score })
}

export function updateUserProfile(data) {
  return http.put('/user/profile', data)
}
