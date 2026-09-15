<template>
  <AppShell
    title="广告位"
    eyebrow="我的"
    subtitle="需要先开通广告位资质：提交资质材料 → 管理员审核 → 才能申请广告位。"
  >
    <template #hero>
      <PageHero
        eyebrow="商户 · 广告位"
        title="申请一个首页广告位"
        lead="先开通资质，再提交广告；两步都由管理员审核。"
        tone="parchment"
        align="left"
      >
        <template #actions>
          <button
            v-if="qualified && !showForm"
            class="btn btn--primary btn--hero"
            type="button"
            @click="showForm = true"
          >
            申请
          </button>
          <a v-else-if="qualified" class="btn btn--secondary btn--hero" href="#my-applications">我的申请</a>
          <a v-else class="btn btn--primary btn--hero" href="#qualification">开通资质</a>
        </template>
      </PageHero>
    </template>

    <template #actions>
      <button class="btn btn--ghost btn--sm" type="button" :disabled="loading" @click="reloadAll">
        {{ loading ? '刷新中…' : '刷新' }}
      </button>
    </template>

    <!-- 资质闸门
         服务端在 POST /ad/applications 入口会检查资质，没有 APPROVED 资质直接 403。
         所以这不是「可选的补充信息」，而是流程的第一步：未开通时**广告申请区整块不渲染**，
         否则用户会先填完一屏表单、点了提交才被拒，而拒因是一句他之前没看到过的规则。 -->
    <section id="qualification" class="panel qual-panel">
      <div class="panel-head">
        <h3>广告位资质</h3>
        <StatusTag
          v-if="qualStatus !== 'NONE'"
          :label="qualLabel"
          :tone="qualTone"
          dot
        />
      </div>

      <SkeletonList v-if="qualLoading" :count="1" />

      <StateBlock v-else-if="qualError" variant="error" :desc="qualError">
        <button class="btn btn--ghost btn--sm" type="button" @click="loadQualification">重新加载</button>
      </StateBlock>

      <template v-else>
        <p class="qual-line">{{ qualDesc }}</p>

        <dl v-if="qualRecord" class="qual-info">
          <div><dt>联系人</dt><dd>{{ qualRecord.applicantName || '—' }}</dd></div>
          <div><dt>联系方式</dt><dd>{{ qualRecord.contact || '—' }}</dd></div>
          <div><dt>主体</dt><dd>{{ qualRecord.company || '未填写' }}</dd></div>
          <div><dt>推广品类</dt><dd>{{ qualRecord.category || '未填写' }}</dd></div>
        </dl>

        <!-- 驳回原因要显眼：用户改材料时唯一有用的信息就是它 -->
        <p v-if="qualRecord?.reviewNote" class="qual-note">
          <strong>审核意见</strong>{{ qualRecord.reviewNote }}
        </p>

        <p v-if="qualOk" class="form-ok">{{ qualOk }}</p>
        <p v-if="qualErr" class="form-error">{{ qualErr }}</p>

        <div class="qual-ops">
          <button
            v-if="qualStatus === 'NONE'"
            class="btn btn--primary btn--sm"
            type="button"
            @click="qualFormOpen = true"
          >
            开通资质
          </button>
          <button
            v-if="qualStatus === 'PENDING'"
            class="btn btn--quiet btn--sm"
            type="button"
            :disabled="qualBusy"
            @click="onWithdrawQualification"
          >
            撤回申请
          </button>
          <button
            v-if="qualStatus === 'REJECTED'"
            class="btn btn--primary btn--sm"
            type="button"
            @click="startQualEdit"
          >
            修改并重新提交
          </button>
          <span v-if="qualified" class="qual-ok">已开通，可直接在下方提交广告位申请。</span>
        </div>

        <form v-if="qualFormOpen" class="qual-form" @submit.prevent="onSubmitQualification">
          <div class="field">
            <label for="q-name">联系人姓名 *</label>
            <input id="q-name" v-model.trim="qualForm.applicantName" class="input" maxlength="50" placeholder="与证件一致" />
          </div>

          <div class="field">
            <label for="q-contact">联系方式 *</label>
            <input id="q-contact" v-model.trim="qualForm.contact" class="input" maxlength="50" placeholder="手机号或微信号" />
          </div>

          <div class="field">
            <label for="q-company">经营主体</label>
            <input id="q-company" v-model.trim="qualForm.company" class="input" maxlength="100" placeholder="个人 / 个体工商户 / 公司名称" />
          </div>

          <div class="field">
            <label for="q-category">推广品类</label>
            <input id="q-category" v-model.trim="qualForm.category" class="input" maxlength="50" placeholder="例如：餐饮 / 家政 / 零售" />
          </div>

          <div class="field">
            <label for="q-intro">简介</label>
            <textarea
              id="q-intro"
              v-model.trim="qualForm.intro"
              class="textarea"
              rows="3"
              maxlength="500"
              placeholder="打算推广什么、面向哪些人群"
            />
          </div>

          <div class="field">
            <span class="field-label">资质证明（选填，如营业执照）</span>
            <ImageUploader v-model="qualForm.licenseImage" purpose="qualification" :max="1" />
          </div>

          <div class="qual-ops">
            <button type="submit" class="btn btn--primary" :disabled="qualSubmitting">
              {{ qualSubmitting ? '提交中…' : (qualEditingId ? '提交修改' : '提交资质申请') }}
            </button>
            <button v-if="qualEditingId" type="button" class="btn btn--quiet" @click="cancelQualEdit">
              取消
            </button>
          </div>
        </form>
      </template>
    </section>

    <div class="ad-grid">
      <div class="ad-main">
        <!-- 未开通资质：这里只说明原因并指回上面，不给一个注定会被 403 的表单 -->
        <section v-if="!qualified" class="panel panel--flat ad-locked">
          <h3>广告位申请暂未开放</h3>
          <p class="lead-body">
            请先在上方完成「广告位资质」的开通。资质通过管理员审核后，这里会出现广告位申请表单。
          </p>
          <a class="btn btn--secondary btn--sm" href="#qualification">去开通资质</a>
        </section>

        <!-- 申请表单：默认收起，避免一屏都是输入框 -->
        <section v-else-if="showForm" class="panel">
          <div class="panel-head">
            <h3>{{ editingId ? '修改广告' : '申请广告位' }}</h3>
            <span class="panel-note">{{ editingId ? '改完需重新审核' : '标题与图片必填' }}</span>
          </div>

          <form class="ad-form" @submit.prevent="onSubmit">
            <div class="field">
              <label for="ad-title">标题</label>
              <input
                id="ad-title"
                v-model.trim="form.title"
                class="input"
                maxlength="100"
                placeholder="楼下咖啡店 · 新店开业"
              />
            </div>

            <div class="field">
              <label for="ad-subtitle">一句话说明（选填）</label>
              <input
                id="ad-subtitle"
                v-model.trim="form.subtitle"
                class="input"
                maxlength="200"
                placeholder="本小区住户第二杯半价"
              />
            </div>

            <div class="field">
              <label for="ad-link">跳转链接（选填）</label>
              <input
                id="ad-link"
                v-model.trim="form.linkUrl"
                class="input"
                maxlength="500"
                placeholder="https://…"
              />
            </div>

            <div class="field">
              <span class="field-label">广告图</span>
              <ImageUploader v-model="form.imageUrl" purpose="adapply" :max="1" />
            </div>

            <!-- 改一条正在轮播的广告意味着它先下线：这条后果必须写清楚 -->
            <p v-if="editingWasLive" class="form-note">
              这条广告正在首页轮播，提交修改后会先下线，等管理员重新审核通过再上线。
            </p>

            <p v-if="error" class="form-error">{{ error }}</p>
            <p v-if="okMsg" class="form-ok">{{ okMsg }}</p>

            <div class="ad-ops">
              <button type="submit" class="btn btn--primary" :disabled="submitting">
                {{ submitting ? '提交中…' : (editingId ? '提交修改' : '提交申请') }}
              </button>
              <button type="button" class="btn btn--quiet" @click="cancelEdit">
                {{ editingId ? '取消修改' : '收起' }}
              </button>
            </div>
          </form>
        </section>

        <section v-else class="panel panel--dark ad-invite">
          <h3>申请一个广告位</h3>
          <button class="btn btn--on-dark" type="button" @click="showForm = true">开始填写</button>
        </section>

        <!-- 我的申请 -->
        <section id="my-applications" class="panel">
          <div class="panel-head">
            <h3>我的申请</h3>
            <span class="panel-note">{{ items.length ? `共 ${items.length} 条` : '' }}</span>
          </div>

          <SkeletonList v-if="loading" :count="2" />

          <StateBlock
            v-else-if="loadError"
            variant="error"
            :desc="loadError"
          >
            <button class="btn btn--ghost btn--sm" type="button" @click="loadMine">重新加载</button>
          </StateBlock>

          <StateBlock
            v-else-if="!items.length"
            title="还没有提交过申请"
            desc="填写标题、上传一张广告图即可提交。"
          >
            <button class="btn btn--primary btn--sm" type="button" @click="showForm = true">申请广告位</button>
          </StateBlock>

          <ul v-else class="list-plain">
            <li v-for="item in items" :key="item.id" class="ad-item">
              <img v-if="item.imageUrl" class="ad-thumb" :src="item.imageUrl" alt="" />
              <div class="ad-item-main">
                <div class="ad-item-head">
                  <strong class="brand-title">{{ item.title }}</strong>
                  <span class="tag" :class="adStatusMeta(item.status).tone">{{ item.statusLabel }}</span>
                </div>
                <p v-if="item.subtitle" class="caption">{{ item.subtitle }}</p>
                <p class="ad-note fine-print">{{ adStatusHint(item) }}</p>
              </div>
              <div class="ad-item-ops">
                <button
                  type="button"
                  class="btn btn--ghost btn--sm"
                  :disabled="busyId === item.id"
                  @click="startEdit(item)"
                >
                  修改
                </button>
                <button
                  v-if="canWithdraw(item.status)"
                  type="button"
                  class="btn btn--quiet btn--sm"
                  :disabled="busyId === item.id"
                  @click="onWithdraw(item)"
                >
                  撤回
                </button>
              </div>
            </li>
          </ul>

          <p v-if="actionError" class="form-error ad-action-error">{{ actionError }}</p>
        </section>
      </div>

      <aside class="ad-rail">
        <section class="panel panel--flat">
          <p class="eyebrow">审核流程</p>
          <ul class="ad-steps">
            <li><span class="step-no">1</span>提交，状态「待审核」</li>
            <li><span class="step-no">2</span>管理员查看图片与文案</li>
            <li><span class="step-no">3</span>通过后立即进入首页轮播</li>
          </ul>
        </section>

        <section class="panel panel--dark">
          <p class="eyebrow on-dark-muted">不接</p>
          <ul class="ad-rule-list on-dark-muted">
            <li>医疗、金融、借贷类</li>
            <li>夸大宣传与虚假折扣</li>
            <li>未授权的图片素材</li>
          </ul>
        </section>
      </aside>
    </div>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import AppShell from '../layouts/AppShell.vue'
