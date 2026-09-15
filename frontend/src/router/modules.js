/**
 * 信息架构的唯一事实来源。
 *
 * 调整前的结构是**扁平**的：顶级导航里 11 个平级链接混在一起
 * （逛一逛[发现/集市/圈子/回收/校园] · 找人帮忙[邻里互助] · 关于我[我的/消息/广告位]），
 * 「发现」和它自己的子功能在视觉上没有任何层级差别，校园与我的同理。
 *
 * 现在收敛为四个顶级模块，子模块不再挤占顶级导航，而是落在二级导航的 tab 条里：
 *
 *   发现 ── 生活广场 / 集市 / 圈子 / 回收 / 邻里互助
 *   校园 ── 论坛（进入前须通过学生认证）
 *   我的 ── 个人主页 / 设置 / 广告位（使用前须开通资质）
 *   消息 ── 无子模块
 *
 * 为什么把这张表抽成独立文件，而不是分别写在 router 与 AppShell 里：
 * 顶级导航、子模块 tab、路由 meta、路由守卫的鉴权判断**读的是同一份数据**。
 * 分散在三处时，加一个子模块要改三个地方，漏掉任何一处就会出现
 * 「导航里有这个入口、点进去 404」或者反过来「页面能打开但没有入口」。
 *
 * 图标也定义在这里。它跟模块是一对一的——「每个模块都要有一个小图标」这件事，
 * 只有在图标与模块写在同一行时才不会漏：以前图标散在 AppShell 的一个局部对象里，
 * 加一个模块时很容易只加导航项、忘了补图标，表现是导航里出现一个没有图形的空位。
 */

/** 线性图标：统一 24×24 视口、1.7 描边、currentColor，纯描边不带填充 */
const s = 'fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"'

const ICONS = {
  // ---- 顶级模块 ----
  discover: `<svg viewBox="0 0 24 24" ${s}><circle cx="12" cy="12" r="8.5"/><path d="m15.5 8.5-2 5-5 2 2-5Z"/></svg>`,
  campus: `<svg viewBox="0 0 24 24" ${s}><path d="M3 9.5 12 5l9 4.5-9 4.5Z"/><path d="M7 11.8V17c0 1.4 2.2 2.5 5 2.5s5-1.1 5-2.5v-5.2"/></svg>`,
  me: `<svg viewBox="0 0 24 24" ${s}><circle cx="12" cy="8.5" r="3.6"/><path d="M5 20c0-3.4 3.1-5.6 7-5.6s7 2.2 7 5.6"/></svg>`,
  messages: `<svg viewBox="0 0 24 24" ${s}><path d="M20 12a7.5 7.5 0 0 1-11 6.6L4 20l1.4-4.2A7.5 7.5 0 1 1 20 12Z"/><path d="M9 11.5h6M9 14.5h4"/></svg>`,
  admin: `<svg viewBox="0 0 24 24" ${s}><path d="M12 3 4.5 6.2v5.3c0 4.3 3.1 8.1 7.5 9.5 4.4-1.4 7.5-5.2 7.5-9.5V6.2Z"/><path d="M9.5 12.2 11.3 14l3.4-3.6"/></svg>`,

  // ---- 发现 ----
  plaza: `<svg viewBox="0 0 24 24" ${s}><path d="M3.5 20.5h17"/><path d="M5.5 20.5V9.5L12 5l6.5 4.5v11"/><path d="M9.8 20.5v-5.2h4.4v5.2"/></svg>`,
  market: `<svg viewBox="0 0 24 24" ${s}><path d="M6 3 3.6 7.4V19a1.6 1.6 0 0 0 1.6 1.6h13.6A1.6 1.6 0 0 0 20.4 19V7.4L18 3Z"/><path d="M3.6 7.4h16.8"/><path d="M15.4 11.4a3.4 3.4 0 0 1-6.8 0"/></svg>`,
  circle: `<svg viewBox="0 0 24 24" ${s}><circle cx="9.5" cy="8" r="3.4"/><path d="M3.5 19.5v-1.2a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v1.2"/><path d="M16.2 5.1a3.4 3.4 0 0 1 0 6.4"/><path d="M17.6 14.6a4 4 0 0 1 2.9 3.7v1.2"/></svg>`,
  recycle: `<svg viewBox="0 0 24 24" ${s}><path d="M12 3.5a8.5 8.5 0 1 1-6.3 2.9"/><path d="M5.7 2.9v3.9h3.9"/></svg>`,
  neighbor: `<svg viewBox="0 0 24 24" ${s}><path d="M20.5 12.5 12 21l-8.5-8.5a4.95 4.95 0 0 1 7-7l1.5 1.5 1.5-1.5a4.95 4.95 0 0 1 7 7Z"/></svg>`,

  // ---- 校园 ----
  forum: `<svg viewBox="0 0 24 24" ${s}><path d="M4 5.5h16v10H9.5L5 19v-3.5H4Z"/><path d="M8 9h8M8 12h5"/></svg>`,

  // ---- 我的 ----
  // 与「我的」的头像图标刻意不同：两者会同时出现在同一屏（顶级导航 + tab 条），
  // 用同一个图形会让人分不清点的是哪一层。
  profile: `<svg viewBox="0 0 24 24" ${s}><rect x="3.5" y="4.5" width="17" height="15" rx="2.2"/><circle cx="12" cy="10.3" r="2.5"/><path d="M8 16.6c0-1.9 1.8-3.1 4-3.1s4 1.2 4 3.1"/></svg>`,
  settings: `<svg viewBox="0 0 24 24" ${s}><circle cx="12" cy="12" r="3.2"/><path d="M12 3v2.2M12 18.8V21M4.2 7.5l1.9 1.1M17.9 15.4l1.9 1.1M4.2 16.5l1.9-1.1M17.9 8.6l1.9-1.1"/></svg>`,
  ad: `<svg viewBox="0 0 24 24" ${s}><path d="M4 10.5v3a1.5 1.5 0 0 0 1.5 1.5H8l6 4V6.5l-6 4H5.5A1.5 1.5 0 0 0 4 12Z"/><path d="M17 9.5a4 4 0 0 1 0 5"/></svg>`
}

