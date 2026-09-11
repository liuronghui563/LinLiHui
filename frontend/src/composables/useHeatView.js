import { nextTick, onBeforeUnmount, watch } from 'vue'
import { recordPostView } from '../api/community'

export function useHeatView(posts) {
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
        seen.delete(id)
      }
    })
  }, { threshold: 0.5 })

  async function observeCards() {
    await nextTick()
    document.querySelectorAll('[data-post-id]').forEach((el) => observer.observe(el))
  }

  watch(posts, observeCards, { flush: 'post' })
  onBeforeUnmount(() => observer.disconnect())

  return { observeCards }
}
