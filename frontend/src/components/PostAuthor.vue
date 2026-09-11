<template>
  <component
    :is="userId ? 'router-link' : 'span'"
    class="post-author"
    :to="userId ? `/users/${userId}` : undefined"
  >
    <img :src="src" :alt="name || '头像'" />
    <span class="name">{{ name || '邻里' }}</span>
  </component>
</template>

<script setup>
import { computed } from 'vue'
import { displayAvatar } from '../utils/user'

const props = defineProps({
  userId: { type: [Number, String], default: null },
  name: { type: String, default: '' },
  avatar: { type: String, default: '' }
})

const src = computed(() => displayAvatar({ id: props.userId, avatar: props.avatar }))
</script>

<style scoped>
.post-author {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: inherit;
  font-weight: 700;
}

.post-author img {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
  background: #efe4d6;
  flex-shrink: 0;
}

.name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

a.post-author:hover .name {
  color: var(--accent);
}
</style>
