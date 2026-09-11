import axios from 'axios'
import { useAuthStore } from '../stores/auth'
import router from '../router'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

const AUTH_PUBLIC = [
  '/auth/captcha',
  '/auth/captcha/verify',
  '/auth/sms/send',
  '/auth/register',
  '/auth/login/',
  '/auth/refresh'
]

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.accessToken) {
    config.headers.Authorization = `Bearer ${auth.accessToken}`
  }
  return config
})

let refreshing = null

function isPublicAuth(url = '') {
  return AUTH_PUBLIC.some((p) => url.includes(p))
}

http.interceptors.response.use(
  async (response) => {
    const body = response.data
    if (body && typeof body.code === 'number' && body.code !== 0) {
      if (body.code === 401 && !isPublicAuth(response.config.url || '')) {
        return handleUnauthorized(response.config, body.message)
      }
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  async (error) => {
    const message = error.response?.data?.message || error.message || '网络异常'
    return Promise.reject(new Error(message))
  }
)

async function handleUnauthorized(originalConfig, message) {
  const auth = useAuthStore()
  if (!auth.refreshToken || originalConfig._retry) {
    auth.clear()
    if (router.currentRoute.value.name !== 'login') {
      router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    }
    return Promise.reject(new Error(message || '登录已过期'))
  }

  originalConfig._retry = true
  try {
    if (!refreshing) {
      refreshing = auth.refreshTokens().finally(() => {
        refreshing = null
      })
    }
    await refreshing
    originalConfig.headers.Authorization = `Bearer ${auth.accessToken}`
    return http(originalConfig)
  } catch (e) {
    auth.clear()
    router.push({ name: 'login' })
    return Promise.reject(e)
  }
}

export default http
