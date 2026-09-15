import http from './http'

export function fetchPosts(params = {}) {
  return http.get('/community/posts', { params })
}

/**
 * 某模块可用的帖子种类。
 * 种类定义在后端枚举里，前端拉取后渲染选择器，两端不各维护一份。
 * @param {'DISCOVER'|'CAMPUS'} module
 */
export function fetchPostKinds(module) {
  return http.get('/community/post-kinds', { params: { module } })
}

export function fetchPlazaHot() {
  return http.get('/community/plaza/hot')
}

export function fetchPostDetail(id) {
  return http.get(`/community/posts/${id}`)
}

export function createPost(data) {
  return http.post('/community/posts', data)
}

export function updatePost(id, data) {
  return http.put(`/community/posts/${id}`, data)
}

export function deletePost(id) {
  return http.delete(`/community/posts/${id}`)
}

export function toggleLike(id) {
  return http.post(`/community/posts/${id}/like`)
}

export function recordPostView(id) {
  return http.post(`/community/posts/${id}/view`)
}

export function fetchCommunityStats() {
  return http.get('/community/stats')
}

export function fetchAuthorPosts(userId, params = {}) {
  return http.get(`/community/posts/author/${userId}`, { params })
}

export function fetchComments(postId) {
  return http.get(`/community/posts/${postId}/comments`)
}

export function addComment(postId, data) {
  return http.post(`/community/posts/${postId}/comments`, data)
}

export function deleteComment(commentId) {
  return http.delete(`/community/comments/${commentId}`)
}
