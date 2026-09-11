<template>
  <component
    :is="linkTo ? 'router-link' : 'span'"
    class="user-avatar"
    :class="[size, presenceClass(user?.presenceStatus)]"
    :to="linkTo || undefined"
    :title="titleText"
  >
    <img :src="displayAvatar(user)" :alt="user?.nickname || '头像'" />
    <i class="status-dot" :aria-label="presenceLabel(user?.presenceStatus)" />
  </component>
</template>

<script setup>
import { computed } from 'vue'
import { displayAvatar } from '../utils/user'
import { presenceClass, presenceLabel } from '../utils/presence'

const props = defineProps({
  user: { type: Object, default: null },
  size: { type: String, default: 'md' },
  to: { type: [String, Object], default: '' }
})

const linkTo = computed(() => props.to || '')
const titleText = computed(() => {
  const name = props.user?.nickname || '邻里'
  return `${name} · ${presenceLabel(props.user?.presenceStatus)}`
})
</script>

<style scoped>
.user-avatar {
  position: relative;
  display: inline-flex;
  flex-shrink: 0;
  border-radius: 50%;
  overflow: visible;
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
  display: block;
  background: #d7e4de;
  box-shadow: 0 0 0 2px #fff;
}

.sm {
  width: 44px;
  height: 44px;
}

.md {
  width: 64px;
  height: 64px;
}

.lg {
  width: 92px;
  height: 92px;
}

.status-dot {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 28%;
  height: 28%;
  min-width: 10px;
  min-height: 10px;
  border-radius: 50%;
  border: 2px solid #fff;
  background: #94a3b8;
  box-shadow: 0 0 0 1px rgba(20, 36, 31, 0.08);
}

.presence-online .status-dot {
  background: #22c55e;
}

.presence-busy .status-dot {
  background: #ef4444;
}

.presence-away .status-dot {
  background: #f59e0b;
}

.presence-studying .status-dot {
  background: #3b82f6;
}

.presence-offline .status-dot {
  background: #94a3b8;
}
</style>
