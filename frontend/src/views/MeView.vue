<template>
  <AppShell
    title="个人主页"
    eyebrow="我的"
  >
    <!-- 通栏近黑瓦片：封面图 + 头像 + 昵称 + 简介 + 统计条，全站音量最高的一幕 -->
    <template #hero>
      <PageHero
        tone="dark"
        align="left"
        eyebrow="邻里 · 个人主页"
        title="个人主页"
        lead="个人资料、我发过的内容与常用入口"
        :cover="auth.user?.coverImage || ''"
      >
        <template #actions>
          <router-link class="btn btn--on-dark btn--sm" to="/me/profile/edit">编辑资料</router-link>
          <router-link class="btn btn--on-dark-ghost btn--sm" to="/discover/neighbor/create">发布求助</router-link>
        </template>

        <div class="me-hero">
          <div class="me-card">
            <UserAvatar :user="auth.user" size="lg" />
            <div class="me-main">
              <div class="me-title">
                <h2>{{ auth.user?.nickname || '邻里用户' }}</h2>
                <span class="tag tag--outline">{{ roleText }}</span>
                <span class="tag tag--outline">{{ presenceLabel(auth.user?.presenceStatus) }}</span>
              </div>
              <p class="me-bio lead-body">{{ auth.user?.bio || '还没有填写简介，去个人信息设置补一句吧。' }}</p>
            </div>
          </div>

          <!-- 一条横带、发丝线分隔，而不是五张各自染色的卡片。
               关注 / 粉丝两项可点，直接进对应的关系列表。 -->
          <div class="stat-strip stat-strip--dark">
            <router-link class="stat stat-link" to="/me/followers">
              <span class="stat-label">关注我的人</span>
              <strong class="stat-value">{{ relation.followerCount ?? '—' }}</strong>
              <span class="stat-hint">谁在留意你</span>
            </router-link>
            <router-link class="stat stat-link" to="/me/following">
              <span class="stat-label">我关注的人</span>
              <strong class="stat-value">{{ relation.followingCount ?? '—' }}</strong>
              <span class="stat-hint">看看他们的近况</span>
            </router-link>
            <router-link class="stat stat-link" to="/discover/neighbor">
              <span class="stat-label">我发布的求助</span>
              <strong class="stat-value">{{ aidStats.myPublishedCount ?? '—' }}</strong>
              <span class="stat-hint">点进去看邻里互助</span>
            </router-link>
            <div class="stat">
              <span class="stat-label">我帮助过</span>
              <strong class="stat-value">{{ aidStats.myHelpingCount ?? '—' }}</strong>
              <span class="stat-hint">累计接单次数</span>
            </div>
            <div class="stat">
              <span class="stat-label">我发布的动态</span>
              <strong class="stat-value">{{ communityStats.myPostCount ?? '—' }}</strong>
              <span class="stat-hint">含发现与校园</span>
            </div>
          </div>
        </div>
      </PageHero>
    </template>

    <div class="me-grid">
      <div class="me-content">
        <!-- 我发过的内容：动态 / 求助两个视角，默认动态 -->
        <div class="segmented me-tabs" role="tablist" aria-label="我的内容">
          <button
            type="button"
            role="tab"
            :aria-selected="tab === 'posts'"
            :aria-pressed="tab === 'posts'"
            @click="switchTab('posts')"
          >
            我的动态{{ posts.length ? ` ${posts.length}` : '' }}
          </button>
          <button
            type="button"
            role="tab"
            :aria-selected="tab === 'aids'"
            :aria-pressed="tab === 'aids'"
            @click="switchTab('aids')"
          >
            我发布的求助{{ aids.length ? ` ${aids.length}` : '' }}
          </button>
        </div>

        <div class="tab-panel">
          <template v-if="tab === 'posts'">
            <PostFeed
              ref="feedRef"
              :posts="posts"
              :loading="tabLoading"
              :error="tabError"
              empty-title="还没有发布动态"
              empty-desc="随手记一句，邻居就能看到你在忙什么。"
              empty-to="/discover"
              empty-action="去发一条"
            />
          </template>

          <template v-else>
            <SkeletonList v-if="tabLoading" :count="3" variant="card" />

            <StateBlock v-else-if="tabError" variant="error" :desc="tabError">
              <button class="btn btn--ghost btn--sm" type="button" @click="loadTab">重新加载</button>
            </StateBlock>

            <StateBlock
              v-else-if="!aids.length"
              title="还没有发布求助"
              desc="发布遇见的难事"
            >
              <router-link class="btn btn--primary btn--sm" to="/discover/neighbor/create">发布求助</router-link>
            </StateBlock>

            <div v-else class="aid-grid">
              <AidCard v-for="item in aids" :key="item.id" :item="item" />
            </div>
          </template>
        </div>
      </div>

      <!-- 常用入口：一张白卡内的发丝线行，而不是五张同款彩色图标卡 -->
      <aside class="me-rail">
        <section class="panel entry-panel">
          <p class="eyebrow entry-label">常用入口</p>
          <ul class="list-plain">
            <li>
              <router-link class="entry" to="/me/following">
                <span class="entry-icon" v-html="icons.user" />
                <span class="entry-text">
                  <strong>我关注的人</strong>
                  <small class="caption">看看他们都发了什么</small>
                </span>
                <span class="entry-arrow">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M5 12h13M12.5 6.5 18 12l-5.5 5.5" />
                  </svg>
                </span>
              </router-link>
            </li>

            <li>
              <router-link class="entry" to="/me/followers">
                <span class="entry-icon" v-html="icons.heart" />
                <span class="entry-text">
                  <strong>关注我的人</strong>
                  <small class="caption">谁在留意你的动态</small>
                </span>
                <span class="entry-arrow">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M5 12h13M12.5 6.5 18 12l-5.5 5.5" />
                  </svg>
                </span>
              </router-link>
            </li>

            <li>
              <router-link class="entry" to="/me/blocked">
                <span class="entry-icon danger" v-html="icons.block" />
                <span class="entry-text">
                  <strong>黑名单</strong>
                  <small class="caption">拉黑后双方内容互不可见</small>
                </span>
                <span class="entry-arrow">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M5 12h13M12.5 6.5 18 12l-5.5 5.5" />
                  </svg>
                </span>
              </router-link>
            </li>

            <li>
              <router-link class="entry" to="/messages">
                <span class="entry-icon" v-html="icons.bell" />
                <span class="entry-text">
                  <strong>我的消息</strong>
                  <small class="caption">接单、评论、关注等通知</small>
                </span>
                <span class="entry-arrow">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M5 12h13M12.5 6.5 18 12l-5.5 5.5" />
                  </svg>
                </span>
              </router-link>
            </li>

            <li>
              <router-link class="entry" to="/me/profile/edit">
                <span class="entry-icon" v-html="icons.gear" />
                <span class="entry-text">
                  <strong>编辑资料</strong>
                  <small class="caption">头像、封面、简介与隐私</small>
                </span>
                <span class="entry-arrow">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M5 12h13M12.5 6.5 18 12l-5.5 5.5" />
                  </svg>
                </span>
              </router-link>
            </li>

            <li>
              <router-link class="entry" to="/me/settings">
                <span class="entry-icon" v-html="icons.sliders" />
                <span class="entry-text">
                  <strong>设置</strong>
                  <small class="caption">深色模式、国家和地区与语言</small>
                </span>
                <span class="entry-arrow">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M5 12h13M12.5 6.5 18 12l-5.5 5.5" />
                  </svg>
                </span>
              </router-link>
            </li>
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
import UserAvatar from '../components/UserAvatar.vue'
import PostFeed from '../components/PostFeed.vue'
import AidCard from '../components/AidCard.vue'
import StateBlock from '../components/StateBlock.vue'
import SkeletonList from '../components/SkeletonList.vue'
import { fetchAidStats, fetchUserPublished } from '../api/aid'
import { fetchAuthorPosts, fetchCommunityStats } from '../api/community'
import { fetchRelation } from '../api/relation'
import { useAuthStore } from '../stores/auth'
import { presenceLabel } from '../utils/presence'

