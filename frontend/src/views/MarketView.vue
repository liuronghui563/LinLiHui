<template>
  <AppShell
    title="集市"
    eyebrow="发现"
    :subtitle="subtitle"
  >
    <!-- 通栏英雄瓦片：这是整页唯一「大声说话」的地方，其余界面全部后退 -->
    <template #hero>
      <PageHero
        eyebrow="邻里汇 · 集市"
        title="生活集市"
        lead="写下成色、价格和联系方式。"
        tone="parchment"
        align="left"
      >
        <template #actions>
          <button class="btn btn--primary" type="button" @click="toggleComposer(true)">
            发布闲置
          </button>
          <button class="btn btn--secondary" type="button" @click="scrollToMine()">
            看我发布的
          </button>
        </template>
      </PageHero>
    </template>

    <template #actions>
      <button
        class="btn btn--primary btn--sm"
        type="button"
        @click="toggleComposer"
      >
        {{ composing ? '收起发布框' : '发布闲置' }}
      </button>
      <button class="btn btn--ghost btn--sm" type="button" :disabled="loading" @click="reload">
        {{ loading ? '刷新中…' : '刷新' }}
      </button>
    </template>

    <div class="market-grid">
      <div class="market-main">
        <!-- 发布器：不单独开页面，集市发一条闲置的字段很少，就地展开最快 -->
        <section v-if="composing" ref="composerRef" class="panel composer enter-rise">
          <div class="panel-head panel-head--plain">
            <h3>发布一件闲置</h3>
            <button class="panel-more" type="button" @click="composing = false">收起</button>
          </div>

          <form class="composer-form" novalidate @submit.prevent="onCreate">
            <div class="field">
              <label for="goods-title">标题</label>
              <input
                id="goods-title"
                v-model.trim="form.title"
                class="input"
                type="text"
                maxlength="100"
                placeholder="例如：九成新折叠书桌"
              />
              <p v-if="errors.title" class="field-error">{{ errors.title }}</p>
              <p v-else class="field-hint">写清「是什么 + 成色」，比写「好物」更容易被点开。</p>
            </div>

            <div class="field-row">
              <div class="field">
                <label for="goods-price">售价（元）</label>
                <input
                  id="goods-price"
                  v-model.trim="form.price"
                  class="input nums"
                  type="number"
                  min="0"
                  step="0.01"
                  placeholder="0.00"
                />
                <p v-if="errors.price" class="field-error">{{ errors.price }}</p>
                <p v-else class="field-hint">必填，最多两位小数。</p>
              </div>

              <div class="field">
                <label for="goods-original">原价（元，选填）</label>
                <input
                  id="goods-original"
                  v-model.trim="form.originalPrice"
                  class="input nums"
                  type="number"
                  min="0"
                  step="0.01"
                  placeholder="不填则不显示划线价"
                />
                <p v-if="errors.originalPrice" class="field-error">{{ errors.originalPrice }}</p>
                <p v-else class="field-hint">填了会在价格旁显示划线价，用来体现折让。</p>
              </div>
            </div>

            <div class="field">
              <label for="goods-category">分类（选填）</label>
              <input
                id="goods-category"
                v-model.trim="form.category"
                class="input"
                type="text"
                maxlength="50"
                list="goods-category-options"
                placeholder="例如：家具 / 数码 / 教材"
              />
              <datalist id="goods-category-options">
                <option v-for="c in knownCategories" :key="c" :value="c" />
              </datalist>
              <p v-if="errors.category" class="field-error">{{ errors.category }}</p>
              <p v-else class="field-hint">分类由卖家自己填，集市列表上方的分类筛选就从这里来。</p>
            </div>

            <div class="field">
              <label for="goods-desc">描述（选填）</label>
              <textarea
                id="goods-desc"
                v-model.trim="form.description"
                class="textarea"
                rows="3"
                maxlength="1000"
                placeholder="成色、尺寸、使用时长、有无瑕疵…"
              />
              <div class="field-foot">
                <span v-if="errors.description" class="field-error">{{ errors.description }}</span>
                <span class="field-hint nums">{{ form.description.length }} / 1000</span>
              </div>
            </div>

            <!-- 联系方式：站内没有私信功能，不填买家就没有任何办法联系卖家。
                 输入框与类型分开放，是因为类型决定买家那边怎么用（电话可直接拨打、微信号只能复制）。 -->
            <div class="field-row contact-row">
              <div class="field">
                <label for="goods-contact-type">联系方式类型（选填）</label>
                <select id="goods-contact-type" v-model="form.contactType" class="select">
                  <option v-for="t in contactTypeOptions" :key="t.value" :value="t.value">
                    {{ t.label }}
                  </option>
                </select>
                <p class="field-hint">决定买家怎么联系你：电话可直接拨打，微信号只能复制。</p>
              </div>

              <div class="field">
                <label for="goods-contact">联系方式（选填）</label>
                <input
                  id="goods-contact"
                  v-model.trim="form.contact"
                  class="input"
                  type="text"
                  maxlength="100"
                  :placeholder="contactPlaceholder"
                />
                <p v-if="errors.contact" class="field-error">{{ errors.contact }}</p>
                <p v-else class="field-hint">仅详情页可见，不会出现在列表和搜索结果里。</p>
              </div>
            </div>

            <div class="field">
              <span class="field-label">实物照片（选填，最多 9 张）</span>
              <ImageUploader v-model="form.images" purpose="post" />
              <p class="field-hint">第一张会作为列表封面，建议拍实物全貌而不是网图。</p>
            </div>

            <p v-if="formError" class="global-error">{{ formError }}</p>

            <div class="composer-foot">
              <span class="field-hint">发布后可在下方「我发布的」里改状态或删除。</span>
              <button class="btn btn--primary btn--sm" type="submit" :disabled="posting">
                {{ posting ? '发布中…' : '发布闲置' }}
              </button>
            </div>
          </form>
        </section>

        <p v-if="successMsg" class="global-ok">{{ successMsg }}</p>
        <p v-if="actionError" class="global-error">{{ actionError }}</p>

        <!-- 筛选条：关键词走回车提交，不做输入即查，避免每敲一个字打一次接口 -->
        <section class="panel filter-panel">
          <form class="search-row" @submit.prevent="applySearch">
            <label class="sr-only" for="market-keyword">按标题搜索闲置</label>
            <input
              id="market-keyword"
              v-model.trim="keywordDraft"
              class="input"
              type="search"
              placeholder="搜索标题关键词，回车确认"
            />
            <button class="btn btn--secondary btn--sm" type="submit">搜索</button>
            <button
              v-if="keyword"
              class="btn btn--quiet btn--sm"
              type="button"
              @click="clearKeyword"
            >
              清除
            </button>
          </form>

          <div class="filter-row">
            <span class="filter-label field-label">状态</span>
            <div class="segmented" role="group" aria-label="按状态筛选">
              <button
                v-for="s in statusOptions"
                :key="s.value"
                type="button"
                :aria-pressed="status === s.value"
                @click="changeStatus(s.value)"
              >
                {{ s.label }}
              </button>
            </div>
            <p class="filter-note fine-print">{{ statusNote }}</p>
          </div>

          <div v-if="categories.length" class="filter-row">
            <span class="filter-label field-label">分类</span>
            <div class="segmented chips" role="group" aria-label="按分类筛选">
              <button
                type="button"
                class="chip"
                :class="{ 'is-on': !category }"
                :aria-pressed="!category"
                @click="changeCategory('')"
              >
                全部
              </button>
              <button
                v-for="c in categories"
                :key="c"
                type="button"
                class="chip"
                :class="{ 'is-on': category === c }"
                :aria-pressed="category === c"
                @click="changeCategory(c)"
              >
                {{ c }}
              </button>
            </div>
          </div>
        </section>

        <SkeletonList v-if="loading" :count="6" variant="card" />

        <StateBlock v-else-if="error" variant="error" :desc="error">
          <button class="btn btn--ghost btn--sm" type="button" @click="reload">重新加载</button>
        </StateBlock>

        <StateBlock
          v-else-if="!goods.length"
          :title="emptyTitle"
          :desc="emptyDesc"
        >
          <button v-if="hasFilter" class="btn btn--ghost btn--sm" type="button" @click="resetFilters">
            查看全部在售
          </button>
          <button v-else class="btn btn--primary btn--sm" type="button" @click="toggleComposer(true)">
            发布第一条闲置
          </button>
        </StateBlock>

        <div v-else class="goods-grid enter-stagger">
          <article v-for="item in goods" :key="item.id" class="goods-card">
            <div class="goods-cover">
              <img
                v-if="item.images?.length"
                :src="item.images[0]"
                alt=""
                loading="lazy"
                @error="onCoverError"
              />
              <span v-else class="goods-cover-fallback" aria-hidden="true" v-html="icons.bag" />
              <span class="tag goods-status" :class="statusTone(item.status)">
                {{ item.statusLabel || item.status }}
              </span>
            </div>

            <div class="goods-body">
              <h3 class="goods-title clamp-2">{{ item.title }}</h3>

              <div class="price-row">
                <strong class="price stat-value nums">{{ money(item.price) }}</strong>
                <s v-if="hasOriginalPrice(item)" class="price-origin fine-print nums">
                  {{ money(item.originalPrice) }}
                </s>
              </div>

              <p v-if="item.description" class="goods-desc caption" :class="{ 'clamp-2': expandedId !== item.id }">
                {{ item.description }}
              </p>
              <button
                v-if="needsExpand(item)"
                class="link-btn link caption"
                type="button"
                @click="expandedId = expandedId === item.id ? null : item.id"
              >
                {{ expandedId === item.id ? '收起描述' : '展开描述' }}
              </button>

              <div class="meta-row">
                <span v-if="item.category" class="tag tag--outline">{{ item.category }}</span>
                <span :class="{ 'meta-dot': item.category }">{{ item.viewCount || 0 }} 次浏览</span>
                <span class="meta-dot">{{ relativeTime(item.createdAt) }}</span>
              </div>

              <footer class="goods-foot">
                <PostAuthor
                  :user-id="item.sellerId"
                  :name="item.sellerName"
                  :avatar="item.sellerAvatar"
                  size="xs"
                />
                <div class="goods-contact">
                  <!-- 联系方式只在详情接口返回，点一下才去取：
                       列表接口不带它，是为了不让全站卖家的手机号被一页页抓走 -->
                  <button
                    v-if="!contactOf(item)"
                    class="link-btn link caption"
                    type="button"
                    :disabled="contactLoadingId === item.id"
                    @click="showContact(item)"
                  >
                    {{ contactLoadingId === item.id ? '读取中…' : '查看联系方式' }}
                  </button>
                  <span v-else-if="contactOf(item).contact" class="contact-value caption">
                    {{ contactOf(item).contactTypeLabel }}：{{ contactOf(item).contact }}
                  </span>
                  <span v-else class="contact-empty fine-print">卖家未留联系方式</span>
                  <router-link class="link-btn link caption" :to="`/users/${item.sellerId}`">去主页</router-link>
                </div>
              </footer>
            </div>
          </article>
        </div>

        <div v-if="!loading && !error && hasMore" class="more-row">
          <button class="btn btn--ghost btn--sm" type="button" :disabled="loadingMore" @click="loadMore">
            {{ loadingMore ? '加载中…' : `加载更多（还有 ${totalElements - goods.length} 条）` }}
          </button>
        </div>

        <!-- 我发布的：独立分区，含已售出与已下架，便于卖家自己收尾 -->
        <section id="mine-section" ref="mineRef" class="panel mine-panel">
          <div class="panel-head">
            <h3>我发布的</h3>
            <router-link class="panel-more" to="/me/profile/edit">编辑资料 →</router-link>
          </div>

          <SkeletonList v-if="mineLoading" :count="3" />

          <StateBlock v-else-if="mineError" variant="error" inline :desc="mineError">
            <button class="btn btn--ghost btn--sm" type="button" @click="loadMine">重新加载</button>
          </StateBlock>

          <StateBlock
            v-else-if="!mineList.length"
            inline
            title="你还没有发布过闲置"
            desc="不用的东西转给邻居，比放在角落积灰有用。"
          >
            <button class="btn btn--primary btn--sm" type="button" @click="toggleComposer(true)">
              发布闲置
            </button>
          </StateBlock>

          <ul v-else class="list-plain">
            <li v-for="item in mineList" :key="item.id" class="mine-item">
              <div class="mine-main">
                <div class="mine-title">
                  <strong class="brand-title">{{ item.title }}</strong>
                  <span class="tag" :class="statusTone(item.status)">
                    {{ item.statusLabel || item.status }}
                  </span>
                </div>
                <div class="meta-row">
                  <span class="nums">{{ money(item.price) }}</span>
                  <span class="meta-dot">{{ item.viewCount || 0 }} 次浏览</span>
                  <span class="meta-dot">{{ relativeTime(item.createdAt) }}</span>
                </div>
              </div>
              <div class="mine-ops">
                <button
                  v-if="item.status !== 'SOLD'"
                  class="btn btn--ghost btn--sm"
                  type="button"
                  :disabled="busyId === item.id"
                  @click="onChangeStatus(item, 'SOLD')"
                >
                  标记已售出
                </button>
                <button
                  v-else
                  class="btn btn--ghost btn--sm"
                  type="button"
                  :disabled="busyId === item.id"
                  @click="onChangeStatus(item, 'ON_SALE')"
                >
                  重新上架
                </button>
                <button
                  class="btn btn--danger btn--sm"
                  type="button"
                  :disabled="busyId === item.id"
                  @click="onDelete(item)"
                >
                  删除
                </button>
              </div>
            </li>
          </ul>
        </section>
      </div>

      <aside class="market-rail">
        <section class="panel">
          <div class="panel-head">
            <h3>发布入口</h3>
          </div>
          <p class="rail-copy lead-body">
            闲置转让只需要标题、价格和一张实物图，就地填完即可上架。
          </p>
          <button class="btn btn--primary btn--block btn--sm" type="button" @click="toggleComposer(true)">
            {{ composing ? '继续编辑发布框' : '发布闲置' }}
          </button>
          <p class="rail-hint fine-print">上架后默认状态是「在售」，卖出后记得标记。</p>
        </section>

        <section class="panel">
          <div class="panel-head">
            <h3>我的闲置</h3>
            <button class="panel-more" type="button" @click="scrollToMine">查看</button>
          </div>
          <div class="rail-stats">
            <div class="stat">
              <span class="stat-label">在售</span>
              <strong class="stat-value">{{ mineCounts.ON_SALE }}</strong>
            </div>
            <div class="stat">
              <span class="stat-label">已预定</span>
              <strong class="stat-value">{{ mineCounts.RESERVED }}</strong>
            </div>
            <div class="stat">
              <span class="stat-label">已售出</span>
              <strong class="stat-value">{{ mineCounts.SOLD }}</strong>
            </div>
          </div>
        </section>

        <!-- 近黑瓦片卡：整页唯一「压场面」的区块，用表面色差收束右栏 -->
        <section class="panel panel--dark">
          <div class="panel-head">
            <h3>当面交易更稳妥</h3>
          </div>
          <ul class="tips lead-body">
            <li>约在小区门口、快递驿站等有人来往的地方交接。</li>
            <li>大件建议先验货再付款，不要提前转账。</li>
            <li>电子产品的序列号、配件清单当面核对一遍。</li>
            <li>交易完成后点击「标记已售出」，避免邻居继续来问。</li>
          </ul>
        </section>
      </aside>
    </div>
  </AppShell>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import AppShell from '../layouts/AppShell.vue'
