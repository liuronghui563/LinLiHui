<template>
  <div ref="rootRef" class="feed">
    <SkeletonList v-if="loading" :count="3" variant="card" />

    <StateBlock v-else-if="error" variant="error" :desc="error" />

    <StateBlock v-else-if="!posts.length" :title="emptyTitle" :desc="emptyDesc">
      <router-link v-if="emptyTo" class="btn btn--primary btn--sm" :to="emptyTo">
        {{ emptyAction }}
      </router-link>
    </StateBlock>

    <template v-else>
      <article
        v-for="post in posts"
        :key="post.id"
        :id="anchorId(post)"
        class="post-card"
        :data-post-id="post.id"
      >
        <header class="post-head">
          <PostAuthor
            :user-id="post.authorId"
            :name="post.authorName"
            :avatar="post.authorAvatar"
            size="sm"
          />
          <span class="post-head-meta">
            <span v-if="showCategory && categoryOf(post)" class="tag tag--outline">{{ categoryOf(post) }}</span>
            <HeatBadge :value="post.heatScore" />
            <span class="time">{{ relativeTime(post.createdAt) }}</span>
          </span>
        </header>

        <p class="post-body">{{ post.content }}</p>
        <ImageGallery :images="post.images || []" />

        <footer class="post-ops">
          <HeartLike
            :liked="post.liked"
            :count="post.likeCount || 0"
            :busy="busyId === post.id"
            @toggle="onLike(post)"
          />
          <CommentThread
            :post-id="post.id"
            :count="post.commentCount || 0"
            @change="(n, heat) => applyComment(post, n, heat)"
          />
          <button
            v-if="canOffline"
            type="button"
            class="btn btn--quiet btn--sm"
            @click="$emit('offline', post)"
          >
            下线
          </button>
          <button
            v-if="canManage(post)"
            type="button"
            class="btn btn--quiet btn--sm del"
            @click="onDelete(post)"
          >
            删除
          </button>
        </footer>
      </article>
    </template>
  </div>
</template>

<script setup>
import { ref, toRef } from 'vue'
import { deletePost, toggleLike } from '../api/community'
import { useAuthStore } from '../stores/auth'
import { useHeatView } from '../composables/useHeatView'
import { relativeTime } from '../utils/format'
import PostAuthor from './PostAuthor.vue'
import ImageGallery from './ImageGallery.vue'
import HeatBadge from './HeatBadge.vue'
import HeartLike from './HeartLike.vue'
import CommentThread from './CommentThread.vue'
import StateBlock from './StateBlock.vue'
import SkeletonList from './SkeletonList.vue'

/**
 * 动态流。
 *
 * 抽取原因：CommunityView 与 PlazaView 的动态卡片结构、点赞/删除/评论逻辑、
 * 时间格式化以及整段样式此前是逐字重复的两份，改一处必然漏另一处。
 * 现在两个页面只负责「取数 + 发布框」，列表行为集中在这里。
 */
const props = defineProps({
  posts: { type: Array, required: true },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  emptyTitle: { type: String, default: '还没有内容' },
  emptyDesc: { type: String, default: '' },
  emptyTo: { type: [String, Object], default: '' },
  emptyAction: { type: String, default: '去发布' },
  anchorPrefix: { type: String, default: 'post' },
  /** 校园互动帖带分类（吐槽/表白/唠嗑/分享日常），需要单独展示 */
  showCategory: { type: Boolean, default: false },
  /**
   * 分类字段名。默认 'category' 保持原有行为；
   * 新数据用 kind/kindLabel 表达种类（category 已废弃、只会是 null），
   * 传 'kindLabel' 即可显示后端下发的种类中文名，无需前端维护映射表。
   */
  categoryKey: { type: String, default: 'category' },
  /** 圈子管理员可把帖子从圈子下线，不删除原动态 */
  canOffline: { type: Boolean, default: false }
})

const emit = defineEmits(['changed', 'offline'])

const auth = useAuthStore()
const rootRef = ref(null)
const busyId = ref(null)

// 作用域限定在本组件根节点内，避免与页面其它列表互相干扰
const { observeCards } = useHeatView(toRef(props, 'posts'), rootRef)
defineExpose({ observeCards })

function anchorId(post) {
  return props.anchorPrefix ? `${props.anchorPrefix}-${post.id}` : undefined
}

/** 取展示用的分类/种类文案：读哪个字段由 categoryKey 决定，默认仍是 category */
function categoryOf(post) {
  return post[props.categoryKey] || ''
}

function canManage(post) {
  return auth.isAdmin || post.authorId === auth.user?.id
}

function applyComment(post, n, heat) {
  post.commentCount = n
  if (heat) post.heatScore = Math.max(0, (post.heatScore || 0) + heat)
}

async function onLike(post) {
  if (busyId.value === post.id) return
  busyId.value = post.id
  try {
    const res = await toggleLike(post.id)
    Object.assign(post, res.data)
  } catch (e) {
    emit('changed', { type: 'error', message: e.message })
  } finally {
    busyId.value = null
  }
}

async function onDelete(post) {
  if (!window.confirm('删除后无法恢复，确认删除这条动态？')) return
  try {
    await deletePost(post.id)
    emit('changed', { type: 'deleted', post })
  } catch (e) {
    emit('changed', { type: 'error', message: e.message })
  }
}
</script>

<style scoped>
.feed {
  display: grid;
  gap: var(--sp-4);
}

/* 动态卡：白底、1px 发丝线、18px 圆角、24px 内距。没有阴影——
   层级来自它与米白页面底之间的色差，而不是投影。 */
.post-card {
  display: grid;
  gap: var(--sp-4);
  padding: var(--sp-6);
  background: var(--canvas);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  transition: border-color 0.18s var(--ease);
  scroll-margin-top: var(--nav-stack);
}

.post-card:hover {
  border-color: var(--line-strong);
}

/* 从广场热度榜跳转过来时高亮定位的那一条 */
.post-card:target {
  border-color: var(--accent);
  outline: 2px solid var(--accent-strong);
  outline-offset: 2px;
}

.post-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  flex-wrap: wrap;
}

.post-head-meta {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  font-size: 12px;
  letter-spacing: -0.12px;
  color: var(--muted-2);
}

/* 正文 17px / 1.47：多出的那一像素是「阅读」而不是「扫读」的节奏 */
.post-body {
  font-size: 17px;
  font-weight: 400;
  line-height: 1.47;
  letter-spacing: -0.374px;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 操作行用一条发丝线与正文分开：比再加一层卡片干净得多 */
.post-ops {
  display: flex;
  align-items: center;
  gap: var(--sp-1);
  flex-wrap: wrap;
  padding-top: var(--sp-4);
  border-top: 1px solid var(--line);
}

.del {
  margin-left: auto;
  color: var(--muted);
}

.del:hover {
  background: var(--danger-soft);
  color: var(--danger);
}

@media (max-width: 734px) {
  .post-card { padding: var(--sp-5); }
}
</style>
