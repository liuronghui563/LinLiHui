<template>
  <div class="state" :class="[`state--${variant}`, { 'state--inline': inline }]">
    <span class="state-icon" aria-hidden="true" v-html="icon" />
    <p class="state-title">{{ resolvedTitle }}</p>
    <p v-if="resolvedDesc" class="state-desc">{{ resolvedDesc }}</p>
    <div v-if="$slots.default" class="state-action">
      <slot />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

/**
 * 统一的空态 / 错误态。
 *
 * 此前各页面只有一行灰字「暂无数据」，或直接把后端异常原文暴露给用户；
 * 加载中则完全没有反馈（首屏是空白）。这里把三种状态收成一套组件，
 * 并提供可选的操作按钮，让空态能「引导下一步」而不是干瞪眼。
 */
const props = defineProps({
  variant: { type: String, default: 'empty' }, // empty | error
  title: { type: String, default: '' },
  desc: { type: String, default: '' },
  inline: { type: Boolean, default: false }
})

const stroke = 'fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"'
const ICONS = {
  empty: `<svg viewBox="0 0 24 24" ${stroke}><path d="M4 8.5 12 4l8 4.5-8 4.5Z"/><path d="M4 8.5V16l8 4.5 8-4.5V8.5"/><path d="M12 13v7.5"/></svg>`,
  error: `<svg viewBox="0 0 24 24" ${stroke}><path d="M12 4.5 21 19.5H3Z"/><path d="M12 10v4"/><path d="M12 17h.01"/></svg>`
}

const icon = computed(() => ICONS[props.variant] || ICONS.empty)

const resolvedTitle = computed(() => {
  if (props.title) return props.title
  return props.variant === 'error' ? '加载失败' : '这里还没有内容'
})

const resolvedDesc = computed(() => {
  if (props.desc) return props.desc
  return props.variant === 'error'
    ? '请稍后重试，或检查网络连接。'
    : '换个筛选条件看看，或者成为第一个发布的人。'
})
</script>

<style scoped>
/* 外观全部来自全局 .state / .state-icon / .state-title / .state-desc / .state-action，
   这里只保留本组件特有的差异：内联态左右留白更窄。 */
.state--inline {
  padding-left: var(--sp-4);
  padding-right: var(--sp-4);
}
</style>
