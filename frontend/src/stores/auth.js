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

/**
 * 后台刷新资料的最短间隔。
 *
 * 外壳在每个视图挂载时都会刷新一次用户资料，而 AppShell 是渲染在**每个页面内部**的，
 * 于是每次跨页面导航都会打一次 `/auth/me`——而 auth-service 是唯一在鉴权时
 * 还要查一次用户行的服务，一个 `/auth/me` 实际是 2 次 `u_sys_user` 主键查询。
 * 这里给它一个下限，理由与未读数角标完全相同。
 */
const PROFILE_MIN_INTERVAL_MS = 30_000

/** 正在飞行的资料请求，用于合并同一时刻的多个调用方 */
let profileInflight = null
/** 上一次后台刷新成功的时间戳 */
let lastProfileAt = 0

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
      // 不清时间戳的话，换账号登录后 30 秒内不会去取新账号的资料
      profileInflight = null
      lastProfileAt = 0
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
    /**
     * 拉取当前用户资料，**总是取最新的**。
     *
     * 保留这个语义是因为资料设置页要靠它回填表单——那里拿到 30 秒前的缓存值
     * 会让用户刚改完的内容看起来没保存。但同一时刻的重复调用会合并成一次请求。
     */
    async loadProfile() {
      if (profileInflight) {
        return profileInflight
      }
      profileInflight = (async () => {
        try {
          const res = await fetchMe()
          this.user = res.data
          localStorage.setItem(USER_KEY, JSON.stringify(this.user))
          lastProfileAt = Date.now()
          return this.user
        } finally {
          profileInflight = null
        }
      })()
      return profileInflight
    },

    /**
     * 外壳用的后台刷新：带最短间隔，失败静默。
     *
     * 与未读数角标同一套做法：把频率控制收在一处，调用点就不必各自判断。
     * 已经登录过并且本地有 user 时，30 秒内不会重复请求。
     */
    async refreshProfileInBackground() {
      if (!this.isLogin) return this.user
      if (this.user && Date.now() - lastProfileAt < PROFILE_MIN_INTERVAL_MS) {
        return this.user
      }
      try {
        return await this.loadProfile()
      } catch {
        // 资料刷新失败不影响页面：本地已有缓存值可用
        return this.user
      }
    },
    async logout() {
      try {
        await logoutApi()
      } catch {
        // ignore
      }
      this.clear()
    },
    /**
     * 退出所有设备：服务端会吊销该账号的全部会话（含刷新令牌），
     * 其他设备上的登录状态会立即失效，需要重新登录。
     */
    async logoutAll() {
      try {
        await logoutApi(true)
      } catch {
        // 即使请求失败也要清掉本地状态，避免残留已失效的令牌
      }
      this.clear()
    }
  }
})