import PageHero from '../components/PageHero.vue'
import ImageUploader from '../components/ImageUploader.vue'
import StateBlock from '../components/StateBlock.vue'
import SkeletonList from '../components/SkeletonList.vue'
import StatusTag from '../components/StatusTag.vue'
import {
  applyAdSlot,
  fetchMyAdApplications,
  fetchMyAdQualification,
  submitAdQualification,
  updateAdApplication,
  updateAdQualification,
  withdrawAdApplication,
  withdrawAdQualification
} from '../api/ad'
import { adStatusHint, adStatusMeta, canWithdraw } from '../utils/adStatus'

/**
 * 广告位申请。
 *
 * 这是「申请 → 管理员审核 → 上线」这条链路的用户端：
 * 提交后落库为 PENDING，**审核通过前不会出现在首页轮播**（服务端按状态过滤）。
 * 页面上不做任何权限判断——谁能审核由服务端说了算。
 *
 * 本轮多了一道**前置闸门**：广告位从顶级入口移入「我的」，并且必须先开通
 * 广告位资质。闸门真正的执行点在服务端（AdService.apply 里的 403），
 * 这里只负责把流程顺序呈现出来，不让用户白填一屏表单。
 */
const items = ref([])
const loading = ref(true)
const loadError = ref('')
const error = ref('')
const okMsg = ref('')
const actionError = ref('')
const submitting = ref(false)
const busyId = ref(null)
const showForm = ref(false)

