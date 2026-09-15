import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { scrollBehavior } from './scroll'
import { ownProfileRedirect } from './guards'

/**
 * 路由表。
 *
 * 顶级模块只有四个：发现 / 校园 / 我的 / 消息（管理员多一个「管理台」）。
 * 子模块**不再提升为顶级路径**，而是挂在所属模块的路径下：
 *
 *   /discover               生活广场（原「发现」的内容，改名后仍是模块首页）
 *   /discover/market        集市
 *   /discover/circle        圈子
 *   /discover/recycle       回收
 *   /discover/neighbor      邻里互助（原先自己有顶级模块「邻里」，现已并入发现）
 *   /campus                 论坛（须学生认证）
 *   /campus/verify          学生认证申请（门禁本身，不受门禁限制）
 *   /me                     个人主页（原「我的」的内容）
 *   /me/settings            设置
 *   /me/ad                  广告位（须先开通资质）
 *
 * 每条路由都用 `meta.module` / `meta.tab` 标注自己在信息架构里的位置，
 * 二级导航的 tab 条据此高亮（见 layouts/AppShell.vue 与 router/modules.js）。
 * 用 meta 而不是「按路径前缀猜」：`/me/following` 这类**不是 tab 但属于某个 tab**
 * 的子页，靠前缀猜会把「关系列表」误判成一个不存在的 tab。
 *
 * 层级改动一律配一条旧路径重定向 —— 通知里的链接、收藏夹、以及已经发出去的消息
 * 里的地址不会因为改版变成 404。
 */
