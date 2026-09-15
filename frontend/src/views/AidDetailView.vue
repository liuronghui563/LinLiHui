<template>
  <AppShell
    title="求助详情"
    :eyebrow="eyebrow"
  >
    <template #hero>
      <!-- 英雄瓦片承载「这件事是什么」：状态、分类、标题、是谁发的 -->
      <PageHero
        v-if="aid"
        :eyebrow="subtitle"
        :title="aid.title"
        tone="parchment"
        size="display"
        align="left"
      >
        <template #actions>
          <router-link class="btn btn--ghost" :to="backTo">返回列表</router-link>
        </template>

        <div class="hero-tags">
          <StatusTag :status="aid.status" dot />
          <span class="tag tag--outline">{{ aid.category }}</span>
          <span class="tag tag--outline">{{ aid.board === 'CAMPUS' ? '校园' : '邻里' }}</span>
          <span class="tag tag--outline">{{ aid.address || '未填写地点' }}</span>
        </div>
      </PageHero>
    </template>

    <SkeletonList v-if="loading" :count="2" variant="card" />

    <StateBlock v-else-if="error && !aid" variant="error" :desc="error">
      <router-link class="btn btn--ghost btn--sm" :to="backTo">返回列表</router-link>
    </StateBlock>

    <div v-else-if="aid" class="detail-grid">
      <!-- 主区：内容与评价 -->
      <div class="detail-main">
        <section class="panel">
          <div class="panel-head panel-head--plain">
            <div class="tag-row">
              <StatusTag :status="aid.status" dot />
              <span class="tag tag--outline">{{ aid.category }}</span>
              <span class="tag tag--outline">{{ aid.board === 'CAMPUS' ? '校园' : '邻里' }}</span>
            </div>
          </div>

          <template v-if="!editing">
            <p class="aid-content">{{ aid.content }}</p>
            <ImageGallery :images="aid.images || []" />
          </template>

          <form v-else class="edit-form" @submit.prevent="onSave">
            <div class="field">
              <label for="edit-title">标题</label>
              <input id="edit-title" v-model.trim="form.title" class="input" maxlength="100" />
            </div>
            <div class="field">
              <label for="edit-category">分类</label>
              <select id="edit-category" v-model="form.category" class="select">
                <option v-for="c in categories" :key="c" :value="c">{{ c }}</option>
              </select>
            </div>
            <div class="field">
              <label for="edit-address">地点</label>
              <input id="edit-address" v-model.trim="form.address" class="input" maxlength="200" />
            </div>
            <div class="field">
              <label for="edit-content">详情</label>
              <textarea id="edit-content" v-model.trim="form.content" class="textarea" rows="6" maxlength="2000" />
            </div>
            <div class="field">
              <span class="field-label">配图</span>
              <ImageUploader v-model="form.images" purpose="aid" />
            </div>
            <div class="edit-ops">
              <button type="submit" class="btn btn--primary btn--sm" :disabled="busy">保存修改</button>
              <button type="button" class="btn btn--quiet btn--sm" @click="editing = false">取消</button>
            </div>
          </form>
        </section>

        <section class="panel">
          <div class="panel-head"><h3>求助评价</h3></div>
          <p class="rate-hint">
            {{ aid.publisherId === myId ? '这是你自己发布的求助，无法自评。' : '完成后可以为这次互助打分，评价会计入信用。' }}
          </p>
          <StarRating
            :avg="aid.ratingAvg || 0"
            :count="aid.ratingCount || 0"
            :my-score="aid.myScore"
            :readonly="aid.publisherId === myId"
            @rate="onRate"
          />
        </section>

        <section v-if="aid.status === 'DONE' && aid.helperId" class="panel">
          <div class="panel-head">
            <h3>求助方对帮助方的评价</h3>
          </div>

          <div v-if="aid.helperReviewScore && !aid.canReviewHelper" class="review-view">
            <StarRating
              :avg="aid.helperReviewScore"
              :count="1"
              :my-score="aid.helperReviewScore"
              readonly
            />
            <p v-if="aid.helperReviewContent" class="review-text">{{ aid.helperReviewContent }}</p>
            <p v-else class="review-text text-muted">未填写文字评价。</p>
          </div>

          <form v-else-if="aid.canReviewHelper" class="helper-form" @submit.prevent="onReviewHelper">
            <p class="rate-hint">这次互助已经完成，写下对「{{ aid.helperName }}」的评价吧。</p>
            <StarRating
              :avg="helperScore"
              :my-score="helperScore"
              hide-meta
              @rate="helperScore = $event"
            />
            <textarea
              v-model.trim="helperContent"
              class="textarea"
              rows="3"
              maxlength="200"
              placeholder="写下这次互助的感受（选填，最多 200 字）"
            />
            <div class="helper-ops">
              <span class="counter nums">{{ helperContent.length }} / 200</span>
              <button type="submit" class="btn btn--primary btn--sm" :disabled="busy || !helperScore">
                {{ aid.helperReviewScore ? '更新评价' : '提交评价' }}
              </button>
            </div>
          </form>

          <p v-else class="rate-hint">仅求助方可评价帮助方。</p>
        </section>
      </div>

      <!-- 侧栏：状态、参与者与操作 -->
      <aside class="detail-rail">
        <section class="panel">
          <div class="panel-head"><h3>当前状态</h3></div>
          <p class="status-note">{{ statusNote }}</p>
          <dl class="fact-list">
            <div>
              <dt>地点</dt>
              <dd>{{ aid.address || '未填写' }}</dd>
            </div>
            <div>
              <dt>发布时间</dt>
              <dd>{{ formatDateTime(aid.createdAt) }}</dd>
            </div>
            <div v-if="aid.updatedAt && aid.updatedAt !== aid.createdAt">
              <dt>最近更新</dt>
              <dd>{{ relativeTime(aid.updatedAt) }}</dd>
            </div>
          </dl>
        </section>

        <section class="panel">
          <div class="panel-head"><h3>参与的人</h3></div>
          <div class="people">
            <div class="person">
              <span class="person-role">求助方</span>
              <PostAuthor
                v-if="aid.publisherId"
                :user-id="aid.publisherId"
                :name="aid.publisherName"
                :avatar="aid.publisherAvatar"
                size="sm"
              />
              <span v-else>{{ aid.publisherName }}</span>
            </div>
            <div class="person">
              <span class="person-role">帮助方</span>
              <PostAuthor
                v-if="aid.helperId"
                :user-id="aid.helperId"
                :name="aid.helperName"
                :avatar="aid.helperAvatar"
                size="sm"
              />
              <span v-else class="text-muted">还没有人接单</span>
            </div>
          </div>
        </section>

        <section v-if="hasActions" class="panel actions-panel">
          <div class="panel-head"><h3>可以做的事</h3></div>
          <div class="action-list">
            <button v-if="canAccept" type="button" class="btn btn--primary btn--block" :disabled="busy" @click="onAccept">
              我来帮忙
            </button>
            <button v-if="canComplete" type="button" class="btn btn--primary btn--block" :disabled="busy" @click="onComplete">
              标记为已完成
            </button>
            <button v-if="canEdit" type="button" class="btn btn--ghost btn--block" :disabled="busy" @click="startEdit">
              编辑内容
            </button>
            <button v-if="canCancel" type="button" class="btn btn--ghost btn--block" :disabled="busy" @click="onCancel">
              取消这条求助
            </button>
            <button v-if="canDelete" type="button" class="btn btn--danger btn--block" :disabled="busy" @click="onDelete">
              删除
            </button>
          </div>
          <p v-if="actionError" class="action-error">{{ actionError }}</p>
        </section>
      </aside>
    </div>

    <!-- 悬浮吸附条（floating-sticky-bar）：磨砂玻璃，滚动时始终把「下一步」放在手边。
         用 sticky 而不是 fixed，页脚因此不会被压住。 -->
    <div v-if="aid && hasActions" class="sticky-bar">
      <div class="sticky-inner">
        <div class="sticky-copy">
          <StatusTag :status="aid.status" dot />
          <span class="sticky-note">{{ statusNote }}</span>
        </div>
        <div class="sticky-ops">
          <button v-if="canAccept" type="button" class="btn btn--primary" :disabled="busy" @click="onAccept">
            我来帮忙
          </button>
          <button v-if="canComplete" type="button" class="btn btn--primary" :disabled="busy" @click="onComplete">
            标记为已完成
          </button>
          <button v-if="canEdit" type="button" class="btn btn--ghost" :disabled="busy" @click="startEdit">
            编辑内容
          </button>
        </div>
      </div>
    </div>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '../layouts/AppShell.vue'
