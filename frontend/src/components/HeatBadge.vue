<template>
  <span class="heat-badge" :class="{ 'is-hot': score >= 30 }" :title="hint">
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 3c.6 3 2.4 4 3.6 5.6A6.3 6.3 0 0 1 17 13a5 5 0 0 1-10 0c0-1.4.5-2.6 1.3-3.7.2 1 .8 1.8 1.6 2.2-.5-2.6.4-5.4 2.1-8.5Z" />
    </svg>
    <span>{{ score }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue'

/**
 * 热度徽标。
 *
 * 原实现是纯橙色粗体文字，在多条动态并排时非常抢眼却信息量有限。
 * 现在默认走中性色（不抢主标题），只有真正「热」的内容才点亮为强调色。
 */
const props = defineProps({
  value: { type: [Number, String], default: 0 }
})

const score = computed(() => Number(props.value) || 0)

const hint = computed(() => `热度 ${score.value} · 浏览 +1 / 点赞 +5 / 评论 +10`)
</script>

<style scoped>
.heat-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--muted-2);
  line-height: 1;
}

.heat-badge svg {
  width: 13px;
  height: 13px;
  fill: currentColor;
  opacity: 0.85;
}

.heat-badge.is-hot {
  color: var(--accent);
}

.heat-badge.is-hot svg {
  fill: var(--accent);
  opacity: 1;
}
</style>
