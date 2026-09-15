<template>
  <AppShell
    title="关系"
    eyebrow="我的"
    :subtitle="subtitle"
  >
    <!-- 通栏白瓦片：模块大标题 + 分段筛选（三个列表共用一个页面） -->
    <template #hero>
      <PageHero
        tone="parchment"
        align="left"
        eyebrow="我的 · 关系"
        :title="title"
        :lead="heroLead"
      >
        <template #actions>
          <div class="rel-tabs segmented" role="tablist">
            <button type="button" :aria-pressed="mode === 'following'" @click="go('following')">我关注的人</button>
            <button type="button" :aria-pressed="mode === 'followers'" @click="go('followers')">关注我的人</button>
            <button type="button" :aria-pressed="mode === 'blocked'" @click="go('blocked')">黑名单</button>
          </div>
        </template>
      </PageHero>
    </template>

    <SkeletonList v-if="loading" :count="3" />

    <StateBlock v-else-if="error" variant="error" :desc="error">
      <button class="btn btn--ghost btn--sm" type="button" @click="load">重新加载</button>
    </StateBlock>

    <StateBlock v-else-if="!items.length" :title="emptyTitle" :desc="emptyDesc">
      <router-link class="btn btn--ghost btn--sm" to="/discover">去发现逛逛</router-link>
    </StateBlock>

    <!-- 一张白卡 + 发丝线行，而不是每行一张卡片 -->
    <ul v-else class="rel-list list-plain panel">
      <li v-for="item in items" :key="item.id" class="rel-item">
        <router-link class="rel-hit" :to="`/users/${item.id}`">
          <UserAvatar :user="item" size="sm" :show-status="false" />
          <span class="rel-text">
            <strong>{{ item.nickname || `用户 ${item.id}` }}</strong>
            <small class="caption">
              {{ item.student && item.school ? item.school : '邻里用户' }}
              <template v-if="item.ratingCount"> · 评分 {{ item.ratingAvg }}</template>
            </small>
          </span>
        </router-link>

        <FollowButton
          :user-id="item.id"
          :initial-relation="relations[item.id] || impliedRelation"
          @changed="onRelationChanged(item, $event)"
        />
      </li>
    </ul>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '../layouts/AppShell.vue'
import PageHero from '../components/PageHero.vue'
import UserAvatar from '../components/UserAvatar.vue'
import FollowButton from '../components/FollowButton.vue'
import StateBlock from '../components/StateBlock.vue'
import SkeletonList from '../components/SkeletonList.vue'
import { fetchBlocked, fetchFollowers, fetchFollowing, fetchRelations } from '../api/relation'

/**
 * 关注 / 粉丝 / 黑名单三合一列表页。
 * 三个列表结构完全一致，用路由 meta 区分数据源，避免三份重复页面。
 */
const route = useRoute()
const router = useRouter()

const items = ref([])
/**
 * 本页的关系状态，key 是 userId。
 *
 * 必须批量取：单条关系接口要跑 6 条 SQL，50 行逐行取就是 51 次 HTTP、约 300 条查询，
 * 而这还叠加上每个请求各自的鉴权查询。改成一次 `?ids=` 之后，
 * 无论一页多少行都是 1 次请求、固定 6 条 SQL。
 */
const relations = ref({})
const loading = ref(true)
const error = ref('')

const mode = computed(() => String(route.meta.relation || 'following'))
const title = computed(() => ({
  following: '我关注的人',
  followers: '关注我的人',
  blocked: '黑名单'
}[mode.value] || '关系列表'))

const subtitle = computed(() => (loading.value ? '正在加载' : `共 ${items.value.length} 人`))

/** 纯展示：瓦片引文随三个列表切换，不参与任何请求与判断。
    只说**真实会发生的事**：关注会通知对方、拉黑是双向屏蔽。
    信息流目前只做「屏蔽过滤」，不做「关注加权」——不要在这里承诺排序变化。 */
const heroLead = computed(() => ({
  following: '你主动关注的人。关注后，对方会收到一条站内通知，你也能在这里随时找到他们。',
  followers: '留意着你的人。有人关注你时，你会收到一条站内通知。',
  blocked: '被你拉黑的人，双方内容互不可见。'
}[mode.value] || ''))

const emptyTitle = computed(() => ({
  following: '还没有关注任何人',
  followers: '还没有人关注你',
  blocked: '黑名单是空的'
}[mode.value]))

const emptyDesc = computed(() => ({
  following: '打开某个人的主页，点「关注」，对方会收到一条通知，你也能在这里找到 TA。',
  followers: '多发几条动态，邻居就会找上你。',
  blocked: '被拉黑的人，其内容不会出现在你的信息流里，你的内容对方也看不到。'
}[mode.value]))

function go(target) {
  router.push({ name: `me-${target}` })
}

/**
 * 由「这是哪个列表」直接推出的关系位。
 *
 * 能推的就不问后端：
 *   - 出现在「我关注的人」里 ⇒ 我一定关注了他；
 *   - 出现在「黑名单」里     ⇒ 我一定拉黑了他；
 *   - 出现在「关注我的人」里 ⇒ 他一定关注了我。
 *
 * 这同时也是**批量接口失败时的兜底**，而且是必要的兜底：
 * 若失败后把 initialRelation 传成 null，FollowButton 会退化成逐行各自请求，
 * 刚好把这次优化抵消掉（一页 50 行 = 50 次请求）。
 */
const impliedRelation = computed(() => ({
  following: mode.value === 'following',
  followedBy: mode.value === 'followers',
  blocked: mode.value === 'blocked',
  blockedBy: false
}))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const fetcher = { following: fetchFollowing, followers: fetchFollowers, blocked: fetchBlocked }[mode.value]
    const res = await fetcher({ page: 0, size: 50 })
    items.value = res.data?.content || res.data || []
    relations.value = {}
    if (items.value.length) {
      // 整页一次取回，替代此前每行各问一次（6 条 SQL/行）
      try {
        const rel = await fetchRelations(items.value.map((i) => i.id))
        relations.value = rel.data || {}
      } catch {
        // 取不到就只用上面推导出来的那几位，宁可少显示状态，也不退回逐行请求
        relations.value = {}
      }
    }
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

/** 拉黑/取关后该用户应从当前列表移除，否则列表与操作结果对不上 */
function onRelationChanged(item, relation) {
  const shouldRemove =
    (mode.value === 'following' && !relation.following) ||
    (mode.value === 'blocked' && !relation.blocked)
  if (shouldRemove) {
    items.value = items.value.filter((i) => i.id !== item.id)
  }
}

onMounted(load)
watch(mode, load)
</script>

<style scoped>
/* 白卡：.list-plain 自带 padding:0，这里把 .panel 的 24px 内距还回来 */
.rel-list {
  padding: var(--sp-6);
}

.rel-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  flex-wrap: wrap;
}

.rel-hit {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  min-width: 0;
  min-height: 44px;
  flex: 1;
}

.rel-text {
  display: grid;
  gap: 1px;
  min-width: 0;
}

/* 17px 沿用正文字号，只把体例抬到 600 */
.rel-text strong {
  font-weight: 600;
}

@media (max-width: 640px) {
  .rel-item {
    align-items: flex-start;
  }
}
</style>
