<template>
  <AppShell
    title="管理"
    eyebrow="运营"
    subtitle="用户数取自本地库，其余由各服务聚合；下游熔断时，只有对应的数字会降级。"
  >
    <!-- 控制室的近黑瓦片：白底的数据条压在它下面，读起来最清楚 -->
    <template #hero>
      <PageHero
        eyebrow="管理 · 控制台"
        title="平台各域运转概览"
        lead="用户、求助、动态与广告"
        tone="dark"
        size="display"
        align="left"
      />
    </template>

    <template #actions>
      <button class="btn btn--ghost btn--sm" type="button" :disabled="loading" @click="load">
        {{ loading ? '刷新中…' : '刷新数据' }}
      </button>
    </template>

    <SkeletonList v-if="loading" :count="2" variant="card" />

    <StateBlock v-else-if="error" variant="error" :desc="error">
      <button class="btn btn--ghost btn--sm" type="button" @click="load">重新加载</button>
    </StateBlock>

    <template v-else>
      <p class="greeting lead-body">{{ data.greeting || '欢迎回来' }}</p>

      <!-- 用户数（本地）单独成带，与跨服务聚合的统计区分开 -->
      <div class="stat-strip overview">
        <div class="stat">
          <span class="stat-label">注册用户</span>
          <strong class="stat-value nums">{{ data.userCount ?? '—' }}</strong>
          <span class="stat-hint">来自 auth-service 本地库</span>
        </div>
        <div class="stat">
          <span class="stat-label">求助总量</span>
          <strong class="stat-value nums">{{ data.aidStats?.total ?? '—' }}</strong>
          <span class="stat-hint">{{ data.aidStats ? `待接单 ${data.aidStats.open}` : 'aid-service 暂不可用（可能已熔断）' }}</span>
        </div>
        <div class="stat">
          <span class="stat-label">动态总量</span>
          <strong class="stat-value nums">{{ data.postStats?.total ?? '—' }}</strong>
          <span class="stat-hint">{{ data.postStats ? `评论 ${data.postStats.commentTotal}` : 'community-service 暂不可用（可能已熔断）' }}</span>
        </div>
        <div class="stat">
          <span class="stat-label">广告点击</span>
          <strong class="stat-value nums">{{ data.adStats?.clicks ?? '—' }}</strong>
          <span class="stat-hint">{{ data.adStats ? `启用 ${data.adStats.enabled}/${data.adStats.total}` : 'ad-service 暂不可用（可能已熔断）' }}</span>
        </div>
      </div>

      <div class="detail-grid">
        <section class="panel">
          <div class="panel-head">
            <h3>求助进度</h3>
            <span v-if="!data.aidStats" class="tag tag--outline">数据不可用</span>
          </div>
          <template v-if="data.aidStats">
            <ul class="breakdown">
              <li>
                <span class="dot dot-open" />
                <span class="label">待接单</span>
                <strong class="nums">{{ data.aidStats.open }}</strong>
              </li>
              <li>
                <span class="dot dot-accepted" />
                <span class="label">进行中</span>
                <strong class="nums">{{ data.aidStats.accepted }}</strong>
              </li>
              <li>
                <span class="dot dot-done" />
                <span class="label">已完成</span>
                <strong class="nums">{{ data.aidStats.done }}</strong>
              </li>
            </ul>
            <p class="breakdown-note">完成率 {{ doneRate }}</p>
          </template>
          <p v-else class="caption">aid-service 暂不可用，稍后再试。</p>
        </section>

        <section class="panel">
          <div class="panel-head">
            <h3>社区活跃</h3>
            <span v-if="!data.postStats" class="tag tag--outline">数据不可用</span>
          </div>
          <template v-if="data.postStats">
            <ul class="breakdown">
              <li>
                <span class="dot dot-accepted" />
                <span class="label">动态</span>
                <strong class="nums">{{ data.postStats.total }}</strong>
              </li>
              <li>
                <span class="dot dot-done" />
                <span class="label">评论</span>
                <strong class="nums">{{ data.postStats.commentTotal }}</strong>
              </li>
              <li>
                <span class="dot dot-open" />
                <span class="label">点赞</span>
                <strong class="nums">{{ data.postStats.likeTotal }}</strong>
              </li>
            </ul>
            <p class="breakdown-note">户均动态 {{ postsPerUser }}</p>
          </template>
          <p v-else class="caption">community-service 暂不可用，稍后再试。</p>
        </section>
      </div>

      <!-- 学生认证审核：校园模块的闸门，通过即授予 student 权限 -->
      <section class="panel review-panel">
        <div class="panel-head">
          <h3>学生认证</h3>
          <span class="panel-note">
            {{ studentLoading ? '加载中…' : pendingStudents.length ? `待审 ${pendingStudents.length} 条` : '暂无待审' }}
          </span>
        </div>

        <SkeletonList v-if="studentLoading" :count="2" />

        <StateBlock
          v-else-if="studentError"
          variant="error"
          :desc="studentError"
          inline
        >
          <button class="btn btn--ghost btn--sm" type="button" @click="loadPendingStudents">重新加载</button>
        </StateBlock>

        <StateBlock
          v-else-if="!pendingStudents.length"
          title="没有待审的学生认证"
          desc="用户提交认证后出现在这里；通过后该用户即可进入校园论坛。"
          inline
        />

        <ul v-else class="list-plain">
          <li v-for="item in pendingStudents" :key="item.id" class="review-item">
            <img v-if="item.proofImage" class="review-thumb" :src="item.proofImage" alt="" />
            <div class="review-main">
              <strong class="brand-title">{{ item.realName || '未填姓名' }}</strong>
              <p class="caption">
                {{ item.school || '未填学校' }}
                <template v-if="item.major"> · {{ item.major }}</template>
                <template v-if="item.grade"> · {{ item.grade }}</template>
              </p>
              <p class="fine-print">
                学号：{{ item.studentNo || '—' }}
                · 申请人：{{ item.applicantNickname || `用户 ${item.userId}` }}
                <template v-if="item.applicantPhone"> · {{ item.applicantPhone }}</template>
              </p>
              <input
                v-model.trim="studentNotes[item.id]"
                class="input review-note"
                maxlength="200"
                placeholder="审核意见（驳回时建议写明原因）"
              />
            </div>
            <div class="review-ops">
              <button
                type="button"
                class="btn btn--primary btn--sm"
                :disabled="studentReviewingId === item.id"
                @click="onReviewStudent(item, true)"
              >
                通过
              </button>
              <button
                type="button"
                class="btn btn--danger btn--sm"
                :disabled="studentReviewingId === item.id"
                @click="onReviewStudent(item, false)"
              >
                驳回
              </button>
            </div>
          </li>
        </ul>

        <p v-if="studentActionError" class="review-error">{{ studentActionError }}</p>
      </section>

      <!-- 广告位资质审核：广告位申请的前置闸门，通过它不会让任何广告上线 -->
      <section class="panel review-panel">
        <div class="panel-head">
          <h3>广告位资质</h3>
          <span class="panel-note">
            {{ qualLoading ? '加载中…' : pendingQuals.length ? `待审 ${pendingQuals.length} 条` : '暂无待审' }}
          </span>
        </div>

        <SkeletonList v-if="qualLoading" :count="2" />

        <StateBlock
          v-else-if="qualError"
          variant="error"
          :desc="qualError"
          inline
        >
          <button class="btn btn--ghost btn--sm" type="button" @click="loadPendingQuals">重新加载</button>
        </StateBlock>

        <StateBlock
          v-else-if="!pendingQuals.length"
          title="没有待审的资质申请"
          desc="用户开通资质后出现在这里；通过后该用户才能提交广告位申请。"
          inline
        />

        <ul v-else class="list-plain">
          <li v-for="item in pendingQuals" :key="item.id" class="review-item">
            <img v-if="item.licenseImage" class="review-thumb" :src="item.licenseImage" alt="" />
            <div class="review-main">
              <strong class="brand-title">{{ item.applicantName || '未填联系人' }}</strong>
              <p class="caption">
                {{ item.company || '未填主体' }}
                <template v-if="item.category"> · {{ item.category }}</template>
              </p>
              <p v-if="item.intro" class="fine-print">{{ item.intro }}</p>
              <p class="fine-print">
                联系方式：{{ item.contact || '—' }}
                · 申请人：{{ item.userNickname || `用户 ${item.userId}` }}
              </p>
              <input
                v-model.trim="qualNotes[item.id]"
                class="input review-note"
                maxlength="200"
                placeholder="审核意见（驳回时建议写明原因）"
              />
            </div>
            <div class="review-ops">
              <button
                type="button"
                class="btn btn--primary btn--sm"
                :disabled="qualReviewingId === item.id"
                @click="onReviewQual(item, true)"
              >
                通过
              </button>
              <button
                type="button"
                class="btn btn--danger btn--sm"
                :disabled="qualReviewingId === item.id"
                @click="onReviewQual(item, false)"
              >
                驳回
              </button>
            </div>
          </li>
        </ul>

        <p v-if="qualActionError" class="review-error">{{ qualActionError }}</p>
      </section>

      <!-- 广告位申请审核：这条链路的闸门就在这里，通过即上线 -->
      <section class="panel review-panel">
        <div class="panel-head">
          <h3>广告位申请</h3>
          <span class="panel-note">
            {{ adLoading ? '加载中…' : pendingAds.length ? `待审 ${pendingAds.length} 条` : '暂无待审' }}
          </span>
        </div>

        <SkeletonList v-if="adLoading" :count="2" />

        <StateBlock
          v-else-if="adError"
          variant="error"
          :desc="adError"
          inline
        >
          <button class="btn btn--ghost btn--sm" type="button" @click="loadPendingAds">重新加载</button>
        </StateBlock>

        <StateBlock
          v-else-if="!pendingAds.length"
          title="没有待审的广告位申请"
          desc="用户提交申请后会出现在这里；通过后广告立即进入首页轮播。"
          inline
        />

        <ul v-else class="list-plain">
          <li v-for="item in pendingAds" :key="item.id" class="review-item">
            <img v-if="item.imageUrl" class="review-thumb" :src="item.imageUrl" alt="" />
            <div class="review-main">
              <strong class="brand-title">{{ item.title }}</strong>
              <p v-if="item.subtitle" class="caption">{{ item.subtitle }}</p>
              <p class="fine-print">
                申请人：{{ item.applicantName || `用户 ${item.applicantId}` }}
                <template v-if="item.linkUrl"> · 跳转 {{ item.linkUrl }}</template>
              </p>
              <input
                v-model.trim="reviewNotes[item.id]"
                class="input review-note"
                maxlength="200"
                placeholder="审核意见（驳回时建议写明原因）"
              />
            </div>
            <div class="review-ops">
              <button
                type="button"
                class="btn btn--primary btn--sm"
                :disabled="reviewingId === item.id"
                @click="onReview(item, true)"
              >
                通过
              </button>
              <button
                type="button"
                class="btn btn--danger btn--sm"
                :disabled="reviewingId === item.id"
                @click="onReview(item, false)"
              >
                驳回
              </button>
            </div>
          </li>
        </ul>

        <p v-if="adActionError" class="review-error">{{ adActionError }}</p>
      </section>

      <!-- 广告位日常管理：审核通过之后的事（上下架 / 排序 / 改文案 / 删除） -->
      <section class="panel manage-panel">
        <div class="panel-head">
          <h3>广告管理</h3>
          <span class="panel-note">
            {{ adsLoading ? '加载中…' : ads.length ? `共 ${ads.length} 条 · 在播 ${liveCount} 条` : '暂无广告' }}
          </span>
        </div>

        <SkeletonList v-if="adsLoading" :count="2" />

        <StateBlock
          v-else-if="adsError"
          variant="error"
          :desc="adsError"
          inline
        >
          <button class="btn btn--ghost btn--sm" type="button" @click="loadAds">重新加载</button>
        </StateBlock>

        <StateBlock
          v-else-if="!ads.length"
          title="还没有任何广告"
          desc="管理员可以直接创建，也可以等用户提交申请后在上一区块审核。"
          inline
        />

        <ul v-else class="list-plain">
          <li v-for="item in ads" :key="item.id" class="manage-item">
            <img v-if="item.imageUrl" class="manage-thumb" :src="item.imageUrl" alt="" />
            <div class="manage-main">
              <div class="manage-head">
                <strong class="brand-title">{{ item.title }}</strong>
                <span class="tag" :class="statusTone(item.status)">{{ item.statusLabel }}</span>
                <span v-if="item.status === 'APPROVED'" class="tag" :class="item.enabled ? 'tag--done' : 'tag--cancelled'">
                  {{ item.enabled ? '在播' : '已下架' }}
                </span>
              </div>
              <p v-if="item.subtitle" class="caption">{{ item.subtitle }}</p>
              <p class="fine-print">
                {{ item.applicantName || '管理员创建' }}
                <template v-if="item.linkUrl"> · {{ item.linkUrl }}</template>
              </p>

              <!-- 编辑：只在展开时出现，平时一行信息就够 -->
              <div v-if="editingAdId === item.id" class="manage-edit">
                <input v-model.trim="editForm.title" class="input" maxlength="100" placeholder="标题" />
                <input v-model.trim="editForm.subtitle" class="input" maxlength="200" placeholder="一句话说明（选填）" />
                <input v-model.trim="editForm.linkUrl" class="input" maxlength="500" placeholder="跳转链接（选填）" />
                <div class="manage-ops">
                  <button type="button" class="btn btn--primary btn--sm" :disabled="busyAdId === item.id" @click="onSaveAd(item)">
                    保存
                  </button>
                  <button type="button" class="btn btn--quiet btn--sm" @click="cancelEditAd">取消</button>
                </div>
              </div>
            </div>

            <div class="manage-ops">
              <label class="manage-sort">
                <span class="fine-print">排序</span>
                <input
                  v-model.number="sortDraft[item.id]"
                  class="input sort-input nums"
                  type="number"
                  min="0"
                  max="9999"
                />
                <button
                  type="button"
                  class="btn btn--ghost btn--sm"
                  :disabled="busyAdId === item.id || sortDraft[item.id] === item.sortOrder"
                  @click="onSaveSort(item)"
                >
                  保存
                </button>
              </label>
              <button
                type="button"
                class="btn btn--ghost btn--sm"
                :disabled="busyAdId === item.id"
                @click="startEditAd(item)"
              >
                编辑
              </button>
              <button
                v-if="item.status === 'APPROVED'"
                type="button"
                class="btn btn--sm"
                :class="item.enabled ? 'btn--danger' : 'btn--secondary'"
                :disabled="busyAdId === item.id"
                @click="onToggleEnabled(item)"
              >
                {{ item.enabled ? '下架' : '上架' }}
              </button>
              <button
                type="button"
                class="btn btn--quiet btn--sm"
                :disabled="busyAdId === item.id"
                @click="onDeleteAd(item)"
              >
                删除
              </button>
            </div>
          </li>
        </ul>

        <p v-if="adsActionError" class="review-error">{{ adsActionError }}</p>
      </section>

      <!-- 叙事收在近黑瓦片卡里：深色区块不用 Action Blue，链接自动走 Sky Link Blue -->
      <section class="panel panel--dark notice">
        <div class="panel-head panel-head--plain">
          <h3>关于这些数据</h3>
        </div>
        <ul class="notice-list on-dark-muted">
          <li>用户数为本地库直接统计；其余三项由 auth-service 通过 Feign 向对应服务获取。</li>
          <li>任一服务不可用时，只有它对应的卡片降级，其余照常显示——不会整页失败。</li>
          <li>热度榜每日 6:00 自动重算，管理台不展示实时排名。</li>
          <li>三条队列互不依赖：<strong>学生认证</strong>决定能否进入校园论坛；<strong>广告位资质</strong>决定能否提交广告位申请；<strong>广告位申请</strong>决定某条广告能否上线。通过资质不会让任何广告上线。</li>
          <li>广告位申请通过即上线；下架不需要再审，改文案则需重新审核。</li>
        </ul>
      </section>
    </template>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import AppShell from '../layouts/AppShell.vue'