// ---------------------------------------------------------------- 资质
const QUAL_TEXT = {
  NONE: { desc: '还没有开通广告位资质。填写下面的材料并提交，管理员审核通过后即可申请广告位。' },
  PENDING: { desc: '资质材料已提交，等待管理员审核。通过后下方会出现广告位申请表单。' },
  APPROVED: { desc: '资质已开通。' },
  REJECTED: { desc: '资质申请没有通过。请按下面的审核意见修改材料后重新提交。' }
}

const qualStatus = ref('NONE')
const qualRecord = ref(null)
const qualLoading = ref(true)
const qualError = ref('')
const qualOk = ref('')
const qualErr = ref('')
const qualSubmitting = ref(false)
const qualBusy = ref(false)
const qualFormOpen = ref(false)
const qualEditingId = ref(null)

const qualForm = reactive({
  applicantName: '',
  contact: '',
  company: '',
  category: '',
  intro: '',
  licenseImage: ''
})

const qualified = computed(() => qualStatus.value === 'APPROVED')
const qualLabel = computed(() => ({
  PENDING: '待审核', APPROVED: '已开通', REJECTED: '已驳回', NONE: '未开通'
}[qualStatus.value] || qualStatus.value))
const qualTone = computed(() => ({
  PENDING: 'open', APPROVED: 'done', REJECTED: 'cancelled', NONE: 'outline'
}[qualStatus.value] || 'outline'))
const qualDesc = computed(() => QUAL_TEXT[qualStatus.value]?.desc || '')

