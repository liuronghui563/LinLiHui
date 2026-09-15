<template>
  <div class="shell" :class="{ 'shell--tabs': showTabs }">
    <!-- 通栏近黑导航：磨砂近黑，避免纯黑一条硬边。 -->
    <header class="gnav">
      <div class="gnav-inner">
        <router-link class="brand" to="/discover" aria-label="邻里汇首页">
          <span class="brand-mark" aria-hidden="true">邻</span>
          <span class="brand-word">邻里汇</span>
        </router-link>

        <button
          class="menu-toggle"
          type="button"
          :aria-expanded="menuOpen"
          aria-controls="global-nav"
          @click="menuOpen = !menuOpen"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path v-if="!menuOpen" d="M4 7h16M4 12h16M4 17h16" />
            <path v-else d="M6 6l12 12M18 6 6 18" />
          </svg>
          <span>{{ menuOpen ? '关闭' : '菜单' }}</span>
        </button>

        <!-- 桌面：顶级模块 + 小图标。图标只用 currentColor 描边，
             因此悬停/选中换文字色时它跟着变，不会出现「字变了图形没变」。 -->
        <nav id="global-nav" class="gnav-links" aria-label="主导航">
          <router-link
            v-for="item in navItems"
            :key="item.to"
            class="gnav-link"
            :to="item.to"
          >
            <span class="gnav-icon" aria-hidden="true" v-html="item.icon" />
            {{ item.label }}
            <span v-if="item.badge" class="nav-badge">{{ item.badge > 99 ? '99+' : item.badge }}</span>
          </router-link>
        </nav>

        <div class="gnav-end">
          <router-link class="gnav-me" :to="selfPath">
            <UserAvatar :user="auth.user" size="sm" :show-status="false" />
            <span class="gnav-me-name">{{ auth.user?.nickname || '邻里用户' }}</span>
          </router-link>
          <button class="gnav-quiet" type="button" @click="onLogout">退出</button>
        </div>
      </div>

      <!-- 移动端抽屉：图标铺满两级导航（窄屏靠图形更快扫读） -->
      <transition name="tray">
        <div v-if="menuOpen" class="tray">
          <div class="tray-inner">
            <div class="tray-group">
              <p class="tray-label">模块</p>
              <router-link
                v-for="item in navItems"
                :key="item.to"
                class="tray-link"
                :to="item.to"
              >
                <span class="tray-icon" aria-hidden="true" v-html="item.icon" />
                <span>{{ item.label }}</span>
                <span v-if="item.badge" class="nav-badge">{{ item.badge > 99 ? '99+' : item.badge }}</span>
              </router-link>
            </div>

            <!-- 当前模块的子模块。抽屉里也必须能到达它们：
                 抽屉在窄屏上取代了桌面导航，而 tab 条虽然在二级导航里仍然可见，
                 但把同一组入口在这里再列一遍，比让人横向滑动去找要省事。 -->
            <div v-if="showTabs" class="tray-group">
              <p class="tray-label">{{ currentModuleLabel }}</p>
              <router-link
                v-for="tab in moduleTabs"
                :key="tab.key"
                class="tray-link tray-link--sub"
                :class="{ 'is-on': tab.key === activeTab }"
                :to="tab.to"
              >
                <span class="tray-icon" aria-hidden="true" v-html="tab.icon" />
                <span>{{ tab.label }}</span>
              </router-link>
            </div>

            <div class="tray-foot">
              <router-link class="tray-link" :to="selfPath">
                <UserAvatar :user="auth.user" size="xs" :show-status="false" />
                <span>{{ auth.user?.nickname || '邻里用户' }}</span>
              </router-link>
              <button class="tray-link" type="button" @click="onLogout">退出登录</button>
            </div>
          </div>
        </div>
      </transition>
    </header>

    <!-- 二级导航：**只有子模块 tab 条这一样东西**。
         原先这里还有一栏「模块名 + 副标题」，写的是「你在大模块的哪一节」——
         而这句话 tab 条本身用高亮就说清楚了，再重复一遍只是白占 60px 高度。

         页面主操作（#actions 插槽）也从这一栏移到了内容区顶部：
         粘性栏的高度必须是个定值，锚点偏移与页面里的 sticky 侧栏都按它算，
         而「有操作按钮 / 没有」是逐页变化的，放进粘性栏会让高度忽高忽低。 -->
    <div v-if="showTabs" class="modtabs-bar">
      <div class="modtabs-inner">
        <ModuleTabs :tabs="moduleTabs" :active="activeTab" />
      </div>
    </div>

    <main class="main">
      <!-- 通栏瓦片（首页英雄区等）。瓦片在容器之外，才能通栏到屏幕边缘。 -->
      <slot name="hero" />

      <div class="page">
        <!-- 页面说明 + 主操作同处一行。没有说明时操作自动靠右，不会留下空位。 -->
        <div v-if="subtitle || $slots.actions" class="page-head">
          <p v-if="subtitle" class="page-lead">{{ subtitle }}</p>
          <div v-if="$slots.actions" class="page-actions">
            <slot name="actions" />
          </div>
        </div>
        <slot />
      </div>

      <!-- 收口瓦片：内容之后、页脚之前的通栏段落（近黑叙事 / 二次行动）。
           同样在 .page 之外，所以是真的通栏，而不是「内缩的巨卡」。 -->
      <slot name="band" />
    </main>

    <SiteFooter />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useNotifyStore } from '../stores/notify'
