<template>
  <div class="stars" :class="{ readonly }">
    <button
      v-for="n in 5"
      :key="n"
      type="button"
      class="star"
      :class="{ on: n <= filled }"
      :disabled="readonly"
      :aria-label="`${n} 星`"
      @click="onPick(n)"
    >
      ★
    </button>
    <span v-if="!hideMeta" class="meta">{{ displayAvg }} · {{ count || 0 }} 人评</span>
    <span v-if="!hideMeta && !readonly && myScore" class="mine">我评 {{ myScore }} 星</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'

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

const displayAvg = computed(() => {
  const value = Number(props.avg) || 0
  return value.toFixed(1)
})

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
  gap: 4px;
}

.star {
  border: none;
  background: transparent;
  color: #d5d0c6;
  font-size: 22px;
  line-height: 1;
  padding: 0 1px;
  cursor: pointer;
}

.star.on {
  color: #e2b23a;
}

.readonly .star {
  cursor: default;
}

.meta,
.mine {
  margin-left: 8px;
  color: var(--muted);
  font-size: 13px;
}
</style>