/**
 * 「我的」= 个人主页。
 *
 * 这一页把原先分开的两页合并了：`/me`（个人中心：资料 + 入口）与
 * `/users/{id}`（个人主页：资料 + 动态 + 求助）。两者封面与统计高度重叠，
 * 顶栏「我的」和右上角头像却落在不同 URL 上，看起来像两个页面。
 * 现在 `/me` 是唯一的「我」页面，访问自己的 `/users/{id}` 会重定向到这里
 * （见 src/router/guards.js），看别人的主页仍是 `/users/{别人的 id}`。
 */
const auth = useAuthStore()
const aidStats = reactive({})
const communityStats = reactive({})
/** 关注 / 粉丝数：后端对「自己」这一路返回的正是这两个计数 */
const relation = reactive({ followerCount: null, followingCount: null })

const tab = ref('posts')
const posts = ref([])
const aids = ref([])
const tabLoading = ref(false)
const tabError = ref('')
const feedRef = ref(null)

const roleText = computed(() => (auth.user?.role === 'ADMIN' ? '管理员' : '邻里用户'))
const myId = computed(() => auth.user?.id || null)

const stroke = 'fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"'
const icons = {
  user: `<svg viewBox="0 0 24 24" ${stroke}><circle cx="12" cy="8.5" r="3.6"/><path d="M5 20c0-3.4 3.1-5.6 7-5.6s7 2.2 7 5.6"/></svg>`,
  heart: `<svg viewBox="0 0 24 24" ${stroke}><path d="M20.5 12.5 12 21l-8.5-8.5a4.95 4.95 0 0 1 7-7l1.5 1.5 1.5-1.5a4.95 4.95 0 0 1 7 7Z"/></svg>`,
  block: `<svg viewBox="0 0 24 24" ${stroke}><circle cx="12" cy="12" r="8.5"/><path d="m6.5 6.5 11 11"/></svg>`,
  bell: `<svg viewBox="0 0 24 24" ${stroke}><path d="M18 16V11a6 6 0 1 0-12 0v5l-1.5 2.5h15Z"/><path d="M10 20.5a2 2 0 0 0 4 0"/></svg>`,
  gear: `<svg viewBox="0 0 24 24" ${stroke}><circle cx="12" cy="12" r="3.2"/><path d="M19.4 14.5a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1v.3a2 2 0 1 1-4 0v-.2a1.6 1.6 0 0 0-2.8-1.1l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0-1.1-2.7H3a2 2 0 1 1 0-4h.2a1.6 1.6 0 0 0 1.1-2.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 2.7-1.1V3a2 2 0 1 1 4 0v.2a1.6 1.6 0 0 0 2.8 1.1l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0 1.1 2.7H21a2 2 0 1 1 0 4h-.2a1.6 1.6 0 0 0-1.4 1.1Z"/></svg>`,
  // 偏好设置用推子（sliders）而不是再一个齿轮：这一页的入口和「编辑资料」
  // 并排出现，两个齿轮会长得一模一样，扫读时分不出哪个是哪个。
  sliders: `<svg viewBox="0 0 24 24" ${stroke}><path d="M4 7h10M18 7h2M4 17h4M12 17h8"/><circle cx="16" cy="7" r="2"/><circle cx="10" cy="17" r="2"/></svg>`
}

