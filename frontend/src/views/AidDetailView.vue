<template>
  <AppShell title="求助详情">
    <p v-if="error" class="error">{{ error }}</p>
    <section v-else-if="aid" class="panel">
      <div class="meta">
        <span>{{ aid.category }}</span>
        <span>{{ statusText(aid.status) }}</span>
      </div>

      <template v-if="!editing">
        <h2>{{ aid.title }}</h2>
        <p class="content">{{ aid.content }}</p>
        <ul class="info">
          <li>地点：{{ aid.address }}</li>
          <li>
            发布者：
            <PostAuthor
              v-if="aid.publisherId"
              :user-id="aid.publisherId"
              :name="aid.publisherName"
              :avatar="aid.publisherAvatar"
            />
            <span v-else>{{ aid.publisherName }}</span>
          </li>
          <li>
            帮助者：
            <PostAuthor
              v-if="aid.helperId"
              :user-id="aid.helperId"
              :name="aid.helperName"
              :avatar="aid.helperAvatar"
            />
            <span v-else>{{ aid.helperName || '暂无' }}</span>
          </li>
        </ul>
        <div class="rate-box">
          <p>星级评价</p>
          <StarRating
            :avg="aid.ratingAvg || 0"
            :count="aid.ratingCount || 0"
            :my-score="aid.myScore"
            :readonly="aid.publisherId === myId"
            @rate="onRate"
          />
        </div>
        <div v-if="aid.status === 'DONE' && aid.helperId" class="rate-box">
          <p>求助方对帮助方「{{ aid.helperName }}」的评价</p>
          <div v-if="aid.helperReviewScore && !aid.canReviewHelper" class="helper-view">
            <StarRating
              :avg="aid.helperReviewScore"
              :count="1"
              :my-score="aid.helperReviewScore"
              readonly
            />
            <p v-if="aid.helperReviewContent" class="review-text">{{ aid.helperReviewContent }}</p>
          </div>
          <form v-if="aid.canReviewHelper" class="helper-form" @submit.prevent="onReviewHelper">
            <StarRating
              :avg="helperScore"
              :my-score="helperScore"
              hide-meta
              @rate="helperScore = $event"
            />
            <textarea
              v-model.trim="helperContent"
              rows="3"
              maxlength="200"
              placeholder="写下这次互助的感受（选填，最多200字）"
            />
            <div class="helper-ops">
              <span>{{ helperContent.length }}/200</span>
              <button type="submit" class="primary" :disabled="busy || !helperScore">
                {{ aid.helperReviewScore ? '更新评价' : '提交评价' }}
              </button>
            </div>
            <p v-if="aid.helperReviewContent && aid.helperReviewScore" class="review-text">
              当前评价：{{ aid.helperReviewContent }}
            </p>
          </form>
        </div>
      </template>

      <form v-else class="edit-form" @submit.prevent="onSave">
        <label>
          <span>标题</span>
          <input v-model.trim="form.title" maxlength="100" />
        </label>
        <label>
          <span>分类</span>
          <select v-model="form.category">
            <option v-for="c in categories" :key="c" :value="c">{{ c }}</option>
          </select>
        </label>
        <label>
          <span>地点</span>
          <input v-model.trim="form.address" maxlength="200" />
        </label>
        <label>
          <span>详情</span>
          <textarea v-model.trim="form.content" rows="5" maxlength="2000" />
        </label>
        <div class="actions">
          <button type="submit" class="primary" :disabled="busy">保存</button>
          <button type="button" class="ghost" @click="editing = false">取消编辑</button>
        </div>
      </form>

      <div v-if="!editing" class="actions">
        <button v-if="canAccept" type="button" class="primary" :disabled="busy" @click="onAccept">我来帮忙</button>
        <button v-if="canComplete" type="button" class="primary" :disabled="busy" @click="onComplete">标记完成</button>
        <button v-if="canEdit" type="button" class="ghost" :disabled="busy" @click="startEdit">编辑</button>
        <button v-if="canCancel" type="button" class="ghost" :disabled="busy" @click="onCancel">取消求助</button>
        <button v-if="canDelete" type="button" class="danger" :disabled="busy" @click="onDelete">删除</button>
        <router-link class="ghost" :to="backTo">返回列表</router-link>
      </div>
    </section>
    <p v-else class="muted">加载中…</p>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '../layouts/AppShell.vue'
import StarRating from '../components/StarRating.vue'
import PostAuthor from '../components/PostAuthor.vue'
import {
  acceptAid,
  cancelAid,
  completeAid,
  deleteAid,
  fetchAidDetail,
  rateAid,
  reviewHelper,
  updateAid
} from '../api/aid'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const aid = ref(null)
const error = ref('')
const busy = ref(false)
const editing = ref(false)
const NEIGHBOR_CATEGORIES = ['搬运', '代购', '陪护', '维修', '其他']
const CAMPUS_CATEGORIES = ['失物招领']
const categories = computed(() => {
  const base = aid.value?.board === 'CAMPUS' ? CAMPUS_CATEGORIES : NEIGHBOR_CATEGORIES
  const current = aid.value?.category
  return current && !base.includes(current) ? [current, ...base] : base
})
const backTo = computed(() => (aid.value?.board === 'CAMPUS' ? { path: '/campus', query: { tab: 'aid' } } : '/aids'))
const form = reactive({ title: '', category: '搬运', address: '', content: '' })
const helperScore = ref(0)
const helperContent = ref('')

