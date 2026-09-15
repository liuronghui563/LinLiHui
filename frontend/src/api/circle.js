import http from './http'

/**
 * 圈子（兴趣小组）。
 *
 * 圈内帖子复用动态数据：帖子本身仍然可以在「发现」流里被点赞、评论，
 * 圈子只是给它加了一个聚合入口，所以这里返回的是标准的 PostResponse，
 * 可以直接交给 PostFeed 渲染。
 */

/** 在圈子里直接发帖，同时进入发现流。 */
export function composeCirclePost(id, data) {
  return http.post(`/circle/${id}/compose`, data)
}

/** 圈子列表，每条带 joined 标记当前用户是否已加入。 */
export function fetchCircleList(params = {}) {
  return http.get('/circle/list', { params })
}

/** 我加入的圈子（含我创建并作为圈主的），按加入时间倒序。 */
export function fetchMyCircles(params = {}) {
  return http.get('/circle/mine', { params })
}

/** 圈子详情，同样带 joined 标记。 */
export function fetchCircleDetail(id) {
  return http.get(`/circle/${id}`)
}

/** 创建圈子。name 必填且不能重名，cover 必须是本站已上传的对象地址。 */
export function createCircle(data) {
  return http.post('/circle', data)
}

/** 加入圈子。已在圈内或圈子已关闭时后端会直接报错。 */
export function joinCircle(id) {
  return http.post(`/circle/${id}/join`)
}

/** 退出圈子。圈主不能退出，只能关闭圈子。 */
export function leaveCircle(id) {
  return http.post(`/circle/${id}/leave`)
}

/** 关闭圈子，仅圈主或管理员；关闭后不可再加入。 */
export function closeCircle(id) {
  return http.post(`/circle/${id}/close`)
}

/** 圈内帖子分页，按收录时间倒序，返回结构与动态流一致。 */
export function fetchCirclePosts(id, params = {}) {
  return http.get(`/circle/${id}/posts`, { params })
}

/** 从圈子下线一条帖子，原动态仍留在发现流。 */
export function removeCirclePost(id, postId) {
  return http.delete(`/circle/${id}/posts/${postId}`)
}

export function fetchCircleMembers(id) {
  return http.get(`/circle/${id}/members`)
}

export function kickCircleMember(id, userId) {
  return http.post(`/circle/${id}/members/${userId}/kick`)
}

export function muteCircleMember(id, userId, muted) {
  return http.post(`/circle/${id}/members/${userId}/mute`, { muted })
}

export function setCircleMemberRole(id, userId, role) {
  return http.post(`/circle/${id}/members/${userId}/role`, { role })
}