import PageHero from '../components/PageHero.vue'
import StateBlock from '../components/StateBlock.vue'
import SkeletonList from '../components/SkeletonList.vue'
import { fetchAdminDashboard } from '../api/auth'
import {
  approveAdApplication,
  approveAdQualification,
  deleteAd,
  fetchAdQualifications,
  fetchAllAdApplications,
  fetchPendingAdApplications,
  rejectAdApplication,
  rejectAdQualification,
  updateAd
} from '../api/ad'
import {
  approveStudentVerification,
  fetchStudentVerifications,
  rejectStudentVerification
} from '../api/student'

const data = reactive({
  greeting: '',
  userCount: null,
  aidStats: null,
  postStats: null,
  adStats: null
})
const loading = ref(true)
const error = ref('')

/* ---------------- 广告位申请审核 ---------------- */
const pendingAds = ref([])
const adLoading = ref(false)
const adError = ref('')
const adActionError = ref('')
const reviewingId = ref(null)
/** 每条申请的审核意见，key 是申请 id */
const reviewNotes = reactive({})

async function loadPendingAds() {
  adLoading.value = true
  adError.value = ''
  try {
    const res = await fetchPendingAdApplications()
    pendingAds.value = res.data || []
  } catch (e) {
    adError.value = e.message
  } finally {
    adLoading.value = false
  }
}

