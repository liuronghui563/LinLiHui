<template>
  <AppShell
    title="圈子"
    eyebrow="发现"
    :subtitle="subtitle"
  >
    <!-- 通栏英雄瓦片：一页只有这一处大声说话，其余交给内容 -->
    <template #hero>
      <PageHero
        eyebrow="邻里汇 · 圈子"
        title="交际圈"
        lead="加入感兴趣的圈子，点开就能看到圈内帖子。"
        tone="parchment"
        align="center"
      >
        <template #actions>
          <button class="btn btn--primary" type="button" @click="toggleCreate(true)">
            创建圈子
          </button>
        </template>
      </PageHero>
    </template>

    <template #actions>
      <button class="btn btn--primary btn--sm" type="button" @click="toggleCreate">
        {{ creating ? '收起创建框' : '创建圈子' }}
      </button>
      <button class="btn btn--ghost btn--sm" type="button" :disabled="loading" @click="reload">
        {{ loading ? '刷新中…' : '刷新' }}
      </button>
    </template>

    <div class="circle-grid">
      <div class="circle-main">
        <!-- 创建圈子：与集市一致，就地展开，不额外开页面 -->
        <section v-if="creating" ref="createRef" class="panel create-panel enter-rise">
          <div class="panel-head panel-head--plain">
            <h3>创建一个圈子</h3>
            <button class="panel-more" type="button" @click="creating = false">收起</button>
          </div>

          <form class="create-form" novalidate @submit.prevent="onCreate">
            <div class="field">
              <label for="circle-name">圈子名称</label>
              <input
                id="circle-name"
                v-model.trim="form.name"
                class="input"
                type="text"
                maxlength="50"
                placeholder="例如：3 号楼遛狗搭子"
              />
              <p v-if="errors.name" class="field-error">{{ errors.name }}</p>
              <p v-else class="field-hint">名称不能与已有圈子重复，最多 50 字。</p>
            </div>

            <div class="field">
              <label for="circle-desc">圈子简介</label>
              <textarea
                id="circle-desc"
                v-model.trim="form.description"
                class="textarea"
                rows="3"
                maxlength="200"
                placeholder="这个圈子是干什么的？谁适合加入？"
              />
              <div class="field-foot">
                <span v-if="errors.description" class="field-error">{{ errors.description }}</span>
                <span class="field-hint nums">{{ form.description.length }} / 200</span>
              </div>
            </div>

            <div class="field">
              <span class="field-label">封面（选填，1 张）</span>
              <ImageUploader v-model="form.cover" purpose="post" :max="1" />
              <p class="field-hint">不设置封面时用名称首字生成占位图，不影响使用。</p>
            </div>

            <p v-if="formError" class="global-error">{{ formError }}</p>

            <div class="create-foot">
              <span class="field-hint">创建后你自动成为圈主，可以发帖、管理成员或关闭圈子。</span>
              <button class="btn btn--primary btn--sm" type="submit" :disabled="submitting">
                {{ submitting ? '创建中…' : '创建圈子' }}
              </button>
            </div>
          </form>
        </section>

        <p v-if="successMsg" class="global-ok">{{ successMsg }}</p>
        <p v-if="actionError" class="global-error">{{ actionError }}</p>

        <!-- 展开态：圈子详情 + 圈内帖子。同一个路由内的展开，不新开页面 -->
        <template v-if="activeId">
          <section class="panel detail">
            <button class="link-btn link caption back" type="button" @click="closeDetail">← 返回圈子列表</button>

            <SkeletonList v-if="detailLoading" :count="1" variant="card" />

            <StateBlock v-else-if="detailError" variant="error" inline :desc="detailError">
              <button class="btn btn--ghost btn--sm" type="button" @click="openDetail(activeId)">重新加载</button>
            </StateBlock>

            <template v-else-if="detail">
              <header class="detail-head">
                <div class="detail-cover">
                  <img v-if="detail.cover" :src="detail.cover" alt="" @error="onCoverError" />
                  <span v-else class="detail-cover-initial tile-title">{{ initialOf(detail.name) }}</span>
                </div>
                <div class="detail-copy">
                  <div class="detail-title">
                    <h2>{{ detail.name }}</h2>
                    <span class="tag" :class="circleTone(detail.status)">{{ circleStatusLabel(detail.status) }}</span>
                    <span v-if="detail.joined" class="tag tag--done">已加入</span>
                  </div>
                  <p class="detail-desc lead-body">{{ detail.description || '圈主还没有写简介。' }}</p>
                  <div class="meta-row">
                    <span class="nums">{{ detail.memberCount || 0 }} 名成员</span>
                    <span class="meta-dot nums">{{ detail.postCount || 0 }} 条圈内帖子</span>
                    <span class="meta-dot">创建于 {{ relativeTime(detail.createdAt) }}</span>
                  </div>
                  <div class="detail-owner meta-row">
                    <span class="owner-label">圈主</span>
                    <PostAuthor
                      :user-id="detail.ownerId"
                      :name="detail.ownerName"
                      :avatar="detail.ownerAvatar"
                      size="xs"
                    />
                  </div>

                  <div class="detail-ops">
                    <button
                      v-if="!detail.joined"
                      class="btn btn--primary btn--sm"
                      type="button"
                      :disabled="opBusy"
                      @click="onJoin(detail)"
                    >
                      {{ opBusy ? '处理中…' : '加入圈子' }}
                    </button>
                    <button
                      v-else-if="!isOwner(detail)"
                      class="btn btn--ghost btn--sm"
                      type="button"
                      :disabled="opBusy"
                      @click="onLeave(detail)"
                    >
                      退出圈子
                    </button>
                    <span v-else class="owner-note fine-print">你是圈主，圈主不能退出，需要停止维护可以关闭圈子。</span>

                    <button
                      v-if="isOwner(detail) && detail.status !== 'CLOSED'"
                      class="btn btn--danger btn--sm"
                      type="button"
                      :disabled="opBusy"
                      @click="onClose(detail)"
                    >
                      关闭圈子
                    </button>
                  </div>
                </div>
              </header>

              <div class="divider" />

              <section v-if="canPost" class="composer">
                <h3 class="feed-title">在圈子里发帖</h3>
                <form novalidate @submit.prevent="onCompose">
                  <textarea
                    v-model.trim="draft"
                    class="textarea"
                    rows="3"
                    maxlength="1000"
                    placeholder="写点圈里的人会感兴趣的内容"
                  />
                  <ImageUploader v-model="draftImages" purpose="post" :max="9" />
                  <div class="composer-foot">
                    <span class="field-hint nums">{{ draft.length }} / 1000</span>
                    <button class="btn btn--primary btn--sm" type="submit" :disabled="posting || !draft">
                      {{ posting ? '发布中…' : '发布到圈子' }}
                    </button>
                  </div>
                </form>
              </section>
              <p v-else-if="detail.joined && detail.muted" class="muted-note fine-print">
                你已被限制在本圈发帖，如有疑问请联系圈主。
              </p>
              <p v-else-if="!detail.joined" class="feed-note fine-print">加入圈子后即可在这里发帖。</p>

              <section v-if="showMembers" class="member-panel">
                <div class="panel-head panel-head--plain">
                  <h3>成员</h3>
                  <span class="panel-note nums">{{ members.length || detail.memberCount || 0 }} 人</span>
                </div>
                <SkeletonList v-if="membersLoading" :count="2" />
                <ul v-else class="member-list">
                  <li v-for="member in members" :key="member.userId" class="member-row">
                    <PostAuthor
                      :user-id="member.userId"
                      :name="member.nickname"
                      :avatar="member.avatar"
                      size="xs"
                    />
                    <span class="member-tags">
                      <span class="tag" :class="roleTone(member.role)">{{ roleLabel(member.role) }}</span>
                      <span v-if="member.muted" class="tag tag--cancelled">已限制发帖</span>
                    </span>
                    <div v-if="memberActions(member).length" class="member-ops">
                      <button
                        v-for="action in memberActions(member)"
                        :key="action.key"
                        class="btn btn--quiet btn--sm"
                        type="button"
                        :disabled="memberBusyId === member.userId"
                        @click="runMemberAction(member, action.key)"
                      >
                        {{ action.label }}
                      </button>
                    </div>
                  </li>
                </ul>
              </section>

              <h3 class="feed-title">圈内帖子</h3>
              <p class="feed-note fine-print">
                圈内帖子同时会出现在「发现」流；圈主与管理员可以把不合适的内容从圈子下线。
              </p>
              <PostFeed
                ref="feedRef"
                :posts="circlePosts"
                :loading="postsLoading"
                :error="postsError"
                :can-offline="canModeratePosts"
                show-category
                category-key="kindLabel"
                empty-title="圈子里还没有帖子"
                empty-desc="加入后就可以在上面发第一条。"
                @offline="onOffline"
                @changed="onFeedChanged"
              />
            </template>
          </section>
        </template>

        <!-- 列表态 -->
        <template v-else>
          <section class="panel toolbar-panel">
            <!-- 关键词只在「全部圈子」下有效：/circle/mine 不支持 keyword，
                 与其让输入框看起来能搜却没反应，不如在「我加入的」里收起来 -->
            <form v-if="!onlyMine" class="search-row" @submit.prevent="applySearch">
              <label class="sr-only" for="circle-keyword">按名称搜索圈子</label>
              <input
                id="circle-keyword"
                v-model.trim="keywordDraft"
                class="input"
                type="search"
                placeholder="搜索圈子名称，回车确认"
              />
              <button class="btn btn--secondary btn--sm" type="submit">搜索</button>
              <button v-if="keyword" class="btn btn--quiet btn--sm" type="button" @click="clearKeyword">
                清除
              </button>
            </form>
            <div class="toolbar-row">
              <div class="segmented" role="group" aria-label="圈子范围">
                <button type="button" :aria-pressed="!onlyMine" @click="changeScope(false)">全部圈子</button>
                <button type="button" :aria-pressed="onlyMine" @click="changeScope(true)">我加入的</button>
              </div>
              <p class="toolbar-note fine-print">{{ scopeNote }}</p>
            </div>
          </section>

          <SkeletonList v-if="loading" :count="4" variant="card" />

          <StateBlock v-else-if="error" variant="error" :desc="error">
            <button class="btn btn--ghost btn--sm" type="button" @click="reload">重新加载</button>
          </StateBlock>

          <StateBlock v-else-if="!circles.length" :title="emptyTitle" :desc="emptyDesc">
            <button v-if="onlyMine" class="btn btn--ghost btn--sm" type="button" @click="changeScope(false)">
              看看全部圈子
            </button>
            <button v-else class="btn btn--primary btn--sm" type="button" @click="toggleCreate(true)">
              创建第一个圈子
            </button>
          </StateBlock>

          <div v-else class="circle-cards enter-stagger">
            <article
              v-for="item in circles"
              :key="item.id"
              class="circle-card"
              :class="{ 'is-active': item.id === activeId }"
            >
              <button class="circle-hit" type="button" @click="openDetail(item.id)">
                <span class="circle-cover">
                  <img v-if="item.cover" :src="item.cover" alt="" loading="lazy" @error="onCoverError" />
                  <span v-else class="circle-cover-initial tile-title">{{ initialOf(item.name) }}</span>
                </span>
                <span class="circle-body">
                  <span class="circle-title">
                    <strong class="brand-title clamp-2">{{ item.name }}</strong>
                    <span v-if="item.joined" class="tag tag--done">已加入</span>
                    <span v-else-if="item.status === 'CLOSED'" class="tag tag--cancelled">已关闭</span>
                  </span>
                  <span class="circle-desc caption clamp-2">{{ item.description || '圈主还没有写简介。' }}</span>
                  <span class="meta-row">
                    <span class="nums">{{ item.memberCount || 0 }} 名成员</span>
                    <span class="meta-dot nums">{{ item.postCount || 0 }} 条帖子</span>
                  </span>
                </span>
              </button>

              <!-- 圈主信息放在按钮之外：<a> 不能嵌在 <button> 里，
                   否则点击作者链接会被按钮吞掉，也是无效 HTML -->
              <footer class="circle-foot">
                <PostAuthor
                  :user-id="item.ownerId"
                  :name="item.ownerName"
                  :avatar="item.ownerAvatar"
                  size="xs"
                />
                <span class="circle-foot-hint fine-print">点卡片看圈内帖子</span>
              </footer>
            </article>
          </div>

          <div v-if="!loading && !error && hasMore" class="more-row">
            <button class="btn btn--ghost btn--sm" type="button" :disabled="loadingMore" @click="loadMore">
              {{ loadingMore ? '加载中…' : `加载更多（还有 ${totalElements - circles.length} 个）` }}
            </button>
          </div>
        </template>
      </div>

      <aside class="circle-rail">
        <!-- 近黑瓦片卡：右栏里唯一压场面的区块，用表面色差收束「怎么用圈子」 -->
        <section class="panel panel--dark">
          <div class="panel-head">
            <h3>怎么用圈子</h3>
          </div>
          <ul class="tips lead-body">
            <li>圈子按兴趣聚合，比如楼栋、宠物、考研、球局。</li>
            <li>创建者自动成为圈主，成员数从 1 起算。</li>
            <li>加入后即可在圈内发帖，也会同步出现在发现流。</li>
            <li>圈主可踢人、禁言、增设管理员；管理员可踢人、下线帖子、限制他人发帖。</li>
            <li>圈主不能退出，只能关闭圈子；关闭后不可再加入。</li>
          </ul>
        </section>

        <section class="panel">
          <div class="panel-head">
            <h3>我加入的圈子</h3>
            <button class="panel-more" type="button" @click="changeScope(true)">只看向这些 →</button>
          </div>
          <SkeletonList v-if="mineLoading" :count="2" />
          <StateBlock
            v-else-if="!myCircles.length"
            inline
            title="还没有加入任何圈子"
            desc="找一个感兴趣的圈子加入，或者自己建一个。"
          />
          <ul v-else class="list-plain mine-list">
            <li v-for="item in myCircles" :key="item.id">
              <button class="mine-row" type="button" @click="openDetail(item.id)">
                <span class="mine-name brand-title">{{ item.name }}</span>
                <span class="nums mine-count fine-print">{{ item.memberCount || 0 }} 人</span>
              </button>
            </li>
          </ul>
        </section>
      </aside>
    </div>
  </AppShell>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '../layouts/AppShell.vue'
