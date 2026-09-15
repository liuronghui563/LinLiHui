<template>
  <p class="skeleton-list" :class="{ 'skeleton-list--cards': variant === 'card' }" aria-hidden="true">
    <span v-for="n in count" :key="n" class="skeleton" :class="variant === 'card' ? 'skeleton-card' : 'skeleton-row'" />
  </p>
  <p class="sr-only" role="status">内容加载中</p>
</template>

<script setup>
/**
 * 加载骨架。
 *
 * 此前列表页在请求返回前是完全空白的，用户无法判断是「没数据」还是「还在加载」；
 * 骨架屏能同时表达「有内容正在来」和大致的内容结构。
 */
defineProps({
  count: { type: Number, default: 3 },
  variant: { type: String, default: 'row' } // row | card
})
</script>

<style scoped>
/* 骨架块的外观来自全局 .skeleton / .skeleton-card；这里只负责排布与渐隐层次 */
.skeleton-list {
  display: grid;
  gap: var(--sp-4);
  margin: 0;
}

.skeleton-row {
  height: 18px;
}

.skeleton-row:nth-child(even) {
  width: 72%;
}

.skeleton-list--cards .skeleton-card:nth-child(2) {
  opacity: 0.72;
}

.skeleton-list--cards .skeleton-card:nth-child(3) {
  opacity: 0.46;
}
</style>