/**
 * 通过 / 驳回。
 *
 * 通过后广告立即进入首页轮播（服务端会清缓存），所以这里重新拉一次待审列表，
 * 让审批过的那条从列表里消失——审核结果与列表状态必须对得上。
 */
async function onReview(item, approved) {
  const note = reviewNotes[item.id] || ''
  if (!approved && !note.trim()) {
    const ok = window.confirm('驳回时没有填写原因，申请人只会看到「已驳回」。确定继续？')
    if (!ok) return
  }
  reviewingId.value = item.id
  adActionError.value = ''
  try {
    if (approved) {
      await approveAdApplication(item.id, note)
    } else {
      await rejectAdApplication(item.id, note)
    }
    delete reviewNotes[item.id]
    await loadPendingAds()
    // 通过的那条会转入「广告管理」，两张表必须同时刷新，否则同一件事在两个列表里说法不一致
    await loadAds()
  } catch (e) {
    adActionError.value = e.message
  } finally {
    reviewingId.value = null
  }
}

/** 状态 → 标签语气。与用户端同一套映射，后端下发中文名。 */
function statusTone(status) {
  if (status === 'APPROVED') return 'tag--done'
  if (status === 'REJECTED') return 'tag--cancelled'
  return 'tag--open'
}

/* ---------------- 学生认证审核 ---------------- */
/**
 * 校园模块的闸门就在这里。
 *
 * 通过之后该用户的 `u_r_sys_user.student` 会被置为 true，并写回学校/专业/年级——
 * 前端的校园门禁读的正是这个字段，所以「审核通过」不是一条记录的状态变化，
 * 而是**一次权限授予**。驳回必须写原因：它是用户改材料的唯一线索。
 */
