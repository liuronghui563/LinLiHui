<template>
  <AppShell title="校园专区">
    <div class="tabs">
      <button type="button" :class="{ active: tab === 'social' }" @click="switchTab('social')">互动帖子</button>
      <button type="button" :class="{ active: tab === 'aid' }" @click="switchTab('aid')">互助帖子</button>
    </div>

    <template v-if="tab === 'social'">
      <form class="composer" @submit.prevent="onCreateSocial">
        <div class="filters">
          <button
            v-for="f in socialCategories"
            :key="f"
            type="button"
            :class="{ active: socialCategory === f }"
            @click="changeSocialCategory(f)"
          >
            {{ f || '全部' }}
          </button>
        </div>
        <select v-model="draftCategory">
          <option v-for="c in socialTypes" :key="c" :value="c">{{ c }}</option>
        </select>
        <textarea
          v-model.trim="draft"
          rows="3"
          maxlength="1000"
          placeholder="发一条吐槽、表白、唠嗑或日常分享…"
        />
        <div class="row">
          <span>{{ draft.length }}/1000</span>
          <button type="submit" class="primary" :disabled="posting || !draft">
            {{ posting ? '发布中…' : '发布互动帖' }}
          </button>
        </div>
      </form>

      <p v-if="error" class="error">{{ error }}</p>
      <div class="feed">
        <article
          v-for="post in socialPosts"
          :key="post.id"
          class="card"
          :data-post-id="post.id"
        >
          <div class="head">
            <PostAuthor
              :user-id="post.authorId"
              :name="post.authorName"
              :avatar="post.authorAvatar"
            />
            <div class="head-meta">
              <HeatBadge :value="post.heatScore" />
              <span>{{ post.category }} · {{ formatTime(post.createdAt) }}</span>
            </div>
          </div>
          <p>{{ post.content }}</p>
          <div class="ops">
            <HeartLike :liked="post.liked" :count="post.likeCount || 0" @toggle="onLike(post)" />
            <CommentThread
              :post-id="post.id"
              :count="post.commentCount || 0"
              @change="(n, heat) => applyComment(post, n, heat)"
            />
            <button v-if="canManage(post)" type="button" class="del" @click="onDeleteSocial(post)">删除</button>
          </div>
        </article>
        <p v-if="!socialPosts.length && !error" class="empty">还没有互动帖，来发第一条吧</p>
      </div>
    </template>

    <template v-else>
      <div class="toolbar">
        <div class="filters">
          <button
            v-for="f in statusFilters"
            :key="f.value"
            type="button"
            :class="{ active: status === f.value }"
            @click="changeStatus(f.value)"
          >
            {{ f.label }}
          </button>
        </div>
        <router-link class="create" :to="{ path: '/aids/create', query: { board: 'CAMPUS', category: '失物招领' } }">
          发布互助帖
        </router-link>
      </div>

      <p v-if="error" class="error">{{ error }}</p>
      <div v-else class="cards">
        <article v-for="item in aidItems" :key="item.id" class="card">
          <div class="meta">
            <span>{{ item.category }}</span>
            <span>{{ statusText(item.status) }}</span>
          </div>
          <h3>{{ item.title }}</h3>
          <p>{{ item.content }}</p>
          <StarRating :avg="item.ratingAvg || 0" :count="item.ratingCount || 0" readonly />
          <div class="foot">
            <span class="publisher">
              <PostAuthor
                :user-id="item.publisherId"
                :name="item.publisherName"
                :avatar="item.publisherAvatar"
              />
              <span v-if="item.address"> · {{ item.address }}</span>
            </span>
            <router-link :to="`/aids/${item.id}`">详情</router-link>
          </div>
        </article>
        <p v-if="!aidItems.length" class="empty">还没有校园互助帖，丢失物品可以发一条招领</p>
      </div>
    </template>
  </AppShell>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '../layouts/AppShell.vue'
import StarRating from '../components/StarRating.vue'
import PostAuthor from '../components/PostAuthor.vue'
import HeartLike from '../components/HeartLike.vue'
import CommentThread from '../components/CommentThread.vue'
import HeatBadge from '../components/HeatBadge.vue'
import { fetchAidList } from '../api/aid'
import { createPost, deletePost, fetchPosts, toggleLike } from '../api/community'
import { useAuthStore } from '../stores/auth'
import { useHeatView } from '../composables/useHeatView'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const socialTypes = ['吐槽', '表白', '唠嗑', '分享日常']
const socialCategories = ['', ...socialTypes]
const statusFilters = [
  { label: '全部', value: '' },
  { label: '待接单', value: 'OPEN' },
  { label: '进行中', value: 'ACCEPTED' },
  { label: '已完成', value: 'DONE' }
]

const tab = ref('social')
const socialCategory = ref('')
const status = ref('')
const socialPosts = ref([])
const aidItems = ref([])
const draft = ref('')
const draftCategory = ref('吐槽')
const posting = ref(false)
const error = ref('')

