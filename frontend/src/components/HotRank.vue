<template>
  <section class="hot panel">
    <div class="panel-head">
      <h2>{{ title }}</h2>
      <router-link v-if="moreTo" class="panel-more" :to="moreTo">全部 →</router-link>
    </div>

    <p class="hot-note">
      按昨日 6 点起的评论热度排序，每日 6 点刷新{{ rankDate ? ` · 数据日期 ${rankDate}` : '' }}
    </p>

    <SkeletonList v-if="loading" :count="3" />

    <StateBlock
      v-else-if="!items.length"
      inline
      variant="empty"
      title="榜单还没生成"
      desc="广场里有人评论之后，这里会每天自动排出热点。"
    >
      <router-link class="btn btn--ghost btn--sm" to="/discover">去广场看看</router-link>
    </StateBlock>

    <template v-else>
      <ol class="rank-list">
        <li v-for="item in visible" :key="item.postId" :class="{ 'is-top': item.rankNo <= 3 }">
          <span class="rank-no">{{ item.rankNo }}</span>
          <div class="rank-body">
            <router-link class="rank-author" :to="`/users/${item.authorId}`">
              <img :src="displayAvatar({ id: item.authorId, avatar: item.authorAvatar, nickname: item.authorName })" alt="" />
              <span>{{ item.authorName || '邻里用户' }}</span>
            </router-link>
            <p class="rank-text">{{ excerpt(item.content) }}</p>
            <span class="rank-meta">
              <HeatBadge :value="item.heatScore" />
              <span>评论 {{ item.commentCount || 0 }}</span>
              <span>赞 {{ item.likeCount || 0 }}</span>
            </span>
          </div>
          <router-link class="rank-go" :to="{ path: '/discover', hash: `#post-${item.postId}` }">查看</router-link>
        </li>
      </ol>

      <button
        v-if="items.length > preview"
        type="button"
        class="btn btn--quiet btn--block more"
        @click="expanded = !expanded"
      >
        {{ expanded ? '收起' : `展开全部（共 ${Math.min(items.length, max)} 条）` }}
      </button>
    </template>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { displayAvatar } from '../utils/user'
import HeatBadge from './HeatBadge.vue'
import SkeletonList from './SkeletonList.vue'
import StateBlock from './StateBlock.vue'

const props = defineProps({
  title: { type: String, default: '每日评论热点' },
  items: { type: Array, default: () => [] },
  rankDate: { type: String, default: '' },
  preview: { type: Number, default: 3 },
  max: { type: Number, default: 16 },
  moreTo: { type: String, default: '' },
  loading: { type: Boolean, default: false }
})

const expanded = ref(false)
const visible = computed(() => {
  const list = props.items.slice(0, props.max)
  return expanded.value ? list : list.slice(0, props.preview)
})

function excerpt(text) {
  const value = String(text || '').replace(/\s+/g, ' ').trim()
  return value.length > 64 ? `${value.slice(0, 64)}…` : value
}
</script>

<style scoped>
.hot-note {
  margin: calc(var(--sp-2) * -1) 0 var(--sp-4);
  font-size: 12px;
  line-height: 1.43;
  letter-spacing: -0.12px;
  color: var(--muted-2);
}

.rank-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.rank-list > li {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-3);
  padding: var(--sp-4) 0;
  border-top: 1px solid var(--line);
}

.rank-list > li:first-child {
  border-top: 0;
  padding-top: 0;
}

.rank-no {
  flex: none;
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: var(--r-xs);
  background: var(--pearl);
  color: var(--muted);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  margin-top: 1px;
}

/* 只有前三名点亮强调色，其余保持中性——避免整列都在喊 */
.is-top .rank-no {
  background: var(--accent);
  color: #fff;
}

.rank-body {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 5px;
}

.rank-author {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: 13px;
  font-weight: 600;
  letter-spacing: -0.1px;
  color: var(--ink-2);
}

.rank-author img {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--parchment);
  box-shadow: 0 0 0 1px var(--line);
}

.rank-author:hover { color: var(--accent); }

.rank-text {
  font-size: 14px;
  line-height: 1.5;
  letter-spacing: -0.224px;
  color: var(--ink);
}

.rank-meta {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-3);
  font-size: 12px;
  letter-spacing: -0.12px;
  color: var(--muted-2);
}

.rank-go {
  flex: none;
  font-size: 13px;
  font-weight: 600;
  color: var(--accent);
  white-space: nowrap;
  margin-top: 1px;
}

.rank-go:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.more {
  margin-top: var(--sp-4);
  font-size: 14px;
}
</style>