import PageHero from '../components/PageHero.vue'
import ImageUploader from '../components/ImageUploader.vue'
import PostAuthor from '../components/PostAuthor.vue'
import StateBlock from '../components/StateBlock.vue'
import SkeletonList from '../components/SkeletonList.vue'
import {
  changeGoodsStatus,
  createGoods,
  deleteGoods,
  fetchGoodsDetail,
  fetchGoodsList,
  fetchMyGoods
} from '../api/market'
import { relativeTime } from '../utils/format'

/**
 * 集市（二手闲置）。
 *
 * 布局沿用「发现」的 主内容 + 右侧信息栏：
 * 左边是筛选与商品网格（浏览的主链路），右边是发布入口、我的闲置与交易提示，
 * 窄屏时右栏折到主内容上方，发布按钮与提示仍然第一眼可见。
 *
 * 状态与状态中文名都由后端下发（status / statusLabel）：
 * 前端只负责把状态映射到设计系统已有的标签色，不再维护枚举文案。
 *
 * 联系方式（contact / contactType / contactTypeLabel）**只在详情接口下发**：
 * 列表接口刻意不带，避免一页几十个卖家的联系方式被脚本批量抓走。
 * 因此卡片上的「查看联系方式」是一次按需的详情请求，取回来后才在卖家信息旁展示。
 */

