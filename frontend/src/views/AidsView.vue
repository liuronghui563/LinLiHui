<template>
  <AppShell :title="pageTitle">
    <div class="toolbar">
      <div class="filters">
        <button
          v-for="f in filters"
          :key="f.value"
          type="button"
          :class="{ active: status === f.value }"
          @click="changeStatus(f.value)"
        >
          {{ f.label }}
        </button>
      </div>
      <router-link class="create" :to="createTo">发布求助</router-link>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <div v-else class="cards">
      <article v-for="item in items" :key="item.id" class="card">
        <div class="meta">
          <span>{{ item.category }}</span>
          <span>{{ statusText(item.status) }}</span>
        </div>
        <h3>{{ item.title }}</h3>
        <p>{{ item.content }}</p>
        <StarRating
          :avg="item.ratingAvg || 0"
          :count="item.ratingCount || 0"
          readonly
        />
        <div class="foot">
          <span class="publisher">
            <PostAuthor
              :user-id="item.publisherId"
              :name="item.publisherName"
              :avatar="item.publisherAvatar"
            />
            <span> · {{ item.address }}</span>
          </span>
          <router-link :to="`/aids/${item.id}`">详情</router-link>
        </div>
      </article>
      <p v-if="!items.length" class="empty">暂无数据</p>
    </div>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '../layouts/AppShell.vue'
import StarRating from '../components/StarRating.vue'
import PostAuthor from '../components/PostAuthor.vue'
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
const error = ref('')
const category = computed(() => String(route.query.category || ''))
const pageTitle = computed(() => (category.value ? `${category.value}互助` : '邻里互助'))
const createTo = computed(() => (
  category.value ? { path: '/aids/create', query: { category: category.value } } : '/aids/create'
))

const statusMap = {
  OPEN: '待接单',
  ACCEPTED: '进行中',
  DONE: '已完成',
  CANCELLED: '已取消'
}

function statusText(s) {
  return statusMap[s] || s
}

async function load() {
  error.value = ''
  try {
    const params = { page: 0, size: 20, board: 'NEIGHBORHOOD' }
    if (status.value) params.status = status.value
    if (category.value) params.category = category.value
    const res = await fetchAidList(params)
    items.value = res.data?.content || []
  } catch (e) {
    error.value = e.message
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
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.filters {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.filters button {
  border: 1px solid var(--line);
  background: #fff;
  padding: 8px 14px;
  cursor: pointer;
}

.filters button.active {
  background: var(--bg-deep);
  color: #fff;
  border-color: var(--bg-deep);
}

.create {
  background: var(--accent);
  color: #fff;
  padding: 10px 16px;
  font-weight: 600;
}

.cards {
  display: grid;
  gap: 12px;
}

.card {
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid var(--line);
  padding: 18px;
  border-radius: var(--radius);
  box-shadow: var(--shadow-soft);
}

.meta {
  display: flex;
  gap: 10px;
  color: var(--muted);
  font-size: 13px;
}

h3 {
  margin: 10px 0 8px;
  font-size: 20px;
}

.card > p {
  margin: 0;
  color: var(--muted);
  line-height: 1.6;
}

.foot {
  margin-top: 14px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 14px;
}

.foot a {
  color: var(--accent);
  font-weight: 600;
}

.publisher {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.empty,
.error {
  color: var(--muted);
}

.error {
  color: var(--danger);
}
</style>
