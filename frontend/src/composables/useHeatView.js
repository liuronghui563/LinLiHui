import { nextTick, onBeforeUnmount, watch } from 'vue'
import { recordPostView } from '../api/community'

/**
 * 进入视口时记录一次浏览（同一篇只记一次），并回填热度。
 *
 * 改动：支持传入组件根元素做作用域限定。原实现用
 * `document.querySelectorAll('[data-post-id]')` 全局查找，
 * 页面上只要存在第二个列表（例如首页同时有「最新求助」和「邻里动态」，
 * 或未来加搜索页），就会出现重复观察与重复计热。
 */
export function useHeatView(posts, rootRef = null) {
  const seen = new Set()

  const observer = new IntersectionObserver((entries) => {
    entries.forEach(async (entry) => {
      if (!entry.isIntersecting) return
      const id = entry.target.getAttribute('data-post-id')
      if (!id || seen.has(id)) return
      seen.add(id)
      try {
        const res = await recordPostView(id)
        const data = res.data
        if (!data) return
        const list = posts.value || []
        const idx = list.findIndex((p) => String(p.id) === String(id))
        if (idx >= 0) {
          list[idx].heatScore = data.heatScore
          list[idx].viewCount = data.viewCount
        }
      } catch {
        // 记录失败则允许重试
        seen.delete(id)
      }
    })
  }, { threshold: 0.5 })

  async function observeCards() {
    await nextTick()
    const root = rootRef?.value || document
    root.querySelectorAll('[data-post-id]').forEach((el) => observer.observe(el))
  }

  watch(posts, observeCards, { flush: 'post' })
  onBeforeUnmount(() => observer.disconnect())

  return { observeCards }
}