const { observeCards } = useHeatView(socialPosts)

const statusMap = {
  OPEN: '待接单',
  ACCEPTED: '进行中',
  DONE: '已完成',
  CANCELLED: '已取消'
}

function statusText(s) {
  return statusMap[s] || s
}

function formatTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

function canManage(post) {
  return auth.isAdmin || post.authorId === auth.user?.id
}

function applyComment(post, n, heat) {
  post.commentCount = n
  if (heat) {
    post.heatScore = Math.max(0, (post.heatScore || 0) + heat)
  }
}

function applyQuery() {
  const incoming = String(route.query.category || '')
  const nextTab = String(route.query.tab || '') === 'aid' || incoming === '失物招领' ? 'aid' : 'social'
  tab.value = nextTab
  if (nextTab === 'social') {
    socialCategory.value = socialCategories.includes(incoming) ? incoming : ''
    if (socialTypes.includes(incoming)) draftCategory.value = incoming
  }
}

function switchTab(next) {
  tab.value = next
  router.replace({ path: '/campus', query: next === 'aid' ? { tab: 'aid' } : {} })
  load()
}

function changeSocialCategory(value) {
  socialCategory.value = value
  if (value) draftCategory.value = value
  loadSocial()
}

function changeStatus(value) {
  status.value = value
  loadAid()
}

async function loadSocial() {
  error.value = ''
  try {
    const params = { page: 0, size: 30, channel: 'CAMPUS' }
    if (socialCategory.value) params.category = socialCategory.value
    const res = await fetchPosts(params)
    socialPosts.value = res.data?.content || res.data || []
    observeCards()
  } catch (e) {
    error.value = e.message
  }
}

async function loadAid() {
  error.value = ''
  try {
    const params = { page: 0, size: 20, board: 'CAMPUS', category: '失物招领' }
    if (status.value) params.status = status.value
    const res = await fetchAidList(params)
    aidItems.value = res.data?.content || []
  } catch (e) {
    error.value = e.message
  }
}

function load() {
  if (tab.value === 'aid') return loadAid()
  return loadSocial()
}

async function onCreateSocial() {
  if (!draft.value) return
  posting.value = true
  error.value = ''
  try {
    await createPost({
      content: draft.value,
      channel: 'CAMPUS',
      category: draftCategory.value
    })
    draft.value = ''
    await loadSocial()
  } catch (e) {
    error.value = e.message
  } finally {
    posting.value = false
  }
}

async function onLike(post) {
  try {
    const res = await toggleLike(post.id)
    const idx = socialPosts.value.findIndex((p) => p.id === post.id)
    if (idx >= 0) socialPosts.value[idx] = res.data
  } catch (e) {
    error.value = e.message
  }
}

async function onDeleteSocial(post) {
  if (!confirm('确认删除该互动帖？')) return
  try {
    await deletePost(post.id)
    await loadSocial()
  } catch (e) {
    error.value = e.message
  }
}

onMounted(() => {
  applyQuery()
  load()
})
watch(() => [route.query.tab, route.query.category], () => {
  applyQuery()
  load()
})
</script>

<style scoped>
.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.tabs button {
  border: 1px solid var(--line);
  background: #fff;
  padding: 10px 18px;
  border-radius: 999px;
  cursor: pointer;
  font-weight: 700;
}

.tabs button.active {
  background: var(--bg-deep);
  color: #fff;
  border-color: var(--bg-deep);
}

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

.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.filters button {
  border: 1px solid var(--line);
  background: #fff;
  padding: 8px 14px;
  cursor: pointer;
  border-radius: 999px;
}

.filters button.active {
  background: var(--bg-deep);
  color: #fff;
  border-color: var(--bg-deep);
}

select,
textarea {
  width: 100%;
  border: 1px solid var(--line);
  padding: 12px;
  font: inherit;
  border-radius: 10px;
}

textarea {
  resize: vertical;
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--muted);
  font-size: 13px;
}

.primary,
.create {
  border: none;
  background: var(--accent);
  color: #fff;
  padding: 10px 16px;
  cursor: pointer;
  font-weight: 600;
  border-radius: 999px;
}

.feed,
.cards {
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
  margin: 12px 0 0;
  line-height: 1.7;
  white-space: pre-wrap;
  color: var(--muted);
}

.ops {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: flex-start;
  margin-top: 12px;
}

.del {
  border: 1px solid var(--line);
  background: #fff;
  color: var(--danger);
  padding: 6px 12px;
  cursor: pointer;
  border-radius: 999px;
}

.meta {
  display: flex;
  gap: 10px;
  color: var(--muted);
  font-size: 13px;
}

h3 {
  margin: 10px 0 8px;
  font-size: 20px;
}

.foot {
  margin-top: 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  font-size: 14px;
}

.publisher {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.foot a {
  color: var(--accent);
  font-weight: 600;
}

.empty,
.error {
  color: var(--muted);
}

.error {
  color: var(--danger);
}
</style>