import { moduleByKey, tabsOf, visibleModules } from '../router/modules'
import ModuleTabs from '../components/ModuleTabs.vue'
import UserAvatar from '../components/UserAvatar.vue'
import SiteFooter from '../components/SiteFooter.vue'

/**
 * 页面骨架。
 *
 * 两层导航：
 *   1. 近黑磨砂全局导航 —— 顶级模块 + 小图标；
 *   2. 磨砂子模块条 —— **只有** tab 条，没有模块名/副标题（见下方模板注释）。
 *
 * `subtitle` 是页面说明，渲染在内容区顶部（不是粘性栏里）。
 *
 * `title` / `eyebrow` 仍然**保留声明**，但已经不渲染了。这是一次有意的
 * 「先断渲染、后清接口」：这两个 prop 有 16 个页面在传，直接删掉声明会让它们
 * 从 props 变成 fallthrough attribute —— Vue 会把未声明的属性原样渲染到根元素上，
 * 于是每个页面凭空多出 `title="生活广场"` 这种原生属性（浏览器还会拿它当悬停提示）。
 * 整批清理属于独立的一次改动，不该混在这次结构调整里。
 */
defineProps({
  title: { type: String, default: '' },
  eyebrow: { type: String, default: '' },
  subtitle: { type: String, default: '' }
})

const auth = useAuthStore()
const notify = useNotifyStore()
const router = useRouter()
const route = useRoute()

/**
 * 未读消息数由 notify store 统一持有，这里只负责**触发刷新**与展示。
 * 刷新频率由 store 的最短间隔兜住，因此调用点写得随意一些也不会变成高频请求。
 */
const unreadCount = computed(() => notify.unreadCount)
const menuOpen = ref(false)

/**
 * 顶级导航：发现 / 校园 / 我的 / 消息（管理员多一个「管理台」）。
 *
 * 集市、圈子、回收、邻里互助不再是顶级入口——它们是「发现」的子模块，
 * 由二级导航的 tab 条承担（见 components/ModuleTabs.vue）。
 * 结构与图标都来自 router/modules.js，导航、tab 条、路由 meta 读的是同一份数据。
 */
const navItems = computed(() =>
  visibleModules(auth.isAdmin).map((m) => ({
    to: m.to,
    label: m.label,
    icon: m.icon,
    // 未读数只挂在「消息」上，别的模块没有角标
    badge: m.key === 'messages' ? unreadCount.value : 0
  }))
)

/** 当前路由所属模块的子模块 tab */
const moduleTabs = computed(() => tabsOf(route.meta?.module))

/** 当前高亮的子模块 key。认证页这类「属于模块但不属于任何 tab」的页面为 null */
const activeTab = computed(() => route.meta?.tab ?? null)

/**
 * 是否渲染子模块条。
 *
 * 两个条件：该模块有子模块，且当前页没有显式 opt-out。
 * 校园的认证页就用了 opt-out —— 认证没过的人点不动「论坛」，
 * 在那里摆一条通往论坛的 tab 只会误导。
 *
 * 只有一个子模块（校园的「论坛」）时**照样渲染**：这一栏现在只由 tab 条构成，
 * 它是页面上唯一的层级提示；藏起来的话，用户在新结构里看不出自己站在哪一节。
 */
const showTabs = computed(() => moduleTabs.value.length > 0 && !route.meta?.hideTabs)

/** 当前模块名，给移动端抽屉里的子模块分组做标题 */
const currentModuleLabel = computed(
  () => moduleByKey(route.meta?.module)?.label || ''
)