function fillQualForm(source) {
  qualForm.applicantName = source?.applicantName || ''
  qualForm.contact = source?.contact || ''
  qualForm.company = source?.company || ''
  qualForm.category = source?.category || ''
  qualForm.intro = source?.intro || ''
  qualForm.licenseImage = source?.licenseImage || ''
}

async function loadQualification() {
  qualLoading.value = true
  qualError.value = ''
  try {
    const res = await fetchMyAdQualification()
    qualStatus.value = res.data?.status || 'NONE'
    qualRecord.value = res.data?.qualification || null
    // 从未提交过就直接展开表单；被驳回时先让用户读完审核意见，
    // 点「修改并重新提交」再回填
    if (qualStatus.value === 'NONE') {
      qualFormOpen.value = true
      fillQualForm(null)
    } else {
      qualFormOpen.value = false
    }
  } catch (e) {
    qualError.value = e.message
  } finally {
    qualLoading.value = false
  }
}

function startQualEdit() {
  fillQualForm(qualRecord.value)
  qualEditingId.value = qualRecord.value?.id || null
  qualFormOpen.value = true
  qualOk.value = ''
  qualErr.value = ''
}

function cancelQualEdit() {
  qualEditingId.value = null
  qualFormOpen.value = false
  qualErr.value = ''
}

