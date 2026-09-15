<template>
  <AppShell
    title="回收"
    eyebrow="发现"
    :subtitle="subtitle"
  >
    <!-- 通栏英雄瓦片：二级导航只留 2 个字定位，大声的话都在这里说一次 -->
    <template #hero>
      <PageHero
        eyebrow="上门回收 · 城区"
        title="二手回收"
        lead="写清品类、地址，回收员上门。"
        tone="parchment"
        align="left"
      />
    </template>

    <template #actions>
      <button class="btn btn--primary btn--sm" type="button" @click="scrollToForm">
        预约上门
      </button>
      <button class="btn btn--ghost btn--sm" type="button" :disabled="guideLoading" @click="reloadGuide">
        {{ guideLoading ? '刷新中…' : '刷新价目' }}
      </button>
    </template>

    <div class="recycle-grid">
      <div class="recycle-main">
        <!-- 品类与计价说明：先讲清楚「什么能收、大概多少钱」，再让人填单 -->
        <section class="panel">
          <div class="panel-head">
            <h2>可回收品类与计价</h2>
            <span class="tag tag--outline">按公斤 / 按台</span>
          </div>

          <SkeletonList v-if="guideLoading" :count="3" />

          <StateBlock v-else-if="guideError" variant="error" inline :desc="guideError">
            <button class="btn btn--ghost btn--sm" type="button" @click="reloadGuide">重新加载</button>
          </StateBlock>

          <StateBlock
            v-else-if="!categories.length"
            inline
            title="暂时拿不到回收价目"
            desc="品类与单价由后端下发，稍后再试一次。"
          >
            <button class="btn btn--ghost btn--sm" type="button" @click="reloadGuide">重新加载</button>
          </StateBlock>

          <template v-else>
            <p class="pricing-note caption">{{ pricingNote }}</p>

            <!-- 价目表：单价靠右对齐成一列，比一格格染色小卡更好比价 -->
            <ul class="list-plain enter-stagger">
              <li v-for="c in categories" :key="c.code" class="category-row">
                <div class="category-main">
                  <h3>{{ c.label }}</h3>
                  <p class="category-hint caption">{{ c.hint }}</p>
                </div>
                <span class="tag" :class="c.unitPrice ? 'tag--open' : 'tag--outline'">
                  {{ priceText(c) }}
                </span>
              </li>
            </ul>

            <div v-if="slots.length" class="slot-strip">
              <span class="slot-label field-label">上门时段</span>
              <span v-for="s in slots" :key="s.code" class="tag tag--outline">
                {{ s.label }} {{ s.window }}
              </span>
            </div>
          </template>
        </section>

        <!-- 预约表单 -->
        <section ref="formRef" class="panel form-panel">
          <div class="panel-head">
            <h2>预约上门</h2>
            <span class="tag tag--outline">只约半天</span>
          </div>

          <form class="recycle-form" novalidate @submit.prevent="onSubmit">
            <div class="field-row">
              <div class="field">
                <label for="recycle-category">回收品类</label>
                <select id="recycle-category" v-model="form.category" class="select">
                  <option value="">请选择品类</option>
                  <option v-for="c in categories" :key="c.code" :value="c.code">
                    {{ c.label }}
                  </option>
                </select>
                <p v-if="errors.category" class="field-error">{{ errors.category }}</p>
                <p v-else class="field-hint">{{ categoryHint }}</p>
              </div>

              <div class="field">
                <label for="recycle-weight">预估重量（公斤，选填）</label>
                <input
                  id="recycle-weight"
                  v-model.trim="form.weightKg"
                  class="input nums"
                  type="number"
                  min="0.01"
                  step="0.01"
                  placeholder="留空由师傅上门称重"
                />
                <p v-if="errors.weightKg" class="field-error">{{ errors.weightKg }}</p>
                <p v-else class="field-hint">{{ estimateHint }}</p>
              </div>
            </div>

            <div class="field">
              <label for="recycle-desc">物品描述（选填）</label>
              <textarea
                id="recycle-desc"
                v-model.trim="form.description"
                class="textarea"
                rows="2"
                maxlength="500"
                placeholder="例如：三个纸箱 + 一袋饮料瓶，已捆好放在门口"
              />
              <div class="field-foot">
                <span v-if="errors.description" class="field-error">{{ errors.description }}</span>
                <span class="field-hint nums">{{ form.description.length }} / 500</span>
              </div>
            </div>

            <div class="field">
              <label for="recycle-address">上门地址</label>
              <input
                id="recycle-address"
                v-model.trim="form.address"
                class="input"
                type="text"
                maxlength="200"
                placeholder="小区 + 楼栋 + 门牌号，例如：幸福小区 3 号楼 2 单元 501"
              />
              <p v-if="errors.address" class="field-error">{{ errors.address }}</p>
              <p v-else class="field-hint">写清楼栋与门牌，师傅不用再打电话问路。</p>
            </div>

            <div class="field-row">
              <div class="field">
                <label for="recycle-phone">联系电话</label>
                <input
                  id="recycle-phone"
                  v-model.trim="form.contactPhone"
                  class="input nums"
                  type="tel"
                  maxlength="11"
                  inputmode="numeric"
                  placeholder="11 位手机号"
                />
                <p v-if="errors.contactPhone" class="field-error">{{ errors.contactPhone }}</p>
                <p v-else class="field-hint">仅用于本次上门联系，不会公开展示。</p>
              </div>

              <div class="field">
                <label for="recycle-date">上门日期</label>
                <input
                  id="recycle-date"
                  v-model="form.appointDate"
                  class="input"
                  type="date"
                  :min="today"
                />
                <p v-if="errors.appointDate" class="field-error">{{ errors.appointDate }}</p>
                <p v-else class="field-hint">不能早于今天。</p>
              </div>
            </div>

            <div class="field">
              <label for="recycle-slot">上门时段</label>
              <select id="recycle-slot" v-model="form.appointSlot" class="select">
                <option value="">请选择时段</option>
                <option v-for="s in slots" :key="s.code" :value="s.code">
                  {{ s.label }}（{{ s.window }}）
                </option>
              </select>
              <p v-if="errors.appointSlot" class="field-error">{{ errors.appointSlot }}</p>
              <p v-else class="field-hint">回收按半天派单，选一个方便在家等的时间段。</p>
            </div>

            <div class="field">
              <label for="recycle-remark">备注（选填）</label>
              <textarea
                id="recycle-remark"
                v-model.trim="form.remark"
                class="textarea"
                rows="2"
                maxlength="200"
                placeholder="例如：家里有老人，请先按门铃；东西在楼道口"
              />
              <div class="field-foot">
                <span v-if="errors.remark" class="field-error">{{ errors.remark }}</span>
                <span class="field-hint nums">{{ form.remark.length }} / 200</span>
              </div>
            </div>

            <p v-if="formError" class="global-error caption">{{ formError }}</p>
            <p v-if="formOk" class="global-ok caption">{{ formOk }}</p>

            <div class="form-foot">
              <span class="field-hint">提交后可在下方「我的预约」里查看状态或取消。</span>
              <button class="btn btn--primary" type="submit" :disabled="submitting || !categories.length">
                {{ submitting ? '提交中…' : '提交预约' }}
              </button>
            </div>
          </form>
        </section>

        <!-- 我的预约 -->
        <section class="panel">
          <div class="panel-head">
            <h2>我的预约</h2>
            <button class="panel-more" type="button" :disabled="ordersLoading" @click="refreshOrders()">
              {{ ordersLoading ? '加载中…' : '刷新' }}
            </button>
          </div>

          <p v-if="actionError" class="global-error caption">{{ actionError }}</p>

          <div class="segmented order-filters" role="group" aria-label="按状态筛选预约">
            <button
              v-for="f in statusFilters"
              :key="f.value"
              type="button"
              :aria-pressed="orderStatus === f.value"
              @click="changeOrderStatus(f.value)"
            >
              {{ f.label }}
            </button>
          </div>

          <SkeletonList v-if="ordersLoading" :count="3" />

          <StateBlock v-else-if="ordersError" variant="error" inline :desc="ordersError">
            <button class="btn btn--ghost btn--sm" type="button" @click="loadOrders()">重新加载</button>
          </StateBlock>

          <StateBlock
            v-else-if="!orders.length"
            inline
            :title="orderEmptyTitle"
            :desc="orderEmptyDesc"
          >
            <button v-if="orderStatus" class="btn btn--ghost btn--sm" type="button" @click="changeOrderStatus('')">
              查看全部预约
            </button>
            <button v-else class="btn btn--primary btn--sm" type="button" @click="scrollToForm">
              预约上门回收
            </button>
          </StateBlock>

          <ul v-else class="list-plain">
            <li v-for="order in orders" :key="order.id" class="order-item">
              <div class="order-main">
                <div class="order-title">
                  <strong class="brand-title">{{ order.categoryLabel || order.category }}</strong>
                  <span class="tag" :class="orderTone(order.status)">
                    {{ order.statusLabel || order.status }}
                  </span>
                  <span v-if="order.estimatedAmount" class="tag tag--gold nums">
                    预估 {{ money(order.estimatedAmount) }}
                  </span>
                </div>
                <div class="meta-row">
                  <span>{{ dateText(order.appointDate) }} {{ order.appointSlotLabel || '' }}</span>
                  <span v-if="order.appointSlotWindow" class="meta-dot">{{ order.appointSlotWindow }}</span>
                  <span v-if="order.weightKg" class="meta-dot nums">估重 {{ order.weightKg }} 公斤</span>
                  <span class="meta-dot">提交于 {{ relativeTime(order.createdAt) }}</span>
                </div>
                <p class="order-address caption">{{ order.address }}</p>
                <p v-if="order.description" class="order-extra fine-print">物品：{{ order.description }}</p>
                <p v-if="order.remark" class="order-extra fine-print">备注：{{ order.remark }}</p>
              </div>

              <div class="order-ops">
                <button
                  v-if="canCancel(order)"
                  class="btn btn--danger btn--sm"
                  type="button"
                  :disabled="busyId === order.id"
                  @click="onCancel(order)"
                >
                  取消预约
                </button>
                <span v-else class="order-note fine-print">{{ cancelHint(order) }}</span>
              </div>
            </li>
          </ul>
        </section>
      </div>

      <aside class="recycle-rail">
        <section class="panel">
          <div class="panel-head">
            <h3>回收流程</h3>
          </div>
          <ol class="steps">
            <li>
              <strong class="brand-title">提交预约</strong>
              <small class="caption">状态为「待确认」</small>
            </li>
            <li>
              <strong class="brand-title">回收员确认</strong>
              <small class="caption">状态变为「已确认」，按约定时段上门</small>
            </li>
            <li>
              <strong class="brand-title">当面称重</strong>
              <small class="caption">现场核验品类与重量</small>
            </li>
            <li>
              <strong class="brand-title">现场结算</strong>
              <small class="caption">完成后状态为「已完成」</small>
            </li>
          </ol>
        </section>

        <section class="panel">
          <div class="panel-head">
            <h3>我的回收</h3>
            <button class="panel-more" type="button" @click="refreshOrders()">刷新</button>
          </div>
          <!-- 三个数字收进一条近黑概览带：这一页唯一的重音量 -->
          <div class="stat-strip stat-strip--dark rail-strip">
            <div class="stat">
              <span class="stat-label">待确认</span>
              <strong class="stat-value">{{ orderCounts.PENDING }}</strong>
            </div>
            <div class="stat">
              <span class="stat-label">已确认</span>
              <strong class="stat-value">{{ orderCounts.CONFIRMED }}</strong>
            </div>
            <div class="stat">
              <span class="stat-label">已完成</span>
              <strong class="stat-value">{{ orderCounts.DONE }}</strong>
            </div>
          </div>
          <p class="rail-hint panel-note">统计按「全部状态」那一份数据计算，切换筛选不会影响它。</p>
        </section>

        <section class="panel panel--dark">
          <div class="panel-head">
            <h3>计价与结算</h3>
          </div>
          <p class="rail-copy caption">{{ pricingNote }}</p>
          <p class="rail-hint panel-note">
            订单里的「预估」= 估重 × 参考单价，只在按公斤计价的品类上出现；
            旧家电等按台评估的品类不显示预估金额。
          </p>
        </section>
      </aside>
    </div>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import AppShell from '../layouts/AppShell.vue'
