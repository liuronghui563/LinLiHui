<template>
  <AppShell
    title="学生认证"
    eyebrow="校园"
    :subtitle="subtitle"
  >
    <!-- 通栏英雄瓦片：这一页只做一件事，瓦片负责说清「为什么需要认证」 -->
    <template #hero>
      <PageHero
        eyebrow="校园 · 进入条件"
        title="开通学生认证"
        lead="校园模块只对通过认证的在校学生开放。提交材料后由管理员审核，通过即可进入论坛。"
        tone="parchment"
        size="display"
        align="left"
      />
    </template>

    <template #actions>
      <router-link v-if="isApproved" class="btn btn--primary btn--sm" to="/campus">进入论坛</router-link>
      <button
        v-else
        class="btn btn--ghost btn--sm"
        type="button"
        :disabled="loading"
        @click="load"
      >
        {{ loading ? '刷新中…' : '刷新状态' }}
      </button>
    </template>

    <SkeletonList v-if="loading" :count="2" variant="card" />

    <StateBlock v-else-if="loadError" variant="error" :desc="loadError">
      <button class="btn btn--ghost btn--sm" type="button" @click="load">重新加载</button>
    </StateBlock>

    <template v-else>
      <!-- 状态：待审 / 已通过 / 已驳回。未提交过时状态为 NONE，这块直接不渲染，
           页面首屏就是表单，不必先看一条「你还没提交」的空状态再点一下。 -->
      <section v-if="status !== 'NONE'" class="panel status-panel">
        <div class="panel-head">
          <h3>认证状态</h3>
          <StatusTag :label="statusLabel" :tone="statusTone" dot />
        </div>

        <p class="status-line">{{ statusDesc }}</p>

        <dl v-if="record" class="info-grid">
          <div><dt>姓名</dt><dd>{{ record.realName || '—' }}</dd></div>
          <div><dt>学校</dt><dd>{{ record.school || '—' }}</dd></div>
          <div><dt>专业</dt><dd>{{ record.major || '未填写' }}</dd></div>
          <div><dt>年级</dt><dd>{{ record.grade || '未填写' }}</dd></div>
          <div><dt>学号</dt><dd>{{ record.studentNo || '—' }}</dd></div>
          <div><dt>提交时间</dt><dd class="nums">{{ formatTime(record.createdAt) }}</dd></div>
        </dl>

        <!-- 驳回原因必须显眼：用户改材料时唯一有用的信息就是它 -->
        <p v-if="record?.reviewNote" class="review-note">
          <strong>审核意见</strong>{{ record.reviewNote }}
        </p>

        <div class="status-ops">
          <router-link v-if="isApproved" class="btn btn--primary btn--sm" to="/campus">
            进入论坛
          </router-link>
          <button
            v-if="status === 'PENDING'"
            class="btn btn--quiet btn--sm"
            type="button"
            :disabled="busy"
            @click="onWithdraw"
          >
            撤回申请
          </button>
          <button
            v-if="status === 'REJECTED'"
            class="btn btn--primary btn--sm"
            type="button"
            @click="startEditing"
          >
            修改并重新提交
          </button>
        </div>
      </section>

      <!-- 表单 -->
      <section v-if="showForm" class="panel">
        <div class="panel-head">
          <h3>{{ editingId ? '修改认证材料' : '提交认证材料' }}</h3>
          <span class="panel-note">带 * 为必填</span>
        </div>

        <form class="verify-form" @submit.prevent="onSubmit">
          <div class="field">
            <label for="sv-name">真实姓名 *</label>
            <input id="sv-name" v-model.trim="form.realName" class="input" maxlength="30" placeholder="与证件一致" />
          </div>

          <div class="field">
            <label for="sv-school">学校 *</label>
            <select id="sv-school" v-model="form.school" class="select">
              <option value="" disabled>请选择学校</option>
              <option v-for="item in SCHOOL_OPTIONS" :key="item" :value="item">{{ item }}</option>
            </select>
          </div>

          <div class="field-row">
            <div class="field">
              <label for="sv-major">专业</label>
              <select id="sv-major" v-model="form.major" class="select">
                <option value="">请选择专业</option>
                <option v-for="item in MAJOR_OPTIONS" :key="item" :value="item">{{ item }}</option>
              </select>
            </div>
            <div class="field">
              <label for="sv-grade">年级</label>
              <select id="sv-grade" v-model="form.grade" class="select">
                <option value="">请选择年级</option>
                <option v-for="item in GRADE_OPTIONS" :key="item" :value="item">{{ item }}</option>
              </select>
            </div>
          </div>

          <div class="field">
            <label for="sv-no">学号 *</label>
            <input id="sv-no" v-model.trim="form.studentNo" class="input" maxlength="40" placeholder="2023xxxxxx" />
          </div>

          <div class="field">
            <span class="field-label">学生证 / 校园卡照片（选填，但能明显加快审核）</span>
            <ImageUploader v-model="form.proofImage" purpose="student" :max="1" />
          </div>

          <p class="form-note">
            认证通过后，学校、专业与年级会写入你的个人资料并展示在主页上；
            在此之前个人资料里不再提供「我是在校学生」的自选开关。
          </p>

          <p v-if="error" class="form-error">{{ error }}</p>
          <p v-if="okMsg" class="form-ok">{{ okMsg }}</p>

          <div class="verify-ops">
            <button type="submit" class="btn btn--primary" :disabled="submitting">
              {{ submitting ? '提交中…' : (editingId ? '提交修改' : '提交认证') }}
            </button>
            <button v-if="editingId" type="button" class="btn btn--quiet" @click="cancelEditing">
              取消
            </button>
          </div>
        </form>
      </section>
    </template>

    <!-- 收口瓦片：把规则一次讲完，避免用户反复提交。
         必须挂在 AppShell 的直接子级上——具名插槽嵌在 <template v-else> 里
         会让模板编译直接失败（Vue 的 codegen 找不到对应的 slot 出口）。
         所以这里不用 v-else 承接，而是自己判一次加载态。 -->
    <template #band>
      <section v-if="!loading && !loadError" class="tile tile--dark verify-band">
        <div class="tile-inner">
          <h2 class="band-title">认证须知</h2>
          <ul class="band-list">
            <li>一位用户同时只能有一条待审申请，重复提交会被拒绝。</li>
            <li>被驳回后可以看到审核意见，改完重新提交即可，不必重新开一份。</li>
            <li>认证只用于判断能否进入校园模块；你的学号不会展示给其他用户。</li>
          </ul>
        </div>
      </section>
    </template>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '../layouts/AppShell.vue'
