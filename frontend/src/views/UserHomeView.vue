<template>
  <AppShell :title="pageTitle">
    <section v-if="privateLocked" class="locked">
      <h2>该账户已设为隐私账户</h2>
      <p>对方开启了隐私保护，他人无法进入查看个人主页。</p>
    </section>
    <p v-else-if="error" class="error">{{ error }}</p>
    <template v-else-if="profile">
      <section class="hero">
        <UserAvatar :user="profile" size="lg" />
        <div class="intro">
          <div class="intro-head">
            <h2>{{ profile.nickname || '邻里用户' }}</h2>
            <router-link v-if="isSelf" class="edit-btn" to="/settings">更改资料</router-link>
          </div>
          <p class="bio">{{ profile.bio || '这位邻居还没有填写简介。' }}</p>
          <StarRating
            :avg="profile.ratingAvg || 0"
            :count="profile.ratingCount || 0"
            :my-score="profile.myScore"
            :readonly="isSelf"
            @rate="onRateUser"
          />
        </div>
      </section>

      <div class="groups">
        <section class="group">
          <h3>基本信息</h3>
          <dl>
            <div><dt>状态</dt><dd>{{ presenceLabel(profile.presenceStatus) }}</dd></div>
            <div><dt>身份</dt><dd>{{ roleText(profile.role) }}</dd></div>
            <div><dt>性别</dt><dd>{{ genderLabel(profile.gender) }}</dd></div>
            <div><dt>加入时间</dt><dd>{{ formatTime(profile.createdAt) || '未填写' }}</dd></div>
          </dl>
        </section>

        <section class="group">
          <h3>居住信息</h3>
          <dl>
            <div><dt>城市</dt><dd>{{ profile.city || '未填写' }}</dd></div>
            <div><dt>小区 / 社区</dt><dd>{{ profile.neighborhood || '未填写' }}</dd></div>
          </dl>
        </section>

        <section v-if="profile.student || profile.school || profile.major || profile.grade" class="group">
          <h3>学生信息</h3>
          <dl>
            <div><dt>身份</dt><dd>{{ profile.student ? '在校学生' : '未标注' }}</dd></div>
            <div><dt>学校</dt><dd>{{ profile.school || '未填写' }}</dd></div>
            <div><dt>专业</dt><dd>{{ profile.major || '未填写' }}</dd></div>
            <div><dt>年级</dt><dd>{{ profile.grade || '未填写' }}</dd></div>
          </dl>
        </section>

        <section class="group">
          <h3>联系方式</h3>
          <dl>
            <div><dt>微信</dt><dd>{{ profile.wechat || '未填写' }}</dd></div>
            <div v-if="profile.phone"><dt>手机</dt><dd>{{ profile.phone }}</dd></div>
            <div v-if="profile.realName"><dt>真实姓名</dt><dd>{{ profile.realName }}</dd></div>
          </dl>
        </section>
      </div>

      <div class="tabs">
        <button type="button" :class="{ active: tab === 'posts' }" @click="switchTab('posts')">动态</button>
        <button type="button" :class="{ active: tab === 'aids' }" @click="switchTab('aids')">发布的求助</button>
      </div>

      <div v-if="tab === 'posts'" class="feed">
        <article v-for="post in posts" :key="post.id" class="card" :data-post-id="post.id">
          <div class="post-head">
            <PostAuthor
              :user-id="post.authorId || profile.id"
              :name="post.authorName || profile.nickname"
              :avatar="post.authorAvatar || profile.avatar"
            />
            <HeatBadge :value="post.heatScore" />
          </div>
          <p>{{ post.content }}</p>
          <div class="ops">
            <HeartLike :liked="post.liked" :count="post.likeCount || 0" @toggle="onLike(post)" />
            <CommentThread :post-id="post.id" :count="post.commentCount || 0" @change="(n, heat) => applyComment(post, n, heat)" />
          </div>
        </article>
        <p v-if="!posts.length" class="empty">还没有动态</p>
      </div>

      <div v-else class="cards">
        <article v-for="item in aids" :key="item.id" class="card">
          <div class="meta-row">
            <span>{{ item.category }}</span>
            <span>{{ statusText(item.status) }}</span>
          </div>
          <h3>{{ item.title }}</h3>
          <p>{{ item.content }}</p>
          <StarRating
            :avg="item.ratingAvg || 0"
            :count="item.ratingCount || 0"
            :my-score="item.myScore"
            readonly
          />
          <router-link :to="`/aids/${item.id}`">查看详情</router-link>
        </article>
        <p v-if="!aids.length" class="empty">还没有发布求助</p>
      </div>
    </template>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '../layouts/AppShell.vue'