import PageHero from '../components/PageHero.vue'
import StateBlock from '../components/StateBlock.vue'
import SkeletonList from '../components/SkeletonList.vue'
import {
  cancelRecycleOrder,
  createRecycleOrder,
  fetchMyRecycleOrders,
  fetchRecycleGuide
} from '../api/recycle'
import { formatDate, relativeTime } from '../utils/format'

/**
 * 上门回收。
 *
 * 版块顺序照居民的实际决策走：先看「什么能收、多少钱、什么时候来」
 * （/recycle/categories），再填预约单，最后是「我的预约」用于改主意时取消。
 * 注意本页整体需要登录（路由 requiresAuth），这个顺序是站内浏览顺序，
 * 不是「登录前先看价目表」——那需要同时放开前端路由与后端接口。
 *
 * 品类、参考单价与上门时段全部由后端下发，前端不写死价目与时段文案——
 * 后端把单价放在枚举里，就是为了避免「页面写 0.8 元/公斤、后端按 0.5 算」的口径分叉。
 */

const statusFilters = [
  { label: '全部', value: '' },
  { label: '待确认', value: 'PENDING' },
  { label: '已确认', value: 'CONFIRMED' },
  { label: '已完成', value: 'DONE' },
  { label: '已取消', value: 'CANCELLED' }
]

