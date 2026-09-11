<template>
  <button
    type="button"
    class="heart-like"
    :class="{ on: liked }"
    :aria-pressed="liked"
    :aria-label="liked ? '取消点赞' : '点赞'"
    @click="$emit('toggle')"
  >
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path
        d="M12.1 21.35 10.6 20C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.6 11.54l-1.3 1.31Z"
      />
    </svg>
    <span>{{ count || 0 }}</span>
  </button>
</template>

<script setup>
defineProps({
  liked: { type: Boolean, default: false },
  count: { type: Number, default: 0 }
})

defineEmits(['toggle'])
</script>

<style scoped>
.heart-like {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--line);
  background: #fff;
  color: var(--muted);
  padding: 6px 12px;
  border-radius: 999px;
  cursor: pointer;
}

.heart-like svg {
  width: 18px;
  height: 18px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.7;
}

.heart-like.on {
  color: #e2556f;
  border-color: rgba(226, 85, 111, 0.35);
  background: #fff5f7;
}

.heart-like.on svg {
  fill: #e2556f;
  stroke: #e2556f;
}
</style>