import UserAvatar from '../components/UserAvatar.vue'
import StarRating from '../components/StarRating.vue'
import HeartLike from '../components/HeartLike.vue'
import CommentThread from '../components/CommentThread.vue'
import PostAuthor from '../components/PostAuthor.vue'
import HeatBadge from '../components/HeatBadge.vue'
import { fetchPublicProfile, rateUser } from '../api/user'
import { fetchAuthorPosts, toggleLike } from '../api/community'
import { fetchUserPublished } from '../api/aid'
import { genderLabel, presenceLabel } from '../utils/presence'
import { useAuthStore } from '../stores/auth'
import { useHeatView } from '../composables/useHeatView'

const route = useRoute()
const auth = useAuthStore()
const profile = ref(null)
const posts = ref([])
const aids = ref([])
const tab = ref('posts')
const error = ref('')
const privateLocked = ref(false)
const { observeCards } = useHeatView(posts)

const userId = computed(() => Number(route.params.id))
const isSelf = computed(() => profile.value?.self || userId.value === auth.user?.id)
const pageTitle = computed(() => {
  if (privateLocked.value) return '隐私账户'
  return isSelf.value ? '我的主页' : `${profile.value?.nickname || '邻里'}的主页`
})

const statusMap = {
  OPEN: '待接单',
  ACCEPTED: '进行中',
  DONE: '已完成',
  CANCELLED: '已取消'
}

function statusText(s) {
  return statusMap[s] || s
}

function roleText(role) {
  if (role === 'ADMIN') return '管理员'
  return '邻里用户'
}

function formatTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

async function loadProfile() {
  error.value = ''
  privateLocked.value = false
  try {
    const res = await fetchPublicProfile(userId.value)
    profile.value = res.data
  } catch (e) {
    const message = e.message || '加载失败'
    privateLocked.value = message.includes('隐私账户')
    error.value = message
    profile.value = null
  }
}

async function loadTab() {
  if (tab.value === 'posts') {
    const res = await fetchAuthorPosts(userId.value, { page: 0, size: 20 })
    posts.value = res.data?.content || []
    observeCards()
  } else {
    const res = await fetchUserPublished(userId.value, { page: 0, size: 20 })
    aids.value = res.data?.content || []
  }
}

function switchTab(value) {
  tab.value = value
  loadTab()
}

async function onRateUser(score) {
  try {
    const res = await rateUser(userId.value, score)
    profile.value = res.data
  } catch (e) {
    error.value = e.message
  }
}

function applyComment(post, n, heat) {
  post.commentCount = n
  if (heat) post.heatScore = Math.max(0, (post.heatScore || 0) + heat)
}

async function onLike(post) {
  try {
    const res = await toggleLike(post.id)
    const idx = posts.value.findIndex((p) => p.id === post.id)
    if (idx >= 0) posts.value[idx] = res.data
  } catch (e) {
    error.value = e.message
  }
}

async function boot() {
  await loadProfile()
  if (!error.value) await loadTab()
}

onMounted(boot)
watch(() => route.params.id, () => {
  tab.value = 'posts'
  boot()
})
</script>

<style scoped>
.hero,
.group,
.card {
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow-soft);
}

.hero {
  display: flex;
  gap: 20px;
  padding: 22px;
  margin-bottom: 18px;
}

.locked {
  max-width: 560px;
  padding: 28px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow-soft);
}

.locked h2 {
  margin: 0 0 10px;
  font-family: "ZCOOL XiaoWei", serif;
  font-weight: 400;
}

.locked p {
  margin: 0;
  color: var(--muted);
  line-height: 1.7;
}

.intro-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.intro h2 {
  margin: 0;
  font-family: "ZCOOL XiaoWei", serif;
  font-weight: 400;
}

.edit-btn {
  padding: 8px 14px;
  border-radius: 999px;
  background: var(--accent);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  white-space: nowrap;
}

.bio {
  margin: 0 0 12px;
  line-height: 1.6;
}

.groups {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 18px;
}

.group {
  padding: 16px 18px;
}

.group h3 {
  margin: 0 0 10px;
  font-size: 15px;
}

dl {
  margin: 0;
  display: grid;
  gap: 8px;
}

dl > div {
  display: grid;
  grid-template-columns: 88px 1fr;
  gap: 8px;
  font-size: 14px;
}

dt {
  color: var(--muted);
}

dd {
  margin: 0;
}

.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}

.tabs button {
  border: 1px solid var(--line);
  background: #fff;
  padding: 8px 14px;
  cursor: pointer;
  border-radius: 999px;
}

.tabs button.active {
  background: var(--bg-deep);
  color: #fff;
  border-color: var(--bg-deep);
}

.feed,
.cards {
  display: grid;
  gap: 12px;
}

.card {
  padding: 18px;
}

.card > p {
  margin: 10px 0 0;
  line-height: 1.7;
  white-space: pre-wrap;
}

.post-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.ops,
.meta-row {
  margin-top: 10px;
  color: var(--muted);
  font-size: 13px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.card a {
  display: inline-block;
  margin-top: 10px;
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

@media (max-width: 860px) {
  .hero,
  .groups {
    grid-template-columns: 1fr;
  }

  .hero {
    flex-direction: column;
  }
}
</style>