const pendingStudents = ref([])
const studentLoading = ref(false)
const studentError = ref('')
const studentActionError = ref('')
const studentReviewingId = ref(null)
/** 每条申请的审核意见，key 是申请 id */
const studentNotes = reactive({})

async function loadPendingStudents() {
  studentLoading.value = true
  studentError.value = ''
  try {
    const res = await fetchStudentVerifications('PENDING')
    pendingStudents.value = res.data || []
  } catch (e) {
    studentError.value = e.message
  } finally {
    studentLoading.value = false
  }
}

async function onReviewStudent(item, approved) {
  const note = studentNotes[item.id] || ''
  if (!approved && !note.trim()) {
    const ok = window.confirm('驳回时没有填写原因，申请人只会看到「已驳回」。确定继续？')
    if (!ok) return
  }
  studentReviewingId.value = item.id
  studentActionError.value = ''
  try {
    if (approved) {
      await approveStudentVerification(item.id, note)
    } else {
      await rejectStudentVerification(item.id, note)
    }
    delete studentNotes[item.id]
    await loadPendingStudents()
    // 通过会改变用户数无关，但看板里的用户统计口径不变，因此不必重拉看板
  } catch (e) {
    studentActionError.value = e.message
  } finally {
    studentReviewingId.value = null
  }
}

