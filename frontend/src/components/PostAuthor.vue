<template>
  <component
    :is="userId ? 'router-link' : 'span'"
    class="post-author"
    :class="`size-${size}`"
    :to="userId ? `/users/${userId}` : undefined"
  >
    <img :src="src" :alt="name || '头像'" loading="lazy" @error="onError" />
    <span class="name">{{ name || '邻里用户' }}</span>
  </component>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { displayAvatar, defaultAvatar } from '../utils/user'

/**
 * 作者胶囊。
 *
 * 新增 size 尺寸：此前固定 32px 头像，在列表页每行都占掉一整块高度；
 * 列表里用 xs，详情页用 md，视觉密度才有层次。
 * 另外头像加载失败时回退到默认图，避免出现破图图标。
 */
const props = defineProps({
  userId: { type: [Number, String], default: null },
  name: { type: String, default: '' },
  avatar: { type: String, default: '' },
  size: { type: String, default: 'sm' } // xs | sm | md
})

const failed = ref(false)
const src = computed(() => (failed.value
  ? defaultAvatar(props.userId, props.name)
  : displayAvatar({ id: props.userId, avatar: props.avatar, nickname: props.name })))

watch(() => props.avatar, () => {
  failed.value = false
})

function onError() {
  failed.value = true
}
</script>

<style scoped>
/* 作者胶囊只写尺寸差异，颜色与链接态一律走令牌 */
.post-author {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  color: inherit;
  font-weight: 600;
  font-size: 14px;
  letter-spacing: -0.224px;
}

.post-author img {
  border-radius: var(--r-pill);
  object-fit: cover;
  background: var(--parchment);
  flex-shrink: 0;
}

.size-xs img { width: 20px; height: 20px; }
.size-sm img { width: 26px; height: 26px; }
.size-md img { width: 32px; height: 32px; }

.size-xs { font-size: 12px; gap: 6px; letter-spacing: -0.12px; }
.size-md { font-size: 14px; }

.name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

a.post-author:hover .name {
  color: var(--accent);
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>