async function onSubmitQualification() {
  qualErr.value = ''
  qualOk.value = ''
  if (!qualForm.applicantName || !qualForm.contact) {
    qualErr.value = '联系人姓名与联系方式为必填项'
    return
  }
  qualSubmitting.value = true
  try {
    const payload = { ...qualForm }
    const res = qualEditingId.value
      ? await updateAdQualification(qualEditingId.value, payload)
      : await submitAdQualification(payload)
    qualOk.value = res.message || '已提交，等待管理员审核'
    qualEditingId.value = null
    qualFormOpen.value = false
    await loadQualification()
  } catch (e) {
    qualErr.value = e.message
  } finally {
    qualSubmitting.value = false
  }
}

async function onWithdrawQualification() {
  if (!qualRecord.value?.id) return
  if (!window.confirm('撤回资质申请？撤回后需要重新提交。')) return
  qualBusy.value = true
  qualErr.value = ''
  qualOk.value = ''
  try {
    const res = await withdrawAdQualification(qualRecord.value.id)
    qualOk.value = res.message || '已撤回申请'
    await loadQualification()
  } catch (e) {
    qualErr.value = e.message
  } finally {
    qualBusy.value = false
  }
}

/** 顶栏刷新按钮：两段状态一起刷新，避免出现「资质已通过但申请区还锁着」的错觉 */
async function reloadAll() {
  await Promise.all([loadQualification(), loadMine()])
}

/**
 * 修改模式：非空表示当前表单在改这一条，而不是新建。
 * `editingWasLive` 单独记一下「改之前是不是正在轮播」——提交前要明确告诉用户
 * 这条会先下线，而不是让他提交完才发现广告不见了。
 */
const editingId = ref(null)
const editingWasLive = ref(false)

const form = reactive({ title: '', subtitle: '', linkUrl: '', imageUrl: '' })

async function loadMine() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await fetchMyAdApplications()
    items.value = res.data || []
  } catch (e) {
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.title = ''
  form.subtitle = ''
  form.linkUrl = ''
  form.imageUrl = ''
}

/** 进入修改模式：把这一条读进表单 */
function startEdit(item) {
  editingId.value = item.id
  editingWasLive.value = item.status === 'APPROVED'
  form.title = item.title || ''
  form.subtitle = item.subtitle || ''
  form.linkUrl = item.linkUrl || ''
  form.imageUrl = item.imageUrl || ''
  error.value = ''
  okMsg.value = ''
  showForm.value = true
}

function cancelEdit() {
  editingId.value = null
  editingWasLive.value = false
  error.value = ''
  resetForm()
  showForm.value = false
}

async function onSubmit() {
  submitting.value = true
  error.value = ''
  okMsg.value = ''
  try {
    if (editingId.value) {
      // 修改 = 重新送审：已通过的会被服务端退回待审并从轮播撤下
      await updateAdApplication(editingId.value, { ...form })
      okMsg.value = editingWasLive.value
        ? '已提交修改，原广告已下线，等待重新审核。'
        : '已提交修改，等待管理员审核。'
      cancelEdit()
    } else {
      await applyAdSlot({ ...form })
      okMsg.value = '已提交，等待管理员审核。'
      resetForm()
    }
    await loadMine()
  } catch (e) {
    error.value = e.message
  } finally {
    submitting.value = false
  }
}

async function onWithdraw(item) {
  if (!window.confirm(`撤回「${item.title}」这条申请？撤回后需要重新提交。`)) return
  busyId.value = item.id
  actionError.value = ''
  try {
    await withdrawAdApplication(item.id)
    if (editingId.value === item.id) cancelEdit()
    await loadMine()
  } catch (e) {
    actionError.value = e.message
  } finally {
    busyId.value = null
  }
}

onMounted(reloadAll)
</script>

<style scoped>
/* ---------- 资质闸门 ---------- */
.qual-panel {
  display: grid;
  gap: var(--sp-4);
  margin-bottom: var(--sp-6);
}

.qual-line {
  font-size: 17px;
  line-height: 1.47;
  letter-spacing: -0.374px;
  color: var(--ink-2);
}