import PageHero from '../components/PageHero.vue'
import ImageUploader from '../components/ImageUploader.vue'
import PostAuthor from '../components/PostAuthor.vue'
import PostFeed from '../components/PostFeed.vue'
import StateBlock from '../components/StateBlock.vue'
import SkeletonList from '../components/SkeletonList.vue'
import {
  closeCircle,
  composeCirclePost,
  createCircle,
  fetchCircleDetail,
  fetchCircleList,
  fetchCircleMembers,
  fetchCirclePosts,
  fetchMyCircles,
  joinCircle,
  kickCircleMember,
  leaveCircle,
  muteCircleMember,
  removeCirclePost,
  setCircleMemberRole
} from '../api/circle'
import { useAuthStore } from '../stores/auth'
import { relativeTime } from '../utils/format'

/**
 * 圈子（兴趣小组）。
 *
 * 详情做成**同一路由内的展开态**：点开卡片后主列切换成「圈子信息 + 圈内帖子」，
 * 不额外增加路由与页面。好处是返回列表不丢滚动位置与筛选条件，
 * 也不用为「详情」再复制一份圈子卡片与加入/退出逻辑。
 * `?circle=<id>` 会同步到地址栏，刷新或分享链接仍能直接落到某个圈子。
 */

const PAGE_SIZE = 12

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const circles = ref([])
const myCircles = ref([])
const circlePosts = ref([])
const keyword = ref('')
const keywordDraft = ref('')
const onlyMine = ref(false)
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const loading = ref(true)
const loadingMore = ref(false)
const error = ref('')
const mineLoading = ref(true)