/**
 * 顶栏头像的去处。
 *
 * 「我的」（导航项）与「个人主页」现在是同一个页面，所以这里写死 /me，
 * 不再拼 `/users/{id}`——两个入口落到不同 URL 上，会让人以为进了两个页面。
 * 访问自己的 `/users/{id}` 由路由守卫重定向到 /me（见 src/router/guards.js）。
 */
const selfPath = '/me'

onMounted(() => {
  if (auth.isLogin) {
    // 带最短间隔的后台刷新，替代每次导航都无条件打一次 /auth/me
    auth.refreshProfileInBackground()
    notify.refresh()
  }
  document.addEventListener('visibilitychange', onVisibilityChange)
})

/**
 * 路由变化时刷新角标：从消息页返回后角标应立即消失。
 * 频率控制不在这里做，而是由 store 的最短间隔统一兜住。
 */
watch(() => route.fullPath, () => {
  menuOpen.value = false
  notify.refresh()
})

/**
 * 从后台标签页切回时补一次。比定时轮询划算：真正需要及时更新的
 * 恰恰是「用户回来了」这一刻，而切换回前台本身受最短间隔约束。
 */
function onVisibilityChange() {
  if (document.visibilityState === 'visible') {
    notify.refresh()
  }
}

async function onLogout() {
  await auth.logout()
  // 不清会在下一个账号登录时先闪一下上一个人的未读数
  notify.reset()
  router.replace('/login')
}

onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<style scoped>
.shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* 有子模块条的模块，粘性栏实际叠得更高。
   在 .shell 上重声明 --nav-stack，而不是让每个页面各自算：
   页面里的 sticky 侧栏与 scroll-margin-top 读的都是 var(--nav-stack)，
   在这里改一次，所有消费方自动跟上，不会出现「改造完发现侧栏被压在子模块条下面」。
   （scroll.js 里的 hash 锚点偏移是 JS 常量，那边由 npm run test:scroll 守着。） */
.shell--tabs {
  --nav-stack: var(--nav-stack-tabs);
}

/* ---------- 全局导航：近黑磨砂，不再用纯黑硬切 ---------- */
.gnav {
  position: sticky;
  top: 0;
  z-index: 40;
  background-color: var(--nav-glass);
  backdrop-filter: saturate(180%) blur(22px);
  -webkit-backdrop-filter: saturate(180%) blur(22px);
  color: var(--on-dark);
}

.gnav-inner {
  width: min(var(--container-wide), 100% - var(--gutter));
  margin: 0 auto;
  height: var(--nav-h);
  /* 品牌与右侧账号区各占一端，模块组吃下中间的全部剩余宽度。
     模块用 space-evenly 在中间均匀分布——不是挤成一小团挂在 Logo 后面；
     由于两侧区块宽度接近，整组仍然是视觉居中的。 */
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: var(--sp-6);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
  justify-self: start;
  -webkit-tap-highlight-color: transparent;
  transition: transform var(--press-out), opacity var(--press-out);
}

.brand:active {
  transform: scale(0.97);
  opacity: 0.82;
  transition: transform var(--press-in), opacity var(--press-in);
}

/* 圆角方印，不是实心圆：圆贴在近黑顶栏上会像一枚 App 图标。 */
.brand-mark {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: var(--r-sm);
  background: var(--accent);
  color: var(--on-dark);
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 600;
  line-height: 1;
  padding-bottom: 1px;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.18);
  transition: transform var(--press-out);
}

/* 中文词标要字距，不要跟拉丁标题一样收紧。 */
.brand-word {
  font-family: var(--font-display);
  font-size: 21px;
  font-weight: 600;
  letter-spacing: 0.12em;
  line-height: 1;
  color: var(--on-dark);
}

.menu-toggle {
  display: none;
  align-items: center;
  gap: 6px;
  margin-left: auto;
  border: 0;
  padding: 0;
  background: transparent;
  color: rgba(255, 255, 255, 0.86);
  font-size: 16px;
  letter-spacing: -0.224px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  transition: transform var(--press-out), opacity var(--press-out), color 0.2s var(--ease);
}

.menu-toggle svg {
  width: 24px;
  height: 24px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.7;
  stroke-linecap: round;
}

.menu-toggle:active {
  transform: scale(0.96);
  opacity: 0.72;
  transition: transform var(--press-in), opacity var(--press-in);
}