/* ---------------- 广告位资质审核 ---------------- */
/**
 * 广告位申请的前置闸门。
 *
 * 与「广告位申请审核」是两条独立的队列，别混：资质回答的是「这个人能不能投放」，
 * 申请回答的是「这一条广告能不能上」。通过资质不会让任何广告上线。
 */
const pendingQuals = ref([])
const qualLoading = ref(false)
const qualError = ref('')
const qualActionError = ref('')
const qualReviewingId = ref(null)
const qualNotes = reactive({})

async function loadPendingQuals() {
  qualLoading.value = true
  qualError.value = ''
  try {
    const res = await fetchAdQualifications('PENDING')
    pendingQuals.value = res.data || []
  } catch (e) {
    qualError.value = e.message
  } finally {
    qualLoading.value = false
  }
}

async function onReviewQual(item, approved) {
  const note = qualNotes[item.id] || ''
  if (!approved && !note.trim()) {
    const ok = window.confirm('驳回时没有填写原因，申请人只会看到「已驳回」。确定继续？')
    if (!ok) return
  }
  qualReviewingId.value = item.id
  qualActionError.value = ''
  try {
    if (approved) {
      await approveAdQualification(item.id, note)
    } else {
      await rejectAdQualification(item.id, note)
    }
    delete qualNotes[item.id]
    await loadPendingQuals()
  } catch (e) {
    qualActionError.value = e.message
  } finally {
    qualReviewingId.value = null
  }
}