import PageHero from '../components/PageHero.vue'
import ImageUploader from '../components/ImageUploader.vue'
import SkeletonList from '../components/SkeletonList.vue'
import StateBlock from '../components/StateBlock.vue'
import StatusTag from '../components/StatusTag.vue'
import { useAuthStore } from '../stores/auth'
import { GRADE_OPTIONS, MAJOR_OPTIONS, SCHOOL_OPTIONS } from '../constants/campus'
import {
  fetchMyStudentVerification,
  submitStudentVerification,
  updateStudentVerification,
  withdrawStudentVerification
} from '../api/student'

/**
 * 学生认证申请页 —— 校园模块的门禁本身。
 *
 * 这一页**不受门禁限制**（路由 meta 上没有 requiresStudent），否则没通过的人
 * 连申请入口都进不去，会变成死循环。
 */

const TEXT = {
  NONE: { label: '未提交', tone: 'outline', desc: '还没有提交过认证材料。填写下面的表单即可开始。' },
  PENDING: { label: '待审核', tone: 'open', desc: '材料已提交，等待管理员审核。审核通过后即可进入校园论坛。' },
  APPROVED: { label: '已通过', tone: 'done', desc: '认证已通过，校园论坛已对你开放。' },
  REJECTED: { label: '已驳回', tone: 'cancelled', desc: '这次认证没有通过。请按下面的审核意见修改材料后重新提交。' }
}

const auth = useAuthStore()
const router = useRouter()

const status = ref('NONE')
const record = ref(null)
const loading = ref(true)
const loadError = ref('')
const submitting = ref(false)
const busy = ref(false)
const error = ref('')
const okMsg = ref('')
/** 正在修改的申请 id；为空表示这是新提交 */
const editingId = ref(null)
/** 表单是否展开。已通过 / 待审核时不展开，避免让人以为还能再提交一份 */
const formOpen = ref(false)

const form = reactive({
  realName: '',
  school: '',
  major: '',
  grade: '',
  studentNo: '',
  proofImage: ''
})

const isApproved = computed(() => status.value === 'APPROVED')
const statusLabel = computed(() => TEXT[status.value]?.label || status.value)
const statusTone = computed(() => TEXT[status.value]?.tone || 'outline')
const statusDesc = computed(() => TEXT[status.value]?.desc || '')
/** NONE 与 REJECTED 都要能填表；PENDING / APPROVED 不给填 */
const showForm = computed(() => formOpen.value)

const subtitle = computed(() => {
  if (loading.value) return '正在读取认证状态'
  if (status.value === 'APPROVED') return '已通过认证 · 校园论坛已开放'
  if (status.value === 'PENDING') return '待审核 · 管理员处理后即可进入论坛'
  if (status.value === 'REJECTED') return '已驳回 · 按审核意见修改后可重新提交'
  return '尚未提交认证材料'
})

function formatTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}

