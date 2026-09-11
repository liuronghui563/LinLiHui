<template>
  <AppShell title="首页总览">
    <nav class="home-actions enter-rise" aria-label="快捷入口">
      <router-link class="action ask" to="/aids/create">发布求助</router-link>
      <router-link class="action help" to="/aids">去帮忙</router-link>
      <router-link class="action feed" to="/community">看动态</router-link>
      <router-link class="action plaza" to="/plaza">生活广场</router-link>
    </nav>

    <section class="welcome">
      <div class="welcome-bg" />
      <div class="welcome-photos" aria-hidden="true">
        <img class="enter-photo" style="--delay: .16s" src="https://picsum.photos/id/1018/640/420" alt="" />
        <img class="enter-photo" style="--delay: .3s" src="https://picsum.photos/id/1036/640/420" alt="" />
      </div>
      <div class="welcome-copy enter-rise" style="--delay: .08s">
        <p class="hello">{{ greeting }}，{{ auth.user?.nickname || '邻里' }}</p>
        <h2>邻里之间，总有人愿意帮一把</h2>
        <p class="welcome-text">发布求助、响应邻居，或去广场分享生活。学生专区也能看到校园里正在发生的事。</p>
      </div>
      <router-link class="settings-link enter-rise" style="--delay: .22s" to="/settings">个人信息设置</router-link>
    </section>

    <AdCarousel class="enter-rise" style="--delay: .18s" />
    <StudentZone class="enter-rise" style="--delay: .28s" />
    <HotRank class="hot-wrap enter-rise" style="--delay: .36s" :items="hotItems" :rank-date="hotDate" more-to="/plaza" />

    <section class="stats enter-rise" style="--delay: .42s">
      <article>
        <p>开放求助</p>
        <strong>{{ aidStats.openCount ?? '-' }}</strong>
      </article>
      <article>
        <p>进行中</p>
        <strong>{{ aidStats.acceptedCount ?? '-' }}</strong>
      </article>
      <article>
        <p>已完成</p>
        <strong>{{ aidStats.doneCount ?? '-' }}</strong>
      </article>
      <article>
        <p>邻里动态</p>
        <strong>{{ communityStats.postCount ?? '-' }}</strong>
      </article>
    </section>

    <div class="grid enter-rise" style="--delay: .5s">
      <section class="panel">
        <div class="panel-head">
          <h2>最新求助</h2>
          <router-link to="/aids">全部</router-link>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <ul v-else class="list">
          <li v-for="item in aids" :key="item.id">
            <div>
              <strong>{{ item.title }}</strong>
              <p class="aid-meta">
                {{ item.category }} · {{ statusText(item.status) }} ·
                <PostAuthor
                  :user-id="item.publisherId"
                  :name="item.publisherName"
                  :avatar="item.publisherAvatar"
                />
              </p>
            </div>
            <router-link :to="`/aids/${item.id}`">查看</router-link>
          </li>
          <li v-if="!aids.length" class="empty">暂无求助，去发布第一条吧</li>
        </ul>
      </section>

      <section class="panel">
        <div class="panel-head">
          <h2>邻里动态</h2>
          <router-link to="/community">全部</router-link>
        </div>
        <ul class="list">
          <li v-for="post in posts" :key="post.id" :data-post-id="post.id">
            <div>
              <PostAuthor
                :user-id="post.authorId"
                :name="post.authorName"
                :avatar="post.authorAvatar"
              />
              <p>{{ post.content }}</p>
            </div>
            <span class="side-meta">
              <HeatBadge :value="post.heatScore" />
              <span class="like-count">♥ {{ post.likeCount || 0 }}</span>
            </span>
          </li>
          <li v-if="!posts.length" class="empty">还没有动态</li>
        </ul>
      </section>
    </div>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import AppShell from '../layouts/AppShell.vue'
import AdCarousel from '../components/AdCarousel.vue'
import StudentZone from '../components/StudentZone.vue'
import HotRank from '../components/HotRank.vue'
import PostAuthor from '../components/PostAuthor.vue'
import HeatBadge from '../components/HeatBadge.vue'
import { fetchAidList, fetchAidStats } from '../api/aid'
import { fetchCommunityStats, fetchPlazaHot, fetchPosts } from '../api/community'
import { useAuthStore } from '../stores/auth'
import { useHeatView } from '../composables/useHeatView'

const auth = useAuthStore()
const aidStats = reactive({})
const communityStats = reactive({})
const aids = ref([])
const posts = ref([])
const hotItems = ref([])
const hotDate = ref('')
const error = ref('')
const { observeCards } = useHeatView(posts)

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 11) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const statusMap = {
  OPEN: '待接单',
  ACCEPTED: '进行中',
  DONE: '已完成',
  CANCELLED: '已取消'
}

function statusText(s) {
  return statusMap[s] || s
}

onMounted(async () => {
  try {
    const [aStats, cStats, aidList, postList, hot] = await Promise.all([
      fetchAidStats(),
      fetchCommunityStats(),
      fetchAidList({ page: 0, size: 5, board: 'NEIGHBORHOOD' }),
      fetchPosts({ page: 0, size: 5, channel: 'COMMUNITY' }),
      fetchPlazaHot()
    ])
    Object.assign(aidStats, aStats.data || {})
    Object.assign(communityStats, cStats.data || {})
    aids.value = aidList.data?.content || aidList.data || []
    posts.value = postList.data?.content || postList.data || []
    hotItems.value = hot.data?.items || []
    hotDate.value = hot.data?.rankDate || ''
    observeCards()
  } catch (e) {
    error.value = e.message
  }
})
</script>

