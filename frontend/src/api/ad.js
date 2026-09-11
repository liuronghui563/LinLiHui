import http from './http'

export function fetchAdCarousel() {
  return http.get('/ad/carousel')
}

export function clickAd(id) {
  return http.post(`/ad/${id}/click`)
}

export function fetchAdList() {
  return http.get('/ad/list')
}

export function createAd(data) {
  return http.post('/ad', data)
}

export function updateAd(id, data) {
  return http.put(`/ad/${id}`, data)
}

export function deleteAd(id) {
  return http.delete(`/ad/${id}`)
}
