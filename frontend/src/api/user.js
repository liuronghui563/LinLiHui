import http from './http'

export function fetchUserProfile() {
  return http.get('/user/profile')
}

export function fetchPublicProfile(userId) {
  return http.get(`/user/${userId}`)
}

export function rateUser(userId, score) {
  return http.put(`/user/${userId}/rating`, { score })
}

export function updateUserProfile(data) {
  return http.put('/user/profile', data)
}