const activeId = ref(null)
const detail = ref(null)
const detailLoading = ref(false)
const detailError = ref('')
const postsLoading = ref(false)
const postsError = ref('')
const opBusy = ref(false)
const actionError = ref('')
const successMsg = ref('')

const creating = ref(false)
const createRef = ref(null)
const submitting = ref(false)
const formError = ref('')
const form = reactive({ name: '', description: '', cover: '' })
const errors = reactive({})
const feedRef = ref(null)
const draft = ref('')
const draftImages = ref([])
const posting = ref(false)
const members = ref([])
const membersLoading = ref(false)
const memberBusyId = ref(null)

const subtitle = computed(() => {
  if (activeId.value) return detail.value ? `圈子：${detail.value.name}` : '正在加载圈子详情'
  if (loading.value) return '正在加载圈子列表'
  if (error.value) return '圈子列表加载失败'
  return `${onlyMine.value ? '我加入的圈子' : '全部圈子'} · 共 ${totalElements.value} 个`
})

const scopeNote = computed(() => (
  onlyMine.value
    ? '含我创建并作为圈主的圈子，按加入时间倒序。'
    : '全部圈子按创建时间倒序，加入过的会标记「已加入」。'
))

const emptyTitle = computed(() => {
  if (keyword.value) return `没有找到名称含「${keyword.value}」的圈子`
  if (onlyMine.value) return '你还没有加入任何圈子'
  return '还没有人创建圈子'
})

