import { ref, unref, watch } from 'vue'
import { blockUser, fetchRelation, followUser, unblockUser, unfollowUser } from '../api/relation'

/**
 * 关注 / 拉黑的状态机。
 *
 * 抽成 composable 的原因与 useCaptcha 相同：这套逻辑（幂等、并发锁、失败提示、
 * 「换一行数据要重查、批量给过就别再问」）是真正会出错的部分，而它在浏览器里
 * 只能靠手点验证。做成 composable 之后，`npm run test:relation` 能直接把
 * 「关注 → 状态翻转 → 再点取关」「拉黑要先确认」「失败要有提示、且状态不乱跳」
 * 这些行为跑成断言。
 *
 * 后端语义（见 auth-service UserRelationService）：
 *   - 关注是幂等的；对方拉黑了我时，关注会被拒绝，但报错**不暴露**「被拉黑」；
 *   - 拉黑会解除双向关注，且过滤是双向的（我拉黑的 ∪ 拉黑我的）；
 *   - 每个写操作都返回**最新的完整关系**，所以前端直接用它覆盖本地状态即可，
 *     不必再补一次查询。
 */

/** 关系默认值。字段与后端 UserRelationResponse 一一对应。 */
export const EMPTY_RELATION = {
  following: false,
  followedBy: false,
  blocked: false,
  blockedBy: false,
  followerCount: 0,
  followingCount: 0
}

/** 允许传 getter 或 ref：组件里传 `() => props.userId`，测试里可以直接传 ref */
function get(value) {
  return typeof value === 'function' ? value() : unref(value)
}

function defaultConfirmBlock() {
  return window.confirm('拉黑后：双方内容互不可见，且会自动解除互相关注。确认拉黑？')
}

/**
 * @param userId          目标用户 id（getter / ref）
 * @param initialRelation 父组件批量取好的关系（getter / ref）；传了就不再各自请求
 * @param options.confirmBlock 拉黑前的二次确认，返回 falsy 表示用户取消。
 *                             可注入是为了能在 Node 里测试（没有 window.confirm）
 */
export function useRelation(userId, initialRelation = null, options = {}) {
  const confirmBlock = options.confirmBlock || defaultConfirmBlock

  const relation = ref({ ...EMPTY_RELATION, ...(get(initialRelation) || {}) })
  const busy = ref(false)
  const error = ref('')

  /** 用后端返回值整体覆盖：它已经是权威的完整关系，逐字段合并反而会留下脏位 */
  function setRelation(next) {
    relation.value = { ...EMPTY_RELATION, ...(next || {}) }
  }

  async function load() {
    error.value = ''
    try {
      const res = await fetchRelation(get(userId))
      if (res?.data) setRelation(res.data)
    } catch (e) {
      // 关系状态取不到时不阻塞页面，只是不显示已关注态
      error.value = e.message || '关系状态加载失败'
    }
  }

  /** 所有写操作的共同外壳：加锁、清错、用返回值覆盖状态、失败时给出提示 */
  async function run(action) {
    if (busy.value) return null
    busy.value = true
    error.value = ''
    try {
      const res = await action(get(userId))
      setRelation(res?.data)
      return relation.value
    } catch (e) {
      error.value = e.message || '操作失败，请稍后重试'
      return null
    } finally {
      busy.value = false
    }
  }

  function toggleFollow() {
    return run(relation.value.following ? unfollowUser : followUser)
  }

  async function toggleBlock() {
    // 取消拉黑是恢复性操作，不拦；拉黑是破坏性的，必须先确认
    if (!relation.value.blocked) {
      const ok = await confirmBlock()
      if (!ok) return null
    }
    return run(relation.value.blocked ? unblockUser : blockUser)
  }

  /** 父组件重新批量取过之后同步过来（切换列表、重新加载都会走到这里） */
  watch(() => get(initialRelation), (next) => {
    if (next) setRelation(next)
  })

  /** 换了一行数据就必须重查，否则按钮会显示上一行的状态 */
  watch(() => get(userId), () => {
    const preset = get(initialRelation)
    if (preset) {
      setRelation(preset)
      return
    }
    load()
  })

  return { relation, busy, error, load, setRelation, toggleFollow, toggleBlock }
}
