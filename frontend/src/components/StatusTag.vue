<template>
  <span class="tag" :class="`tag--${tone}`">
    <i v-if="dot" class="tag-dot" />
    {{ label }}
  </span>
</template>

<script setup>
import { computed } from 'vue'

/**
 * 求助状态标签。
 *
 * 原实现把「状态 → 文案」的映射表复制在 HomeView、AidsView、UserHomeView 三处，
 * 且一律渲染成无色的纯文本（`{{ statusText(item.status) }}`），
 * 列表里扫读不出哪些还等着人接、哪些已经完成。
 * 这里把映射收敛为唯一来源，并配上颜色语义：
 * 橙=待接单（需要行动）、青=进行中、绿=已完成、灰=已取消。
 */
const STATUS = {
  OPEN: { label: '待接单', tone: 'open' },
  ACCEPTED: { label: '进行中', tone: 'accepted' },
  DONE: { label: '已完成', tone: 'done' },
  CANCELLED: { label: '已取消', tone: 'cancelled' }
}

const props = defineProps({
  status: { type: String, default: '' },
  // 频道标签复用同一套外观
  label: { type: String, default: '' },
  tone: { type: String, default: '' },
  dot: { type: Boolean, default: false }
})

const meta = computed(() => STATUS[props.status] || null)
const label = computed(() => props.label || meta.value?.label || props.status || '未知')
const tone = computed(() => props.tone || meta.value?.tone || 'outline')
</script>
