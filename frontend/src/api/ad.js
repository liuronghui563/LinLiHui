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

// ---------------------------------------------------------------- 广告位申请
// 申请面向所有登录用户；审核（待审列表 / 通过 / 驳回）只有管理员能调，
// 服务端的规则见 ad-service 的 SecurityConfig，前端不做权限判断。

/** 提交申请：落库为待审，管理员通过后才进首页轮播 */
export function applyAdSlot(data) {
  return http.post('/ad/applications', data)
}

/** 我的申请（含审核意见） */
export function fetchMyAdApplications() {
  return http.get('/ad/applications/mine')
}

/** 撤回自己的待审申请 */
export function withdrawAdApplication(id) {
  return http.delete(`/ad/applications/${id}`)
}

/**
 * 修改自己的广告。
 * 语义是「改了就要重新审」：已通过的会退回待审并立刻从首页轮播撤下。
 */
export function updateAdApplication(id, data) {
  return http.put(`/ad/applications/${id}`, data)
}

/** 管理端：待审列表 */
export function fetchPendingAdApplications() {
  return http.get('/ad/applications/pending')
}

/** 管理端：全部广告（含已通过的），用于审核通过之后的日常管理 */
export function fetchAllAdApplications() {
  return http.get('/ad/applications/all')
}

/** 管理端：通过（通过即上线，首页轮播缓存同时失效） */
export function approveAdApplication(id, note = '') {
  return http.post(`/ad/applications/${id}/approve`, { note })
}

/** 管理端：驳回，可附原因 */
export function rejectAdApplication(id, note = '') {
  return http.post(`/ad/applications/${id}/reject`, { note })
}

// ---------------------------------------------------------------- 广告位资质
// 资质是「人的」属性（一个用户一份），广告是「内容」的属性（一个用户多条）。
// 没有通过审核的资质，POST /ad/applications 会被服务端以 403 拦下 ——
// 因此流程是「先开资质，再申请广告位」，前端按这个顺序引导。
//
// 注意两个前缀只差一个 s：用户侧是单数 /ad/qualification，管理侧是复数
// /ad/qualifications。服务端的规则顺序也是按这个区分的。

/** 提交资质申请 */
export function submitAdQualification(data) {
  return http.post('/ad/qualification', data)
}

/**
 * 我的资质状态。
 *
 * data.status 为 'PENDING' | 'APPROVED' | 'REJECTED' | 'NONE'（'NONE' = 从未提交），
 * data.qualified 是「能否提交广告位申请」的直接判据。
 */
export function fetchMyAdQualification() {
  return http.get('/ad/qualification/mine')
}

/** 修改并重新提交资质申请 */
export function updateAdQualification(id, data) {
  return http.put(`/ad/qualification/${id}`, data)
}

/** 撤回自己的待审资质申请 */
export function withdrawAdQualification(id) {
  return http.delete(`/ad/qualification/${id}`)
}

/** 管理端：资质列表（status 省略即全部），按提交时间升序 */
export function fetchAdQualifications(status = 'PENDING') {
  return http.get('/ad/qualifications', { params: status ? { status } : {} })
}

/** 管理端：通过资质 */
export function approveAdQualification(id, note = '') {
  return http.post(`/ad/qualifications/${id}/approve`, { note })
}

/** 管理端：驳回资质，可附原因 */
export function rejectAdQualification(id, note = '') {
  return http.post(`/ad/qualifications/${id}/reject`, { note })
}