const emptyDesc = computed(() => {
  if (keyword.value) return '换个关键词试试，或者自己创建一个。'
  if (onlyMine.value) return '去「全部圈子」看看，找到感兴趣的再加入。'
  return '把同一栋楼、同一个爱好的人聚到一起，圈子是比动态流更聚焦的地方。'
})

const hasMore = computed(() => page.value + 1 < totalPages.value)

/** 后端 CircleResponse 没有下发状态中文名（商品与回收订单都有），这里做最小映射 */
function circleStatusLabel(status) {
  return status === 'CLOSED' ? '已关闭' : '正常'
}

function circleTone(status) {
  return status === 'CLOSED' ? 'tag--cancelled' : 'tag--done'
}

function isOwner(item) {
  return Boolean(item) && (item.ownerId === auth.user?.id || auth.isAdmin)
}

const myRole = computed(() => {
  if (!detail.value) return ''
  return detail.value.myRole || (isOwner(detail.value) ? 'OWNER' : '')
})

const isCircleOwnerRole = computed(() => myRole.value === 'OWNER' || isOwner(detail.value))
const isCircleAdminRole = computed(() => myRole.value === 'ADMIN')
const canPost = computed(() => Boolean(detail.value?.canPost) && detail.value?.status !== 'CLOSED')
const canModeratePosts = computed(() => isCircleOwnerRole.value || isCircleAdminRole.value || auth.isAdmin)
const canAppointAdmin = computed(() => isCircleOwnerRole.value || auth.isAdmin)
const showMembers = computed(() => Boolean(detail.value && (detail.value.joined || auth.isAdmin)))

