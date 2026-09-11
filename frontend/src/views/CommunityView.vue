<template>
  <AppShell title="邻里动态">
    <form class="composer" @submit.prevent="onCreate">
      <textarea
        v-model.trim="content"
        rows="3"
        maxlength="1000"
        placeholder="分享一条邻里动态…"
      />
      <div class="row">
        <span>{{ content.length }}/1000</span>
        <button type="submit" class="primary" :disabled="posting || !content">
          {{ posting ? '发布中…' : '发布' }}
        </button>
      </div>
    </form>

    <p v-if="error" class="error">{{ error }}</p>
    <div class="feed">
      <article v-for="post in posts" :key="post.id" class="card" :data-post-id="post.id">
        <div class="head">
          <PostAuthor
            :user-id="post.authorId"
            :name="post.authorName"
            :avatar="post.authorAvatar"
          />
          <div class="head-meta">
            <HeatBadge :value="post.heatScore" />
            <span>{{ formatTime(post.createdAt) }}</span>
          </div>
        </div>
        <p>{{ post.content }}</p>
        <div class="ops">
          <HeartLike :liked="post.liked" :count="post.likeCount || 0" @toggle="onLike(post)" />
          <CommentThread :post-id="post.id" :count="post.commentCount || 0" @change="(n, heat) => applyComment(post, n, heat)" />
          <button
            v-if="canManage(post)"
            type="button"
            class="del"
            @click="onDelete(post)"
          >
            删除
          </button>
        </div>
      </article>
      <p v-if="!posts.length && !error" class="empty">还没有动态，来发第一条吧</p>
    </div>
  </AppShell>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AppShell from '../layouts/AppShell.vue'
import HeartLike from '../components/HeartLike.vue'
import CommentThread from '../components/CommentThread.vue'
import PostAuthor from '../components/PostAuthor.vue'
import HeatBadge from '../components/HeatBadge.vue'
import { createPost, deletePost, fetchPosts, toggleLike } from '../api/community'
import { useAuthStore } from '../stores/auth'
import { useHeatView } from '../composables/useHeatView'

const auth = useAuthStore()
const posts = ref([])
const content = ref('')
const posting = ref(false)
const error = ref('')
const { observeCards } = useHeatView(posts)

function formatTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

function canManage(post) {
  return auth.isAdmin || post.authorId === auth.user?.id
}

function applyComment(post, n, heat) {
  post.commentCount = n
  if (heat) post.heatScore = Math.max(0, (post.heatScore || 0) + heat)
}

async function load() {
  error.value = ''
  try {
    const res = await fetchPosts({ page: 0, size: 30, channel: 'COMMUNITY' })
    posts.value = res.data?.content || res.data || []
    observeCards()
  } catch (e) {
    error.value = e.message
  }
}

async function onCreate() {
  posting.value = true
  error.value = ''
  try {
    await createPost({ content: content.value })
    content.value = ''
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    posting.value = false
  }
}

async function onLike(post) {
  try {
    const res = await toggleLike(post.id)
    const updated = res.data
    const idx = posts.value.findIndex((p) => p.id === post.id)
    if (idx >= 0) posts.value[idx] = updated
  } catch (e) {
    error.value = e.message
  }
}

async function onDelete(post) {
  if (!confirm('确认删除该动态？')) return
  try {
    await deletePost(post.id)
    await load()
  } catch (e) {
    error.value = e.message
  }
}

onMounted(load)
</script>

<style scoped>
.composer,
.card {
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid var(--line);
  padding: 18px;
  border-radius: var(--radius);
}

.composer {
  margin-bottom: 16px;
  display: grid;
  gap: 10px;
}

textarea {
  width: 100%;
  border: 1px solid var(--line);
  padding: 12px;
  font: inherit;
  resize: vertical;
  border-radius: 10px;
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--muted);
  font-size: 13px;
}

.primary {
  border: none;
  background: var(--accent);
  color: #fff;
  padding: 10px 16px;
  cursor: pointer;
  font-weight: 600;
  border-radius: 999px;
}

.feed {
  display: grid;
  gap: 12px;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  color: var(--muted);
  font-size: 13px;
}

.head-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.card > p {
  margin: 12px 0;
  line-height: 1.7;
  white-space: pre-wrap;
}

.ops {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: flex-start;
}

.del {
  border: 1px solid var(--line);
  background: #fff;
  color: var(--danger);
  padding: 6px 12px;
  cursor: pointer;
  border-radius: 999px;
}

.empty,
.error {
  color: var(--muted);
}

.error {
  color: var(--danger);
}
</style>