const statusMap = {
  OPEN: '待接单',
  ACCEPTED: '进行中',
  DONE: '已完成',
  CANCELLED: '已取消'
}

function statusText(s) {
  return statusMap[s] || s
}

const myId = computed(() => auth.user?.id)
const isAdmin = computed(() => auth.isAdmin)

const canAccept = computed(() => aid.value?.status === 'OPEN' && aid.value.publisherId !== myId.value)
const canComplete = computed(() => {
  if (aid.value?.status !== 'ACCEPTED') return false
  return aid.value.publisherId === myId.value || aid.value.helperId === myId.value
})
const canCancel = computed(() => aid.value?.status === 'OPEN' && aid.value.publisherId === myId.value)
const canEdit = computed(() => aid.value?.status === 'OPEN' && aid.value.publisherId === myId.value)
const canDelete = computed(() => {
  if (!aid.value) return false
  if (isAdmin.value) return true
  return aid.value.publisherId === myId.value && aid.value.status !== 'ACCEPTED'
})

async function load() {
  error.value = ''
  try {
    const res = await fetchAidDetail(route.params.id)
    applyAid(res.data)
  } catch (e) {
    error.value = e.message
  }
}

function applyAid(data) {
  aid.value = data
  helperScore.value = data?.helperReviewScore || 0
  helperContent.value = data?.helperReviewContent || ''
}

function startEdit() {
  form.title = aid.value.title
  form.category = aid.value.category
  form.address = aid.value.address
  form.content = aid.value.content
  editing.value = true
}

async function onSave() {
  busy.value = true
  error.value = ''
  try {
    const res = await updateAid(route.params.id, { ...form })
    applyAid(res.data)
    editing.value = false
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

async function run(action) {
  busy.value = true
  error.value = ''
  try {
    const res = await action(route.params.id)
    applyAid(res.data)
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

function onAccept() {
  return run(acceptAid)
}

async function onRate(score) {
  busy.value = true
  error.value = ''
  try {
    const res = await rateAid(route.params.id, score)
    applyAid(res.data)
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

async function onReviewHelper() {
  if (!helperScore.value) {
    error.value = '请先选择星级'
    return
  }
  busy.value = true
  error.value = ''
  try {
    const res = await reviewHelper(route.params.id, {
      score: helperScore.value,
      content: helperContent.value
    })
    applyAid(res.data)
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

function onComplete() {
  return run(completeAid)
}

function onCancel() {
  return run(cancelAid)
}

async function onDelete() {
  if (!confirm('确认删除该求助？')) return
  busy.value = true
  error.value = ''
  try {
    await deleteAid(route.params.id)
    router.replace(backTo.value)
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.panel {
  max-width: 720px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid var(--line);
  padding: 24px;
}

.meta {
  display: flex;
  gap: 10px;
  color: var(--muted);
  font-size: 13px;
}

h2 {
  margin: 12px 0;
  font-size: 28px;
  font-family: "ZCOOL XiaoWei", serif;
  font-weight: 400;
}

.content {
  margin: 0;
  line-height: 1.7;
  white-space: pre-wrap;
}

.info {
  margin: 18px 0 0;
  padding: 0;
  list-style: none;
  color: var(--muted);
  line-height: 1.9;
}

.info li {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rate-box {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
}

.rate-box p {
  margin: 0 0 8px;
  color: var(--muted);
  font-size: 13px;
}

.review-text {
  margin: 10px 0 0;
  line-height: 1.6;
  white-space: pre-wrap;
  color: var(--ink);
}

.helper-form {
  display: grid;
  gap: 10px;
}

.helper-ops {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  color: var(--muted);
  font-size: 13px;
}

.edit-form {
  display: grid;
  gap: 12px;
  margin-top: 12px;
}

label {
  display: grid;
  gap: 6px;
}

label span {
  font-size: 13px;
  color: var(--muted);
}

input,
select,
textarea {
  width: 100%;
  border: 1px solid var(--line);
  padding: 10px 12px;
  font: inherit;
}

.actions {
  margin-top: 24px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.primary,
.ghost,
.danger {
  padding: 12px 18px;
  border: none;
  cursor: pointer;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
}

.primary {
  background: var(--accent);
  color: #fff;
  font-weight: 600;
}

.ghost {
  border: 1px solid var(--line);
  background: #fff;
  color: var(--ink);
}

.danger {
  background: var(--danger);
  color: #fff;
}

.error {
  color: var(--danger);
}

.muted {
  color: var(--muted);
}
</style>
