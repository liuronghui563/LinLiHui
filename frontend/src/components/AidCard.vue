<template>
  <article class="aid-card">
    <router-link class="aid-hit" :to="`/aids/${item.id}`" :aria-label="item.title">
      <header class="aid-head">
        <StatusTag :status="item.status" dot />
        <span class="tag tag--outline">{{ item.category }}</span>
        <span v-if="showBoard" class="tag tag--outline">
          {{ item.board === 'CAMPUS' ? '校园' : '邻里' }}
        </span>
      </header>

      <h3 class="aid-title">{{ item.title }}</h3>
      <p class="aid-excerpt clamp-2">{{ item.content }}</p>
      <div v-if="item.images?.length" class="aid-thumbs">
        <img v-for="(url, i) in item.images.slice(0, 3)" :key="url + i" :src="url" alt="" />
      </div>

      <div class="aid-place">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M12 21s6.5-5.4 6.5-10.4A6.5 6.5 0 0 0 5.5 10.6C5.5 15.6 12 21 12 21Z" />
          <circle cx="12" cy="10.4" r="2.4" />
        </svg>
        <span>{{ item.address || '未填写地点' }}</span>
      </div>

      <footer class="aid-foot">
        <PostAuthor
          :user-id="item.publisherId"
          :name="item.publisherName"
          :avatar="item.publisherAvatar"
          size="xs"
        />
        <span class="aid-time">{{ relativeTime(item.createdAt) }}</span>
      </footer>
    </router-link>

    <div v-if="showRating" class="aid-rating">
      <StarRating :avg="item.ratingAvg || 0" :count="item.ratingCount || 0" readonly />
    </div>
  </article>
</template>

<script setup>
import { relativeTime } from '../utils/format'
import StatusTag from './StatusTag.vue'
import PostAuthor from './PostAuthor.vue'
import StarRating from './StarRating.vue'

/**
 * 求助卡片。
 *
 * 此前 AidsView / CampusView / UserHomeView 各自实现了一份求助卡片，
 * 且都没有状态标签与地点图标，列表里扫读不出「哪些还等着人接」。
 * 现在统一为一个组件，并让整张卡片可点击（而不是只有右下角一个「详情」链接）。
 */
defineProps({
  item: { type: Object, required: true },
  showRating: { type: Boolean, default: true },
  showBoard: { type: Boolean, default: false }
})
</script>

<style scoped>
/* 工具卡（store-utility-card）：白底 + 1px hairline + 18px 圆角 + 24px 内距。
   整张卡可点，所以卡片本身就是最大的点按目标。 */
.aid-card {
  display: grid;
  gap: var(--sp-4);
  padding: var(--sp-6);
  background: var(--canvas);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  transition: border-color 0.18s var(--ease);
}

.aid-card:hover {
  border-color: var(--line-strong);
}

.aid-hit {
  display: grid;
  gap: var(--sp-3);
}

.aid-head {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

/* 卡片标题走展示字号：一屏里 6 张卡，标题必须能一眼扫到 */
.aid-title {
  font-family: var(--font-display);
  font-size: 21px;
  font-weight: 600;
  line-height: 1.24;
  letter-spacing: -0.374px;
  transition: color 0.16s var(--ease);
}

.aid-card:hover .aid-title {
  color: var(--accent);
}

.aid-excerpt {
  font-size: 17px;
  font-weight: 400;
  line-height: 1.47;
  letter-spacing: -0.374px;
  color: var(--muted);
}

.aid-thumbs {
  display: flex;
  gap: var(--sp-2);
}

.aid-thumbs img {
  width: 64px;
  height: 64px;
  object-fit: cover;
  border-radius: var(--r-sm);
  border: 1px solid var(--line);
  background: var(--parchment);
}

.aid-place {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  letter-spacing: -0.12px;
  color: var(--muted);
  min-width: 0;
}

.aid-place svg {
  width: 13px;
  height: 13px;
  flex: none;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.7;
  opacity: 0.7;
}

.aid-place span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 作者行用发丝线收口，把「谁发的」和「什么事」分层 */
.aid-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  padding-top: var(--sp-4);
  border-top: 1px solid var(--line);
  font-size: 12px;
  letter-spacing: -0.12px;
  color: var(--muted-2);
}

.aid-rating {
  padding-top: var(--sp-3);
  border-top: 1px solid var(--line);
}

@media (max-width: 734px) {
  .aid-card { padding: var(--sp-5); }
}
</style>