function roleLabel(role) {
  if (role === 'OWNER') return '圈主'
  if (role === 'ADMIN') return '管理员'
  return '成员'
}

function roleTone(role) {
  if (role === 'OWNER') return 'tag--done'
  if (role === 'ADMIN') return 'tag--open'
  return 'tag--outline'
}

function canRestrictMember(member) {
  if (!member || member.role === 'OWNER' || member.userId === auth.user?.id) return false
  if (isCircleOwnerRole.value || auth.isAdmin) return true
  return isCircleAdminRole.value && member.role === 'MEMBER'
}

function memberActions(member) {
  const actions = []
  if (!canRestrictMember(member) && !(canAppointAdmin.value && member.role !== 'OWNER' && member.userId !== auth.user?.id)) {
    return actions
  }
  if (canAppointAdmin.value && member.role === 'MEMBER') {
    actions.push({ key: 'admin', label: '设为管理员' })
  }
  if (canAppointAdmin.value && member.role === 'ADMIN') {
    actions.push({ key: 'member', label: '取消管理员' })
  }
  if (canRestrictMember(member)) {
    actions.push({ key: member.muted ? 'unmute' : 'mute', label: member.muted ? '解除限制' : '限制发帖' })
    actions.push({ key: 'kick', label: '踢出' })
  }
  return actions
}

function initialOf(name) {
  return String(name || '圈').trim().charAt(0) || '圈'
}

function onCoverError(event) {
  event.target.style.display = 'none'
  event.target.parentElement?.classList.add('is-broken')
}