const PAGE_SIZE = 24
/** 与后端 GoodsStatus 一致；筛选时 status 不传等于在售，所以这里显式传值 */
const statusOptions = [
  { label: '在售', value: 'ON_SALE' },
  { label: '已预定', value: 'RESERVED' },
  { label: '已售出', value: 'SOLD' },
  { label: '已下架', value: 'OFF' }
]

/**
 * 联系方式类型。
 *
 * 这里保留一份取值表是因为它要渲染成下拉选项（后端只在详情响应里给出单个类型的中文名，
 * 发布表单里还没有任何实例可以参照）；中文名与后端 GoodsContactType 保持一致。
 */
const contactTypeOptions = [
  { label: '微信', value: 'WECHAT' },
  { label: '手机', value: 'PHONE' },
  { label: 'QQ', value: 'QQ' },
  { label: '其他', value: 'OTHER' }
]

const stroke = 'fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"'
const icons = {
  bag: `<svg viewBox="0 0 24 24" ${stroke}><path d="M6 3 3.6 7.4V19a1.6 1.6 0 0 0 1.6 1.6h13.6A1.6 1.6 0 0 0 20.4 19V7.4L18 3Z"/><path d="M3.6 7.4h16.8"/><path d="M15.4 11.4a3.4 3.4 0 0 1-6.8 0"/></svg>`
}

