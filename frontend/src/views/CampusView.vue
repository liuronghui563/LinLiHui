<template>
  <AppShell
    title="论坛"
    eyebrow="校园"
    :subtitle="subtitle"
  >
    <!-- 通栏英雄瓦片：米白起手 → 白色内容瓦片 → 近黑瓦片收尾 -->
    <template #hero>
      <PageHero
        eyebrow="校园 · 同校的人"
        title="校园专区"
        lead="唠嗑、找搭子、失物招领"
        tone="parchment"
        align="left"
      />
    </template>

    <template #actions>
      <router-link class="btn btn--primary btn--sm" :to="lostCreateTo">发布失物招领</router-link>
      <button class="btn btn--ghost btn--sm" type="button" :disabled="loading" @click="refresh">
        {{ loading ? '刷新中…' : '刷新' }}
      </button>
    </template>

    <div class="campus-body">
      <!-- 发帖区：**不套外层白框**。
           标题、分区切换直接落在页面米白底上，书写台自己就是唯一的表面——
           以前这里还有一层白色瓦片，等于给发帖区加了一圈没必要的白边。
           分区切换是这一页**唯一**的「按种类筛选」入口，就放在这里；
           下面的内容区不再重复排一遍同样的筛选。 -->
      <section class="campus-write">
        <div class="tile-inner">
          <header class="band-head">
            <p class="eyebrow">校园互动</p>
            <h2>发一条，或者先看看别人在聊什么</h2>
          </header>

          <!-- 内容分区：种类全部来自后端（module 可选值 CAMPUS），
               前端不写死中文标签；「失物招领」因为数据结构不同单独成区 -->
          <div class="section-bar">
            <div class="segmented" role="tablist" aria-label="校园内容分区">
              <button
                type="button"
                role="tab"
                :aria-selected="section === 'posts' && !activeKind"
                :aria-pressed="section === 'posts' && !activeKind"
                @click="switchSection('')"
              >
                全部帖子
              </button>
              <button
                v-for="k in postKinds"
                :key="k.code"
                type="button"
                role="tab"
                :aria-selected="section === 'posts' && activeKind === k.code"
                :aria-pressed="section === 'posts' && activeKind === k.code"
                @click="switchSection(k.code)"
              >
                {{ k.label }}
              </button>
              <button
                type="button"
                role="tab"
                :aria-selected="section === 'lost'"
                :aria-pressed="section === 'lost'"
                @click="switchSection(lostKindCode)"
              >
                {{ lostKind?.label || '失物招领' }}
              </button>
            </div>
            <p class="section-note caption">{{ sectionNote }}</p>
          </div>

          <p v-if="!kinds.length && !kindsLoading" class="section-warn caption">
            帖子种类没能加载出来，分区筛选与发布器暂不可用，点右上角「刷新」重试。
          </p>

          <!-- 发布器只在帖子分区出现：失物招领的字段（地点/时间/悬赏）在邻里互助的发布页 -->
          <section v-if="section === 'posts'" ref="composerRef" class="composer">
            <div class="composer-head">
              <UserAvatar :user="auth.user" size="sm" />
              <div class="composer-title">
                <strong class="brand-title">{{ composerTitle }}</strong>
                <small class="caption">{{ composerHint }}</small>
              </div>
            </div>

            <div class="kind-chips" role="group" aria-label="选择帖子种类">
              <button
                v-for="k in postKinds"
                :key="k.code"
                type="button"
                class="btn btn--ghost btn--sm chip"
                :class="{ 'is-on': draftKind === k.code }"
                :title="k.hint"
                @click="draftKind = k.code"
              >
                {{ k.label }}
              </button>
            </div>

            <form @submit.prevent="onCreatePost">
              <textarea
                v-model.trim="draft"
                class="textarea"
                rows="3"
                maxlength="1000"
                :placeholder="draftPlaceholder"
              />
              <ImageUploader v-model="draftImages" purpose="post" />
              <div class="composer-foot">
                <span class="counter nums fine-print">{{ draft.length }} / 1000</span>
                <button type="submit" class="btn btn--primary btn--sm" :disabled="posting || !draft">
                  {{ posting ? '发布中…' : `发布${currentDraftKind?.label || '帖子'}` }}
                </button>
              </div>
            </form>
          </section>

          <!-- 失物招领分区：这里不摆帖子发布器（字段完全不同），
               给一句说明 + 唯一入口，避免上半页空出一大块。 -->
          <section v-else class="composer composer--lost">
            <div class="composer-head">
              <span class="lost-mark" aria-hidden="true">拾</span>
              <div class="composer-title">
                <strong class="brand-title">失物招领单独发布</strong>
                <small class="caption">要填丢失或拾获的地点与时间，表单在邻里互助的发布页里。</small>
              </div>
            </div>
            <div class="composer-foot">
              <span class="counter fine-print">认领、交接、完成都能在详情里跟进</span>
              <router-link class="btn btn--primary btn--sm" :to="lostCreateTo">发布失物招领</router-link>
            </div>
          </section>
        </div>
      </section>

      <!-- 瓦片二（米白）：内容流 + 右栏。这里不再排筛选条，直接给内容。 -->
      <section class="tile tile--parchment band">
        <div class="tile-inner campus-grid">
          <div class="campus-main">
            <p v-if="actionError" class="global-error caption">{{ actionError }}</p>
            <p v-if="locateNote" class="locate-note caption">{{ locateNote }}</p>

            <!-- 分区一：帖子（除失物招领外的全部校园种类） -->
            <template v-if="section === 'posts'">
              <PostFeed
                ref="feedRef"
                :posts="posts"
                :loading="postsLoading"
                :error="postsError"
                show-category
                category-key="kindLabel"
                :empty-title="postEmptyTitle"
                :empty-desc="postEmptyDesc"
                @changed="onFeedChanged"
              />
            </template>

            <!-- 分区二：失物招领（数据在 u_r_aid_request，与帖子不是一张表） -->
            <template v-else>
              <div class="filter-row">
                <span class="filter-label caption">按状态看</span>
                <div class="segmented" role="group" aria-label="按状态筛选失物招领">
                  <button
                    v-for="f in statusFilters"
                    :key="f.value"
                    type="button"
                    :aria-pressed="aidStatus === f.value"
                    @click="changeAidStatus(f.value)"
                  >
                    {{ f.label }}
                  </button>
                </div>
                <p class="filter-note caption">认领、交接、完成都能在详情里跟进。</p>
              </div>

              <SkeletonList v-if="aidLoading" :count="3" variant="card" />

              <StateBlock v-else-if="aidError" variant="error" :desc="aidError">
                <button class="btn btn--ghost btn--sm" type="button" @click="loadAid">重新加载</button>
              </StateBlock>

              <StateBlock
                v-else-if="!aidItems.length"
                :title="aidEmptyTitle"
                :desc="aidEmptyDesc"
              >
                <router-link class="btn btn--primary btn--sm" :to="lostCreateTo">发布失物招领</router-link>
                <button
                  v-if="aidStatus"
                  class="btn btn--ghost btn--sm"
                  type="button"
                  @click="changeAidStatus('')"
                >
                  查看全部状态
                </button>
              </StateBlock>

              <div v-else class="aid-grid enter-stagger">
                <AidCard
                  v-for="item in aidItems"
                  :key="item.id"
                  :item="item"
                  :show-rating="false"
                />
              </div>
            </template>
          </div>

          <!-- 右栏不做白卡堆叠：条目标签 + 发丝线行，选中靠表面色差 -->
          <aside class="campus-rail">
            <section class="rail-block">
              <p class="eyebrow">发布入口</p>
              <button class="btn btn--primary btn--block btn--sm" type="button" @click="focusComposer">
                写一条校园帖
              </button>
              <router-link class="btn btn--ghost btn--block btn--sm rail-btn" :to="lostCreateTo">
                发布失物招领
              </router-link>
              <p class="rail-hint panel-note">
                失物招领要填丢失/拾获地点与时间，表单在邻里互助的发布页里。
              </p>
            </section>

            <section class="rail-block">
              <p class="eyebrow">校园分区速览</p>
              <ul class="zone-list">
                <li v-for="k in kinds" :key="k.code">
                  <button
                    class="zone-row"
                    type="button"
                    :class="{ 'is-on': isZoneActive(k.code) }"
                    @click="switchSection(k.code)"
                  >
                    <span class="zone-label brand-title">{{ k.label }}</span>
                    <span class="zone-hint caption">{{ k.hint }}</span>
                  </button>
                </li>
              </ul>
              <p class="rail-hint panel-note">分区与说明都由后端下发，前端不另外维护一份种类表。</p>
            </section>
          </aside>
        </div>
      </section>
    </div>

    <!-- 收口瓦片：真通栏的近黑叙事（在 .page 之外，所以贴屏幕边缘） -->
    <template #band>
      <section class="tile tile--dark">
        <div class="tile-inner notes-grid">
          <div class="notes-head">
            <h2>校园须知</h2>
            <p class="tile-lead">分区与种类都由后端下发，这里只说清怎么发、怎么被同校的人看到。</p>
          </div>
          <ul class="tips on-dark-muted">
            <li>帖子分区按种类聚合，发帖时选好种类，邻居才能在对应分区看到。</li>
            <li>失物招领单独成区：它走邻里互助的数据结构，需要地点与时间。</li>
            <li>帖子和失物招领都可以带图，照片比文字更快被认出来。</li>
            <li>找搭子、拼车这类信息建议写清时间与集合地点。</li>
          </ul>
        </div>
      </section>
    </template>
  </AppShell>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '../layouts/AppShell.vue'