const categories = ref([])
const slots = ref([])
const pricingNote = ref('')

const guideLoading = ref(true)
const guideError = ref('')

const orders = ref([])
const ordersLoading = ref(true)
const ordersError = ref('')
const orderStatus = ref('')
/** 右栏统计用独立的一份数据，避免切换筛选后数字跟着变 */
const allOrders = ref([])
const busyId = ref(null)

const formRef = ref(null)
const submitting = ref(false)
const formError = ref('')
const formOk = ref('')
/** 取消预约等「列表侧」操作的错误单独放，不要混进表单的错误位 */
const actionError = ref('')
const form = reactive({
  category: '',
  weightKg: '',
  description: '',
  address: '',
  contactPhone: '',
  appointDate: '',
  appointSlot: '',
  remark: ''
})
const errors = reactive({})

/** 用本地时区算「今天」：toISOString 是 UTC，凌晨 8 点前会算成昨天 */
const today = formatDate(new Date())

const subtitle = computed(() => {
  if (guideLoading.value) return '正在加载可回收品类与计价'
  if (guideError.value) return '价目与服务时段加载失败'
  const pending = orderCounts.value.PENDING
  return pending ? `${categories.value.length} 个品类可回收 · 你有 ${pending} 条预约待确认` : `共 ${categories.value.length} 个品类可回收`
})