const goods = ref([])
const categories = ref([])
const mineList = ref([])
const status = ref('ON_SALE')
const category = ref('')
const keyword = ref('')
const keywordDraft = ref('')
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const loading = ref(true)
const loadingMore = ref(false)
const error = ref('')
const mineLoading = ref(true)
const mineError = ref('')
const busyId = ref(null)
const expandedId = ref(null)
const actionError = ref('')
const successMsg = ref('')

const composing = ref(false)
const composerRef = ref(null)
const mineRef = ref(null)
const posting = ref(false)
const formError = ref('')
const form = reactive({
  title: '',
  price: '',
  originalPrice: '',
  category: '',
  description: '',
  images: [],
  contact: '',
  contactType: 'WECHAT'
})
const errors = reactive({})

/**
 * 已取回的联系方式，按商品 id 缓存。
 *
 * 缓存的原因：详情接口每次调用都会给卖家加一次浏览量，
 * 只是为了再看一眼号码而反复请求，会把「浏览数」变成点击数的噪音。
 */
const contacts = reactive({})
const contactLoadingId = ref(null)

const contactPlaceholder = computed(() => {
  if (form.contactType === 'WECHAT') return '你的微信号'
  if (form.contactType === 'PHONE') return '11 位手机号'
  if (form.contactType === 'QQ') return '你的 QQ 号'
  return '留一个买家能找到你的方式'
})

