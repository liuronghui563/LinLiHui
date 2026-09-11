import { defineStore } from 'pinia'
import {
  loginByPassword,
  loginBySms,
  loginByInternal,
  register,
  refreshToken as refreshTokenApi,
  fetchMe,
  logoutApi
} from '../api/auth'

const ACCESS_KEY = 'cq_access_token'
const REFRESH_KEY = 'cq_refresh_token'
const USER_KEY = 'cq_user'

function loadJson(key) {
  try {
    const raw = localStorage.getItem(key)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: localStorage.getItem(ACCESS_KEY) || '',
    refreshToken: localStorage.getItem(REFRESH_KEY) || '',
    user: loadJson(USER_KEY)
  }),
  getters: {
    isLogin: (s) => Boolean(s.accessToken),
    isAdmin: (s) => s.user?.role === 'ADMIN'
  },
  actions: {
    persist(tokenPayload) {
      this.accessToken = tokenPayload.accessToken
      this.refreshToken = tokenPayload.refreshToken
      this.user = tokenPayload.user
      localStorage.setItem(ACCESS_KEY, this.accessToken)
      localStorage.setItem(REFRESH_KEY, this.refreshToken)
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
    },
    clear() {
      this.accessToken = ''
      this.refreshToken = ''
      this.user = null
      localStorage.removeItem(ACCESS_KEY)
      localStorage.removeItem(REFRESH_KEY)
      localStorage.removeItem(USER_KEY)
    },
    async passwordLogin(form) {
      const res = await loginByPassword(form)
      this.persist(res.data)
      return res.data
    },
    async smsLogin(form) {
      const res = await loginBySms(form)
      this.persist(res.data)
      return res.data
    },
    async internalLogin(code) {
      const res = await loginByInternal({ code })
      this.persist(res.data)
      return res.data
    },
    async registerAccount(form) {
      const res = await register(form)
      this.persist(res.data)
      return res.data
    },
    async refreshTokens() {
      const res = await refreshTokenApi(this.refreshToken)
      this.persist(res.data)
      return res.data
    },
    async loadProfile() {
      const res = await fetchMe()
      this.user = res.data
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
      return this.user
    },
    async logout() {
      try {
        await logoutApi()
      } catch {
        // ignore
      }
      this.clear()
    }
  }
})