async function loadList() {
  loading.value = true
  error.value = ''
  try {
    const params = { page: page.value, size: PAGE_SIZE }
    if (keyword.value && !onlyMine.value) params.keyword = keyword.value
    const res = onlyMine.value ? await fetchMyCircles(params) : await fetchCircleList(params)
    const data = res.data || {}
    const content = data.content || []
    circles.value = page.value > 0 ? [...circles.value, ...content] : content
    totalPages.value = data.totalPages ?? 1
    totalElements.value = data.totalElements ?? content.length
  } catch (e) {
    error.value = e.message
    if (page.value === 0) circles.value = []
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

/** 右栏「我加入的圈子」：与主列表分开取，切换筛选时不必重查 */
async function loadMine() {
  mineLoading.value = true
  try {
    const res = await fetchMyCircles({ page: 0, size: 8 })
    myCircles.value = res.data?.content || []
  } catch {
    // 右栏取不到不影响主列表
    myCircles.value = []
  } finally {
    mineLoading.value = false
  }
}

async function reload() {
  page.value = 0
  await Promise.all([loadList(), loadMine()])
}

function applySearch() {
  keyword.value = keywordDraft.value
  page.value = 0
  loadList()
}

function clearKeyword() {
  keywordDraft.value = ''
  keyword.value = ''
  page.value = 0
  loadList()
}

function changeScope(mine) {
  onlyMine.value = mine
  // 「我加入的」不支持关键词，切过去时把关键词清掉，避免切回「全部」时还带着旧条件
  if (mine && keyword.value) {
    keyword.value = ''
    keywordDraft.value = ''
  }
  page.value = 0
  loadList()
}

function loadMore() {
  page.value += 1
  loadingMore.value = true
  loadList()
}

function toggleCreate(open) {
  creating.value = open === true ? true : !creating.value
  if (creating.value) {
    nextTick(() => createRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
  }
}

async function openDetail(id) {
  const numericId = Number(id)
  activeId.value = numericId
  detail.value = null
  detailError.value = ''
  postsError.value = ''
  circlePosts.value = []
  members.value = []
  draft.value = ''
  draftImages.value = []
  detailLoading.value = true
  postsLoading.value = true
  if (String(route.query.circle || '') !== String(numericId)) {
    // 用路由名同步 query：路径层级改过一次，写死路径就会静默走一遍旧路径重定向
    router.replace({ name: 'discover-circle', query: { circle: numericId } })
  }
  try {
    const res = await fetchCircleDetail(numericId)
    detail.value = res.data || null
  } catch (e) {
    detailError.value = e.message
  } finally {
    detailLoading.value = false
  }
  await loadCirclePosts(numericId)
  if (detail.value?.joined || auth.isAdmin) {
    await loadMembers(numericId)
  } else {
    members.value = []
  }
}

async function loadCirclePosts(id) {
  postsLoading.value = true
  postsError.value = ''
  try {
    const res = await fetchCirclePosts(id, { page: 0, size: 20 })
    circlePosts.value = res.data?.content || []
    feedRef.value?.observeCards()
  } catch (e) {
    postsError.value = e.message
    circlePosts.value = []
  } finally {
    postsLoading.value = false
  }
}

async function loadMembers(id) {
  membersLoading.value = true
  try {
    const res = await fetchCircleMembers(id)
    members.value = res.data || []
  } catch {
    members.value = []
  } finally {
    membersLoading.value = false
  }
}

function closeDetail() {
  activeId.value = null
  detail.value = null
  circlePosts.value = []
  members.value = []
  draft.value = ''
  draftImages.value = []
  if (route.query.circle) router.replace({ name: 'discover-circle' })
}

async function onJoin(item) {
  opBusy.value = true
  actionError.value = ''
  try {
    const res = await joinCircle(item.id)
    Object.assign(item, res.data || {})
    if (detail.value?.id === item.id) Object.assign(detail.value, res.data || {})
    // 列表里的成员数与加入标记都要跟着变，直接回填而不是整表重取
    syncListItem(res.data)
    successMsg.value = `已加入「${item.name}」`
    loadMine()
    if (detail.value?.id === item.id) loadMembers(item.id)
  } catch (e) {
    actionError.value = e.message
  } finally {
    opBusy.value = false
  }
}

async function onLeave(item) {
  opBusy.value = true
  actionError.value = ''
  try {
    const res = await leaveCircle(item.id)
    Object.assign(item, res.data || {})
    if (detail.value?.id === item.id) Object.assign(detail.value, res.data || {})
    syncListItem(res.data)
    successMsg.value = `已退出「${item.name}」`
    members.value = []
    loadMine()
  } catch (e) {
    actionError.value = e.message
  } finally {
    opBusy.value = false
  }
}

async function onClose(item) {
  // 关闭后不可再加入，属于不可逆操作，必须二次确认
  if (!window.confirm(`关闭「${item.name}」后不可再加入，确认关闭？`)) return
  opBusy.value = true
  actionError.value = ''
  try {
    const res = await closeCircle(item.id)
    if (detail.value?.id === item.id) Object.assign(detail.value, res.data || {})
    syncListItem(res.data)
    successMsg.value = `「${item.name}」已关闭`
  } catch (e) {
    actionError.value = e.message
  } finally {
    opBusy.value = false
  }
}

function syncListItem(updated) {
  if (!updated?.id) return
  const target = circles.value.find((c) => c.id === updated.id)
  if (target) Object.assign(target, updated)
}

function clearErrors() {
  Object.keys(errors).forEach((k) => { delete errors[k] })
}

/** 与后端 CreateCircleRequest 注解保持一致的前置校验 */
function validate() {
  clearErrors()
  const name = form.name.trim()
  if (!name) errors.name = '圈子名称不能为空'
  else if (name.length > 50) errors.name = '圈子名称最多 50 字'
  if (form.description.length > 200) errors.description = '圈子简介最多 200 字'
  return Object.keys(errors).length === 0
}

async function onCreate() {
  formError.value = ''
  successMsg.value = ''
  if (!validate()) {
    formError.value = '请先修正表单中标红的问题。'
    return
  }
  submitting.value = true
  try {
    const res = await createCircle({
      name: form.name.trim(),
      description: form.description.trim() || null,
      cover: form.cover || null
    })
    form.name = ''
    form.description = ''
    form.cover = ''
    creating.value = false
    successMsg.value = '圈子创建成功，你已经是圈主。'
    await Promise.all([loadList(), loadMine()])
    if (!onlyMine.value && res.data?.id) openDetail(res.data.id)
  } catch (e) {
    // 名称重复等业务错误由后端返回，直接展示而不是前端猜
    formError.value = e.message
  } finally {
    submitting.value = false
  }
}

async function onCompose() {
  if (!detail.value || !draft.value) return
  posting.value = true
  actionError.value = ''
  try {
    await composeCirclePost(detail.value.id, {
      content: draft.value,
      channel: 'DISCOVER',
      kind: 'DAILY',
      images: draftImages.value
    })
    draft.value = ''
    draftImages.value = []
    successMsg.value = '已发布到圈子'
    const res = await fetchCircleDetail(detail.value.id)
    detail.value = res.data || detail.value
    syncListItem(res.data)
    await loadCirclePosts(detail.value.id)
  } catch (e) {
    actionError.value = e.message
  } finally {
    posting.value = false
  }
}

async function runMemberAction(member, key) {
  if (!detail.value) return
  if (key === 'kick' && !window.confirm(`将「${member.nickname || '该成员'}」移出圈子？`)) return
  memberBusyId.value = member.userId
  actionError.value = ''
  try {
    if (key === 'kick') await kickCircleMember(detail.value.id, member.userId)
    else if (key === 'mute') await muteCircleMember(detail.value.id, member.userId, true)
    else if (key === 'unmute') await muteCircleMember(detail.value.id, member.userId, false)
    else if (key === 'admin') await setCircleMemberRole(detail.value.id, member.userId, 'ADMIN')
    else if (key === 'member') await setCircleMemberRole(detail.value.id, member.userId, 'MEMBER')
    const res = await fetchCircleDetail(detail.value.id)
    detail.value = res.data || detail.value
    syncListItem(res.data)
    await loadMembers(detail.value.id)
  } catch (e) {
    actionError.value = e.message
  } finally {
    memberBusyId.value = null
  }
}

async function onOffline(post) {
  if (!detail.value || !post?.id) return
  if (!window.confirm('从圈子下线这条帖子？发现流里仍会保留。')) return
  actionError.value = ''
  try {
    await removeCirclePost(detail.value.id, post.id)
    successMsg.value = '已从圈子下线'
    const res = await fetchCircleDetail(detail.value.id)
    detail.value = res.data || detail.value
    syncListItem(res.data)
    await loadCirclePosts(detail.value.id)
  } catch (e) {
    actionError.value = e.message
  }
}

function onFeedChanged(event) {
  if (event?.type === 'error') {
    actionError.value = event.message
    return
  }
  if (event?.type === 'deleted' && detail.value) loadCirclePosts(detail.value.id)
}

onMounted(async () => {
  await Promise.all([loadList(), loadMine()])
  const fromQuery = Number(route.query.circle || 0)
  if (fromQuery) openDetail(fromQuery)
})

// 浏览器前进/后退或外部链接带 ?circle= 时，展开态要跟着地址栏走
watch(() => route.query.circle, (next) => {
  const id = Number(next || 0)
  if (!id && activeId.value) {
    activeId.value = null
    detail.value = null
    circlePosts.value = []
    members.value = []
    return
  }
  if (id && id !== activeId.value) openDetail(id)
})
</script>

<style scoped>
/* 布局差异：主列 1.6fr + 右侧信息栏 1fr。只写列数、对齐与粘性偏移。 */
.circle-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(280px, 1fr);
  gap: var(--sp-6);
  align-items: start;
}

.circle-main {
  display: grid;
  gap: var(--sp-4);
  min-width: 0;
}

/* 粘在磨砂二级导航之下，而不是贴着视口顶端，否则会被两条 sticky 栏压住 */
.circle-rail {
  display: grid;
  gap: var(--sp-4);
  position: sticky;
  top: calc(var(--nav-stack) + var(--sp-5));
}

/* ---------- 创建 ---------- */
.create-form {
  display: grid;
  gap: var(--sp-4);
}

.field-foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--sp-3);
}

