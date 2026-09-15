<template>
  <AppShell
    title="生活广场"
    eyebrow="发现"
    :subtitle="subtitle"
  >
    <template #hero>
      <!-- 通栏英雄瓦片：整站唯一「大声说话」的地方。
           大标题是**带称呼的问候**（用户名，早上好/中午好/晚上好），
           副文案承担原来那句「这里是什么」的说明。 -->
      <PageHero
        eyebrow="生活广场"
        :title="greeting"
        lead="说说你今天遇到了什么"
        tone="parchment"
        size="hero"
      >
        <template #actions>
          <router-link class="btn btn--primary btn--hero" to="/discover/neighbor/create">发布一条求助</router-link>
          <router-link class="btn btn--secondary btn--hero" to="/discover/neighbor">看看邻居需要什么</router-link>
        </template>
      </PageHero>

      <!-- 广告走通栏宽容器，比正文栏更宽，避免缩在阅读栏里像一条窄贴。 -->
      <div class="discover-ads">
        <AdCarousel />
      </div>
    </template>

    <template #actions>
      <button class="btn btn--ghost btn--sm" type="button" :disabled="loading" @click="load">
        {{ loading ? '刷新中…' : '刷新' }}
      </button>
    </template>

    <div class="discover-grid">
      <div class="discover-main">
        <!-- 发布器：种类由后端下发，前端不维护映射表 -->
        <section class="composer panel">
          <div class="composer-head">
            <UserAvatar :user="auth.user" size="sm" />
            <div class="composer-title">
              <strong>{{ composerTitle }}</strong>
              <small>{{ composerHint }}</small>
            </div>
          </div>

          <div class="kind-chips" role="group" aria-label="选择帖子种类">
            <button
              v-for="k in kinds"
              :key="k.code"
              type="button"
              class="chip"
              :class="{ 'is-on': draftKind === k.code }"
              :title="k.hint"
              @click="draftKind = k.code"
            >
              {{ k.label }}
            </button>
          </div>

          <form @submit.prevent="onCreate">
            <textarea
              v-model.trim="draft"
              class="textarea"
              rows="3"
              maxlength="1000"
              :placeholder="draftPlaceholder"
            />
            <div class="composer-foot">
              <span class="counter nums">{{ draft.length }} / 1000</span>
              <button type="submit" class="btn btn--primary btn--sm" :disabled="posting || !draft">
                {{ posting ? '发布中…' : '发布' }}
              </button>
            </div>
          </form>
        </section>

        <!-- 种类筛选 -->
        <div class="filter-row">
          <div class="segmented" role="group" aria-label="按种类筛选">
            <button
              type="button"
              :aria-pressed="activeKind === ''"
              @click="changeKind('')"
            >
              全部
            </button>
            <button
              v-for="k in kinds"
              :key="k.code"
              type="button"
              :aria-pressed="activeKind === k.code"
              @click="changeKind(k.code)"
            >
              {{ k.label }}
            </button>
          </div>
        </div>

        <p v-if="actionError" class="global-error">{{ actionError }}</p>
        <p v-if="locateNote" class="locate-note">{{ locateNote }}</p>

        <PostFeed
          ref="feedRef"
          :posts="posts"
          :loading="loading"
          :error="error"
          show-category
          category-key="kindLabel"
          empty-title="这个分类下还没有内容"
          empty-desc="换个分类看看，或者成为第一个发布的人。"
        />
      </div>

      <aside class="discover-rail">
        <HotRank :items="hotItems" :rank-date="hotDate" :loading="loading" />
      </aside>
    </div>
  </AppShell>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '../layouts/AppShell.vue'
import PageHero from '../components/PageHero.vue'
import AdCarousel from '../components/AdCarousel.vue'
import UserAvatar from '../components/UserAvatar.vue'
import PostFeed from '../components/PostFeed.vue'
import HotRank from '../components/HotRank.vue'
import { createPost, fetchPlazaHot, fetchPostKinds, fetchPosts } from '../api/community'
import { useAuthStore } from '../stores/auth'
import { greetingFor } from '../utils/greeting'

/**
 * 本页 = 「发现」模块的默认子模块「生活广场」。
 *
 * 它原来同时挂着「邻里动态 / 生活广场」两个视角，但两者读的是**同一份数据**
 * （都传 channel=DISCOVER，只是标签与提示语不同）—— 也就是说那个切换器
 * 从来没有真的换过内容。本轮改成子模块导航后，它更没有存在的理由：
 * 「邻里互助」已经是一个独立的子模块，再在广场里留一个同名的视角，
 * 只会让人以为广场里还藏着另一批求助数据。
 */
const auth = useAuthStore()
const route = useRoute()

const posts = ref([])
const hotItems = ref([])
const hotDate = ref('')
const kinds = ref([])
const draft = ref('')
const draftKind = ref('')
const activeKind = ref('')
const posting = ref(false)
const loading = ref(true)
const error = ref('')
const actionError = ref('')
const locateNote = ref('')
const feedRef = ref(null)
/** 高亮定时器：重复定位时先清掉上一个，避免旧定时器把新高亮提前摘掉 */
let flashTimer = null

const subtitle = computed(() => {
  if (loading.value) return '正在加载生活广场内容'
  return `生活广场 · 共 ${posts.value.length} 条`
})

/**
 * 首页大标题：带称呼的问候。
 *
 * 问候语本身由 utils/greeting 决定（早/中/晚三段）；这里只负责取昵称。
 * 昵称拿不到时退化成单纯的问候，不会出现「，早上好」这种开头缺主语的句子。
 */
const greeting = computed(() => greetingFor(auth.user?.nickname))