import PageHero from '../components/PageHero.vue'
import UserAvatar from '../components/UserAvatar.vue'
import ImageUploader from '../components/ImageUploader.vue'
import PostFeed from '../components/PostFeed.vue'
import AidCard from '../components/AidCard.vue'
import StateBlock from '../components/StateBlock.vue'
import SkeletonList from '../components/SkeletonList.vue'
import { fetchAidList } from '../api/aid'
import { createPost, fetchPostKinds, fetchPosts } from '../api/community'
import { useAuthStore } from '../stores/auth'

/**
 * 校园专区。
 *
 * 布局：左侧主内容（帖子流 / 失物招领）+ 右侧信息栏（发布入口、分区速览、校园须知），
 * 窄屏时右栏折到主内容上方——与「发现」保持同一套栅格，切换模块时骨架不跳。
 *
 * 分区只有两个，但**种类覆盖后端下发的全部校园种类**：
 *  - 帖子分区走 GET /community/posts?channel=CAMPUS&kind=xxx，用 PostFeed 渲染；
 *  - 失物招领分区走 GET /aid/list?board=CAMPUS&category=失物招领，用 AidCard 渲染。
 *    它之所以要单列，是因为这条数据存在 u_r_aid_request 里（有地点、时间、状态流转），
 *    和帖子不是同一种结构，混在一个列表里会出现一半卡片没有地点、一半没有状态。
 *
 * 种类中文名与说明一律来自 GET /community/post-kinds?module=CAMPUS，
 * 前端不写死「唠嗑/吐槽/表白」这类标签，后端加一个种类这里会自动多一个分区。
 */