const subtitle = computed(() => {
  if (loading.value) return '正在加载在售闲置'
  if (error.value) return '集市数据加载失败'
  const label = statusOptions.find((s) => s.value === status.value)?.label || '在售'
  return `邻里闲置转让 · ${label} · 共 ${totalElements.value} 条`
})

const statusNote = computed(() => {
  if (status.value === 'ON_SALE') return '默认只看在售，这是集市首页应有的视角。'
  if (status.value === 'SOLD') return '已售出的商品不再接受询问，但可以重新上架。'
  if (status.value === 'RESERVED') return '已预定表示有邻居正在谈，通常很快会成交。'
  return '已下架的商品只有卖家自己还能看到并重新上架。'
})

const hasFilter = computed(() => Boolean(keyword.value || category.value) || status.value !== 'ON_SALE')

const emptyTitle = computed(() => {
  if (keyword.value) return `没有找到与「${keyword.value}」相关的闲置`
  if (category.value) return `「${category.value}」分类下还没有闲置`
  if (status.value !== 'ON_SALE') {
    return `没有${statusOptions.find((s) => s.value === status.value)?.label || ''}的商品`
  }
  return '集市还没有上架的商品'
})

const emptyDesc = computed(() => {
  if (keyword.value) return '换个关键词试试，或者发布一条求购需求让邻居看到。'
  if (category.value) return '换个分类看看，或者把你手里的闲置发到这个分类下。'
  if (status.value !== 'ON_SALE') return '切回「在售」看看邻居们正在出的东西。'
  return '家里用不上的东西，转给需要的邻居，比放着积灰有用。'
})

const hasMore = computed(() => page.value + 1 < totalPages.value)
/** 发布框里的分类联想：用列表里真实出现过的分类，而不是前端硬编一份 */
const knownCategories = computed(() => categories.value)
const mineCounts = computed(() => {
  const counts = { ON_SALE: 0, RESERVED: 0, SOLD: 0, OFF: 0 }
  mineList.value.forEach((item) => {
    if (counts[item.status] !== undefined) counts[item.status] += 1
  })
  return counts
})

