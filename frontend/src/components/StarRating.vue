<template>
  <div class="stars" :class="{ readonly }">
    <span class="star-row" role="img" :aria-label="ariaText">
      <button
        v-for="n in 5"
        :key="n"
        type="button"
        class="star"
        :class="{ on: n <= filled }"
        :disabled="readonly"
        :aria-label="`评 ${n} 星`"
        @click="onPick(n)"
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
          <path d="m12 3.6 2.6 5.3 5.9.85-4.25 4.15 1 5.85L12 17l-5.25 2.75 1-5.85L3.5 9.75l5.9-.85Z" />
        </svg>
      </button>
    </span>

    <span v-if="!hideMeta" class="meta">
      <strong>{{ displayAvg }}</strong>
      <span class="text-muted">· {{ count || 0 }} 人评</span>
    </span>
    <span v-if="!hideMeta && !readonly && myScore" class="mine">我评 {{ myScore }} 星</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'

/**
 * 星级评分。
 *
 * 原实现用「★」字符渲染，不同系统/字体下字形差异很大（有的系统会渲染成彩色 emoji），
 * 与其他线性图标风格冲突；改为内联 SVG，形状与描边完全可控。
 */
const props = defineProps({
  avg: { type: Number, default: 0 },
  count: { type: Number, default: 0 },
  myScore: { type: Number, default: null },
  readonly: { type: Boolean, default: false },
  hideMeta: { type: Boolean, default: false }
})

const emit = defineEmits(['rate'])

const filled = computed(() => {
  if (!props.readonly && props.myScore) return props.myScore
  return Math.round(Number(props.avg) || 0)
})

const displayAvg = computed(() => (Number(props.avg) || 0).toFixed(1))

const ariaText = computed(() => `平均 ${displayAvg.value} 星，共 ${props.count || 0} 人评价`)

function onPick(n) {
  if (props.readonly) return
  emit('rate', n)
}
</script>

<style scoped>
.stars {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--sp-2);
}

.star-row {
  display: inline-flex;
  gap: 2px;
}

/* 未评 = 发丝灰，已评 = 唯一强调色；星星本身是「状态」，不做色块、不加阴影 */
.star {
  border: 0;
  padding: 0 1px;
  background: transparent;
  color: var(--line-strong);
  cursor: pointer;
  line-height: 0;
  transition: color 0.14s var(--ease), transform var(--press-out);
}

.star svg {
  width: 16px;
  height: 16px;
}

.star.on {
  color: var(--accent);
}

.star.on svg {
  fill: currentColor;
}

.star:not(.on):not(:disabled):hover {
  color: var(--muted-2);
}

.star:not(:disabled):active {
  transform: scale(0.96);
  transition: color var(--press-in), transform var(--press-in);
}

.readonly .star {
  cursor: default;
}

.meta,
.mine {
  font-size: 12px;
  letter-spacing: -0.12px;
  color: var(--muted-2);
}

.meta strong {
  color: var(--ink-2);
  font-size: 14px;
  font-variant-numeric: tabular-nums;
}

.mine {
  padding: 2px 8px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-pill);
  background: transparent;
  color: var(--muted);
}
</style>