/** 失物招领的 kind 编码（后端 PostKind.LOST_FOUND），单独成区 */
const LOST_FOUND = 'LOST_FOUND'

const statusFilters = [
  { label: '全部', value: '' },
  { label: '待接单', value: 'OPEN' },
  { label: '进行中', value: 'ACCEPTED' },
  { label: '已完成', value: 'DONE' }
]

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const kinds = ref([])
const kindsLoading = ref(true)
const section = ref('posts')
const activeKind = ref('')
const aidStatus = ref('')
const posts = ref([])
const aidItems = ref([])
const draft = ref('')
const draftKind = ref('')
const draftImages = ref([])
const posting = ref(false)
const postsLoading = ref(true)
const aidLoading = ref(false)
const postsError = ref('')
const aidError = ref('')
const actionError = ref('')
const locateNote = ref('')
const feedRef = ref(null)
const composerRef = ref(null)
/** 高亮定时器：重复定位时先清掉上一个，避免旧定时器把新高亮提前摘掉 */
let flashTimer = null

/** 帖子种类：后端下发的全部校园种类里，去掉失物招领（它有独立分区与独立数据结构） */
const postKinds = computed(() => kinds.value.filter((k) => k.code !== LOST_FOUND))
const lostKind = computed(() => kinds.value.find((k) => k.code === LOST_FOUND))
const lostKindCode = computed(() => lostKind.value?.code || LOST_FOUND)
const currentDraftKind = computed(() => postKinds.value.find((k) => k.code === draftKind.value))
const activeKindHint = computed(() => kinds.value.find((k) => k.code === activeKind.value)?.hint || '')
const loading = computed(() => (section.value === 'lost' ? aidLoading.value : postsLoading.value))

