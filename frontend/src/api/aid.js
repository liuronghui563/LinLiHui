import http from './http'

export function fetchAidList(params = {}) {
  return http.get('/aid/list', { params })
}

export function fetchAidDetail(id) {
  return http.get(`/aid/${id}`)
}

export function createAid(data) {
  return http.post('/aid', data)
}

export function updateAid(id, data) {
  return http.put(`/aid/${id}`, data)
}

export function deleteAid(id) {
  return http.delete(`/aid/${id}`)
}

export function acceptAid(id) {
  return http.post(`/aid/${id}/accept`)
}

export function completeAid(id) {
  return http.post(`/aid/${id}/complete`)
}

export function cancelAid(id) {
  return http.post(`/aid/${id}/cancel`)
}

export function fetchMyPublished() {
  return http.get('/aid/mine/published')
}

export function fetchMyHelping() {
  return http.get('/aid/mine/helping')
}

export function fetchAidStats() {
  return http.get('/aid/stats')
}

export function fetchUserPublished(userId, params = {}) {
  return http.get(`/aid/user/${userId}/published`, { params })
}

export function rateAid(id, score) {
  return http.put(`/aid/${id}/rating`, { score })
}

export function reviewHelper(id, data) {
  return http.put(`/aid/${id}/helper-review`, data)
}