import PageHero from '../components/PageHero.vue'
import StarRating from '../components/StarRating.vue'
import PostAuthor from '../components/PostAuthor.vue'
import ImageGallery from '../components/ImageGallery.vue'
import ImageUploader from '../components/ImageUploader.vue'
import StatusTag from '../components/StatusTag.vue'
import StateBlock from '../components/StateBlock.vue'
import SkeletonList from '../components/SkeletonList.vue'
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
import { formatDateTime, relativeTime } from '../utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const aid = ref(null)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const busy = ref(false)
const editing = ref(false)

const NEIGHBOR_CATEGORIES = ['搬运', '代购', '陪护', '维修', '其他']
const CAMPUS_CATEGORIES = ['失物招领']
const categories = computed(() => {
  const base = aid.value?.board === 'CAMPUS' ? CAMPUS_CATEGORIES : NEIGHBOR_CATEGORIES
  const current = aid.value?.category
  return current && !base.includes(current) ? [current, ...base] : base
})

const backTo = computed(() => (
  aid.value?.board === 'CAMPUS' ? { path: '/campus', query: { tab: 'aid' } } : '/discover/neighbor'
))

// 二级导航的 eyebrow 是**顶级模块**：校园互助属于「校园」，其余属于「发现」。
// 标题仍然是「求助详情」，这一页是两个模块共用的。
const eyebrow = computed(() => (aid.value?.board === 'CAMPUS' ? '校园' : '发现'))
const subtitle = computed(() => {
  if (!aid.value) return '查看求助的详细信息与当前进度'
  return `${aid.value.category} · 由 ${aid.value.publisherName || '邻居'} 发布`
})