const lostCreateTo = { path: '/aids/create', query: { board: 'CAMPUS', category: '失物招领' } }

const subtitle = computed(() => {
  if (section.value === 'lost') {
    if (aidLoading.value) return '正在加载失物招领信息'
    if (aidError.value) return '失物招领加载失败'
    return `校园失物招领 · 共 ${aidItems.value.length} 条`
  }
  if (postsLoading.value) return '正在加载校园帖子'
  if (postsError.value) return '校园帖子加载失败'
  const label = activeKind.value ? `「${kinds.value.find((k) => k.code === activeKind.value)?.label}」分区` : '全部帖子'
  return `校园${label} · 共 ${posts.value.length} 条`
})

const sectionNote = computed(() => {
  if (section.value === 'lost') {
    return '失物招领：丢了或捡到东西都发这里，需要填写地点与时间。'
  }
  if (activeKind.value) {
    return `${kinds.value.find((k) => k.code === activeKind.value)?.label}：${activeKindHint.value}`
  }
  return '全部帖子：按发布时间倒序，右上角可以按种类只看某一种。'
})

const composerTitle = computed(() => `发一条${currentDraftKind.value?.label || '校园帖'}`)
const composerHint = computed(() => currentDraftKind.value?.hint || '先选一个种类，再写点什么')
const draftPlaceholder = computed(() => (
  `写点什么…（当前种类：${currentDraftKind.value?.label || '未选择'}）`
))

const postEmptyTitle = computed(() => (
  activeKind.value
    ? `「${kinds.value.find((k) => k.code === activeKind.value)?.label}」分区还很安静`
    : '校园里还没有帖子'
))

const postEmptyDesc = computed(() => {
  if (activeKind.value) {
    // 种类的说明来自后端 hint，取不到时不留一个孤零零的句号
    const hint = activeKindHint.value ? `${activeKindHint.value}。` : ''
    return `${hint}第一条由你开头，同校的人就能在分区里看到。`
  }
  return '唠嗑、吐槽、表白、找搭子、分享日常都可以发在这里。'
})

const aidEmptyTitle = computed(() => (
  aidStatus.value
    ? `没有${statusFilters.find((f) => f.value === aidStatus.value)?.label || ''}的失物招领`
    : '还没有失物招领信息'
))

const aidEmptyDesc = computed(() => (
  aidStatus.value
    ? '换个状态看看，或者自己发一条。'
    : '校园卡、雨伞、耳机最容易丢也最容易还回去，丢了或捡到都发一条。'
))

function isZoneActive(code) {
  return code === LOST_FOUND ? section.value === 'lost' : section.value === 'posts' && activeKind.value === code
}

/** 分区切换：分区与种类都写回地址栏，刷新或分享后仍停在同一处 */
function switchSection(code) {
  if (code === LOST_FOUND) {
    section.value = 'lost'
    activeKind.value = ''
  } else {
    section.value = 'posts'
    activeKind.value = code || ''
    if (code) draftKind.value = code
  }
  syncQuery()
  load()
}

function changeAidStatus(value) {
  aidStatus.value = value
  loadAid()
}

function syncQuery() {
  const query = section.value === 'lost'
    ? { view: 'lost' }
    : (activeKind.value ? { view: 'posts', kind: activeKind.value } : { view: 'posts' })
  const current = route.query
  // 相同的 query 不触发 replace，避免与下面的 watch 互相拉扯
  if (String(current.view || '') === String(query.view) && String(current.kind || '') === String(query.kind || '')) return
  router.replace({ path: '/campus', query })
}

/**
 * 把地址栏参数映射到分区状态。
 * 兼容三类历史链接：?tab=aid（来自求助详情/发布页）、?category=吐槽（来自首页校园磁贴）、
 * ?kind=RANT（本页自己写回的编码）。
 */
