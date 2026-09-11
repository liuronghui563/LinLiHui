<template>
  <section class="hot">
    <div class="head">
      <div>
        <h2>{{ title }}</h2>
        <p>按昨日 6 点起的评论热度每日 6 点刷新{{ rankDate ? ` · ${rankDate}` : '' }}</p>
      </div>
      <router-link v-if="moreTo" :to="moreTo">全部</router-link>
    </div>
    <ol class="list">
      <li v-for="item in visible" :key="item.postId">
        <span class="no">{{ item.rankNo }}</span>
        <div class="body">
          <router-link class="author" :to="`/users/${item.authorId}`">
            <img :src="displayAvatar({ id: item.authorId, avatar: item.authorAvatar })" alt="" />
            <span>{{ item.authorName }}</span>
          </router-link>
          <p>{{ excerpt(item.content) }}</p>
          <span class="meta">
            <HeatBadge :value="item.heatScore" />
            · 评论 {{ item.commentCount || 0 }} · ♥ {{ item.likeCount || 0 }}
          </span>
        </div>
        <router-link class="go" :to="{ path: '/plaza', hash: `#post-${item.postId}` }">查看</router-link>
      </li>
      <li v-if="!items.length" class="empty">暂无热点，去广场分享生活吧</li>
    </ol>
    <button
      v-if="items.length > preview"
      type="button"
      class="more"
      @click="expanded = !expanded"
    >
      {{ expanded ? '收起排行榜' : `展开全部（最多 ${max} 条）` }}
    </button>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { displayAvatar } from '../utils/user'
import HeatBadge from './HeatBadge.vue'

const props = defineProps({
  title: { type: String, default: '每日评论热点' },
  items: { type: Array, default: () => [] },
  rankDate: { type: String, default: '' },
  preview: { type: Number, default: 3 },
  max: { type: Number, default: 16 },
  moreTo: { type: String, default: '' }
})

const expanded = ref(false)
const visible = computed(() => {
  const list = props.items.slice(0, props.max)
  return expanded.value ? list : list.slice(0, props.preview)
})

function excerpt(text) {
  const value = String(text || '').replace(/\s+/g, ' ').trim()
  return value.length > 72 ? `${value.slice(0, 72)}…` : value
}
</script>

<style scoped>
.hot {
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow-soft);
  padding: 20px;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 8px;
}

.head h2 {
  margin: 0;
  font-size: 18px;
}

.head p {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 13px;
}

.head a {
  color: var(--accent);
  font-size: 14px;
  white-space: nowrap;
}

.list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.list li {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 0;
  border-top: 1px solid var(--line);
}

.list li:first-child {
  border-top: none;
}

.no {
  flex: 0 0 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: var(--bg-deep);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.list li:nth-child(-n + 3) .no {
  background: #c45c4a;
}

.body {
  flex: 1;
  min-width: 0;
}

.author {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--ink);
  font-weight: 700;
}

.author img {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
  background: #efe4d6;
}

.body p {
  margin: 6px 0 0;
  line-height: 1.5;
}

.meta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  color: var(--muted);
  font-size: 12px;
}

.go {
  color: var(--accent);
  white-space: nowrap;
  font-weight: 600;
}

.empty {
  color: var(--muted);
}

.more {
  margin-top: 8px;
  width: 100%;
  border: 1px dashed var(--line);
  background: transparent;
  color: var(--accent);
  padding: 10px;
  border-radius: 12px;
  cursor: pointer;
  font-weight: 600;
}
</style>