const categoryHint = computed(() => {
  const c = categories.value.find((x) => x.code === form.category)
  return c ? c.hint : '不同品类的计价方式不同，选好后单价会写在下方预估里。'
})

/** 预估金额只是给居民一个量级，最终以后端落库值与上门称重为准 */
const estimateHint = computed(() => {
  const c = categories.value.find((x) => x.code === form.category)
  if (!c) return '留空也可以，师傅会现场称重。'
  if (!c.unitPrice) return `${c.label}（${c.unit}）不按重量估价，由师傅上门评估。`
  const weight = Number(form.weightKg)
  if (!Number.isFinite(weight) || weight <= 0) return `参考单价 ${c.unitPrice} 元/${c.unit}，填了估重会即时算出预估金额。`
  const amount = (weight * Number(c.unitPrice)).toFixed(2)
  return `按参考单价计算，预估约 ${amount} 元；实际以现场称重为准。`
})

const orderCounts = computed(() => {
  const counts = { PENDING: 0, CONFIRMED: 0, DONE: 0, CANCELLED: 0 }
  allOrders.value.forEach((o) => {
    if (counts[o.status] !== undefined) counts[o.status] += 1
  })
  return counts
})

const orderEmptyTitle = computed(() => (
  orderStatus.value
    ? `没有${statusFilters.find((f) => f.value === orderStatus.value)?.label || ''}的预约`
    : '还没有预约记录'
))

const orderEmptyDesc = computed(() => (
  orderStatus.value
    ? '换个状态看看，或者重新提交一次预约。'
    : '填一份预约单，回收员会按你选的时间段上门称重结算。'
))

function priceText(category) {
  if (!category.unitPrice) return '上门评估'
  return `${category.unitPrice} 元/${category.unit}`
}

function money(value) {
  const n = Number(value)
  return Number.isFinite(n) ? `¥${n.toFixed(2)}` : ''
}

function orderTone(status) {
  if (status === 'PENDING') return 'tag--open'
  if (status === 'CONFIRMED') return 'tag--accepted'
  if (status === 'DONE') return 'tag--done'
  return 'tag--cancelled'
}

/** 上门日期只做字符串裁剪：后端给的是 LocalDate（YYYY-MM-DD），转 Date 反而会因时区漂移一天 */
function dateText(value) {
  return String(value || '').slice(0, 10)
}

