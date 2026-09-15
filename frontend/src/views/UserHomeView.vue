<template>
  <AppShell
    title="主页"
    eyebrow="邻里"
  >
    <template #actions>
      <router-link v-if="isSelf" class="btn btn--ghost btn--sm" to="/me/profile/edit">更改资料</router-link>
    </template>

    <!-- 通栏近黑瓦片：封面图 + 头像 + 昵称 + 简介 + 评分，个人主页的「封面」 -->
    <template #hero>
      <PageHero
        v-if="profile"
        tone="dark"
        align="left"
        eyebrow="邻里 · 公开资料"
        :title="pageTitle"
        :lead="subtitle"
        :cover="profile.coverImage || ''"
      >
        <div class="hero-card">
          <UserAvatar :user="profile" size="lg" />
          <div class="hero-main">
            <div class="hero-title">
              <h2>{{ profile.nickname || '邻里用户' }}</h2>
              <span class="tag tag--outline">{{ roleText(profile.role) }}</span>
              <span class="tag tag--outline">{{ presenceLabel(profile.presenceStatus) }}</span>
            </div>
            <p class="hero-bio lead-body">{{ profile.bio || '这位邻居还没有填写简介。' }}</p>
            <StarRating
              :avg="profile.ratingAvg || 0"
              :count="profile.ratingCount || 0"
              :my-score="profile.myScore"
              :readonly="isSelf"
              @rate="onRateUser"
            />

            <!-- 关注 / 拉黑：看别人的主页时，这里是**唯一**的入口。
                 以前个人主页一个按钮都没有，而关注列表的空态却写着
                 「在个人主页点『关注』」——那句话指向的按钮根本不存在。
                 放在近黑封面里，所以用 tone="dark"（深色底要换 Sky Link Blue）。 -->
            <FollowButton
              v-if="!isSelf"
              :user-id="userId"
              tone="dark"
              :initial-relation="relation"
              @changed="onRelationChanged"
            />
          </div>
        </div>
      </PageHero>
    </template>

    <SkeletonList v-if="loading" :count="2" variant="card" />

    <StateBlock
      v-else-if="privateLocked"
      variant="empty"
      title="该账户已设为隐私账户"
      desc="对方开启了隐私保护，他人无法查看个人主页。"
    >
      <router-link class="btn btn--ghost btn--sm" to="/community">去看看邻里动态</router-link>
    </StateBlock>

    <StateBlock v-else-if="error" variant="error" :desc="error">
      <router-link class="btn btn--ghost btn--sm" to="/">返回首页</router-link>
    </StateBlock>

    <template v-else-if="profile">
      <!-- 统计：来自后端跨服务聚合，下游不可用时整块隐藏而不是显示 0。
           关注 / 粉丝两项来自关系接口，与聚合统计相互独立。 -->
      <div v-if="(home && (home.aidStats || home.postStats)) || relation" class="stat-strip">
        <div v-if="home?.aidStats" class="stat">
          <span class="stat-label">发布的求助</span>
          <strong class="stat-value">{{ home.aidStats.publishedCount }}</strong>
        </div>
        <div v-if="home?.aidStats" class="stat">
          <span class="stat-label">帮助他人</span>
          <strong class="stat-value">{{ home.aidStats.helpingCount }}</strong>
        </div>
        <div v-if="home?.aidStats" class="stat">
          <span class="stat-label">已完成</span>
          <strong class="stat-value">{{ home.aidStats.doneCount }}</strong>
        </div>
        <div v-if="home?.postStats" class="stat">
          <span class="stat-label">发布的动态</span>
          <strong class="stat-value">{{ home.postStats.postCount }}</strong>
        </div>
        <div v-if="relation" class="stat">
          <span class="stat-label">{{ isSelf ? '关注我的人' : '关注 TA 的人' }}</span>
          <strong class="stat-value">{{ relation.followerCount ?? 0 }}</strong>
        </div>
        <div v-if="relation" class="stat">
          <span class="stat-label">{{ isSelf ? '我关注的人' : 'TA 关注的人' }}</span>
          <strong class="stat-value">{{ relation.followingCount ?? 0 }}</strong>
        </div>
      </div>
      <p v-else-if="home" class="stat-unavailable caption">统计数据来自其他服务，当前暂不可用。</p>

      <!-- 资料明细：白卡 + 发丝线分隔的键值行 -->
      <div class="info-grid grid-auto">
        <section class="panel">
          <div class="panel-head"><h3>基本信息</h3></div>
          <dl class="fact-list">
            <div><dt class="caption">加入时间</dt><dd>{{ formatDate(profile.createdAt) || '未填写' }}</dd></div>
            <div><dt class="caption">性别</dt><dd>{{ genderLabel(profile.gender) }}</dd></div>
            <div><dt class="caption">城市</dt><dd>{{ profile.city || '未填写' }}</dd></div>
            <div><dt class="caption">小区 / 社区</dt><dd>{{ profile.neighborhood || '未填写' }}</dd></div>
          </dl>
        </section>

        <section v-if="profile.student || profile.school || profile.major || profile.grade" class="panel">
          <div class="panel-head"><h3>学生信息</h3></div>
          <dl class="fact-list">
            <div><dt class="caption">身份</dt><dd>{{ profile.student ? '在校学生' : '未标注' }}</dd></div>
            <div><dt class="caption">学校</dt><dd>{{ profile.school || '未填写' }}</dd></div>
            <div><dt class="caption">专业</dt><dd>{{ profile.major || '未填写' }}</dd></div>
            <div><dt class="caption">年级</dt><dd>{{ profile.grade || '未填写' }}</dd></div>
          </dl>
        </section>

        <section class="panel">
          <div class="panel-head"><h3>联系方式</h3></div>
          <dl class="fact-list">
            <div><dt class="caption">微信</dt><dd>{{ profile.wechat || '未填写' }}</dd></div>
            <div v-if="profile.phone"><dt class="caption">手机</dt><dd>{{ profile.phone }}</dd></div>
            <div v-if="profile.realName"><dt class="caption">真实姓名</dt><dd>{{ profile.realName }}</dd></div>
          </dl>
        </section>
      </div>

      <!-- 内容 tabs -->
      <div class="tabs segmented" role="tablist">
        <button
          type="button"
          role="tab"
          :aria-selected="tab === 'posts'"
          :aria-pressed="tab === 'posts'"
          @click="switchTab('posts')"
        >
          动态{{ posts.length ? ` ${posts.length}` : '' }}
        </button>
        <button
          type="button"
          role="tab"
          :aria-selected="tab === 'aids'"
          :aria-pressed="tab === 'aids'"
          @click="switchTab('aids')"
        >
          发布的求助{{ aids.length ? ` ${aids.length}` : '' }}
        </button>
      </div>

      <div class="tab-panel">
        <template v-if="tab === 'posts'">
          <PostFeed
            ref="feedRef"
            :posts="posts"
            :loading="tabLoading"
            empty-title="还没有动态"
            empty-desc="他/她还没有发布过内容。"
            :anchor-prefix="''"
          />
        </template>
        <template v-else>
          <SkeletonList v-if="tabLoading" :count="3" variant="card" />
          <StateBlock
            v-else-if="!aids.length"
            title="还没有发布求助"
            desc="目前没有公开的求助记录。"
          />
          <div v-else class="aid-grid enter-stagger">
            <AidCard v-for="item in aids" :key="item.id" :item="item" />
          </div>
        </template>
      </div>
    </template>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '../layouts/AppShell.vue'
