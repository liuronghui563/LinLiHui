<template>
  <AppShell
    title="消息"
    eyebrow="通知"
    :subtitle="subtitle"
  >
    <!-- 通栏英雄瓦片：白色表面，语气安静——这一页的重点在下面的列表 -->
    <template #hero>
      <PageHero
        eyebrow="通知"
        title="邻里之间的回音"
        lead="邻居的回应、评论与关注。"
        tone="parchment"
        size="display"
        align="left"
      />
    </template>

    <template #actions>
      <button
        v-if="unread > 0"
        class="btn btn--ghost btn--sm"
        type="button"
        :disabled="busy"
        @click="onReadAll"
      >
        全部标为已读
      </button>
    </template>

    <div class="msg-bar">
      <div class="segmented" role="tablist">
        <button
          type="button"
          role="tab"
          :aria-pressed="onlyUnread === false"
          @click="setFilter(false)"
        >
          全部
        </button>
        <button
          type="button"
          role="tab"
          :aria-pressed="onlyUnread === true"
          @click="setFilter(true)"
        >
          未读{{ unread ? ` (${unread})` : '' }}
        </button>
      </div>
    </div>

    <SkeletonList v-if="loading" :count="4" />

    <StateBlock
      v-else-if="error"
      variant="error"
      :desc="error"
    >
      <button class="btn btn--ghost btn--sm" type="button" @click="load">重新加载</button>
    </StateBlock>

    <StateBlock
      v-else-if="!items.length"
      :title="onlyUnread ? '没有未读消息' : '还没有收到消息'"
      desc="有人接你的求助、评论你的动态或关注你时，消息会出现在这里。"
    >
      <router-link class="btn btn--primary btn--sm" to="/discover">去发现看看</router-link>
    </StateBlock>

    <!-- 白卡 + 发丝线：整块读起来像一张纸，未读只在行内点一个小蓝点 -->
    <ul v-else class="msg-list panel list-plain">
      <li
        v-for="item in items"
        :key="item.id"
        class="msg-item"
        :class="{ 'is-unread': !item.read }"
      >
        <router-link class="msg-hit" :to="item.link || '/discover'" @click="onOpen(item)">
          <span class="msg-icon" :class="`type-${item.type}`" aria-hidden="true" v-html="iconOf(item.type)" />
          <span class="msg-body">
            <span class="msg-top">
              <strong class="msg-title">{{ item.title }}</strong>
              <span class="msg-time fine-print">{{ relativeTime(item.createdAt) }}</span>
            </span>
            <span v-if="item.content" class="msg-content caption">{{ item.content }}</span>
            <span class="msg-type fine-print">{{ item.typeLabel }}</span>
          </span>
        </router-link>
        <button type="button" class="msg-del btn btn--quiet btn--sm" title="删除这条消息" @click="onDelete(item)">删除</button>
      </li>
    </ul>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AppShell from '../layouts/AppShell.vue'
import PageHero from '../components/PageHero.vue'
import StateBlock from '../components/StateBlock.vue'
import SkeletonList from '../components/SkeletonList.vue'
import {
  deleteNotification,
  fetchNotifications,
  markAllNotificationsRead,
  markNotificationRead
} from '../api/notify'
import { useNotifyStore } from '../stores/notify'
import { relativeTime } from '../utils/format'

const notify = useNotifyStore()

const items = ref([])
/**
 * 未读数直接用 store 的值，不再各自请求一次 `/notify/unread-count`。
 *
 * 此前这里是 `Promise.all([列表, 未读数])`：打开消息页时 AppShell 的路由监听
 * 已经发了一次未读数请求，这里再发一次，同一个数字被查了两遍库。
 * 现在角标与页面标题读的是同一个数字，天然一致。
 */
const unread = computed(() => notify.unreadCount)
const onlyUnread = ref(false)
const loading = ref(true)
const busy = ref(false)
const error = ref('')

const subtitle = computed(() => {
  if (loading.value) return '正在加载消息'
  if (unread.value) return `有 ${unread.value} 条未读`
  return items.value.length ? `共 ${items.value.length} 条` : '还没有收到消息'
})