function canCancel(order) {
  return order.status === 'PENDING' || order.status === 'CONFIRMED'
}

function cancelHint(order) {
  if (order.status === 'DONE') return '已完成，无法取消'
  if (order.status === 'CANCELLED') return '已取消'
  return ''
}

async function loadGuide() {
  guideLoading.value = true
  guideError.value = ''
  try {
    const res = await fetchRecycleGuide()
    const data = res.data || {}
    categories.value = data.categories || []
    slots.value = data.slots || []
    pricingNote.value = data.pricingNote || ''
    // 日期默认今天（后端要求不能早于今天），品类与时段不预选：
    // 品类决定计价口径，预选错一个就等于报了错单，让居民自己确认一次更稳妥
    if (!form.appointDate) form.appointDate = today
  } catch (e) {
    guideError.value = e.message
  } finally {
    guideLoading.value = false
  }
}

async function reloadGuide() {
  await loadGuide()
}

async function loadOrders(status = orderStatus.value) {
  ordersLoading.value = true
  ordersError.value = ''
  try {
    const params = { page: 0, size: 30 }
    if (status) params.status = status
    const res = await fetchMyRecycleOrders(params)
    orders.value = res.data?.content || []
    // 不带 status 的这次请求本身就是「全部」，直接复用为右栏统计，少打一次接口
    if (!status) allOrders.value = orders.value
  } catch (e) {
    ordersError.value = e.message
    orders.value = []
  } finally {
    ordersLoading.value = false
  }
}

/** 右栏统计始终基于「全部状态」，所以单独取一次不带 status 的列表 */
async function loadAllOrders() {
  try {
    const res = await fetchMyRecycleOrders({ page: 0, size: 50 })
    allOrders.value = res.data?.content || []
  } catch {
    allOrders.value = []
  }
}

function changeOrderStatus(value) {
  orderStatus.value = value
  loadOrders(value)
}

/** 当前筛选的列表与右栏统计来自两份数据，刷新时一起取，避免数字与列表不一致 */
function refreshOrders() {
  const tasks = [loadOrders()]
  // 无筛选时 loadOrders 已经拿到了「全部」，不必再打一次
  if (orderStatus.value) tasks.push(loadAllOrders())
  return Promise.all(tasks)
}

function scrollToForm() {
  formRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function clearErrors() {
  Object.keys(errors).forEach((k) => { delete errors[k] })
}

/** 校验规则与后端 CreateRecycleOrderRequest 的注解保持一致 */
function validate() {
  clearErrors()
  if (!form.category) errors.category = '请选择回收品类'

  if (form.weightKg !== '') {
    const weight = Number(form.weightKg)
    if (!Number.isFinite(weight)) errors.weightKg = '估重必须是数字'
    else if (weight <= 0) errors.weightKg = '估重需大于 0'
    else if (!/^\d{1,6}(\.\d{1,2})?$/.test(String(form.weightKg))) errors.weightKg = '估重最多两位小数'
  }

  if (form.description.length > 500) errors.description = '物品描述最多 500 字'

  const address = form.address.trim()
  if (!address) errors.address = '地址不能为空'
  else if (address.length > 200) errors.address = '地址最多 200 字'

  const phone = form.contactPhone.trim()
  if (!phone) errors.contactPhone = '联系电话不能为空'
  else if (!/^1\d{10}$/.test(phone)) errors.contactPhone = '手机号格式错误，应为 1 开头的 11 位数字'

  if (!form.appointDate) errors.appointDate = '请选择上门日期'
  else if (dateText(form.appointDate) < today) errors.appointDate = '上门日期不能早于今天'

  if (!form.appointSlot) errors.appointSlot = '请选择上门时段'
  if (form.remark.length > 200) errors.remark = '备注最多 200 字'

  return Object.keys(errors).length === 0
}

async function onSubmit() {
  formError.value = ''
  formOk.value = ''
  if (!validate()) {
    formError.value = '请先修正表单中标红的问题。'
    return
  }
  submitting.value = true
  try {
    const res = await createRecycleOrder({
      category: form.category,
      weightKg: form.weightKg === '' ? null : Number(form.weightKg),
      description: form.description.trim() || null,
      address: form.address.trim(),
      contactPhone: form.contactPhone.trim(),
      appointDate: form.appointDate,
      appointSlot: form.appointSlot,
      remark: form.remark.trim() || null
    })
    const amount = res.data?.estimatedAmount
    formOk.value = amount
      ? `预约成功，预估 ${money(amount)} 元，实际以师傅上门称重为准。`
      : '预约成功，回收员确认后会按约定时段上门。'
    // 只清掉与「这一单」强相关的字段，地址与电话保留，方便同一户再约一次
    form.weightKg = ''
    form.description = ''
    form.remark = ''
    await refreshOrders()
  } catch (e) {
    formError.value = e.message
  } finally {
    submitting.value = false
  }
}

async function onCancel(order) {
  // 取消会终止已经派出的上门安排，属于危险操作，必须二次确认
  const when = `${dateText(order.appointDate)} ${order.appointSlotLabel || ''}`.trim()
  if (!window.confirm(`确认取消 ${when} 的「${order.categoryLabel || order.category}」回收预约？`)) return
  busyId.value = order.id
  actionError.value = ''
  try {
    const res = await cancelRecycleOrder(order.id)
    Object.assign(order, res.data || {})
    // 取消后该单通常不再符合当前筛选（例如正在看「待确认」），直接重取一次
    await refreshOrders()
  } catch (e) {
    actionError.value = e.message
  } finally {
    busyId.value = null
  }
}

onMounted(async () => {
  await Promise.all([loadGuide(), loadOrders()])
  if (orderStatus.value) await loadAllOrders()
})
</script>

<style scoped>
.recycle-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(280px, 1fr);
  gap: var(--sp-5);
  align-items: start;
}

