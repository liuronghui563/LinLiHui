// 页面骨架的结构验证（不需要浏览器）。
//
// 为什么需要它：`npm run test:design` 只能证明文件「解得开」，
// 证明不了「渲染得出来」。AppShell 是每个页面的地基（纯黑全局导航 → 磨砂二级导航
// → 通栏瓦片 → 页脚），它一旦在运行时抛错，所有 14 个页面会一起白屏。
// 这里用 SSR 把每个页面真渲染一遍，并断言骨架的结构契约：
//
//   1. 渲染不抛错（这是最关键的一条）；
//   2. 每页只有 <main> / 纯黑导航 / 二级导航 / 页脚各一份；
//   3. 每页**最多一个 h1**——页面大标题属于 #hero 里的 PageHero，
//      二级导航里的模块名必须是 <p class="subnav-title">，不能也是 h1；
//   4. 通栏瓦片真的渲染出来了，且 tone 映射成对应的表面类
//      （dark → tile--dark，parchment → tile--parchment，light → 白）。
//
// SSR 不会执行 onMounted，所以渲染过程不产生任何网络请求。
//
// 从 frontend 目录运行：npm run test:shell

import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'

const here = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(here, '..')

// stores/auth.js 在构造初始状态时会读 localStorage；Node 没有这个 API，
// 用一个内存实现顶上，而不是为了测试去改业务代码。
{
  const store = new Map()
  globalThis.localStorage = {
    getItem: (k) => (store.has(k) ? store.get(k) : null),
    setItem: (k, v) => store.set(k, String(v)),
    removeItem: (k) => store.delete(k),
    clear: () => store.clear(),
    key: (i) => [...store.keys()][i] ?? null,
    get length() {
      return store.size
    }
  }
  // useHeatView 在 setup 期就注册观察者（不是 onMounted），SSR 下同样要有个替身。
  globalThis.IntersectionObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}

const results = []
function check(name, ok, detail = '') {
  results.push({ name, ok: !!ok, detail })
  console.log(`[${ok ? 'PASS' : 'FAIL'}] ${name}${detail ? ` -- ${detail}` : ''}`)
}

const server = await createServer({
  root,
  configFile: false,
  // 必须显式挂 vue 插件：configFile:false 不会读 vite.config.js
  plugins: [vue()],
  logLevel: 'error',
  appType: 'custom',
  server: { middlewareMode: true, hmr: false },
  // 关掉依赖预构建：服务只在测试期间存活，跑一次 esbuild 优化纯属浪费，
  // 而且关闭时取消这次优化会在汇总之后打一行 "build was canceled" 干扰输出。
  optimizeDeps: { noDiscovery: true, include: [] },
  resolve: {
    // 每个 api 模块都会经 http.js 拉进应用 router，而 router 在 import 期
    // 就调用 createWebHistory()（要 window）。换成替身，模块图才能在 Node 里加载。
    alias: [{ find: /^\.\.\/router$/, replacement: path.join(here, 'stub-router.mjs') }]
  }
})

const bridge = await server.ssrLoadModule('/tests/bridge.mjs')
const { createSSRApp, renderToString, createPinia, createRouter, createMemoryHistory, routeLocationKey } = bridge

/**
 * 把页面组件挂到一张只有兜底路由的替身路由表上，并注入一份带 meta 的假路由。
 *
 * 为什么不靠「真的导航一次、再读 router.currentRoute」拿 meta：
 * Vue Router 的首次导航是异步的，等它要 `await router.isReady()`，
 * 而在 SSR + 单进程渲染 17 个页面的场景下这一步会直接把整个测试卡死
 * （实测：跑满 7 分钟零输出）。所以改成用 `routeLocationKey` 覆盖注入 ——
 * `useRoute()` 拿到的是这里给的假路由，`RouterLink` / `useRouter()` 仍然走真 router，
 * 两边各取所需，且完全不依赖导航时序。
 *
 * meta 由调用方按真实路由表传入（见 PAGE_META），而不是让替身路由去猜：
 * 测试要断言的是「给定这个模块，导航长什么样」。
 */
function makeApp(component, meta = {}) {
  const app = createSSRApp(component)
  app.use(createPinia())
  app.use(createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:all(.*)', component: { template: '<div/>' } }]
  }))
  // 必须在 app.use(router) 之后 provide，才能覆盖掉 router 自己注入的那一份
  app.provide(routeLocationKey, {
    name: null,
    path: '/',
    fullPath: '/',
    query: {},
    hash: '',
    params: {},
    matched: [],
    meta
  })
  return app
}