const stroke = 'fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"'
const ICONS = {
  AID: `<svg viewBox="0 0 24 24" ${stroke}><path d="M20.5 12.5 12 21l-8.5-8.5a4.95 4.95 0 0 1 7-7l1.5 1.5 1.5-1.5a4.95 4.95 0 0 1 7 7Z"/></svg>`,
  POST: `<svg viewBox="0 0 24 24" ${stroke}><rect x="3" y="4.5" width="18" height="15" rx="2.5"/><path d="M7 9h10M7 12.5h10M7 16h6"/></svg>`,
  USER: `<svg viewBox="0 0 24 24" ${stroke}><circle cx="12" cy="8.5" r="3.6"/><path d="M5 20c0-3.4 3.1-5.6 7-5.6s7 2.2 7 5.6"/></svg>`,
  SYSTEM: `<svg viewBox="0 0 24 24" ${stroke}><circle cx="12" cy="12" r="8.5"/><path d="M12 8v4.5M12 16h.01"/></svg>`
}

function iconOf(type = '') {
  if (type.startsWith('AID')) return ICONS.AID
  if (type.startsWith('POST')) return ICONS.POST
  if (type === 'FOLLOWED') return ICONS.USER
  return ICONS.SYSTEM
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const list = await fetchNotifications({ page: 0, size: 30, unreadOnly: onlyUnread.value })
    items.value = list.data?.content || list.data || []
    if (onlyUnread.value) {
      // 只看未读时，列表的 totalElements 就是未读总数——顺手写回 store，
      // 这样这次筛选不需要额外发一次 COUNT 请求，数字还是精确的。
      notify.setCount(list.data?.totalElements ?? items.value.length)
    }
  } catch (e) {
    // 通知服务未部署时给出可读提示，而不是抛原始网络错误
    error.value = '消息服务暂时不可用，请稍后再试。'
    items.value = []
  } finally {
    loading.value = false
  }
}

function setFilter(value) {
  onlyUnread.value = value
  load()
}

async function onOpen(item) {
  if (item.read) return
  try {
    await markNotificationRead(item.id)
    item.read = true
    // 就地扣减，不再为一次点击重新查库
    notify.decrease()
  } catch {
    // 标记失败不阻塞跳转
  }
}

async function onReadAll() {
  busy.value = true
  try {
    await markAllNotificationsRead()
    items.value.forEach((i) => { i.read = true })
    notify.clear()
  } finally {
    busy.value = false
  }
}

async function onDelete(item) {
  try {
    await deleteNotification(item.id)
    items.value = items.value.filter((i) => i.id !== item.id)
    if (!item.read) notify.decrease()
  } catch {
    // 忽略
  }
}

onMounted(() => {
  load()
  // 直接深链进消息页时 store 可能还是空的。refresh 自带最短间隔与并发去重，
  // 因此这次调用与 AppShell 挂载时的那次最多只会有一次真正打后端。
  notify.refresh()
})
</script>

<style scoped>
/* 只写布局差异：颜色 / 字号 / 圆角 / 按钮外观一律来自全局类与 var(--*) */

.msg-bar {
  margin-bottom: var(--sp-5);
}

/* .panel 给白卡与 hairline，.list-plain 给行间发丝线；这里只把内距调回一张纸的呼吸感 */
.msg-list {
  padding: var(--sp-2) var(--sp-6);
}

.msg-item {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  transition: background-color 0.16s var(--ease);
}

.msg-item:hover {
  background: var(--pearl);
}

.msg-hit {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: flex-start;
  gap: var(--sp-3);
}

/* 图标退回中性底：蓝色只留给「未读」这一个信号 */
.msg-icon {
  width: 32px;
  height: 32px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--pearl);
  color: var(--muted-2);
}

.msg-icon :deep(svg) {
  width: 16px;
  height: 16px;
}

.msg-body {
  display: grid;
  gap: 3px;
  min-width: 0;
  flex: 1;
}

.msg-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--sp-3);
}

/* 标题走正文体例；未读用一个小蓝点 + 600 字重，不整行染色 */
.msg-title {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  font-weight: 400;
}

.msg-item.is-unread .msg-title {
  font-weight: 600;
}

.msg-item.is-unread .msg-title::before {
  content: "";
  width: 6px;
  height: 6px;
  flex: none;
  border-radius: 50%;
  background: var(--accent);
}

.msg-content {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.msg-del {
  flex: none;
}

@media (max-width: 640px) {
  .msg-list {
    padding: var(--sp-2) var(--sp-4);
  }
}
</style>