/* ---------------- 广告管理（审核通过之后） ---------------- */
const ads = ref([])
const adsLoading = ref(false)
const adsError = ref('')
const adsActionError = ref('')
const busyAdId = ref(null)
const editingAdId = ref(null)
const editForm = reactive({ title: '', subtitle: '', linkUrl: '' })
/** 排序草稿：key 是广告 id，保存后与列表值比较以决定按钮是否可点 */
const sortDraft = reactive({})

const liveCount = computed(() => ads.value.filter((a) => a.status === 'APPROVED' && a.enabled).length)

async function loadAds() {
  adsLoading.value = true
  adsError.value = ''
  try {
    const res = await fetchAllAdApplications()
    ads.value = res.data || []
    for (const item of ads.value) sortDraft[item.id] = item.sortOrder ?? 0
  } catch (e) {
    adsError.value = e.message
  } finally {
    adsLoading.value = false
  }
}

/**
 * 管理动作共用外壳。
 *
 * 每个动作成功后都重拉列表：状态、排序、上下架必须是服务端的真实结果，
 * 不能靠本地改字段——下架会影响首页轮播，这里显示错了会误导运营。
 */
async function runAdAction(item, action, done) {
  busyAdId.value = item.id
  adsActionError.value = ''
  try {
    await action()
    if (done) done()
    await loadAds()
  } catch (e) {
    adsActionError.value = e.message
  } finally {
    busyAdId.value = null
  }
}

function startEditAd(item) {
  editingAdId.value = item.id
  editForm.title = item.title || ''
  editForm.subtitle = item.subtitle || ''
  editForm.linkUrl = item.linkUrl || ''
}

function cancelEditAd() {
  editingAdId.value = null
}

/** 改文案走通用更新接口，必须回传完整对象（后端是整体覆盖语义） */
function onSaveAd(item) {
  return runAdAction(item, () => updateAd(item.id, {
    title: editForm.title,
    subtitle: editForm.subtitle,
    imageUrl: item.imageUrl,
    linkUrl: editForm.linkUrl,
    sortOrder: item.sortOrder,
    enabled: item.enabled
  }), () => cancelEditAd())
}

function onSaveSort(item) {
  return runAdAction(item, () => updateAd(item.id, {
    title: item.title,
    subtitle: item.subtitle,
    imageUrl: item.imageUrl,
    linkUrl: item.linkUrl,
    sortOrder: Number(sortDraft[item.id]) || 0,
    enabled: item.enabled
  }))
}

/** 上下架：审核结果不变，只是「现在播不播」。这条不需要再审一次。 */
function onToggleEnabled(item) {
  return runAdAction(item, () => updateAd(item.id, {
    title: item.title,
    subtitle: item.subtitle,
    imageUrl: item.imageUrl,
    linkUrl: item.linkUrl,
    sortOrder: item.sortOrder,
    enabled: !item.enabled
  }))
}