.create-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  flex-wrap: wrap;
  padding-top: var(--sp-2);
  border-top: 1px solid var(--line);
}

/* ---------- 工具栏：外形交给全局 .segmented / .input / .btn ---------- */
.toolbar-panel {
  display: grid;
  gap: var(--sp-4);
}

.search-row {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

.search-row .input { flex: 1; min-width: 220px; }

.toolbar-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  flex-wrap: wrap;
}

/* ---------- 圈子卡片：米白底上的白色工具卡，无阴影、无渐变 ---------- */
.circle-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--sp-4);
  align-items: start;
}

.circle-card {
  background: var(--canvas);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  overflow: hidden;
  transition: border-color 0.18s var(--ease);
}

.circle-card:hover {
  border-color: var(--accent);
}

.circle-card.is-active { border-color: var(--accent); }

.circle-hit {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  gap: var(--sp-4);
  width: 100%;
  padding: var(--sp-5);
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

/* 占位封面用米白底 + 墨色首字；染色块留给状态标签，不抢强调色。
   圆角与卡片一致（--r-lg）：它是卡片里的配图，不该自带第二套圆角档位。 */
.circle-cover,
.detail-cover {
  display: grid;
  place-items: center;
  overflow: hidden;
  border-radius: var(--r-lg);
  background: var(--parchment);
  color: var(--ink-2);
}

.circle-cover {
  width: 84px;
  height: 84px;
}

.circle-cover img,
.detail-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.circle-cover.is-broken,
.detail-cover.is-broken {
  background: var(--parchment);
}

.circle-body {
  display: grid;
  gap: var(--sp-2);
  align-content: start;
  min-width: 0;
}

.circle-title {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

.circle-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-5);
  border-top: 1px solid var(--line);
}