function statusTone(value) {
  if (value === 'ON_SALE') return 'tag--open'
  if (value === 'RESERVED') return 'tag--gold'
  if (value === 'SOLD') return 'tag--done'
  return 'tag--cancelled'
}

function money(value) {
  if (value === null || value === undefined || value === '') return '面议'
  const n = Number(value)
  return Number.isFinite(n) ? `¥${n.toFixed(2)}` : '面议'
}

function hasOriginalPrice(item) {
  const original = Number(item.originalPrice)
  const price = Number(item.price)
  return Number.isFinite(original) && original > 0 && original > price
}

function needsExpand(item) {
  return String(item.description || '').length > 40
}

function onCoverError(event) {
  // 图片失效时退化为占位底色，避免列表里出现破图图标
  event.target.style.display = 'none'
  event.target.parentElement?.classList.add('is-broken')
}

/** 已取回的联系方式；没取过返回 undefined，模板据此决定显示按钮还是号码。 */
function contactOf(item) {
  return contacts[item.id]
}

/**
 * 按需取回某件商品的联系方式。
 *
 * 走详情接口（GET /market/goods/{id}），因为列表接口刻意不下发联系方式；
 * 取回后缓存在 contacts 里，避免重复请求把卖家的浏览量刷成点击数。
 */
async function showContact(item) {
  if (contactLoadingId.value) return
  contactLoadingId.value = item.id
  actionError.value = ''
  try {
    const res = await fetchGoodsDetail(item.id)
    const data = res.data || {}
    if (!data.contact) {
      // 卖家没留联系方式：这不是错误，但仍要记下「已经查过」，
      // 否则按钮会一直停在那里，点一次发一次请求
      contacts[item.id] = { contact: null, contactType: null, contactTypeLabel: '' }
      return
    }
    contacts[item.id] = {
      contact: data.contact,
      contactType: data.contactType,
      contactTypeLabel: data.contactTypeLabel || '联系方式'
    }
  } catch (e) {
    actionError.value = e.message
  } finally {
    contactLoadingId.value = null
  }
}