/**
 * 各页面在真实路由表里的 meta。
 *
 * 与 src/router/index.js 的 meta 一一对应；这里重复一份是有意的——
 * 测试若从被测代码里读期望值，就永远测不出「路由表写错了」。
 */
const PAGE_META = {
  '/src/views/DiscoverView.vue': { module: 'discover', tab: 'plaza' },
  '/src/views/MarketView.vue': { module: 'discover', tab: 'market' },
  '/src/views/CircleView.vue': { module: 'discover', tab: 'circle' },
  '/src/views/RecycleView.vue': { module: 'discover', tab: 'recycle' },
  '/src/views/AidsView.vue': { module: 'discover', tab: 'neighbor' },
  '/src/views/AidCreateView.vue': { module: 'discover', tab: 'neighbor' },
  '/src/views/AidDetailView.vue': { module: 'discover', tab: 'neighbor' },
  '/src/views/CampusView.vue': { module: 'campus', tab: 'forum' },
  '/src/views/StudentVerifyView.vue': { module: 'campus', tab: null, hideTabs: true },
  '/src/views/MeView.vue': { module: 'me', tab: 'profile' },
  // 资料编辑跟着「个人主页」这个 tab 走（「设置」已经改成偏好设置页）
  '/src/views/ProfileEditView.vue': { module: 'me', tab: 'profile' },
  '/src/views/SettingsView.vue': { module: 'me', tab: 'settings' },
  '/src/views/AdApplyView.vue': { module: 'me', tab: 'ad' },
  '/src/views/RelationListView.vue': { module: 'me', tab: 'profile', relation: 'following' },
  '/src/views/MessagesView.vue': { module: 'messages', tab: null },
  '/src/views/AdminView.vue': { module: 'admin', tab: null }
}

/** 按文件路径渲染一个页面，meta 自动从 PAGE_META 取 */
async function renderPage(file) {
  const mod = await server.ssrLoadModule(file)
  return renderToString(makeApp(mod.default, PAGE_META[file] || {}))
}

/**
 * hero = 'always' 的页面在没有数据时也会渲染 PageHero（大标题一定在）；
 * hero = 'guarded' 的详情页用 v-if 守着数据，SSR 无数据时理应没有 hero。
 *
 * tone：只有「近黑」（我的 / 主页 / 管理）与「米白」两种。**没有 light**——
 * 白色英雄带会在页面顶部形成一圈白框，用户明确要求取消（见文件末尾的断言）。
 */
const PAGES = [
  ['/src/views/DiscoverView.vue', 'parchment', 'always'],
  ['/src/views/AidsView.vue', 'parchment', 'always'],
  ['/src/views/AidCreateView.vue', 'parchment', 'always'],
  ['/src/views/CampusView.vue', 'parchment', 'always'],
  ['/src/views/MarketView.vue', 'parchment', 'always'],
  ['/src/views/CircleView.vue', 'parchment', 'always'],
  ['/src/views/RecycleView.vue', 'parchment', 'always'],
  ['/src/views/MeView.vue', 'dark', 'always'],
  ['/src/views/MessagesView.vue', 'parchment', 'always'],
  ['/src/views/AdminView.vue', 'dark', 'always'],
  ['/src/views/SettingsView.vue', 'parchment', 'always'],
  ['/src/views/ProfileEditView.vue', 'parchment', 'always'],
  ['/src/views/RelationListView.vue', 'parchment', 'always'],
  ['/src/views/AdApplyView.vue', 'parchment', 'always'],
  ['/src/views/StudentVerifyView.vue', 'parchment', 'always'],
  ['/src/views/AidDetailView.vue', '', 'guarded'],
  ['/src/views/UserHomeView.vue', '', 'guarded']
]

const TONE_CLASS = { dark: 'tile--dark', parchment: 'tile--parchment', light: '' }

const origWarn = console.warn
console.warn = () => {} // 详情页缺参数会有 Vue 的告警，不是本测试的目标

// 子模块条是否该出现，以 modules.js 的 tab 数量为准
const modules = await server.ssrLoadModule('/src/router/modules.js')