import PageHero from '../components/PageHero.vue'
import UserAvatar from '../components/UserAvatar.vue'
import StarRating from '../components/StarRating.vue'
import FollowButton from '../components/FollowButton.vue'
import PostFeed from '../components/PostFeed.vue'
import AidCard from '../components/AidCard.vue'
import StateBlock from '../components/StateBlock.vue'
import SkeletonList from '../components/SkeletonList.vue'
import { fetchRelation } from '../api/relation'
import { fetchUserHome, rateUser } from '../api/user'
import { fetchAuthorPosts } from '../api/community'
import { fetchUserPublished } from '../api/aid'
import { genderLabel, presenceLabel } from '../utils/presence'
import { formatDate } from '../utils/format'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const auth = useAuthStore()

const profile = ref(null)
const home = ref(null)
const posts = ref([])
const aids = ref([])
const tab = ref('posts')
const loading = ref(true)
const tabLoading = ref(false)
const error = ref('')
const privateLocked = ref(false)
const feedRef = ref(null)
/**
 * 我与 TA 的关系（关注 / 拉黑 / 粉丝数）。
 *
 * 这里取一次、再交给 FollowButton 当 initialRelation：单点场景本来就是一次请求，
 * 而且统计条也要用它来显示粉丝数与关注数——让按钮自己再查一次就是重复请求。
 */
const relation = ref(null)

const userId = computed(() => Number(route.params.id))
const isSelf = computed(() => profile.value?.self || userId.value === auth.user?.id)

const pageTitle = computed(() => {
  if (privateLocked.value) return '隐私账户'
  return isSelf.value ? '我的主页' : `${profile.value?.nickname || '邻里'}的主页`
})

