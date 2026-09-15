<template>
  <AppShell
    :title="pageTitle"
    eyebrow="发现"
    :subtitle="subtitle"
  >
    <template #hero>
      <PageHero
        eyebrow="邻里互助 · 城区"
        title="需要搭把手的事，都写在这里"
        lead="助人为乐 甘于奉献"
        tone="parchment"
        size="display"
      >
        <template #actions>
          <router-link class="btn btn--primary btn--hero" :to="createTo">发布求助</router-link>
          <router-link class="btn btn--secondary btn--hero" to="/campus">去校园互助看看</router-link>
        </template>
      </PageHero>
    </template>

    <template #actions>
      <router-link class="btn btn--primary" :to="createTo">发布求助</router-link>
    </template>

    <div class="toolbar">
      <div class="segmented" role="group" aria-label="按状态筛选">
        <button
          v-for="f in filters"
          :key="f.value"
          type="button"
          :aria-pressed="status === f.value"
          @click="changeStatus(f.value)"
        >
          {{ f.label }}
        </button>
      </div>
      <p v-if="category" class="toolbar-note">
        正在查看分类「{{ category }}」
        <router-link class="clear" to="/discover/neighbor">清除</router-link>
      </p>
    </div>

    <SkeletonList v-if="loading" :count="4" variant="card" />

    <StateBlock v-else-if="error" variant="error" :desc="error">
      <button class="btn btn--ghost btn--sm" type="button" @click="load">重新加载</button>
    </StateBlock>

    <StateBlock
      v-else-if="!items.length"
      :title="emptyTitle"
      :desc="emptyDesc"
    >
      <router-link class="btn btn--primary btn--sm" :to="createTo">发布求助</router-link>
      <button v-if="status" class="btn btn--ghost btn--sm" type="button" @click="changeStatus('')">
        查看全部状态
      </button>
    </StateBlock>

    <div v-else class="aid-grid enter-stagger">
      <AidCard v-for="item in items" :key="item.id" :item="item" />
    </div>

    <!-- 收口瓦片：通栏近黑底把列表「合上」，并给二次行动留一个位置。
         放在 #band 插槽里（.page 之外），所以是真的通栏。 -->
    <template #band>
      <section class="tile tile--dark aid-band">
        <div class="tile-inner aid-band-inner">
          <div class="aid-band-copy">
            <h2 class="tile-title">没找到能接的单？</h2>
            <p class="tile-lead">把你需要的写出来，让邻居先看见你。一句话就够。</p>
          </div>
          <router-link class="btn btn--on-dark btn--hero" :to="createTo">发布求助</router-link>
        </div>
      </section>
    </template>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '../layouts/AppShell.vue'
import PageHero from '../components/PageHero.vue'
import AidCard from '../components/AidCard.vue'
import StateBlock from '../components/StateBlock.vue'
import SkeletonList from '../components/SkeletonList.vue'
import { fetchAidList } from '../api/aid'

const route = useRoute()

const filters = [
  { label: '全部', value: '' },
  { label: '待接单', value: 'OPEN' },
  { label: '进行中', value: 'ACCEPTED' },
  { label: '已完成', value: 'DONE' }
]

const status = ref('')
const items = ref([])
const loading = ref(true)
const error = ref('')

const category = computed(() => String(route.query.category || ''))
const pageTitle = computed(() => (category.value ? `${category.value}互助` : '邻里互助'))
const createTo = computed(() => (
  // 写路径而不是路由名：页面骨架的 SSR 测试用的是一张只有兜底路由的替身路由表，
  // 具名路由在那里解析不出来会直接把整页渲染打挂。
  category.value
    ? { path: '/discover/neighbor/create', query: { category: category.value } }
    : '/discover/neighbor/create'
))

const subtitle = computed(() => {
  if (loading.value) return '正在加载邻里互助信息'
  if (error.value) return '数据加载失败'
  const label = filters.find((f) => f.value === status.value)?.label || '全部'
  return `邻里板块 · ${label} · 共 ${items.length} 条`
})

const emptyTitle = computed(() => (status.value ? '该状态下暂无求助' : '还没有人发布求助'))
const emptyDesc = computed(() => (
  status.value
    ? '换个状态看看，或者发布一条新的求助。'
    : '遇到需要搭把手的事，发出来邻居们就能看到。'
))

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = { page: 0, size: 20, board: 'NEIGHBORHOOD' }
    if (status.value) params.status = status.value
    if (category.value) params.category = category.value
    const res = await fetchAidList(params)
    items.value = res.data?.content || []
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function changeStatus(value) {
  status.value = value
  load()
}

onMounted(load)
watch(() => route.query.category, load)
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  flex-wrap: wrap;
  margin-bottom: var(--sp-6);
}

.toolbar-note {
  font-size: 13px;
  letter-spacing: -0.1px;
  color: var(--muted);
}

.clear {
  margin-left: 6px;
  color: var(--accent);
  font-weight: 600;
}

.clear:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}

/* 工具卡网格：1360px 下 4 列，1068px 3 列，833px 2 列，640px 1 列 */
.aid-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--sp-5);
  align-items: start;
}

/* 收口瓦片的内部排版：左边一句叙事，右边一枚白色胶囊 */
.aid-band-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-8);
  flex-wrap: wrap;
}

.aid-band-copy {
  display: grid;
  gap: var(--sp-3);
  max-width: 46ch;
}

@media (max-width: 734px) {
  .aid-band-inner { gap: var(--sp-6); }
}
</style>