.recycle-main {
  display: grid;
  gap: var(--sp-4);
  min-width: 0;
  align-content: start;
}

.recycle-rail {
  display: grid;
  gap: var(--sp-4);
  position: sticky;
  /* 粘在两级导航之下，而不是贴着视口顶端 */
  top: calc(var(--nav-stack) + var(--sp-4));
}

/* ---------- 品类与计价：价目表用发丝线分行，不做一格格染色小卡 ---------- */
.pricing-note {
  margin-bottom: var(--sp-4);
  padding: var(--sp-4) var(--sp-5);
  border-radius: var(--r-md);
  background: var(--parchment);
}

.category-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--sp-4);
}

.category-main {
  display: grid;
  gap: var(--sp-1);
  min-width: 0;
}

.slot-strip {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
  margin-top: var(--sp-4);
  padding-top: var(--sp-4);
  border-top: 1px solid var(--line);
}

/* ---------- 表单 ---------- */
.form-panel { scroll-margin-top: var(--nav-stack); }

.recycle-form {
  display: grid;
  gap: var(--sp-4);
}

.field-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--sp-4);
}

.field-foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--sp-3);
}

.form-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  flex-wrap: wrap;
  padding-top: var(--sp-3);
  border-top: 1px solid var(--line);
}

/* ---------- 我的预约 ---------- */
.order-filters { margin-bottom: var(--sp-4); }

.order-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-4);
  flex-wrap: wrap;
}

.order-main {
  display: grid;
  gap: var(--sp-1);
  min-width: 0;
  flex: 1;
}

.order-title {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

.order-address {
  word-break: break-all;
}

.order-extra {
  word-break: break-word;
}

.order-ops {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex: none;
}

/* ---------- 右栏 ---------- */
.steps {
  margin: 0;
  padding-left: 1.2em;
  display: grid;
}

.steps li {
  padding: var(--sp-3) 0;
  border-top: 1px solid var(--line);
}

.steps li:first-child {
  border-top: 0;
  padding-top: 0;
}

.steps strong { display: block; }

/* 概览带在窄栏里排成三列等宽，把每条的内距收窄 */
.rail-strip {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.rail-strip > * {
  padding: var(--sp-4) var(--sp-3);
}

.rail-hint { margin-top: var(--sp-3); }

.rail-copy { margin-bottom: var(--sp-3); }

/* ---------- 提示条：一条细左规，不用整块染色 ---------- */
.global-error,
.global-ok {
  padding: var(--sp-2) 0 var(--sp-2) var(--sp-3);
  border-left: 2px solid currentColor;
}

.global-error { color: var(--danger); }

.global-ok { color: var(--success); }

@media (max-width: 1040px) {
  .recycle-grid { grid-template-columns: minmax(0, 1fr); }
  .recycle-rail { position: static; order: -1; }
}
</style>
