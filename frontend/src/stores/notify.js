import { defineStore } from 'pinia'
import { fetchUnreadCount } from '../api/notify'

/**
 * 未读消息数（导航角标）的唯一来源。
 *
 * 为什么收成一个 store：此前 AppShell 在**每次路由变化**时都请求一次
 * `/notify/unread-count`，而消息页自己又请求一次——打开消息页会同时发两次，
 * 在页面上点几下就是十几次 `COUNT(*)`。角标是全局唯一的一个数字，
 * 却由两个组件各自去查库，属于典型的「同一份数据多处拉取」。
 *
 * 三条约束，缺一条都会退化成高频轮询：
 *   1. **最短刷新间隔**：`MIN_INTERVAL_MS` 内的重复请求直接复用上次结果，不打后端；
 *   2. **并发去重**：同一时刻只有一个请求在飞，其他调用方共享同一个 Promise；
 *   3. **本地可写**：标记已读 / 全部已读后就地改数字，不必为一次点击再查一次库。
 *
 * 刻意**不做定时轮询**。通知只可能因别人操作而产生，而用户切换页面本身就会触发
 * 一次（受最短间隔约束），定时器的边际收益很小，代价却是每个在线用户
 * 恒定的每分钟一次 COUNT。真要更实时，应该上 SSE（见 docs/ROADMAP.md 12.3），
 * 而不是把轮询调密——调密只是把库的压力换成「看起来快了一点」。
 */

/** 两次真实请求之间的最短间隔。路由变化再频繁也不会超过这个频率。 */
export const MIN_INTERVAL_MS = 30_000

/* 下面两个是模块级变量而不是 state：
   - 它们不是 UI 状态，放进 state 只会让 Vue 去代理一个 Promise；
   - 全应用只应有一份，放在模块作用域天然共享。 */

/** 上一次真实请求的时间戳（0 表示从未请求过） */
let lastLoadedAt = 0
/** 正在飞行的请求，用于合并同一时刻的多个调用方 */
let inflight = null

export const useNotifyStore = defineStore('notify', {
  state: () => ({
    unreadCount: 0,
    /**
     * 是否成功取到过真实值。
     *
     * 失败时保持 false 并让角标不显示：notify-service 未部署时
     * 显示一个「0」会让人以为确实没有未读消息，而事实是根本不知道。
     */
    loaded: false
  }),

  getters: {
    hasUnread: (s) => s.unreadCount > 0
  },

  actions: {
    /**
     * 刷新未读数。
     *
     * @param {{ force?: boolean }} options force=true 时忽略最短间隔（例如刚做完
     *        一个会改变未读数的操作），但仍然参与并发去重
     * @returns {Promise<number>} 当前的未读数
     */
    async refresh({ force = false } = {}) {
      if (!force && lastLoadedAt && Date.now() - lastLoadedAt < MIN_INTERVAL_MS) {
        return this.unreadCount
      }
      if (inflight) {
        return inflight
      }
      inflight = (async () => {
        try {
          const res = await fetchUnreadCount()
          this.unreadCount = Math.max(0, Number(res?.data?.count) || 0)
          this.loaded = true
        } catch {
          // 静默降级：通知服务不可用不应该影响外壳渲染
          this.loaded = false
        } finally {
          // 失败也记录时间：通知服务挂掉时，每次路由变化都重试同样是没有意义的压力
          lastLoadedAt = Date.now()
          inflight = null
        }
        return this.unreadCount
      })()
      return inflight
    },

    /** 标记单条已读后就地扣减，避免为一次点击再查一次库。 */
    decrease(n = 1) {
      this.unreadCount = Math.max(0, this.unreadCount - n)
    },

    /** 全部标记已读后就地清零。 */
    clear() {
      this.unreadCount = 0
    },

    /**
     * 用已知的准确数字直接覆盖。
     *
     * 消息页在「只看未读」时，列表接口的 totalElements 就是未读数——
     * 顺手写回来，比再发一次 COUNT 请求更准也更省。
     */
    setCount(count) {
      this.unreadCount = Math.max(0, Number(count) || 0)
      this.loaded = true
    },

    /** 退出登录 / 切换账号时重置，避免下一个账号看到上一个人的角标。 */
    reset() {
      this.unreadCount = 0
      this.loaded = false
      lastLoadedAt = 0
      inflight = null
    }
  }
})
