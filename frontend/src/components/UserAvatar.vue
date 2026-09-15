<template>
  <component
    :is="linkTo ? 'router-link' : 'span'"
    class="user-avatar"
    :class="[size, presenceClass(user?.presenceStatus)]"
    :to="linkTo || undefined"
    :title="titleText"
  >
    <img :src="src" :alt="user?.nickname || '头像'" @error="onError" />
    <i v-if="showStatus" class="status-dot" :aria-label="presenceLabel(user?.presenceStatus)" />
  </component>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { defaultAvatar, displayAvatar } from '../utils/user'
import { presenceClass, presenceLabel } from '../utils/presence'

/**
 * 用户头像。
 *
 * 改动：图片加载失败时回退到默认头像（此前会显示浏览器破图图标）；
 * 新增 showStatus 开关——在列表等密集场景里不需要状态小圆点。
 */
const props = defineProps({
  user: { type: Object, default: null },
  size: { type: String, default: 'md' }, // xs | sm | md | lg
  to: { type: [String, Object], default: '' },
  showStatus: { type: Boolean, default: true }
})

const failed = ref(false)
const linkTo = computed(() => props.to || '')
const src = computed(() => (
  failed.value ? defaultAvatar(props.user?.id, props.user?.nickname) : displayAvatar(props.user)
))

const titleText = computed(() => {
  const name = props.user?.nickname || '邻里'
  return props.showStatus ? `${name} · ${presenceLabel(props.user?.presenceStatus)}` : name
})

watch(() => props.user?.avatar, () => {
  failed.value = false
})

function onError() {
  failed.value = true
}
</script>

<style scoped>
.user-avatar {
  position: relative;
  display: inline-flex;
  flex-shrink: 0;
  border-radius: 50%;
}

/* 头像只带一圈发丝线，不带投影：它躺在表面上，而不是浮在表面上 */
.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
  display: block;
  background: var(--parchment);
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.06);
}

.xs { width: 22px; height: 22px; }
.sm { width: 32px; height: 32px; }
.md { width: 52px; height: 52px; }
.lg { width: 84px; height: 84px; }

.status-dot {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 28%;
  height: 28%;
  min-width: 9px;
  min-height: 9px;
  border-radius: 50%;
  border: 2px solid var(--canvas);
  background: #8e8e93;
}

/* 在线状态是语义色，不是品牌色：它不参与「单一强调色」的规则 */
.presence-online .status-dot { background: #34c759; }
.presence-busy .status-dot { background: #ff3b30; }
.presence-away .status-dot { background: #ff9f0a; }
.presence-studying .status-dot { background: #0071e3; }
.presence-offline .status-dot { background: #8e8e93; }
</style>