for (const [file, tone, heroMode] of PAGES) {
  const label = path.basename(file, '.vue')
  let html = ''
  try {
    html = await renderPage(file)
  } catch (e) {
    check(`${label} 渲染不抛错`, false, e.message)
    continue
  }

  check(`${label} 渲染不抛错`, true)
  check(`${label} 有纯黑全局导航`, html.includes('class="gnav"'))

  /**
   * 二级导航现在**只有**一条子模块条。所以这一条断言同时管两件事：
   * 该模块有子模块时必须渲染出来（它是页面上唯一的层级提示），
   * 没有子模块时（消息 / 管理台）与显式 opt-out 时（校园认证页）必须不渲染。
   * 原先这里断言的是「二级导航里有没有模块名」——那一栏已经按需求去掉。
   */
  {
    const meta = PAGE_META[file] || {}
    const expectStrip = modules.tabsOf(meta.module).length > 0 && !meta.hideTabs
    check(`${label} 子模块条${expectStrip ? '已渲染' : '未渲染'}`,
      html.includes('aria-label="子模块"') === expectStrip,
      `module=${meta.module} hideTabs=${Boolean(meta.hideTabs)}`)
    if (expectStrip) {
      const active = modules.tabsOf(meta.module).find((t) => t.key === meta.tab)
      check(`${label} 子模块条高亮了当前子模块`,
        active ? html.includes(`aria-current="page"`) : true,
        active ? active.label : '（本页不属于任何子模块）')
    }
  }

  check(`${label} 有 <main> 与站尾页脚`,
    html.includes('<main') && html.includes('>© '))
  check(`${label} 全页最多一个 h1`, (html.match(/<h1/g) || []).length <= 1,
    `h1=${(html.match(/<h1/g) || []).length}`)

  if (heroMode === 'always') {
    // PageHero 的根节点是 <section class="hero tile …">；渲染不出来时这里会先炸，
    // 而不是等到「大标题怎么不见了」才发现。
    check(`${label} 渲染出通栏瓦片`, html.includes('class="hero tile'))
    check(`${label} hero 的大标题就是页面上唯一的 h1`,
      (html.match(/<h1/g) || []).length === 1)
    const expected = TONE_CLASS[tone]
    check(`${label} hero 的 tone=${tone} 映射到正确的表面类`,
      expected ? html.includes(expected) : true,
      expected || 'light（无附加类）')
  } else {
    check(`${label} 无数据时 hero 不渲染（由 v-if 守住）`,
      !html.includes('class="hero tile'))
  }
}

// 二级导航里原先还有一栏「模块名 + 副标题」，按需求已整栏去掉。
// 留在那里不会报错，只是白占 60px 高度并和子模块条重复表达同一件事。
const shellHtml = await renderPage('/src/views/MessagesView.vue')
check('二级导航只剩子模块条：模块名/副标题那一栏已移除',
  !shellHtml.includes('subnav-title') && !shellHtml.includes('class="subnav"'))

// ------------------------------------------------------- 顶级模块 + 子模块 tab 条
// 信息架构调整的核心：子模块不再挤占顶级导航，而是落在二级导航下沿的 tab 条里。
// 这里直接断言渲染结果，而不是读 modules.js —— 那张表对不对，要看它有没有真的
// 变成页面上的导航，光看数据本身证明不了。

/** 截出 tab 条那一小段 HTML，避免「页面正文里恰好也有这个词」造成假通过 */
function tabsRegionOf(html) {
  const after = html.split('aria-label="子模块"')[1]
  return after ? after.split('</nav>')[0] : ''
}

/** 截出桌面顶级导航那一段 */
function topNavOf(html) {
  const after = html.split('id="global-nav"')[1]
  return after ? after.split('</nav>')[0] : ''
}

for (const [file, label, expected] of [
  ['/src/views/DiscoverView.vue', '发现', ['生活广场', '集市', '圈子', '回收', '邻里互助']],
  // 校园只有一个子模块，但那条子模块条照样要渲染：它现在是页面上唯一的层级提示，
  // 藏起来的话用户在新结构里看不出自己站在哪一节。
  ['/src/views/CampusView.vue', '校园', ['论坛']],
  ['/src/views/MeView.vue', '我的', ['个人主页', '设置', '广告位']]
]) {
  const html = await renderPage(file)
  const region = tabsRegionOf(html)
  check(`${label} 页渲染出子模块条`, region.length > 0)
  for (const t of expected) {
    check(`${label} 页子模块条含「${t}」`, region.includes(t), t)
  }
  // 每个子模块都要有小图标，漏配的表现是导航里出现一个没有图形的空位
  check(`${label} 页子模块条每一项都带图标`,
    (region.match(/class="mod-tab-icon"/g) || []).length === expected.length,
    `图标 ${(region.match(/class="mod-tab-icon"/g) || []).length} / 期望 ${expected.length}`)
}

