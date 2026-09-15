import http from './http'

/**
 * 学生认证。
 *
 * 认证是进入「校园」模块的前置条件：提交 → 管理员审核 → 通过后
 * `u_r_sys_user.student` 才会变成 true，前端的路由守卫读的就是它。
 *
 * 注意说明：路径里没有 /admin 的那些是用户侧接口；管理侧的审核接口在
 * `admin` 命名下（见文件末尾），服务端用 `/api/admin/**` 的 ADMIN 规则保护，
 * 前端不做权限判断。
 */

/** 提交认证申请。已有待审 / 已通过时后端返回 400，前端直接把 message 显示出来即可 */
export function submitStudentVerification(data) {
  return http.post('/student/verification', data)
}

/**
 * 我的认证状态。
 *
 * data.status 为 'PENDING' | 'APPROVED' | 'REJECTED' | 'NONE'，
 * 其中 'NONE' 表示从未提交过（不是数据库里的枚举值，是响应层补的哨兵值）。
 */
export function fetchMyStudentVerification() {
  return http.get('/student/verification/mine')
}

/** 修改并重新提交（只能改自己的、且当前为待审或已驳回的记录） */
export function updateStudentVerification(id, data) {
  return http.put(`/student/verification/${id}`, data)
}

/** 撤回自己的待审申请 */
export function withdrawStudentVerification(id) {
  return http.delete(`/student/verification/${id}`)
}

// ---------------------------------------------------------------- 管理侧审核

/** 待审列表（status 省略即全部），按提交时间升序 */
export function fetchStudentVerifications(status = 'PENDING') {
  return http.get('/admin/student/verifications', { params: status ? { status } : {} })
}

/** 通过。通过后该用户立即获得校园模块权限 */
export function approveStudentVerification(id, note = '') {
  return http.post(`/admin/student/verifications/${id}/approve`, { note })
}

/** 驳回，可附原因（会出现在用户的认证页上） */
export function rejectStudentVerification(id, note = '') {
  return http.post(`/admin/student/verifications/${id}/reject`, { note })
}