.gnav-links {
  display: flex;
  align-items: center;
  justify-content: space-evenly;
  gap: var(--sp-2);
  justify-self: stretch;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
}

.gnav-links::-webkit-scrollbar { display: none; }

/* 分组之间的发丝线分隔符已随「顶级导航只剩模块」一并移除：
   现在这一行只有 4-5 个模块，不再需要把「逛一逛 / 找人帮忙 / 关于我」切开。 */
.gnav-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: var(--nav-h);
  padding: 0 4px;
  font-size: 16px;
  font-weight: 400;
  letter-spacing: -0.224px;
  color: rgba(255, 255, 255, 0.64);
  white-space: nowrap;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
  transition: color 0.22s var(--ease), transform var(--press-out), opacity var(--press-out);
}

.gnav-link:active {
  transform: scale(0.97);
  opacity: 0.72;
  transition: color var(--press-in), transform var(--press-in), opacity var(--press-in);
}

/* 顶级模块的小图标。18px 比文字略小一点：图标在这里是辅助识别，
   不是主体——放大到与文字同高会让这条安静的文字链变成一排按钮。 */
.gnav-icon {
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  flex: none;
  opacity: 0.7;
  transition: opacity 0.22s var(--ease);
}

.gnav-icon :deep(svg) {
  width: 18px;
  height: 18px;
}

.gnav-link.router-link-active {
  color: #fff;
}

.gnav-link.router-link-active .gnav-icon { opacity: 1; }