<style scoped>
.home-actions {
  position: sticky;
  top: 8px;
  z-index: 35;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin: -8px 0 18px;
  padding: 10px;
  background: rgba(255, 250, 244, 0.94);
  border: 1px solid var(--line);
  border-radius: 18px;
  box-shadow: var(--shadow-soft);
  backdrop-filter: blur(12px);
}

.home-actions .action {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 46px;
  padding: 10px 12px;
  text-align: center;
  background: #fff;
  color: var(--ink);
  font-weight: 700;
  border-radius: 999px;
  border: 1px solid var(--line);
}

.home-actions .action.ask {
  background: #e07a3d;
  color: #fff;
  border-color: #e07a3d;
}

.home-actions .action.help {
  background: #3d8ea6;
  color: #fff;
  border-color: #3d8ea6;
}

.home-actions .action.feed {
  background: #f3c98b;
  color: #1d1a17;
  border-color: #e2b56a;
}

.home-actions .action.plaza {
  background: var(--bg-deep);
  color: #fff;
  border-color: var(--bg-deep);
}

.hot-wrap {
  margin: 18px 0 0;
}

.welcome {
  position: relative;
  overflow: hidden;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 20px;
  margin-bottom: 22px;
  padding: 28px;
  min-height: 220px;
  border-radius: var(--radius);
  color: #f7f1e8;
  box-shadow: var(--shadow);
}

.welcome-bg {
  position: absolute;
  inset: -6%;
  background:
    linear-gradient(135deg, rgba(36, 48, 68, 0.78), rgba(224, 122, 61, 0.42), rgba(61, 142, 166, 0.28)),
    url("https://picsum.photos/id/1015/1600/720") center/cover;
  animation: kenburns 9s ease-out both;
}

.welcome-photos {
  position: absolute;
  right: 28px;
  top: 22px;
  display: grid;
  gap: 10px;
  width: 168px;
  z-index: 1;
}

.welcome-photos img {
  width: 100%;
  height: 72px;
  object-fit: cover;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  box-shadow: var(--shadow-soft);
}

.welcome-photos img:nth-child(2) {
  width: 78%;
  margin-left: auto;
}

.welcome-copy,
.settings-link {
  position: relative;
  z-index: 1;
}

.hello {
  margin: 0;
  letter-spacing: 0.08em;
  opacity: 0.86;
  font-size: 13px;
}

.welcome h2 {
  margin: 8px 0 0;
  font-family: "ZCOOL XiaoWei", serif;
  font-size: clamp(28px, 4vw, 40px);
  font-weight: 400;
}

.welcome-text {
  margin: 10px 0 0;
  max-width: 36ch;
  line-height: 1.7;
  opacity: 0.9;
}

.settings-link {
  padding: 10px 16px;
  background: rgba(255, 255, 255, 0.16);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 999px;
  white-space: nowrap;
  font-weight: 600;
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-top: 18px;
}

.stats article,
.panel {
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow-soft);
}

.stats article {
  padding: 18px 20px;
}

.stats article:nth-child(1) {
  background: rgba(224, 122, 61, 0.12);
}

.stats article:nth-child(2) {
  background: rgba(61, 142, 166, 0.12);
}

.stats article:nth-child(3) {
  background: rgba(212, 160, 23, 0.14);
}

.stats article:nth-child(4) {
  background: rgba(36, 48, 68, 0.08);
}

.stats article:nth-child(1) strong {
  color: #c45c28;
}

.stats article:nth-child(2) strong {
  color: #2d7388;
}

.stats article:nth-child(3) strong {
  color: #b08612;
}

.stats article:nth-child(4) strong {
  color: var(--bg-deep);
}

.stats p {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}

.stats strong {
  display: block;
  margin-top: 10px;
  font-size: 28px;
}

.grid {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 16px;
  margin-top: 22px;
}

.panel {
  padding: 20px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.panel-head h2 {
  margin: 0;
  font-size: 18px;
}

.panel-head a {
  color: var(--accent);
  font-size: 14px;
}

.list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.list li {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 0;
  border-top: 1px solid var(--line);
}

.list li:first-child {
  border-top: none;
}

.list p {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.5;
}

.aid-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.list > li > a {
  color: var(--accent);
  white-space: nowrap;
}

.side-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.like-count {
  color: #e2556f;
  white-space: nowrap;
}

.empty,
.error {
  color: var(--muted);
}

.error {
  color: var(--danger);
}

@media (max-width: 960px) {
  .welcome,
  .stats,
  .grid {
    grid-template-columns: 1fr;
  }

  .home-actions {
    grid-template-columns: 1fr 1fr;
  }

  .welcome {
    flex-direction: column;
    align-items: flex-start;
  }

  .welcome-photos {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .welcome-bg {
    animation: none;
  }
}

@media (max-width: 640px) {
  .stats,
  .home-actions {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