.qual-info {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: var(--sp-4);
  margin: 0;
  padding: var(--sp-4) 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.qual-info dt {
  font-size: 12px;
  letter-spacing: 0.06em;
  color: var(--muted-2);
  margin-bottom: var(--sp-1);
}

.qual-info dd {
  margin: 0;
  font-size: 17px;
  letter-spacing: -0.374px;
  color: var(--ink);
}

/* 驳回原因给独立浅色底，不与正文混在一起 */
.qual-note {
  display: grid;
  gap: var(--sp-1);
  padding: var(--sp-4) var(--sp-5);
  border-radius: var(--r-md);
  background: var(--accent-soft);
  font-size: 15px;
  line-height: 1.5;
  color: var(--ink-2);
}

.qual-note strong {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--accent);
}

.qual-ops {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex-wrap: wrap;
}

.qual-ok {
  font-size: 15px;
  letter-spacing: -0.224px;
  color: var(--muted);
}

.qual-form {
  display: grid;
  gap: var(--sp-4);
  padding-top: var(--sp-4);
  border-top: 1px solid var(--line);
}

/* 未开通资质时的占位：说明原因 + 指回上面的入口，不给表单 */
.ad-locked {
  display: grid;
  gap: var(--sp-3);
  justify-items: start;
}

.ad-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(280px, 1fr);
  gap: var(--sp-6);
  align-items: start;
}

.ad-main {
  display: grid;
  gap: var(--sp-5);
  min-width: 0;
}

.ad-rail {
  display: grid;
  gap: var(--sp-5);
  align-content: start;
}

.ad-form {
  display: grid;
  gap: var(--sp-4);
}

.ad-ops {
  display: flex;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

.form-error {
  padding: var(--sp-3) var(--sp-4);
  border: 1px solid rgba(192, 69, 58, 0.28);
  border-radius: var(--r-md);
  color: var(--danger);
  font-size: 14px;
  line-height: 1.5;
}

.form-ok {
  padding: var(--sp-3) var(--sp-4);
  border-radius: var(--r-md);
  background: var(--parchment);
  color: var(--success);
  font-size: 14px;
  line-height: 1.5;
}

/* 修改正在轮播的广告 = 它先下线：这条后果必须在提交前说清 */
.form-note {
  padding: var(--sp-3) var(--sp-4);
  border-radius: var(--r-md);
  background: var(--parchment);
  color: var(--warning);
  font-size: 13px;
  line-height: 1.6;
  letter-spacing: -0.1px;
}

.ad-action-error {
  margin-top: var(--sp-4);
}

/* 未展开时的引导块（近黑叙事）：一句话 + 一个按钮就够 */
.ad-invite {
  display: grid;
  gap: var(--sp-4);
  justify-items: start;
}

/* 申请行：缩略图 + 文案 + 操作 */
.ad-item {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-4);
}

.ad-item-ops {
  display: flex;
  gap: var(--sp-2);
  flex-wrap: wrap;
  flex: none;
}

.ad-thumb {
  width: 96px;
  height: 60px;
  flex: none;
  object-fit: cover;
  border-radius: var(--r-sm);
  border: 1px solid var(--line);
  background: var(--parchment);
}

.ad-item-main {
  display: grid;
  gap: var(--sp-1);
  min-width: 0;
  flex: 1;
}

.ad-item-head {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

.ad-note {
  margin: 0;
}

.ad-steps {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: var(--sp-3);
}

.ad-steps li {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-3);
  font-size: 14px;
  line-height: 1.5;
  color: var(--ink-2);
}

.step-no {
  width: 22px;
  height: 22px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 50%;
  border: 1px solid var(--line-strong);
  color: var(--muted);
  font-size: 12px;
  font-weight: 600;
}

.ad-rule-list {
  margin: 0;
  padding-left: 1.1em;
  display: grid;
  gap: var(--sp-2);
  font-size: 14px;
  line-height: 1.5;
}

@media (max-width: 1040px) {
  .ad-grid { grid-template-columns: minmax(0, 1fr); }
}

@media (max-width: 640px) {
  .ad-item { flex-wrap: wrap; }
  .ad-thumb { width: 100%; height: 132px; }
}
</style>
