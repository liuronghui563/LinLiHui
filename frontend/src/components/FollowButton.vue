<template>
  <div class="relation-actions" :class="`relation-actions--${tone}`">
    <button
      v-if="!relation.blocked && !relation.blockedBy"
      type="button"
      class="btn btn--sm"
      :class="relation.following ? 'btn--ghost' : 'btn--secondary'"
      :disabled="busy"
      @click="onToggleFollow"
    >
      {{ relation.following ? '已关注' : '+ 关注' }}
    </button>

    <button
      type="button"
      class="btn btn--sm"
      :class="relation.blocked ? 'btn--ghost' : 'btn--danger'"
      :disabled="busy"
      @click="onToggleBlock"
    >
      {{ relation.blocked ? '已拉黑' : '拉黑' }}
    </button>

    <p v-if="relation.blockedBy" class="hint">对方已将你拉黑，双方内容互不可见。</p>
    <p v-else-if="error" class="hint hint--error" role="alert">{{ error }}</p>
  </div>
</template>

<script setup>
import { useRelation } from '../composables/useRelation'

/**
 * 关注 / 拉黑 按钮组。
 *
 * 行为全部在 useRelation 里（幂等、并发锁、失败提示），这里只负责渲染与确认弹窗。
 *
 * tone 决定它落在哪种表面上：
 *   - light（默认）：卡片、列表、白底——用 Action Blue 描边；
 *   - dark：个人主页的近黑封面——深色底上 Action Blue 会消失，改用 Sky Link Blue，
 *     拉黑的危险色也换成深色底专用的那一档。
 */
const props = defineProps({
  userId: { type: [Number, String], required: true },
  /**
   * 由列表页**一次性批量取好**的关系状态。
   *
   * 传了就不再各自请求：这是把「一页 50 行 = 50 次请求、约 300 条 SQL」
   * 压成「1 次请求、固定 6 条 SQL」的关键。
   * 不传（个人主页这种单点场景）才自己查一次，那时 1 次请求本来就是合理的。
   */
  initialRelation: { type: Object, default: null },
  tone: { type: String, default: 'light' } // light | dark
})

const emit = defineEmits(['changed'])

const { relation, busy, error, load, toggleFollow, toggleBlock } = useRelation(
  () => props.userId,
  () => props.initialRelation
)

async function onToggleFollow() {
  const next = await toggleFollow()
  if (next) emit('changed', next)
}

async function onToggleBlock() {
  const next = await toggleBlock()
  if (next) emit('changed', next)
}

// 已由父组件批量提供状态时不必再问一次
if (!props.initialRelation) load()
</script>

<style scoped>
/* 只有布局差异：按钮外观（胶囊 / 描边 / 危险态）全部走全局 .btn 变体。
   关注是状态切换，所以「关注」用描边胶囊（唯一强调色描边，不是蓝色实心块），
   「已关注」回到珍珠灰胶囊；拉黑是破坏性操作，走 .btn--danger 的细描边。 */
.relation-actions {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

.hint {
  flex-basis: 100%;
  font-size: 12px;
  line-height: 1.43;
  letter-spacing: -0.12px;
  color: var(--muted-2);
}

/* ---------- 近黑封面上的变体 ----------
   深色底上 Action Blue（#0066cc）几乎看不见，必须换成 Sky Link Blue；
   珍珠灰胶囊在近黑上会变成一块刺眼的白，改成透明 + 白色描边。 */
.relation-actions--dark :deep(.btn--secondary) {
  border-color: var(--accent-on-dark);
  color: var(--accent-on-dark);
}

.relation-actions--dark :deep(.btn--secondary:hover:not(:disabled)) {
  background: rgba(41, 151, 255, 0.12);
}

.relation-actions--dark :deep(.btn--ghost) {
  background: transparent;
  border-color: var(--line-on-dark-strong);
  color: var(--on-dark);
}

.relation-actions--dark :deep(.btn--ghost:hover:not(:disabled)) {
  border-color: #fff;
  color: #fff;
}

.relation-actions--dark :deep(.btn--danger) {
  border-color: rgba(255, 107, 96, 0.4);
  color: var(--danger-on-dark);
}

.relation-actions--dark :deep(.btn--danger:hover:not(:disabled)) {
  background: rgba(255, 107, 96, 0.12);
  border-color: var(--danger-on-dark);
}

.relation-actions--dark .hint {
  color: var(--on-dark-muted);
}

.relation-actions--dark .hint--error {
  color: var(--danger-on-dark);
}

/* 失败提示在浅底上仍是危险色 */
.hint--error {
  color: var(--danger);
}
</style>