async function onDeleteAd(item) {
  if (!window.confirm(`删除「${item.title}」？删除后它的申请记录也会一起消失。`)) return
  await runAdAction(item, () => deleteAd(item.id))
}

const doneRate = computed(() => {
  const stats = data.aidStats
  if (!stats || !stats.total) return '—'
  return `${Math.round((stats.done / stats.total) * 100)}%`
})

const postsPerUser = computed(() => {
  if (!data.postStats || !data.userCount) return '—'
  return (data.postStats.total / data.userCount).toFixed(1)
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await fetchAdminDashboard()
    Object.assign(data, res.data)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  // 四块互不依赖：看板挂了也要能审核与管理，
  // 且审核通过后管理列表必须跟着变，所以各拉各的。
  loadPendingStudents()
  loadPendingQuals()
  loadPendingAds()
  loadAds()
})
</script>

<style scoped>
/* 只写布局差异：颜色 / 字号 / 圆角 / 按钮外观一律来自全局类与 var(--*) */

.greeting {
  margin-bottom: var(--sp-5);
}

.overview {
  margin-bottom: var(--sp-6);
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: var(--sp-4);
  margin-bottom: var(--sp-6);
}

.breakdown {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: var(--sp-3);
}

.breakdown li {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.breakdown .label {
  flex: 1;
  color: var(--muted);
}

.breakdown strong {
  font-weight: 600;
}

/* 状态只用一个 8px 小圆点表达：单一强调色 + 一个成功绿 */
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: none;
}

.dot-open { background: var(--accent); }
.dot-accepted { background: var(--muted-2); }
.dot-done { background: var(--success); }

.breakdown-note {
  margin-top: var(--sp-4);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--line);
}

/* 近黑卡里的说明文字：用深色底上的弱化白，而不是浅底灰 */
.notice .notice-list {
  margin: 0;
  padding-left: var(--sp-5);
  display: grid;
  gap: var(--sp-2);
}

/* ---------- 广告位申请审核 ---------- */
.review-panel {
  margin-bottom: var(--sp-6);
}

.review-item {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-4);
}

.review-thumb {
  width: 132px;
  height: 82px;
  flex: none;
  object-fit: cover;
  border-radius: var(--r-sm);
  border: 1px solid var(--line);
  background: var(--parchment);
}

.review-main {
  display: grid;
  gap: var(--sp-2);
  min-width: 0;
  flex: 1;
}

.review-note {
  margin-top: var(--sp-1);
}

.review-ops {
  display: flex;
  gap: var(--sp-2);
  flex-wrap: wrap;
  flex: none;
}

.review-error {
  margin-top: var(--sp-4);
  padding: var(--sp-3) var(--sp-4);
  border: 1px solid rgba(192, 69, 58, 0.28);
  border-radius: var(--r-md);
  color: var(--danger);
  font-size: 14px;
}

/* ---------- 广告管理（审核通过之后） ---------- */
.manage-panel {
  margin-bottom: var(--sp-6);
}

.manage-item {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-4);
}

.manage-thumb {
  width: 116px;
  height: 72px;
  flex: none;
  object-fit: cover;
  border-radius: var(--r-sm);
  border: 1px solid var(--line);
  background: var(--parchment);
}

.manage-main {
  display: grid;
  gap: var(--sp-2);
  min-width: 0;
  flex: 1;
}

.manage-head {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

.manage-edit {
  display: grid;
  gap: var(--sp-2);
  margin-top: var(--sp-1);
}

.manage-ops {
  display: flex;
  align-items: flex-end;
  gap: var(--sp-2);
  flex-wrap: wrap;
  flex: none;
}

/* 排序：小数字输入 + 保存。宽度固定，避免数字变化时整行抖动 */
.manage-sort {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.sort-input {
  width: 72px;
  min-height: 34px;
  padding: 6px 12px;
  text-align: center;
}

@media (max-width: 640px) {
  .detail-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .review-item,
  .manage-item { flex-wrap: wrap; }
  .review-thumb,
  .manage-thumb { width: 100%; height: 132px; }
}
</style>