function applyQuery() {
  const q = route.query
  const categoryParam = String(q.category || '').trim()
  const kindParam = String(q.kind || '').trim().toUpperCase()
  const tabParam = String(q.tab || '')
  const viewParam = String(q.view || '')

  const wantLost = viewParam === 'lost' || tabParam === 'aid'
    || categoryParam === (lostKind.value?.label || '失物招领')
    || kindParam === LOST_FOUND

  if (wantLost) {
    section.value = 'lost'
    activeKind.value = ''
    return
  }

  section.value = 'posts'
  const matched = kinds.value.find((k) => k.code === kindParam)
    || kinds.value.find((k) => k.label === categoryParam)
  if (matched && matched.code !== LOST_FOUND) {
    activeKind.value = matched.code
    draftKind.value = matched.code
  } else {
    activeKind.value = ''
  }
}

async function loadKinds() {
  kindsLoading.value = true
  try {
    const res = await fetchPostKinds('CAMPUS')
    kinds.value = res.data || []
    if (!draftKind.value && postKinds.value.length) draftKind.value = postKinds.value[0].code
  } catch {
    // 种类拉不到时发布器会没有选项，但不该让整个页面报错；页面上会给出提示
    kinds.value = []
  } finally {
    kindsLoading.value = false
  }
}

async function loadPosts(options = {}) {
  postsLoading.value = true
  postsError.value = ''
  try {
    const params = { page: 0, size: 30, channel: 'CAMPUS' }
    if (activeKind.value) params.kind = activeKind.value
    const res = await fetchPosts(params)
    posts.value = res.data?.content || res.data || []
    feedRef.value?.observeCards()
  } catch (e) {
    postsError.value = e.message
    posts.value = []
  } finally {
    postsLoading.value = false
  }
  // 定位必须放在 loading 归位之后：骨架屏还没换成卡片时，DOM 里根本没有目标节点
  if (options?.locate) await locateFromQuery()
}

async function loadAid() {
  aidLoading.value = true
  aidError.value = ''
  try {
    const params = { page: 0, size: 20, board: 'CAMPUS', category: '失物招领' }
    if (aidStatus.value) params.status = aidStatus.value
    const res = await fetchAidList(params)
    aidItems.value = res.data?.content || []
  } catch (e) {
    aidError.value = e.message
    aidItems.value = []
  } finally {
    aidLoading.value = false
  }
}

function load(options = {}) {
  return section.value === 'lost' ? loadAid() : loadPosts(options)
}

/**
 * 通知跳转定位。
 *
 * <p>community-service 为校园帖的「被评论/被点赞」生成的通知链接是 /campus?post={id}
 * （见 CommunityService.postLink）。这里等列表渲染完成后把目标帖子滚到视野中间，
 * 并短暂高亮一下，让人一眼看到自己是被哪条帖子叫过来的。
 *
 * <p>与 DiscoverView 的实现保持一致：PostFeed 自带的 .post-card:target 只在地址栏
 * 带 #hash 时生效，所以这里补一个短命的 is-flash 类，而不是去改共享组件的行为。
 */
async function locateFromQuery() {
  const targetId = String(route.query.post || '').trim()
  if (!targetId) return
  locateNote.value = ''
  await nextTick()
  const el = document.getElementById(`post-${targetId}`)
  if (!el) {
    // 不静默吞掉：帖子可能被作者删除，也可能不在当前分区，或被「第一页 30 条」截断
    locateNote.value = '这条帖子不在当前列表里，可能已被删除，或不在当前分区下。'
    return
  }
  el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  el.classList.add('is-flash')
  if (flashTimer) window.clearTimeout(flashTimer)
  flashTimer = window.setTimeout(() => el.classList.remove('is-flash'), 2400)
}

/** 刷新：种类（分区筛选与发布器的数据源）没取到时一并重取 */
async function refresh() {
  if (!kinds.value.length) await loadKinds()
  await load()
}

