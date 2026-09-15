import { defineStore } from 'pinia'

/**
 * 偏好设置：主题（深浅色）与国家和地区 / 语言。
 *
 * 为什么单独一个 store，而不是塞进 auth：这两项是**设备级**偏好，
 * 与账号无关——退出登录不该把深色模式一起重置掉，换台设备也该各自记各自的。
 * 所以它们存 localStorage，不跟着令牌走。
 *
 * 全部读写都对 `window` / `localStorage` 做了存在性判断：这套代码会被
 * `npm run test:shell` 在纯 Node 里 SSR 渲染（没有 window，localStorage 是
 * 测试注入的替身），少一个判断就会让整页渲染直接抛错。
 */

const THEME_KEY = 'cq_theme'
const LOCALE_KEY = 'cq_locale'

/** 深色模式只认这三个取值；'system' 是默认，跟随操作系统 */
export const THEME_OPTIONS = [
  { value: 'system', label: '跟随系统' },
  { value: 'light', label: '浅色' },
  { value: 'dark', label: '深色' }
]

/**
 * 国家和地区 / 语言。
 *
 * 合成一项而不是拆成两个下拉：这两者实际上绑在一起决定「用哪种中文」，
 * 拆开会出现「中国大陆 + English」这种没人要的组合。
 */
export const LOCALE_OPTIONS = [
  { value: 'zh-CN', region: '中国大陆', language: '简体中文' },
  { value: 'zh-HK', region: '中国香港', language: '繁體中文' },
  { value: 'zh-TW', region: '中国台湾', language: '繁體中文' },
  { value: 'en-US', region: '美国', language: 'English' },
  { value: 'ja-JP', region: '日本', language: '日本語' }
]

const DEFAULT_THEME = 'system'
const DEFAULT_LOCALE = 'zh-CN'

const hasWindow = () => typeof window !== 'undefined'

function read(key, fallback) {
  if (!hasWindow() || !window.localStorage) return fallback
  try {
    const raw = window.localStorage.getItem(key)
    return raw || fallback
  } catch {
    // 隐私模式下 localStorage 可能直接抛错，读不到就用默认值，不该影响页面
    return fallback
  }
}

/**
 * 读一个「取值必须落在白名单里」的偏好。
 *
 * 不能直接用 read()：存储里可能是**未知值**——旧版本写过、手改过、
 * 或者同一台机器上跑过别的分支。未知值留在 state 里的后果不是「看起来怪」，
 * 而是设置页上的控件**一个都不选中**（`aria-pressed` 全 false）、
 * `<select>` 显示空白，用户点之前完全不知道当前是什么状态。
 * 所以读的时候就把白名单之外的值打回默认值。
 */
function readEnum(key, allowed, fallback) {
  const raw = read(key, fallback)
  return allowed.includes(raw) ? raw : fallback
}

const THEME_VALUES = THEME_OPTIONS.map((o) => o.value)
const LOCALE_VALUES = LOCALE_OPTIONS.map((o) => o.value)

function write(key, value) {
  if (!hasWindow() || !window.localStorage) return
  try {
    window.localStorage.setItem(key, value)
  } catch {
    // 写不进去只意味着「下次打开恢复默认」，不是需要打断用户的错误
  }
}

function systemPrefersDark() {
  if (!hasWindow() || typeof window.matchMedia !== 'function') return false
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

/** 把 'system' 解析成实际生效的 'light' / 'dark' */
function resolveTheme(pref, prefersDark = systemPrefersDark()) {
  if (pref === 'dark' || pref === 'light') return pref
  // 未知值也走「跟随系统」：resolveTheme 拿到的已经是 readEnum 过滤过的合法值，
  // 这里的兜底只防调用方直接传进来一个野值
  return prefersDark ? 'dark' : 'light'
}

/** 把主题写到 <html data-theme>，深浅两套令牌靠这个属性切换（见 styles.css 2.1） */
function paintTheme(pref) {
  if (!hasWindow() || !document?.documentElement) return
  document.documentElement.dataset.theme = resolveTheme(pref)
}

/** 语言偏好落到 <html lang>，屏幕阅读器与浏览器翻译都读它 */
function paintLocale(locale) {
  if (!hasWindow() || !document?.documentElement) return
  document.documentElement.setAttribute('lang', locale)
  document.documentElement.dataset.locale = locale
}

/**
 * 首屏启动：在挂载应用之前把已保存的偏好落到 <html> 上。
 *
 * 必须在 main.js 里**先于 mount** 调用。放到组件里再做的话，浅色界面会先画一帧，
 * 再跳成深色——也就是深色模式用户每次刷新都闪一下白屏，而这个闪烁比慢 50ms 刺眼得多。
 */
export function applyStoredPrefs() {
  paintTheme(readEnum(THEME_KEY, THEME_VALUES, DEFAULT_THEME))
  paintLocale(readEnum(LOCALE_KEY, LOCALE_VALUES, DEFAULT_LOCALE))
}

/** 系统主题变化时重新上色（只在偏好为 'system' 时有意义） */
function bindSystemTheme(onChange) {
  if (!hasWindow() || typeof window.matchMedia !== 'function') return () => {}
  const media = window.matchMedia('(prefers-color-scheme: dark)')
  const handler = () => onChange()
  // Safari 14 之前只有 addListener，这里两种都试一次
  if (typeof media.addEventListener === 'function') {
    media.addEventListener('change', handler)
    return () => media.removeEventListener('change', handler)
  }
  if (typeof media.addListener === 'function') {
    media.addListener(handler)
    return () => media.removeListener(handler)
  }
  return () => {}
}

export const usePrefsStore = defineStore('prefs', {
  state: () => ({
    theme: readEnum(THEME_KEY, THEME_VALUES, DEFAULT_THEME),
    locale: readEnum(LOCALE_KEY, LOCALE_VALUES, DEFAULT_LOCALE),
    /** 系统当前是否偏好深色。只用于在设置页显示「跟随系统 → 实际是深色」 */
    systemDark: systemPrefersDark()
  }),
  getters: {
    /** 实际生效的主题（把 'system' 解析掉） */
    resolvedTheme: (s) => resolveTheme(s.theme, s.systemDark),
    themeLabel: (s) => THEME_OPTIONS.find((o) => o.value === s.theme)?.label || '跟随系统',
    localeOption: (s) => LOCALE_OPTIONS.find((o) => o.value === s.locale) || LOCALE_OPTIONS[0],
    /** 例如「中国大陆 · 简体中文」 */
    localeLabel() {
      return `${this.localeOption.region} · ${this.localeOption.language}`
    }
  },
  actions: {
    setTheme(value) {
      this.theme = value
      write(THEME_KEY, value)
      paintTheme(value)
    },
    setLocale(value) {
      this.locale = value
      write(LOCALE_KEY, value)
      paintLocale(value)
    },
    /**
     * 订阅系统主题变化。
     *
     * 无论当前偏好是不是 'system' 都要更新 `systemDark`：设置页上那句
     * 「跟随系统（当前深色）」要在用户还没切过去之前就显示正确。
     *
     * 幂等：重复调用只会复用同一个订阅，不会叠加监听。
     */
    bindSystem() {
      if (this._unbind) return
      this._unbind = bindSystemTheme(() => {
        this.systemDark = systemPrefersDark()
        if (this.theme === 'system') paintTheme('system')
      })
    },
    unbindSystem() {
      this._unbind?.()
      this._unbind = null
    }
  }
})