/** 拉「我发过的内容」。两种内容在两张表里，按当前 tab 各取一条。 */
async function loadTab() {
  if (!myId.value) {
    posts.value = []
    aids.value = []
    return
  }
  tabLoading.value = true
  tabError.value = ''
  try {
    if (tab.value === 'posts') {
      const res = await fetchAuthorPosts(myId.value, { page: 0, size: 20 })
      posts.value = res.data?.content || []
      feedRef.value?.observeCards()
    } else {
      const res = await fetchUserPublished(myId.value, { page: 0, size: 20 })
      aids.value = res.data?.content || []
    }
  } catch (e) {
    tabError.value = e.message
  } finally {
    tabLoading.value = false
  }
}

function switchTab(value) {
  if (tab.value === value) return
  tab.value = value
  loadTab()
}

onMounted(async () => {
  // 本地缓存为空时先补齐资料：下面的内容请求要按 userId 取
  if (!auth.user?.id) {
    try {
      await auth.loadProfile()
    } catch {
      // 取不到就只显示能显示的部分
    }
  }
  try {
    const [a, c, r] = await Promise.all([
      fetchAidStats(),
      fetchCommunityStats(),
      // 关系接口对「自己」返回的就是「我的粉丝数 / 我关注的人数」
      myId.value ? fetchRelation(myId.value) : Promise.resolve(null)
    ])
    Object.assign(aidStats, a.data || {})
    Object.assign(communityStats, c.data || {})
    if (r?.data) {
      relation.followerCount = r.data.followerCount ?? 0
      relation.followingCount = r.data.followingCount ?? 0
    }
  } catch {
    // 统计失败不影响页面主体；计数保持「—」
  }
  await loadTab()
})
</script>

