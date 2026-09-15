import http from './http'

/**
 * 关注与拉黑。
 *
 * 拉黑是**双向屏蔽**：拉黑后双方的内容在信息流里互相不可见，
 * 且会自动解除双向关注。后端的过滤在 SQL 层完成，前端只需调用这几个接口。
 */

export function fetchRelation(userId) {
  return http.get(`/user/${userId}/relation`)
}

/**
 * 批量取关系状态，返回 `{ [userId]: relation }` 的对象。
 *
 * 列表页必须用这个而不是逐行 fetchRelation：单条接口要跑 6 条 SQL，
 * 一页 50 行就是 51 次请求、约 300 条查询；这里一次请求、固定 6 条 SQL。
 */
export function fetchRelations(userIds = []) {
  if (!userIds.length) return Promise.resolve({ code: 0, data: {} })
  return http.get('/user/relations', { params: { ids: userIds.join(',') } })
}

export function followUser(userId) {
  return http.post(`/user/${userId}/follow`)
}

export function unfollowUser(userId) {
  return http.delete(`/user/${userId}/follow`)
}

export function blockUser(userId) {
  return http.post(`/user/${userId}/block`)
}

export function unblockUser(userId) {
  return http.delete(`/user/${userId}/block`)
}

export function fetchFollowing(params = {}) {
  return http.get('/user/following', { params })
}

export function fetchFollowers(params = {}) {
  return http.get('/user/followers', { params })
}

export function fetchBlocked(params = {}) {
  return http.get('/user/blocked', { params })
}