const currentKind = computed(() => kinds.value.find((k) => k.code === draftKind.value))
const composerTitle = computed(() => `发一条${currentKind.value?.label || '内容'}`)
const composerHint = computed(() => currentKind.value?.hint || '选一个种类，再写点什么')
const draftPlaceholder = computed(() => `写点什么…（当前种类：${currentKind.value?.label || '未选择'}）`)

function changeKind(value) {
  activeKind.value = value
  load()
}

async function loadKinds() {
  try {
    const res = await fetchPostKinds('DISCOVER')
    kinds.value = res.data || []
    if (!draftKind.value && kinds.value.length) draftKind.value = kinds.value[0].code
  } catch {
    kinds.value = []
  }
}

async function load(options = {}) {
  loading.value = true
  error.value = ''
  try {
    const params = { page: 0, size: 30, channel: 'DISCOVER' }
    if (activeKind.value) params.kind = activeKind.value
    const [feed, hot] = await Promise.all([fetchPosts(params), fetchPlazaHot()])
    posts.value = feed.data?.content || feed.data || []
    hotItems.value = hot.data?.items || []
    hotDate.value = hot.data?.rankDate || ''
    feedRef.value?.observeCards()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
  // 定位必须放在 loading 归位之后：骨架屏还没换成卡片时，DOM 里根本没有目标节点
  if (options?.locate) await locateFromQuery()
}

/**
 * 通知跳转定位。
 *
 * notify-service 为「动态被评论/被点赞」生成的通知链接是 /discover?post={id}
 * （见 NotificationType.link）。这里的职责是：等列表渲染完成后把目标动态滚到
 * 视野中间，并短暂高亮一下，让人一眼看到自己是被哪条动态叫过来的。
 *
 * PostFeed 已有的 `.post-card:target` 只在地址栏带 #hash 时生效，
 * 所以这里补一个短命的 is-flash 类，而不是去改共享组件的行为。
 */
async function locateFromQuery() {
  const targetId = String(route.query.post || '').trim()
  if (!targetId) return
  locateNote.value = ''
  await nextTick()
  const el = document.getElementById(`post-${targetId}`)
  if (!el) {
    // 不静默吞掉：动态可能被作者删除，也可能不在当前视角/分类下
    locateNote.value = '这条动态不在当前列表里，可能已被删除，或不在当前分类下。'
    return
  }
  el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  el.classList.add('is-flash')
  if (flashTimer) window.clearTimeout(flashTimer)
  flashTimer = window.setTimeout(() => el.classList.remove('is-flash'), 2400)
}

async function onCreate() {
  posting.value = true
  actionError.value = ''
  try {
    await createPost({ content: draft.value, channel: 'DISCOVER', kind: draftKind.value })
    draft.value = ''
    await load()
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
  if (event?.type === 'deleted') load()
}

onMounted(async () => {
  await loadKinds()
  // 仅首屏定位一次：刷新时不必再把用户拉回那条动态
  await load({ locate: true })
})
</script>

<style scoped>
.discover-ads {
  width: min(var(--container-wide), calc(100% - 24px));
  margin: 0 auto;
}

.discover-ads :deep(.carousel) {
  margin-bottom: var(--sp-7);
}

.discover-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(280px, 1fr);
  gap: var(--sp-6);
  align-items: start;
}

.discover-main {
  display: grid;
  gap: var(--sp-5);
  min-width: 0;
}

.discover-rail {
  position: sticky;
  top: calc(var(--nav-stack) + var(--sp-6));
}

/* 发布器：白卡 + 24px 内距，是内容区唯一「交互优先」的区块 */
.composer {
  display: grid;
  gap: var(--sp-4);
}

.composer-head {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.composer-title {
  display: grid;
  line-height: 1.35;
}

.composer-title strong {
  font-size: 15px;
  letter-spacing: -0.2px;
}

.composer-title small {
  font-size: 12px;
  letter-spacing: -0.12px;
  color: var(--muted);
}

.kind-chips {
  display: flex;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

/* 配置器胶囊（configurator-option-chip）：未选=白底细描边，选中=2px Focus Blue */
.chip {
  padding: 12px 16px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-pill);
  background: var(--canvas);
  color: var(--ink);
  font-size: 14px;
  letter-spacing: -0.224px;
  cursor: pointer;
  transition: border-color 0.16s var(--ease), color 0.16s var(--ease);
}

.chip:hover { border-color: var(--ink-2); }

.chip.is-on {
  border: 2px solid var(--accent-strong);
  padding: 11px 15px;   /* 内缩 1px，选中时尺寸不变、不跳动 */
  font-weight: 600;
}

.composer form {
  display: grid;
  gap: var(--sp-3);
}

.composer-foot {
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

.filter-row {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex-wrap: wrap;
}

/* 行内提示：不整条染色，只用一条发丝线 + 小字 */
.global-error,
.locate-note {
  padding: var(--sp-3) var(--sp-4);
  border-radius: var(--r-md);
  border: 1px solid var(--line);
  background: var(--canvas);
  font-size: 14px;
  letter-spacing: -0.224px;
}

.global-error {
  border-color: rgba(192, 69, 58, 0.28);
  color: var(--danger);
}

.locate-note { color: var(--muted); }

/* 通知里的 /discover?post={id} 跳转过来时，短暂高亮目标动态。
   PostFeed 的 .post-card:target 只在地址栏带 #hash 时命中，这里用临时类补上，
   不去改动共享组件的既有行为。 */
.discover-main :deep(.post-card.is-flash) {
  border-color: var(--accent);
  outline: 2px solid var(--accent-strong);
  outline-offset: 2px;
  transition: border-color 0.2s var(--ease);
}

@media (max-width: 1040px) {
  .discover-grid { grid-template-columns: minmax(0, 1fr); }
  .discover-rail { position: static; order: -1; }
}
</style>
