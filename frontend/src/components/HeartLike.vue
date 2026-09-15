<template>
  <button
    type="button"
    class="heart-like"
    :class="{ on: liked, bump }"
    :aria-pressed="liked"
    :aria-label="liked ? '取消点赞' : '点赞'"
    :disabled="busy"
    @click="onClick"
  >
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.7"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      <path
        d="M12.1 21.35 10.6 20C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.6 11.54l-1.3 1.31Z"
      />
    </svg>
    <span class="count">{{ count || 0 }}</span>
  </button>
</template>

<script setup>
import { ref } from 'vue'

/**
 * 点赞按钮。
 * 新增：请求期间禁用（避免连点产生重复请求）、点赞成功时的一次轻微回弹反馈。
 */
const props = defineProps({
  liked: { type: Boolean, default: false },
  count: { type: Number, default: 0 },
  busy: { type: Boolean, default: false }
})

const emit = defineEmits(['toggle'])
const bump = ref(false)

function onClick() {
  if (!props.liked) {
    bump.value = true
    window.setTimeout(() => { bump.value = false }, 260)
  }
  emit('toggle')
}
</script>

<style scoped>
/* 状态切换型按钮：不做色块，只在「灰 → Action Blue」之间克制地变色。
   形状/内距保持原有的小胶囊尺寸，避免撑高 post-ops 这一行。 */
.heart-like {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px 5px 10px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-pill);
  background: transparent;
  color: var(--muted);
  font-size: 14px;
  letter-spacing: -0.224px;
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  transition: color 0.16s var(--ease), border-color 0.16s var(--ease),
    transform var(--press-out);
}

.heart-like:hover:not(:disabled) {
  color: var(--ink-2);
  border-color: var(--muted-2);
}

/* 全站统一的按下微交互 */
.heart-like:active:not(:disabled) {
  transform: scale(0.96);
  transition: color var(--press-in), border-color var(--press-in),
    transform var(--press-in);
}

.heart-like:disabled {
  opacity: 0.42;
  cursor: progress;
}

.heart-like svg {
  width: 16px;
  height: 16px;
  transition: transform 0.22s var(--ease);
}

/* 已点赞：唯一的变化是颜色与描边转成强调色 + 心形实心 */
.heart-like.on {
  color: var(--accent);
  border-color: var(--accent);
}

.heart-like.on svg {
  fill: currentColor;
}

.heart-like.bump svg {
  transform: scale(1.22);
}
</style>