@media (hover: hover) and (pointer: fine) {
  .gnav-link:hover { color: rgba(255, 255, 255, 0.92); }
  .gnav-link:hover .gnav-icon { opacity: 0.92; }
  .gnav-link.router-link-active:hover { color: #fff; }
  .gnav-me:hover { color: #fff; }
  .gnav-quiet:hover { color: #fff; }
}

.nav-badge {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  display: grid;
  place-items: center;
  border-radius: var(--r-pill);
  background: var(--accent);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.gnav-end {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  flex: none;
  justify-self: end;
}

.gnav-me {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  color: rgba(255, 255, 255, 0.78);
  font-size: 16px;
  letter-spacing: -0.224px;
  -webkit-tap-highlight-color: transparent;
  transition: color 0.22s var(--ease), transform var(--press-out), opacity var(--press-out);
}

.gnav-me:active {
  transform: scale(0.97);
  opacity: 0.72;
  transition: color var(--press-in), transform var(--press-in), opacity var(--press-in);
}

.gnav-me-name {
  max-width: 9em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gnav-me :deep(img) {
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.22);
}

/* 工具按钮：安静的矩形，按下才显出一层浅底，避免一块实心黑贴在纯黑顶栏上 */
.gnav-quiet {
  border: 0;
  background: transparent;
  color: rgba(255, 255, 255, 0.72);
  border-radius: var(--r-sm);
  padding: 8px 15px;
  font-size: 14px;
  letter-spacing: -0.224px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  transition: background-color 0.22s var(--ease), color 0.22s var(--ease),
    transform var(--press-out);
}

.gnav-quiet:active {
  transform: scale(0.96);
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
  transition: background-color var(--press-in), color var(--press-in),
    transform var(--press-in);
}

/* ---------- 移动端抽屉 ---------- */
.tray {
  position: absolute;
  top: var(--nav-h);
  left: 0;
  right: 0;
  background-color: var(--nav-bg);
  border-top: 1px solid var(--line-on-dark);
  max-height: calc(100vh - var(--nav-h));
  overflow: auto;
}

.tray-inner {
  width: min(var(--container-wide), 100% - var(--gutter));
  margin: 0 auto;
  padding: var(--sp-6) 0 var(--sp-7);
  display: grid;
  gap: var(--sp-6);
}

.tray-label {
  font-size: 12px;
  letter-spacing: 0.06em;
  color: rgba(255, 255, 255, 0.48);
  margin-bottom: var(--sp-2);
}

.tray-group { display: grid; }

.tray-link {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  min-height: 44px;
  border: 0;
  padding: 0 8px;
  margin: 0 -8px;
  border-radius: var(--r-md);
  background: transparent;
  color: rgba(255, 255, 255, 0.88);
  font-size: 17px;
  letter-spacing: -0.374px;
  text-align: left;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  transition: color 0.22s var(--ease), transform var(--press-out),
    background-color 0.22s var(--ease);
}

.tray-link.router-link-active { color: #fff; }
.tray-link:active {
  transform: scale(0.98);
  background: rgba(255, 255, 255, 0.08);
  transition: color var(--press-in), transform var(--press-in),
    background-color var(--press-in);
}

/* 子模块项：缩进到与顶级模块的图标文字对齐的位置，且不再配图标。
   32px = 图标宽 20px + gap 12px。 */
.tray-link--sub {
  padding-left: 32px;
  font-size: 16px;
  color: rgba(255, 255, 255, 0.72);
}

.tray-link--sub.is-on { color: #fff; }

.tray-icon {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  flex: none;
  opacity: 0.7;
}

.tray-icon :deep(svg) { width: 20px; height: 20px; }

.tray-foot {
  border-top: 1px solid var(--line-on-dark);
  padding-top: var(--sp-4);
  display: grid;
}

.tray-enter-active,
.tray-leave-active {
  transition: opacity 0.28s var(--ease), transform 0.28s var(--ease);
}
.tray-enter-from,
.tray-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* 窗口被拉宽后，抽屉必须让位给桌面导航（否则会出现两套导航同时可见） */
@media (min-width: 834px) {
  .tray { display: none; }
}

/* ---------- 二级导航：只有子模块条 ---------- */
/* 磨砂玻璃，粘在全局导航正下方。整条的高度就是 tab 的高度（--modtabs-h），
   没有第二行，也没有「模块名 + 副标题」那一栏。 */
.modtabs-bar {
  position: sticky;
  top: var(--nav-h);
  z-index: 30;
  /* 与页面底同色的磨砂：不要底边线。顶栏 / tab / 页面靠色差衔接，
     再画一条发丝会把三层切成三块硬板。 */
  background: var(--surface-glass);
  backdrop-filter: saturate(180%) blur(20px);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
}

/* 与 .page 同宽，tab 文字才能和下面的正文左对齐 */
.modtabs-inner {
  width: min(var(--container), 100% - var(--gutter));
  margin: 0 auto;
}

/* ---------- 内容 ---------- */
.main {
  flex: 1;
  /* 每次换页淡入一次。只动 opacity：transform 会让 .main 成为后代
     fixed/sticky 的包含块，页面里的吸顶侧栏会直接失效。 */
  animation: page-in 0.3s var(--ease);
}

/* tab 条下面的英雄区少留一点顶空，三层才不会各占一块孤岛 */
.shell--tabs :deep(.hero) {
  padding-top: var(--sp-7);
  padding-bottom: var(--sp-6);
}

.page {
  width: min(var(--container), 100% - var(--gutter));
  margin: 0 auto;
  /* 原来上下 48 / 80px 在内容不多的页面上会显得空，收到 24 / 48px */
  padding: var(--sp-6) 0 var(--sp-8);
}

/* 页面说明 + 主操作。主操作原本挂在粘性栏右侧，现在移到内容区顶部：
   粘性栏的高度必须是定值（锚点偏移与页面里的 sticky 侧栏都按它算），
   而「这页有没有操作按钮」是逐页变化的。 */
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--sp-4);
  flex-wrap: wrap;
  margin-bottom: var(--sp-5);
}

.page-actions {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  flex-wrap: wrap;
  /* 没有说明文字时靠右，不会顶到左边 */
  margin-left: auto;
}

.page-lead {
  margin: 0;
  max-width: 46ch;
  font-size: 17px;
  line-height: 1.47;
  letter-spacing: -0.374px;
  color: var(--muted);
}

@media (max-width: 1068px) {
  .gnav-links { gap: 18px; }
  .gnav-end { gap: var(--sp-3); }
  .gnav-link { font-size: 13px; }
  /* 收起昵称，右侧只留头像 + 退出：否则窄桌面下右侧列被撑宽，
     中间那组模块就不再是视口居中的了 */
  .gnav-me-name { display: none; }
  .nav-badge { min-width: 16px; height: 16px; font-size: 10px; }
}

/* 833px 以下：全局导航收起为「品牌 + 菜单」，抽屉接管导航。
   栅格在这里必须换回 flex——三列里的两列被隐藏后，剩下的元素会停在错误的列上。 */
@media (max-width: 833px) {
  .gnav-inner { display: flex; }

  .menu-toggle { display: inline-flex; }

  .gnav-links { display: none; }

  .gnav-end { display: none; }

  .modtabs-inner {
    width: min(100% - var(--gutter), var(--container));
  }

  .page {
    width: min(100% - var(--gutter), var(--container));
    padding: var(--sp-6) 0 var(--sp-8);
  }
}
</style>