/* ---------- 详情展开态：同一路由内切换主列，不新开页面 ---------- */
.detail { display: grid; gap: var(--sp-4); }

.back { margin-bottom: var(--sp-1); }

.detail-head {
  display: grid;
  grid-template-columns: 108px minmax(0, 1fr);
  gap: var(--sp-5);
}

.detail-cover {
  width: 108px;
  height: 108px;
  border-radius: var(--r-lg);
}

.detail-copy {
  display: grid;
  gap: var(--sp-3);
  min-width: 0;
}

.detail-title {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

/* 简介走 .lead-body（17px 正文），只限制阅读行长 */
.detail-desc { max-width: 68ch; }

.owner-label { color: var(--muted-2); }

.detail-ops {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
}

.feed-note { margin-top: calc(var(--sp-1) * -1); }

.composer {
  display: grid;
  gap: var(--sp-3);
}

.composer-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
}

.muted-note {
  padding: var(--sp-3) var(--sp-4);
  border-radius: var(--r-md);
  background: var(--danger-soft);
  color: var(--danger);
}

.member-panel {
  display: grid;
  gap: var(--sp-3);
}

.member-list {
  display: grid;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.member-row {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex-wrap: wrap;
  padding: var(--sp-3) 0;
  border-top: 1px solid var(--line);
}

.member-row:first-child { border-top: 0; }

.member-tags {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.member-ops {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-left: auto;
}

/* ---------- 右栏 ---------- */
/* 清单走 .lead-body（17px 正文）；放进 .panel--dark 时颜色由全局令牌自动切成 --on-dark-muted */
.tips {
  margin: 0;
  padding-left: 1.1em;
  display: grid;
  gap: var(--sp-3);
}

.mine-list > li { padding: var(--sp-3) 0; }

.mine-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.mine-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mine-row:hover .mine-name { color: var(--accent); }

.mine-count { flex: none; }

/* ---------- 其他 ---------- */
/* 链接型按钮：文字样式交给全局 .link / .caption，这里只留排布 */
.link-btn {
  justify-self: start;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.more-row { display: flex; justify-content: center; }

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

@media (max-width: 1040px) {
  .circle-grid { grid-template-columns: minmax(0, 1fr); }
  .circle-rail { position: static; order: -1; }
}

@media (max-width: 720px) {
  .detail-head { grid-template-columns: minmax(0, 1fr); }
  .circle-hit { grid-template-columns: 64px minmax(0, 1fr); }
  .circle-cover { width: 64px; height: 64px; }
}
</style>