/** 把状态翻译成「接下来会发生什么」，比单纯显示「进行中」有用 */
const statusNote = computed(() => {
  switch (aid.value?.status) {
    case 'OPEN':
      return '正在等待邻居接单。接单后会自动通知双方。'
    case 'ACCEPTED':
      return '已有邻居接单，双方可以按约定时间联系。完成后任意一方可标记完成。'
    case 'DONE':
      return '这次互助已完成。求助方可以评价帮助方，评价会计入信用。'
    case 'CANCELLED':
      return '这条求助已取消。如需帮助可以重新发布一条。'
    default:
      return ''
  }
})

const form = reactive({ title: '', category: '搬运', address: '', content: '', images: [] })
const helperScore = ref(0)
const helperContent = ref('')

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
const hasActions = computed(() => (
  canAccept.value || canComplete.value || canEdit.value || canCancel.value || canDelete.value
))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await fetchAidDetail(route.params.id)
    applyAid(res.data)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
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
  form.images = [...(aid.value.images || [])]
  editing.value = true
}

async function onSave() {
  busy.value = true
  actionError.value = ''
  try {
    const res = await updateAid(route.params.id, { ...form })
    applyAid(res.data)
    editing.value = false
  } catch (e) {
    actionError.value = e.message
  } finally {
    busy.value = false
  }
}

async function run(action) {
  busy.value = true
  actionError.value = ''
  try {
    const res = await action(route.params.id)
    applyAid(res.data)
  } catch (e) {
    actionError.value = e.message
  } finally {
    busy.value = false
  }
}

function onAccept() {
  return run(acceptAid)
}

function onComplete() {
  return run(completeAid)
}

function onCancel() {
  return run(cancelAid)
}

async function onRate(score) {
  busy.value = true
  actionError.value = ''
  try {
    const res = await rateAid(route.params.id, score)
    applyAid(res.data)
  } catch (e) {
    actionError.value = e.message
  } finally {
    busy.value = false
  }
}

async function onReviewHelper() {
  if (!helperScore.value) {
    actionError.value = '请先选择星级'
    return
  }
  busy.value = true
  actionError.value = ''
  try {
    const res = await reviewHelper(route.params.id, {
      score: helperScore.value,
      content: helperContent.value
    })
    applyAid(res.data)
  } catch (e) {
    actionError.value = e.message
  } finally {
    busy.value = false
  }
}