/** 顶级模块顺序即导航顺序 */
export const MODULES = [
  {
    key: 'discover',
    label: '发现',
    to: '/discover',
    icon: ICONS.discover,
    tabs: [
      { key: 'plaza', label: '生活广场', to: '/discover', icon: ICONS.plaza },
      { key: 'market', label: '集市', to: '/discover/market', icon: ICONS.market },
      { key: 'circle', label: '圈子', to: '/discover/circle', icon: ICONS.circle },
      { key: 'recycle', label: '回收', to: '/discover/recycle', icon: ICONS.recycle },
      { key: 'neighbor', label: '邻里互助', to: '/discover/neighbor', icon: ICONS.neighbor }
    ]
  },
  {
    key: 'campus',
    label: '校园',
    to: '/campus',
    icon: ICONS.campus,
    /**
     * 整个校园模块需要学生认证。
     *
     * 门禁挂在**模块**上而不是「论坛」这一个 tab 上：要求里写的是
     * 「校园模块须开通学生认证」，将来校园下再加子模块时不必再逐个补标记。
     * 判定依据是 `auth.user.student`，而它现在**只能由管理员审核认证后写入**
     * （个人资料里的自助勾选已在同一轮改动中移除），所以这个前端判断背后
     * 是一个真实存在的服务端事实。
     */
    requiresStudent: true,
    tabs: [{ key: 'forum', label: '论坛', to: '/campus', icon: ICONS.forum }]
  },
  {
    key: 'me',
    label: '我的',
    to: '/me',
    icon: ICONS.me,
    tabs: [
      { key: 'profile', label: '个人主页', to: '/me', icon: ICONS.profile },
      { key: 'settings', label: '设置', to: '/me/settings', icon: ICONS.settings },
      { key: 'ad', label: '广告位', to: '/me/ad', icon: ICONS.ad }
    ]
  },
  {
    key: 'messages',
    label: '消息',
    to: '/messages',
    icon: ICONS.messages,
    tabs: []
  },
  {
    key: 'admin',
    label: '管理台',
    to: '/admin',
    icon: ICONS.admin,
    adminOnly: true,
    tabs: []
  }
]

/** 按 key 取模块，找不到返回 null（路由里写了不存在的 module 时不该抛错） */
export function moduleByKey(key) {
  return MODULES.find((m) => m.key === key) || null
}

/**
 * 当前用户可见的顶级模块。
 *
 * 管理台只在 `role === 'ADMIN'` 时出现。过滤放在这里而不是各调用点，
 * 是为了让导航与「模块是否存在」的判断始终一致。
 */
export function visibleModules(isAdmin) {
  return MODULES.filter((m) => !m.adminOnly || isAdmin)
}

/**
 * 某个模块的子模块 tab。
 *
 * 只要有子模块就返回——二级导航现在**只**由这一条 tab 条构成（模块名与副标题
 * 已经去掉），它是页面上唯一的层级提示。哪怕模块只有一个子模块（校园的「论坛」），
 * 也要把名字摆出来，否则用户在新结构里看不出自己站在哪一节。
 */
export function tabsOf(key) {
  const mod = moduleByKey(key)
  return mod ? mod.tabs : []
}