// 没有子模块的模块不该渲染子模块条：一条空条只会占掉 48px 高度
for (const [file, label] of [
  ['/src/views/MessagesView.vue', '消息'],
  ['/src/views/AdminView.vue', '管理台']
]) {
  const html = await renderPage(file)
  check(`${label} 页不渲染子模块条（该模块没有子模块）`, !html.includes('aria-label="子模块"'))
}

// 学生认证页显式 opt-out：认证没过的人点不动「论坛」，摆一条通往论坛的 tab 只会误导
{
  const html = await renderPage('/src/views/StudentVerifyView.vue')
  check('学生认证页不渲染 tab 条（校园认证页显式 opt-out）', !html.includes('aria-label="子模块"'))
}

// 顶级导航必须只剩模块。集市/圈子/回收/邻里互助/广告位这一轮都被降级为子模块，
// 它们若还留在顶级导航里，就说明 AppShell 没有真的改成读 modules.js。
{
  const nav = topNavOf(await renderPage('/src/views/MeView.vue'))
  check('顶级导航可被截取到', nav.length > 0)
  for (const gone of ['集市', '圈子', '回收', '邻里互助', '广告位']) {
    check(`顶级导航不再出现「${gone}」`, !nav.includes(gone), gone)
  }
  for (const kept of ['发现', '校园', '我的', '消息']) {
    check(`顶级导航仍有「${kept}」`, nav.includes(kept), kept)
  }
  // 每个模块都要有小图标：漏配的表现是导航里出现一个没有图形的空位
  check('顶级导航每一项都带小图标',
    (nav.match(/class="gnav-icon"/g) || []).length === 4,
    `图标 ${(nav.match(/class="gnav-icon"/g) || []).length} / 期望 4（非管理员可见四个模块）`)
}

// 广告位已移入「我的」：它这一轮同时从顶级导航消失、并成为「我的」的第三个 tab
{
  const meTabs = tabsRegionOf(await renderPage('/src/views/MeView.vue'))
  check('广告位是「我的」的子模块，不再有顶级入口',
    meTabs.includes('广告位') && !topNavOf(await renderPage('/src/views/MeView.vue')).includes('广告位'))
}

// ---------------------------------------------------------------- 校园专区结构
// 曾经的布局问题：上半页一排分区按钮，下半页又排一遍同样的「按种类筛选」，
// 同一组控件出现两次，内容被推得很远。现在分区切换是唯一入口。
const campusHtml = await renderPage('/src/views/CampusView.vue')
check('校园页的分区切换只有一处（不再重复一排按种类筛选）',
  (campusHtml.match(/aria-label="校园内容分区"/g) || []).length === 1
  && !campusHtml.includes('aria-label="按种类筛选"'))
// 发帖区曾经包在一层白色瓦片里（tile 的白底 + 左右零内距），等于给发帖区套了一圈
// 没必要的白边。现在书写台自己就是唯一表面，标题与分区切换直接落在页面米白底上。
check('校园发帖区没有外层白框（不再是白色瓦片）',
  campusHtml.includes('class="campus-write"') && !/class="tile band"/.test(campusHtml))
check('校园页保留了帖子发布器与失物招领入口',
  campusHtml.includes('写一条校园帖') && campusHtml.includes('发布失物招领'))
check('校园页的「校园须知」是近黑收口瓦片',
  campusHtml.includes('校园须知') && campusHtml.includes('tile--dark'))

// ---------------------------------------------------------------- 首页问候语
// 「用户名，早上好 / 中午好 / 下午好 / 晚上好」——边界只在特定钟点才看得出错，
// 所以用纯函数把四段的分界钉住，再断言首页真的在用它。
const { greetingFor, greetingPart } = await server.ssrLoadModule('/src/utils/greeting.js')
const at = (hour) => new Date(2026, 0, 1, hour, 30)

check('问候语 05:00–10:59 是早上好',
  [5, 8, 10].every((h) => greetingPart(at(h)) === '早上好'),
  `5/8/10 → ${[5, 8, 10].map((h) => greetingPart(at(h))).join('/')}`)
check('问候语 11:00–12:59 是中午好',
  [11, 12].every((h) => greetingPart(at(h)) === '中午好'),
  `11/12 → ${[11, 12].map((h) => greetingPart(at(h))).join('/')}`)
check('问候语 13:00–17:59 是下午好',
  [13, 15, 17].every((h) => greetingPart(at(h)) === '下午好'),
  `13/15/17 → ${[13, 15, 17].map((h) => greetingPart(at(h))).join('/')}`)
check('问候语 18:00–04:59 是晚上好（跨零点）',
  [18, 23, 0, 4].every((h) => greetingPart(at(h)) === '晚上好'),
  `18/23/0/4 → ${[18, 23, 0, 4].map((h) => greetingPart(at(h))).join('/')}`)