<style scoped>
/* 瓦片内的延伸内容：身份一行 + 统计条，只写排布，外观全走全局类 */
.me-hero {
  display: grid;
  gap: var(--sp-7);
}

.me-card {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-5);
}

.me-main {
  display: grid;
  gap: var(--sp-3);
  min-width: 0;
  flex: 1;
}

.me-title {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

/* 全局标题色是 --ink，放在近黑瓦片上会消失，必须换成 on-dark 令牌 */
.me-title h2 {
  color: var(--on-dark);
}

.me-bio {
  color: var(--on-dark-muted);
  max-width: 46ch;
}

/* 深色瓦片上的描边标签：浅底的 --line-strong 在近黑上会发白刺眼 */
.me-hero .tag {
  color: var(--on-dark-muted);
  border-color: var(--line-on-dark-strong);
}

/* 可点统计在深色底上不能回落到浅底的 Action Blue */
.me-hero .stat-link:hover {
  background: transparent;
}

.me-hero .stat-link:hover .stat-value {
  color: var(--accent-on-dark);
}

/* ---------- 内容 + 右栏：与「发现」同一套栅格，切模块时骨架不跳 ---------- */
.me-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(280px, 1fr);
  gap: var(--sp-6);
  align-items: start;
}

.me-content {
  display: grid;
  gap: var(--sp-5);
  min-width: 0;
}

.me-tabs {
  justify-self: start;
}

.me-rail {
  display: grid;
  gap: var(--sp-5);
  align-content: start;
  position: sticky;
  top: calc(var(--nav-stack) + var(--sp-6));
}

.aid-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--sp-4);
  align-items: start;
}

/* 入口行：发丝线来自 .list-plain，这里只负责行内的横向排布 */
.entry-label {
  margin-bottom: var(--sp-3);
}

.entry {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  min-height: 48px;
}

.entry-icon {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  flex: none;
  color: var(--muted-2);
}

.entry-icon.danger {
  color: var(--danger);
}

.entry-icon :deep(svg) {
  width: 20px;
  height: 20px;
}

.entry-text {
  display: grid;
  gap: 1px;
  min-width: 0;
  flex: 1;
}

/* 17px 沿用正文字号，只把体例抬到 600 */
.entry-text strong {
  font-weight: 600;
}

.entry-arrow {
  display: grid;
  place-items: center;
  color: var(--muted-2);
  flex: none;
}

.entry-arrow svg {
  width: 16px;
  height: 16px;
}

.entry:hover .entry-text strong,
.entry:hover .entry-arrow {
  color: var(--accent);
}

@media (max-width: 1040px) {
  .me-grid { grid-template-columns: minmax(0, 1fr); }
  .me-rail { position: static; }
}

@media (max-width: 734px) {
  .me-card {
    flex-direction: column;
    gap: var(--sp-4);
  }
}
</style>