const subtitle = computed(() => {
  if (privateLocked.value) return '对方开启了隐私保护'
  if (!profile.value) return '查看邻里资料与公开内容'
  const parts = []
  if (home.value?.aidStats) parts.push(`发布求助 ${home.value.aidStats.publishedCount} 次`)
  if (home.value?.postStats) parts.push(`发布动态 ${home.value.postStats.postCount} 条`)
  return parts.length ? parts.join(' · ') : '查看邻里资料与公开内容'
})

function roleText(role) {
  return role === 'ADMIN' ? '管理员' : '邻里用户'
}

async function loadHome() {
  loading.value = true
  error.value = ''
  privateLocked.value = false
  try {
    const res = await fetchUserHome(userId.value)
    home.value = res.data
    profile.value = res.data?.profile || null
  } catch (e) {
    const message = e.message || '加载失败'
    privateLocked.value = message.includes('隐私账户')
    error.value = message
    profile.value = null
    home.value = null
  } finally {
    loading.value = false
  }
}

/**
 * 关系状态单独取：它不是用户主页聚合接口的一部分
 * （聚合接口只给资料与内容统计，关系属于 auth-service 自己的域）。
 * 拿不到就只是不显示按钮与粉丝数，不影响页面其余部分。
 */
async function loadRelation() {
  if (!userId.value || userId.value === auth.user?.id) return
  try {
    const res = await fetchRelation(userId.value)
    relation.value = { ...(res.data || {}) }
  } catch {
    relation.value = null
  }
}

/** 关注 / 拉黑成功后，按钮状态与粉丝数都以接口返回的关系为准 */
function onRelationChanged(next) {
  if (next) relation.value = { ...next }
}

async function loadTab() {
  tabLoading.value = true
  try {
    if (tab.value === 'posts') {
      const res = await fetchAuthorPosts(userId.value, { page: 0, size: 20 })
      posts.value = res.data?.content || []
      feedRef.value?.observeCards()
    } else {
      const res = await fetchUserPublished(userId.value, { page: 0, size: 20 })
      aids.value = res.data?.content || []
    }
  } catch (e) {
    error.value = e.message
  } finally {
    tabLoading.value = false
  }
}

function switchTab(value) {
  tab.value = value
  loadTab()
}

async function onRateUser(score) {
  try {
    const res = await rateUser(userId.value, score)
    profile.value = res.data
  } catch (e) {
    error.value = e.message
  }
}

async function boot() {
  await loadHome()
  if (!error.value) await loadTab()
  // 关系与内容互不依赖，串行 await 只会让按钮晚一拍出现
  loadRelation()
}

onMounted(boot)
watch(() => route.params.id, () => {
  tab.value = 'posts'
  posts.value = []
  aids.value = []
  relation.value = null
  boot()
})
</script>

<style scoped>
/* 封面内的身份一行；外观全走全局类，这里只写排布 */
.hero-card {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-5);
}

.hero-main {
  display: grid;
  gap: var(--sp-3);
  min-width: 0;
  flex: 1;
}

.hero-title {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

/* 全局标题色是 --ink，放在近黑瓦片上会消失 */
.hero-title h2 {
  color: var(--on-dark);
}

.hero-bio {
  color: var(--on-dark-muted);
  max-width: 46ch;
}

/* 深色瓦片上的描边标签：浅底的 --line-strong 在近黑上会发白刺眼 */
.hero-card .tag {
  color: var(--on-dark-muted);
  border-color: var(--line-on-dark-strong);
}

/* StarRating 按浅底配色，次要文字在近黑上要抬高对比才读得清 */
.hero-card :deep(.meta) {
  color: var(--on-dark-muted);
}

.hero-card :deep(.meta strong) {
  color: var(--on-dark);
}

.stat-unavailable {
  margin: 0 0 var(--sp-4);
}

.info-grid {
  margin: var(--sp-5) 0;
}

.fact-list {
  margin: 0;
  display: grid;
  gap: var(--sp-3);
}

.fact-list > div {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  gap: var(--sp-3);
}

.fact-list dd {
  margin: 0;
  color: var(--ink-2);
  word-break: break-word;
}

.tabs {
  margin-bottom: var(--sp-4);
}

.tab-panel {
  min-height: 120px;
}

.aid-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--sp-4);
  align-items: start;
}

@media (max-width: 734px) {
  .hero-card {
    flex-direction: column;
    gap: var(--sp-4);
  }

  .fact-list > div {
    grid-template-columns: 72px minmax(0, 1fr);
  }
}
</style>
