<template>
  <span class="heat-badge" :title="`热度 ${score}（刷到 +1 / 点赞 +5 / 评论 +10）`">
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 2s6 6.2 6 11.2A6 6 0 0 1 6 13.2C6 8.2 12 2 12 2Zm0 20a8 8 0 0 0 8-8c0-3.7-2-7.2-4.4-10.1.8 2.4.6 4.4-.4 5.8-1.2 1.7-3.2 2.2-3.2 2.2s.9-2.1-.4-4.4C10.2 5.7 12 2 12 2Z" />
    </svg>
    <span>{{ score }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  value: { type: [Number, String], default: 0 }
})

const score = computed(() => Number(props.value) || 0)
</script>

<style scoped>
.heat-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #c45c28;
  font-weight: 700;
  font-size: 13px;
  line-height: 1;
}

.heat-badge svg {
  width: 16px;
  height: 16px;
  fill: #e07a3d;
}
</style>