async function loadList({ append = false } = {}) {
  if (append) loadingMore.value = true
  else loading.value = true
  error.value = ''
  try {
    const params = { page: page.value, size: PAGE_SIZE, status: status.value }
    if (category.value) params.category = category.value
    if (keyword.value) params.keyword = keyword.value
    const res = await fetchGoodsList(params)
    const data = res.data || {}
    const content = data.content || []
    goods.value = append ? [...goods.value, ...content] : content
    totalPages.value = data.totalPages ?? 1
    totalElements.value = data.totalElements ?? content.length
  } catch (e) {
    error.value = e.message
    if (!append) goods.value = []
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

/** 无筛选时把当前页真实出现过的分类收集成筛选项（后端没有分类字典接口） */
function collectCategories() {
  if (category.value || keyword.value || status.value !== 'ON_SALE') return
  const found = []
  goods.value.forEach((item) => {
    if (item.category && !found.includes(item.category)) found.push(item.category)
  })
  categories.value = found.slice(0, 12)
}

async function loadMine() {
  mineLoading.value = true
  mineError.value = ''
  try {
    const res = await fetchMyGoods({ page: 0, size: 50 })
    mineList.value = res.data?.content || []
  } catch (e) {
    mineError.value = e.message
    mineList.value = []
  } finally {
    mineLoading.value = false
  }
}

async function reload() {
  page.value = 0
  await loadList()
  collectCategories()
}

function applySearch() {
  keyword.value = keywordDraft.value
  page.value = 0
  loadList().then(collectCategories)
}

function clearKeyword() {
  keywordDraft.value = ''
  keyword.value = ''
  page.value = 0
  loadList().then(collectCategories)
}

function changeStatus(value) {
  status.value = value
  page.value = 0
  loadList().then(collectCategories)
}

function changeCategory(value) {
  category.value = value
  page.value = 0
  loadList().then(collectCategories)
}

function resetFilters() {
  keyword.value = ''
  keywordDraft.value = ''
  category.value = ''
  status.value = 'ON_SALE'
  page.value = 0
  loadList().then(collectCategories)
}

function loadMore() {
  page.value += 1
  loadList({ append: true })
}

function toggleComposer(open) {
  composing.value = open === true ? true : !composing.value
  if (composing.value) {
    nextTick(() => composerRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
  }
}

function scrollToMine() {
  mineRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function clearErrors() {
  Object.keys(errors).forEach((k) => { delete errors[k] })
}

/** 前端只做「提交前能被立刻发现」的校验，规则与后端 DTO 注解保持一致 */
function validate() {
  clearErrors()
  const title = form.title.trim()
  if (!title) errors.title = '标题不能为空'
  else if (title.length > 100) errors.title = '标题最多 100 字'

  const price = Number(form.price)
  if (form.price === '') errors.price = '价格不能为空'
  else if (!Number.isFinite(price)) errors.price = '价格必须是数字'
  else if (price < 0) errors.price = '价格不能为负'
  else if (!/^\d{1,8}(\.\d{1,2})?$/.test(String(form.price))) errors.price = '价格最多两位小数，且整数部分不超过 8 位'

  if (form.originalPrice !== '') {
    const original = Number(form.originalPrice)
    if (!Number.isFinite(original)) errors.originalPrice = '原价必须是数字'
    else if (original < 0) errors.originalPrice = '原价不能为负'
    else if (!/^\d{1,8}(\.\d{1,2})?$/.test(String(form.originalPrice))) errors.originalPrice = '原价最多两位小数'
  }

  if (form.category.length > 50) errors.category = '分类最多 50 字'
  if (form.description.length > 1000) errors.description = '描述最多 1000 字'
  // 联系方式与后端 @Size(max = 100) 对齐；类型无需校验（下拉框取值固定）
  if (form.contact.length > 100) errors.contact = '联系方式最多 100 字'

  return Object.keys(errors).length === 0
}

async function onCreate() {
  formError.value = ''
  successMsg.value = ''
  if (!validate()) {
    formError.value = '请先修正表单中标红的问题。'
    return
  }
  posting.value = true
  try {
    await createGoods({
      title: form.title.trim(),
      price: Number(form.price),
      originalPrice: form.originalPrice === '' ? null : Number(form.originalPrice),
      category: form.category.trim() || null,
      description: form.description.trim() || null,
      images: form.images,
      // 没填联系方式时类型一并置空：后端也会这么清，前端先对齐语义
      contact: form.contact.trim() || null,
      contactType: form.contact.trim() ? form.contactType : null
    })
    form.title = ''
    form.price = ''
    form.originalPrice = ''
    form.category = ''
    form.description = ''
    form.images = []
    form.contact = ''
    form.contactType = 'WECHAT'
    composing.value = false
    successMsg.value = '发布成功，已上架到集市。'
    // 新商品按发布时间排在第一位，重新拉第一页就能看到
    status.value = 'ON_SALE'
    page.value = 0
    await Promise.all([loadList().then(collectCategories), loadMine()])
  } catch (e) {
    formError.value = e.message
  } finally {
    posting.value = false
  }
}

async function onChangeStatus(item, next) {
  busyId.value = item.id
  actionError.value = ''
  try {
    const res = await changeGoodsStatus(item.id, next)
    Object.assign(item, res.data || {})
    // 改完状态可能不再符合当前筛选条件，重新拉一次列表才不会出现「明明改完了还在里面」
    if ((status.value === 'ON_SALE' && next !== 'ON_SALE') || status.value === next) {
      await loadList()
    }
    await loadMine()
  } catch (e) {
    actionError.value = e.message
  } finally {
    busyId.value = null
  }
}

async function onDelete(item) {
  // 删除不可恢复，必须二次确认
  if (!window.confirm(`删除后无法恢复，确认删除「${item.title}」？`)) return
  busyId.value = item.id
  actionError.value = ''
  try {
    await deleteGoods(item.id)
    goods.value = goods.value.filter((g) => g.id !== item.id)
    mineList.value = mineList.value.filter((g) => g.id !== item.id)
    // 缓存里那条联系方式已经没有对应商品了，一并清掉
    delete contacts[item.id]
    totalElements.value = Math.max(0, totalElements.value - 1)
  } catch (e) {
    actionError.value = e.message
  } finally {
    busyId.value = null
  }
}

onMounted(async () => {
  await Promise.all([loadList().then(collectCategories), loadMine()])
})
</script>

<style scoped>
/* 布局差异：主列 1.6fr + 右侧信息栏 1fr。除列数、对齐与粘性偏移外不写别的。 */
.market-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(280px, 1fr);
  gap: var(--sp-6);
  align-items: start;
}

.market-main {
  display: grid;
  gap: var(--sp-4);
  min-width: 0;
}

/* 粘在磨砂二级导航之下，而不是贴着视口顶端，否则会被两条 sticky 栏压住 */
.market-rail {
  display: grid;
  gap: var(--sp-4);
  position: sticky;
  top: calc(var(--nav-stack) + var(--sp-5));
}

/* ---------- 发布器 ---------- */
.composer-form {
  display: grid;
  gap: var(--sp-4);
}

.field-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--sp-4);
}

.field-foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--sp-3);
}

