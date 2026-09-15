import http from './http'

export function fetchCaptcha() {
  return http.get('/auth/captcha')
}

export function verifyCaptcha(data) {
  return http.post('/auth/captcha/verify', data)
}

export function sendSms(data) {
  return http.post('/auth/sms/send', data)
}

export function register(data) {
  return http.post('/auth/register', data)
}

export function loginByPassword(data) {
  return http.post('/auth/login/password', data)
}

export function loginBySms(data) {
  return http.post('/auth/login/sms', data)
}

export function loginByInternal(data) {
  return http.post('/auth/login/internal', data)
}

export function refreshToken(refreshToken) {
  return http.post('/auth/refresh', { refreshToken })
}

export function fetchMe() {
  return http.get('/auth/me')
}

/**
 * 退出登录。
 * @param {boolean} all 为 true 时吊销该账号全部会话（退出所有设备）
 */
export function logoutApi(all = false) {
  return http.post(all ? '/auth/logout?all=true' : '/auth/logout')
}

export function fetchUserHome() {
  return http.get('/user/home')
}

export function fetchAdminDashboard() {
  return http.get('/admin/dashboard')
}