const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/RegisterView.vue'),
    meta: { public: true }
  },

  // ---------- 模块一：发现 ----------
  {
    path: '/discover',
    name: 'discover',
    component: () => import('../views/DiscoverView.vue'),
    meta: { requiresAuth: true, module: 'discover', tab: 'plaza' }
  },
  {
    path: '/discover/market',
    name: 'discover-market',
    component: () => import('../views/MarketView.vue'),
    meta: { requiresAuth: true, module: 'discover', tab: 'market' }
  },
  {
    path: '/discover/circle',
    name: 'discover-circle',
    component: () => import('../views/CircleView.vue'),
    meta: { requiresAuth: true, module: 'discover', tab: 'circle' }
  },
  {
    path: '/discover/recycle',
    name: 'discover-recycle',
    component: () => import('../views/RecycleView.vue'),
    meta: { requiresAuth: true, module: 'discover', tab: 'recycle' }
  },
  {
    path: '/discover/neighbor',
    name: 'discover-neighbor',
    component: () => import('../views/AidsView.vue'),
    meta: { requiresAuth: true, module: 'discover', tab: 'neighbor' }
  },
  // 静态段 `create` 必须排在动态段 `:id` 之前，否则 /discover/neighbor/create
  // 会被 :id 吃掉，表现成「点发布求助却打开了 id 为 create 的详情页」
  {
    path: '/discover/neighbor/create',
    name: 'neighbor-create',
    component: () => import('../views/AidCreateView.vue'),
    meta: { requiresAuth: true, module: 'discover', tab: 'neighbor' }
  },
  {
    path: '/discover/neighbor/:id',
    name: 'neighbor-detail',
    component: () => import('../views/AidDetailView.vue'),
    meta: { requiresAuth: true, module: 'discover', tab: 'neighbor' }
  },

  // ---------- 模块二：校园 ----------
  // 论坛是校园模块唯一的子模块，因此它就是模块首页。
  // requiresStudent 写在模块首页上；认证页本身当然不能再被门禁拦住。
  {
    path: '/campus',
    name: 'campus',
    component: () => import('../views/CampusView.vue'),
    meta: { requiresAuth: true, requiresStudent: true, module: 'campus', tab: 'forum' }
  },
  {
    path: '/campus/verify',
    name: 'campus-verify',
    component: () => import('../views/StudentVerifyView.vue'),
    // hideTabs：认证还没过的人点不动「论坛」，摆一条通往论坛的 tab 只会误导
    meta: { requiresAuth: true, module: 'campus', tab: null, hideTabs: true }
  },

  // ---------- 模块三：我的 ----------
  {
    path: '/me',
    name: 'me',
    component: () => import('../views/MeView.vue'),
    meta: { requiresAuth: true, module: 'me', tab: 'profile' }
  },
  {
    path: '/me/settings',
    name: 'me-settings',
    component: () => import('../views/SettingsView.vue'),
    meta: { requiresAuth: true, module: 'me', tab: 'settings' }
  },
  // 资料编辑挂在「个人主页」这个 tab 下，而不是「设置」：
  // 「设置」回答「应用怎么运行」（深色模式、地区语言），
  // 「个人主页」回答「别人看到我什么样」（昵称、头像、封面、简介）。
  // 两者混在一页时，改个深色模式要翻过一整张资料表单才找得到。
  {
    path: '/me/profile/edit',
    name: 'me-profile-edit',
    component: () => import('../views/ProfileEditView.vue'),
    meta: { requiresAuth: true, module: 'me', tab: 'profile' }
  },
  {
    path: '/me/ad',
    name: 'me-ad',
    component: () => import('../views/AdApplyView.vue'),
    meta: { requiresAuth: true, module: 'me', tab: 'ad' }
  },
  // 关系列表不是子模块，而是「个人主页」下的子页：它复用的是同一个 tab
  {
    path: '/me/following',
    name: 'me-following',
    component: () => import('../views/RelationListView.vue'),
    meta: { requiresAuth: true, module: 'me', tab: 'profile', relation: 'following' }
  },
  {
    path: '/me/followers',
    name: 'me-followers',
    component: () => import('../views/RelationListView.vue'),
    meta: { requiresAuth: true, module: 'me', tab: 'profile', relation: 'followers' }
  },
  {
    path: '/me/blocked',
    name: 'me-blocked',
    component: () => import('../views/RelationListView.vue'),
    meta: { requiresAuth: true, module: 'me', tab: 'profile', relation: 'blocked' }
  },

  // ---------- 模块四：消息 ----------
  {
    path: '/messages',
    name: 'messages',
    component: () => import('../views/MessagesView.vue'),
    meta: { requiresAuth: true, module: 'messages', tab: null }
  },

  // ---------- 其他 ----------
  {
    path: '/users/:id',
    name: 'user-home',
    component: () => import('../views/UserHomeView.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/admin',
    name: 'admin',
    component: () => import('../views/AdminView.vue'),
    meta: { requiresAuth: true, roles: ['ADMIN'], module: 'admin', tab: null }
  },

  // ---------- 旧路径兼容 ----------
  // 本轮把集市/圈子/回收/邻里互助从顶级路径移到了 /discover 下，
  // 广告位从 /ad/apply 移到了「我的」，逐条重定向，避免收藏夹与通知里的链接失效。
  { path: '/', redirect: { name: 'discover' } },
  { path: '/community', redirect: { name: 'discover' } },
  { path: '/plaza', redirect: { name: 'discover' } },
  { path: '/market', redirect: { name: 'discover-market' } },
  { path: '/circle', redirect: { name: 'discover-circle' } },
  { path: '/recycle', redirect: { name: 'discover-recycle' } },
  { path: '/neighbor', redirect: { name: 'discover-neighbor' } },
  { path: '/neighbor/create', redirect: { name: 'neighbor-create' } },
  { path: '/neighbor/:id', redirect: (to) => ({ name: 'neighbor-detail', params: { id: to.params.id } }) },
  // 更早的 /aids 系列（在「邻里」还叫 aids 的时期）
  { path: '/aids', redirect: { name: 'discover-neighbor' } },
  { path: '/aids/create', redirect: { name: 'neighbor-create' } },
  { path: '/aids/:id', redirect: (to) => ({ name: 'neighbor-detail', params: { id: to.params.id } }) },
  { path: '/ad/apply', redirect: { name: 'me-ad' } },
  { path: '/settings', redirect: { name: 'me-settings' } },
  { path: '/profile', redirect: { name: 'me' } },
  { path: '/:pathMatch(.*)*', redirect: { name: 'discover' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  // 滚动策略见 ./scroll：只换 query 的「同页筛选」不再把人弹回顶端
  scrollBehavior
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    if (auth.isLogin && (to.name === 'login' || to.name === 'register')) {
      return { name: 'discover' }
    }
    return true
  }
  if (to.meta.requiresAuth && !auth.isLogin) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  /**
   * 学生认证门禁。
   *
   * 判定用的是 `auth.user.student`，它现在**只能由管理员审核通过后写入**
   * （个人资料里的自助勾选已移除），因此这不是一个可以前端绕过的装饰性判断。
   *
   * 注意拿的是本地缓存的 user：硬刷新时 user 由 localStorage 恢复，
   * 不会出现「刷新一下就被踢回认证页」的假阳性。缓存值最多滞后到下一次
   * 资料刷新（30 秒节流），而认证状态只会由未通过变成已通过，
   * 所以滞后的后果是「刚被批准的人要多看一次认证页」——
   * 认证页会用实时状态把这个情况自动纠正过来（见 StudentVerifyView）。
   */
  if (to.meta.requiresStudent && !auth.user?.student) {
    return { name: 'campus-verify' }
  }

  // 自己的主页 = 「我的」：两个入口统一落到 /me，不再有两张几乎一样的页面
  const selfRedirect = ownProfileRedirect(to, auth.user?.id)
  if (selfRedirect) return selfRedirect
  if (to.meta.roles?.length) {
    const role = auth.user?.role
    if (!to.meta.roles.includes(role)) {
      return { name: 'discover' }
    }
  }
  return true
})

export default router