.composer-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  flex-wrap: wrap;
  padding-top: var(--sp-2);
  border-top: 1px solid var(--line);
}

/* ---------- 筛选 / 工具栏：外形全部交给全局 .segmented / .input / .btn ---------- */
.filter-panel {
  display: grid;
  gap: var(--sp-4);
}

.search-row {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

.search-row .input {
  flex: 1;
  min-width: 220px;
}

.filter-row {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex-wrap: wrap;
}

/* .chips / .chip / .is-on 仅作为钩子保留（模板绑定与测试可能引用），外观完全走 .segmented button */

/* ---------- 商品网格：米白底上的白色工具卡，无阴影、无渐变 ---------- */
.goods-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: var(--sp-4);
  align-items: start;
}

.goods-card {
  display: grid;
  grid-template-rows: auto 1fr;
  background: var(--canvas);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  overflow: hidden;
}

.goods-cover {
  position: relative;
  aspect-ratio: 4 / 3;
  background: var(--parchment);
  display: grid;
  place-items: center;
  overflow: hidden;
  border-bottom: 1px solid var(--line);
}

.goods-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 图片失效时隐藏 img，留出占位底色，避免列表里出现破图图标 */
.goods-cover.is-broken { background: var(--parchment); }

.goods-cover-fallback {
  width: 40px;
  height: 40px;
  color: var(--muted-2);
  opacity: 0.7;
}

.goods-cover-fallback :deep(svg) { width: 100%; height: 100%; }

.goods-status {
  position: absolute;
  top: var(--sp-3);
  left: var(--sp-3);
  background: var(--canvas);
}

.goods-body {
  display: grid;
  gap: var(--sp-3);
  align-content: start;
  padding: var(--sp-5);
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

.goods-desc {
  white-space: pre-wrap;
  word-break: break-word;
}

/* 链接型按钮：文字样式交给全局 .link / .caption，这里只留排布 */
.link-btn {
  justify-self: start;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.goods-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
  margin-top: var(--sp-1);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--line);
}

/* 卖家信息旁的联系方式：取回号码后这里会整行撑开，
   故用 flex-wrap 让长微信号换行而不是把卡片挤变形 */
.goods-contact {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--sp-2);
  flex-wrap: wrap;
  min-width: 0;
}

/* 联系方式是卡片里的真实内容，比 .caption 的灰再重一点（用父级限定确保胜出） */
.goods-contact .contact-value {
  color: var(--ink-2);
  word-break: break-all;
}

.contact-row {
  align-items: start;
}

.more-row {
  display: flex;
  justify-content: center;
}

/* ---------- 我发布的 ---------- */
.mine-panel { scroll-margin-top: calc(var(--nav-stack) + var(--sp-5)); }

.mine-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  flex-wrap: wrap;
}

.mine-main {
  display: grid;
  gap: var(--sp-1);
  min-width: 0;
  flex: 1;
}

.mine-title {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

.mine-ops {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

/* ---------- 右栏 ---------- */
.rail-copy { margin-bottom: var(--sp-4); }

.rail-hint { margin-top: var(--sp-3); }

.rail-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--sp-3);
}

/* 清单走 .lead-body（17px 正文）；放进 .panel--dark 时颜色由全局令牌自动切成 --on-dark-muted */
.tips {
  margin: 0;
  padding-left: 1.1em;
  display: grid;
  gap: var(--sp-3);
}

/* ---------- 提示条：一行状态，不做整卡染色 ---------- */
.global-error {
  padding: var(--sp-3) var(--sp-4);
  border-radius: var(--r-md);
  background: var(--danger-soft);
  color: var(--danger);
}

.global-ok {
  padding: var(--sp-3) var(--sp-4);
  border-radius: var(--r-md);
  background: var(--success-soft);
  color: var(--success);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}

/* 1040 以下折成一列：右栏上移到主内容之前，发布入口仍然第一眼可见 */
@media (max-width: 1040px) {
  .market-grid { grid-template-columns: minmax(0, 1fr); }
  .market-rail { position: static; order: -1; }
}
</style>