/** 从右栏「发布入口」跳到发布框：切回帖子分区后要等发布框渲染出来再滚动 */
async function focusComposer() {
  if (section.value !== 'posts') {
    switchSection('')
    await nextTick()
  }
  composerRef.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

async function onCreatePost() {
  if (!draft.value) return
  posting.value = true
  actionError.value = ''
  try {
    await createPost({
      content: draft.value,
      channel: 'CAMPUS',
      kind: draftKind.value,
      images: draftImages.value
    })
    draft.value = ''
    draftImages.value = []
    // 新帖属于当前种类，留在原分区并重取第一页就能看到
    await loadPosts()
  } catch (e) {
    actionError.value = e.message
  } finally {
    posting.value = false
  }
}

function onFeedChanged(event) {
  if (event?.type === 'error') {
    actionError.value = event.message
    return
  }
  if (event?.type === 'deleted') loadPosts()
}

onMounted(async () => {
  // 种类先到，才能把 ?category=吐槽 这类中文参数映射到 kind 编码
  await loadKinds()
  applyQuery()
  // 仅首屏定位一次：通知里的 /campus?post={id} 跳过来时把人带到那条帖子；
  // 切分区/刷新时不必再把用户拉回去
  await load({ locate: true })
})

// 站内跳转（求助详情返回、浏览器前进后退）会改地址栏，状态要跟着走；
// 若参数变化没有改变分区，就不重复取数（本页自己 replace 的情况正属于这一类）
watch(() => route.query, () => {
  const before = `${section.value}|${activeKind.value}`
  applyQuery()
  if (`${section.value}|${activeKind.value}` !== before) load()
})
</script>

<style scoped>
/* ---------- 瓦片节奏：白（写） → 米白（看） → 近黑（须知） ---------- */
.campus-body {
  display: grid;
}

/* 瓦片左右不留内距，内容与 .page 的文字左边缘对齐 */
.band {
  padding-left: 0;
  padding-right: 0;
}

/* 发帖区不再是一块白色瓦片：外层白框取消，标题与分区切换直接落在页面米白底上。
   上方不再加内距——英雄瓦片的 80px 与 .page 的 48px 已经给足了空气；
   下方留一点，与内容区的 80px 合起来构成一次段落呼吸。 */
.campus-write {
  padding: 0 0 var(--sp-7);
}

.band-head {
  display: grid;
  gap: var(--sp-2);
  margin-bottom: var(--sp-6);
}

/* ---------- 布局：主内容 1.6fr + 右栏 1fr ---------- */
.campus-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(280px, 1fr);
  gap: var(--sp-7);
  align-items: start;
}

.campus-main {
  display: grid;
  gap: var(--sp-5);
  min-width: 0;
  align-content: start;
}

.campus-rail {
  display: grid;
  gap: var(--sp-7);
  align-content: start;
  position: sticky;
  top: calc(var(--nav-stack) + var(--sp-4));
}

/* 分区切换：这一页唯一的「按种类筛选」入口。
   胶囊组可能换行，所以用 align-items: flex-start，说明文字在右侧顶端对齐。 */
.section-bar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-5);
  flex-wrap: wrap;
}

.section-bar .segmented {
  flex-wrap: wrap;
  background: var(--parchment);
}

.section-note {
  max-width: 34ch;
  padding-top: 6px;   /* 与胶囊里的文字基线对齐 */
}

.section-warn {
  margin-top: var(--sp-4);
  padding: var(--sp-2) 0 var(--sp-2) var(--sp-3);
  border-left: 2px solid currentColor;
  color: var(--warning);
}

/* ---------- 发布器：这一页唯一的表面 ----------
   外层白框已经取消，所以书写台自己就是白卡：白底 + 1px 发丝线 + 18px 圆角，
   直接落在页面米白底上。卡内的输入框反过来用米白「井」——
   白卡里再放白输入框会看不出边界，米白底才读得出「这里可以写字」，
   聚焦时再变回白底 + 蓝色描边（与全站焦点语言一致）。 */
.composer {
  display: grid;
  gap: var(--sp-5);
  margin-top: var(--sp-6);
  padding: var(--sp-6);
  background: var(--canvas);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
}

.composer .textarea {
  background: var(--parchment);
  border-color: transparent;
}

.composer .textarea:focus {
  background: var(--canvas);
  border-color: var(--accent-strong);
}