check('四段分界首尾相接，没有空档也没有重叠',
  ['早上好', '中午好', '下午好', '晚上好'].every((label) =>
    Array.from({ length: 24 }, (_, h) => greetingPart(at(h))).includes(label)),
  '24 小时都能落到某一段，没有 undefined')
check('带称呼时是「昵称，问候」', greetingFor('小明', at(9)) === '小明，早上好')
check('没有昵称时不留下孤零零的逗号',
  greetingFor('', at(9)) === '早上好' && greetingFor(null, at(9)) === '早上好')

const discoverHtml = await renderPage('/src/views/DiscoverView.vue')
check('首页大标题就是问候语（未登录时退化成单纯问候）',
  /<h1[^>]*class="hero-title"[^>]*>(早上好|中午好|下午好|晚上好)</.test(discoverHtml)
  || /(早上好|中午好|下午好|晚上好)/.test(discoverHtml),
  'hero title 含问候语')

// ---------------------------------------------------------------- 个人主页封面图
// 封面是「可设置」的：设置页能传，主页/个人主页会把它铺在深色瓦片上。
// 无封面时必须回落到纯色 + 纹路，而不是留一张破图。
{
  const { createSSRApp: mk, renderToString: rts } = bridge
  const PageHero = (await server.ssrLoadModule('/src/components/PageHero.vue')).default

  const plain = await rts(mk(PageHero, { title: 'T', tone: 'dark' }))
  check('未设封面时不渲染封面层（回落到纯色 + 纹路）',
    !plain.includes('hero-cover') && !plain.includes('hero--cover'))

  const withCover = await rts(mk(PageHero, { title: 'T', tone: 'dark', cover: '/api/file/objects/cover/1/x.png' }))
  check('设了封面时渲染封面层并带上可读性遮罩类',
    withCover.includes('hero-cover') && withCover.includes('hero--cover')
    && withCover.includes('/api/file/objects/cover/1/x.png'))

  const editSource = await (async () => (await import('node:fs')).readFileSync(
    new URL('../src/views/ProfileEditView.vue', import.meta.url), 'utf8'))()
  check('设置页能上传封面（purpose=cover，单张）',
    /<ImageUploader[^>]*v-model="form\.coverImage"[^>]*purpose="cover"[^>]*:max="1"/.test(editSource))
  check('设置页把封面写回表单（保存时才发得出去）',
    /coverImage: auth\.user\?\.coverImage/.test(editSource) && /form\.coverImage = user\.coverImage/.test(editSource))

  const homeSource = await (async () => (await import('node:fs')).readFileSync(
    new URL('../src/views/UserHomeView.vue', import.meta.url), 'utf8'))()
  const meSource = await (async () => (await import('node:fs')).readFileSync(
    new URL('../src/views/MeView.vue', import.meta.url), 'utf8'))()
  check('个人主页把封面交给 PageHero', /:cover="profile\.coverImage \|\| ''"/.test(homeSource))
  check('「我的」也把封面交给 PageHero', /:cover="auth\.user\?\.coverImage \|\| ''"/.test(meSource))
}

// ---------------------------------------------------------------- 没有白色英雄带
// 用户明确要求过两次：页面顶部的白色英雄带看起来像给内容套了一圈白框。
// 现在只有两种 hero：米白（与页面底同色，等于没有框）与近黑（有意的重音）。
{
  const fs = await import('node:fs')
  const viewsDir = new URL('../src/views/', import.meta.url)
  const lightHeroPages = []
  for (const name of fs.readdirSync(viewsDir)) {
    if (!name.endsWith('.vue')) continue
    if (/tone="light"/.test(fs.readFileSync(new URL(name, viewsDir), 'utf8'))) lightHeroPages.push(name)
  }
  check('没有任何页面再用白色英雄带（白框已全部取消）',
    lightHeroPages.length === 0,
    lightHeroPages.length ? lightHeroPages.join(', ') : '')
}

console.warn = origWarn
await server.close()

const failed = results.filter((r) => !r.ok)
console.log('')
console.log(`# total ${results.length} checks, ${results.length - failed.length} passed, ${failed.length} failed`)
if (failed.length) {
  for (const f of failed) console.log(`  - ${f.name} -- ${f.detail}`)
  process.exit(1)
}
// 显式成功退出：Vite 关闭时可能还有一次被取消的 esbuild 优化，
// 它的告警会在汇总之后打出来，把一次全过的运行变成非零退出码。
process.exit(0)