function fillForm(source) {
  form.realName = source?.realName || ''
  form.school = SCHOOL_OPTIONS.includes(source?.school) ? source.school : ''
  form.major = MAJOR_OPTIONS.includes(source?.major) ? source.major : ''
  form.grade = GRADE_OPTIONS.includes(source?.grade) ? source.grade : ''
  form.studentNo = source?.studentNo || ''
  form.proofImage = source?.proofImage || ''
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await fetchMyStudentVerification()
    status.value = res.data?.status || 'NONE'
    record.value = res.data?.verification || null

    if (status.value === 'NONE' || status.value === 'REJECTED') {
      // 从未提交：直接展开空表单。被驳回：先展开、但保持空白，
      // 等用户点「修改并重新提交」再回填 —— 否则驳回原因还没读完，
      // 整页就已经变回一堆输入框了。
      if (status.value === 'NONE') {
        formOpen.value = true
        fillForm(null)
      } else {
        formOpen.value = false
      }
    } else {
      formOpen.value = false
    }

    /**
     * 状态是「已通过」但本地缓存的 user 还认为不是学生时，补一次资料刷新。
     *
     * 场景：管理员刚通过，用户回到这一页。`auth.user` 是登录时写进
     * localStorage 的快照，而路由守卫读的正是它 —— 不刷新的话，
     * 点「进入论坛」会被守卫原样弹回这一页，看起来像按钮坏了。
     */
    if (status.value === 'APPROVED' && !auth.user?.student) {
      await auth.loadProfile().catch(() => null)
    }
  } catch (e) {
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

function startEditing() {
  fillForm(record.value)
  editingId.value = record.value?.id || null
  formOpen.value = true
  okMsg.value = ''
  error.value = ''
}

function cancelEditing() {
  editingId.value = null
  formOpen.value = false
  error.value = ''
  okMsg.value = ''
}

async function onSubmit() {
  error.value = ''
  okMsg.value = ''

  // 前端只做「明显为空」的拦截，真正的校验以服务端为准（那里有 @Valid）
  if (!form.realName || !form.school || !form.studentNo) {
    error.value = '真实姓名、学校与学号为必填项'
    return
  }
  if (!SCHOOL_OPTIONS.includes(form.school)) {
    error.value = '请从列表中选择学校'
    return
  }
  if (form.major && !MAJOR_OPTIONS.includes(form.major)) {
    error.value = '请从列表中选择专业'
    return
  }
  if (form.grade && !GRADE_OPTIONS.includes(form.grade)) {
    error.value = '请从列表中选择年级'
    return
  }

  submitting.value = true
  try {
    const payload = { ...form }
    const res = editingId.value
      ? await updateStudentVerification(editingId.value, payload)
      : await submitStudentVerification(payload)
    okMsg.value = res.message || '已提交，等待管理员审核'
    editingId.value = null
    formOpen.value = false
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    submitting.value = false
  }
}

async function onWithdraw() {
  if (!record.value?.id) return
  busy.value = true
  error.value = ''
  okMsg.value = ''
  try {
    const res = await withdrawStudentVerification(record.value.id)
    okMsg.value = res.message || '已撤回申请'
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

onMounted(load)

// router 目前只用于「进入论坛」这类跳转的兜底；保留引用以便后续加
// 「审核通过后自动跳转」时不必再改 import
void router
</script>

<style scoped>
.status-panel {
  display: grid;
  gap: var(--sp-4);
}

.status-line {
  font-size: 17px;
  line-height: 1.47;
  letter-spacing: -0.374px;
  color: var(--ink-2);
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: var(--sp-4);
  margin: 0;
  padding: var(--sp-4) 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.info-grid dt {
  font-size: 12px;
  letter-spacing: 0.06em;
  color: var(--muted-2);
  margin-bottom: var(--sp-1);
}

.info-grid dd {
  margin: 0;
  font-size: 17px;
  letter-spacing: -0.374px;
  color: var(--ink);
}

/* 驳回原因是这一页最有行动价值的信息，给它一个独立的浅色底，不与正文混在一起 */
.review-note {
  display: grid;
  gap: var(--sp-1);
  padding: var(--sp-4) var(--sp-5);
  border-radius: var(--r-md);
  background: var(--accent-soft);
  font-size: 15px;
  line-height: 1.5;
  color: var(--ink-2);
}

.review-note strong {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--accent);
}

.status-ops,
.verify-ops {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex-wrap: wrap;
}

.verify-form {
  display: grid;
  gap: var(--sp-5);
}

.field-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: var(--sp-4);
}

.verify-band .band-title {
  margin-bottom: var(--sp-4);
}

.band-list {
  display: grid;
  gap: var(--sp-2);
  margin: 0;
  padding-left: var(--sp-5);
  font-size: 17px;
  line-height: 1.47;
  letter-spacing: -0.374px;
  color: rgba(255, 255, 255, 0.8);
}
</style>