.composer-head {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.composer-title {
  display: grid;
  gap: var(--sp-1);
}

/* 失物招领分区没有帖子发布器，用同一套骨架给一句说明 + 唯一入口 */
.composer--lost {
  align-items: start;
}

.lost-mark {
  width: 36px;
  height: 36px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--parchment);
  color: var(--ink-2);
  font-size: 16px;
  font-weight: 600;
}

.kind-chips {
  display: flex;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

/* 种类胶囊（configurator-option-chip）：落在白卡上，用米白底 + 透明描边（Pearl 语法）；
   选中换 2px Focus Blue 描边 + 白底，不做整块染色。
   内距各减 1px 抵消描边增量，选中时不跳动。 */
.chip {
  background: var(--parchment);
  border-color: transparent;
  color: var(--ink-2);
}

.chip:hover:not(:disabled) {
  background: var(--pearl);
  border-color: var(--line-strong);
  color: var(--ink);
}

.chip.is-on {
  background: var(--canvas);
  border: 2px solid var(--accent-strong);
  padding: 7px 14px;
  color: var(--ink);
  font-weight: 600;
}

.composer form {
  display: grid;
  gap: var(--sp-4);
}

/* 计数与发布按钮用一条发丝线收口，把书写区与操作区分开 */
.composer-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  flex-wrap: wrap;
  padding-top: var(--sp-4);
  border-top: 1px solid var(--line);
}

/* ---------- 内容区里的筛选行（只剩失物招领的「状态」这一条轴） ---------- */
.filter-row {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  flex-wrap: wrap;
  padding-bottom: var(--sp-2);
}

.filter-label {
  color: var(--muted);
}

.filter-note {
  max-width: 40ch;
}

/* ---------- 失物招领 ---------- */
.aid-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--sp-4);
  align-items: start;
}

/* ---------- 右栏：标签 + 发丝线，没有卡片底 ---------- */
.rail-block {
  display: grid;
  gap: var(--sp-3);
}

.zone-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: var(--sp-1);
}

.zone-row {
  display: grid;
  gap: var(--sp-1);
  width: 100%;
  padding: var(--sp-3);
  border: 0;
  border-radius: var(--r-lg);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.16s var(--ease);
}

/* 选中与悬停都只在米白底上浮起一块白，不用描边和色块 */
.zone-row:hover { background: var(--canvas); }

.zone-row.is-on { background: var(--canvas); }

.zone-row.is-on .zone-label { color: var(--accent); }

/* ---------- 校园须知：近黑瓦片里两栏叙事 ---------- */
.notes-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: var(--sp-8);
  align-items: start;
}

.notes-head {
  display: grid;
  gap: var(--sp-3);
}

.tips {
  margin: 0;
  padding-left: 1.1em;
  display: grid;
  gap: var(--sp-3);
}

/* ---------- 提示条 ---------- */
.global-error,
.locate-note {
  padding: var(--sp-2) 0 var(--sp-2) var(--sp-3);
  border-left: 2px solid currentColor;
}

.global-error { color: var(--danger); }

.locate-note { color: var(--warning); }

/* 通知里的 /campus?post={id} 跳转过来时，短暂高亮目标帖子。
   PostFeed 的 .post-card:target 只在地址栏带 #hash 时命中，这里用临时类补上，
   不去改动共享组件的既有行为。 */
.campus-main :deep(.post-card.is-flash) {
  border-color: var(--accent);
  outline: 2px solid var(--accent-strong);
  outline-offset: 2px;
  transition: border-color 0.2s var(--ease);
}

/* 右栏折到主内容上方：1040px 以下一栏放不下「流 + 栏」 */
@media (max-width: 1040px) {
  .campus-grid { grid-template-columns: minmax(0, 1fr); }
  .campus-rail {
    position: static;
    order: -1;
    gap: var(--sp-6);
    padding-bottom: var(--sp-6);
    border-bottom: 1px solid var(--line);
  }
}

@media (max-width: 833px) {
  .notes-grid { grid-template-columns: minmax(0, 1fr); gap: var(--sp-6); }
  .section-note { max-width: none; padding-top: 0; }
  .campus-grid { gap: var(--sp-6); }
}

@media (max-width: 734px) {
  /* 书写台在窄屏收回内距，但不回到 0——贴边的问题不能再出现 */
  .composer { padding: var(--sp-5); }
}
</style>