async function onDelete() {
  if (!window.confirm('删除后无法恢复，确认删除这条求助？')) return
  busy.value = true
  actionError.value = ''
  try {
    await deleteAid(route.params.id)
    router.replace(backTo.value)
  } catch (e) {
    actionError.value = e.message
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.hero-tags {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(280px, 1fr);
  gap: var(--sp-6);
  align-items: start;
}

.detail-main,
.detail-rail {
  display: grid;
  gap: var(--sp-5);
  align-content: start;
}

.detail-rail {
  position: sticky;
  top: calc(var(--nav-stack) + var(--sp-6));
}

.tag-row {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

/* 详情正文：17px，行高比列表里松一档——这一块是「读」而不是「扫」 */
.aid-content {
  font-size: 17px;
  font-weight: 400;
  line-height: 1.65;
  letter-spacing: -0.374px;
  white-space: pre-wrap;
  word-break: break-word;
  max-width: 68ch;
}

/* 状态说明用米白底小字块，而不是又一层白卡 */
.status-note {
  margin-bottom: var(--sp-4);
  padding: var(--sp-4);
  border-radius: var(--r-md);
  background: var(--parchment);
  font-size: 13px;
  line-height: 1.6;
  letter-spacing: -0.1px;
  color: var(--muted);
}

.fact-list {
  margin: 0;
  display: grid;
}

.fact-list > div {
  display: grid;
  gap: 2px;
  padding: var(--sp-3) 0;
  border-top: 1px solid var(--line);
}

.fact-list > div:first-child {
  border-top: 0;
  padding-top: 0;
}

.fact-list dt {
  font-size: 12px;
  letter-spacing: -0.12px;
  color: var(--muted-2);
}

.fact-list dd {
  margin: 0;
  font-size: 14px;
  letter-spacing: -0.224px;
  color: var(--ink);
}

.people {
  display: grid;
  gap: var(--sp-4);
}

.person {
  display: grid;
  gap: 5px;
}

.person-role {
  font-size: 12px;
  letter-spacing: -0.12px;
  color: var(--muted-2);
}

.actions-panel .action-list {
  display: grid;
  gap: var(--sp-2);
}

.action-error {
  margin-top: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  border: 1px solid rgba(192, 69, 58, 0.28);
  border-radius: var(--r-md);
  color: var(--danger);
  font-size: 14px;
  letter-spacing: -0.224px;
}

.rate-hint {
  margin-bottom: var(--sp-3);
  font-size: 14px;
  line-height: 1.5;
  letter-spacing: -0.224px;
  color: var(--muted);
}

.review-view {
  display: grid;
  gap: var(--sp-3);
}

.review-text {
  padding: var(--sp-4);
  border-radius: var(--r-md);
  background: var(--parchment);
  font-size: 17px;
  line-height: 1.6;
  letter-spacing: -0.374px;
  white-space: pre-wrap;
}

.helper-form {
  display: grid;
  gap: var(--sp-3);
}

.helper-ops {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
}

.counter {
  font-size: 12px;
  letter-spacing: -0.12px;
  color: var(--muted-2);
}

.edit-form {
  display: grid;
  gap: var(--sp-4);
}

.edit-ops {
  display: flex;
  gap: var(--sp-2);
}

/* 悬浮吸附条：米白 80% + 背景模糊，粘在视口底部。
   用 sticky 而不是 fixed，所以滚到页脚时它会自然归位，不会压住页脚。 */
.sticky-bar {
  position: sticky;
  bottom: 0;
  z-index: 20;
  margin-top: var(--sp-7);
  /* 向两侧各溢出半个 gutter，让吸附条看起来是通栏的，而不是又一张卡 */
  margin-left: calc(var(--gutter) * -0.5);
  margin-right: calc(var(--gutter) * -0.5);
  background: rgba(245, 245, 247, 0.8);
  backdrop-filter: saturate(180%) blur(20px);
  border-top: 1px solid var(--line);
}

.sticky-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  flex-wrap: wrap;
  min-height: 64px;
  padding: var(--sp-3) var(--sp-4);
}

.sticky-copy {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  min-width: 0;
}

.sticky-note {
  font-size: 14px;
  letter-spacing: -0.224px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 46ch;
}

.sticky-ops {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

@media (max-width: 1000px) {
  .detail-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .detail-rail {
    position: static;
  }
}

@media (max-width: 734px) {
  .sticky-note { display: none; }
}
</style>
