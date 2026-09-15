// 路由滚动策略 + 「我的 / 个人主页」合并的行为验证（不需要浏览器）。
//
// 背景一：原来的实现是 `scrollBehavior: () => ({ top: 0 })`——**任何**导航都回到顶部。
// 于是「校园页切一个帖子分类」「广场页切内容视角」这类只换 query 的筛选，
// 会把人从列表中间弹回页面顶端，被迫重新往下滚。这就是用户报的那个 bug。
//
// 背景二：`/me`（个人中心）与 `/users/{id}`（个人主页）原本是两张高度重叠的页面，
// 顶栏「我的」与右上角头像落在不同 URL 上。现在规范成同一个页面：访问自己的
// `/users/{id}` 重定向到 `/me`，判断逻辑抽在 guards.js 里，这里直接断言。
//
// 背景三（信息架构改版）：二级导航下沿多了一条子模块 tab 条，「发现」与「我的」
// 因此比其它模块多 56px 的粘性高度。锚点偏移必须按**当前路由所属模块**算出不同值，
// 否则在这些页面上锚点会停在标题上方一截、被 tab 条压住。
//
// scrollBehavior / stickyOffsetFor / ownProfileRedirect 都是纯函数，在 Node 里断言即可；
// 再补静态断言，确保 router/index.js 真的在用它俩，并校验 JS 常量与 CSS 变量同步。
//
// 从 frontend 目录运行：npm run test:scroll

import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { MODTABS_OFFSET, STICKY_OFFSET, scrollBehavior, stickyOffsetFor } from '../src/router/scroll.js'
import { ownProfileRedirect } from '../src/router/guards.js'
import { tabsOf } from '../src/router/modules.js'

const here = path.dirname(fileURLToPath(import.meta.url))
const frontendRoot = path.resolve(here, '..')

const results = []
function check(name, ok, detail = '') {
  results.push({ name, ok: !!ok, detail })
  console.log(`[${ok ? 'PASS' : 'FAIL'}] ${name}${detail ? ` -- ${detail}` : ''}`)
}

/**
 * 造一个最小的 route 对象。
 *
 * meta 必须能传：粘性偏移是按 `meta.module` 查「这个模块有没有子模块 tab 条」的，
 * 不传 meta 就永远只能测到「没有 tab 条」那一半，正是最容易出错的那半测不到。
 */
const loc = (fullPath, meta = {}) => {
  const [beforeHash, hashPart] = fullPath.split('#')
  const [pathname, search = ''] = beforeHash.split('?')
  const query = Object.fromEntries(new URLSearchParams(search))
  return {
    path: pathname,
    fullPath,
    query,
    meta,
    hash: hashPart !== undefined ? `#${hashPart}` : ''
  }
}

// ---------------------------------------------------------------- 同页筛选不复位
// 这是 bug 本身：校园页换分类。
check('校园页换分类（只换 query）不滚动',
  scrollBehavior(loc('/campus?view=posts&kind=RANT'), loc('/campus?view=posts')) === false)

// 别处同样的模式，一并钉住。
check('广场页切筛选（只换 query）不滚动',
  scrollBehavior(loc('/discover?kind=RANT'), loc('/discover')) === false)
check('校园页切到失物招领分区不滚动',
  scrollBehavior(loc('/campus?view=lost'), loc('/campus?view=posts&kind=RANT')) === false)
check('同路径、query 完全相同也不滚动',
  scrollBehavior(loc('/campus?view=posts'), loc('/campus?view=posts')) === false)

// ---------------------------------------------------------------- 真正换页面回到顶部
check('换页面回到顶部',
  JSON.stringify(scrollBehavior(loc('/discover/market'), loc('/discover'))) === JSON.stringify({ top: 0 }))
check('从详情回到列表算换页面，回到顶部',
  JSON.stringify(scrollBehavior(loc('/discover/neighbor'), loc('/discover/neighbor/12')))
  === JSON.stringify({ top: 0 }))

// ---------------------------------------------------------------- 前进/后退优先
const saved = { left: 0, top: 1280 }
check('浏览器前进/后退回到原位置（压过其它规则）',
  scrollBehavior(loc('/discover#post-3'), loc('/discover/market'), saved) === saved)

// ---------------------------------------------------------------- 锚点
// 「发现」有 5 个子模块，页面比别的模块多一条 tab 条
const discoverMeta = { module: 'discover', tab: 'plaza' }
const hashResult = scrollBehavior(loc('/discover#post-42', discoverMeta), loc('/discover/market', discoverMeta))
check('带 #hash 时滚到锚点',
  hashResult && hashResult.el === '#post-42', JSON.stringify(hashResult))
check('同一页内跳到锚点（热度榜的「查看」）也滚到锚点',
  scrollBehavior(loc('/discover#post-7', discoverMeta), loc('/discover', discoverMeta)).el === '#post-7')

// ---------------------------------------------------------------- 锚点偏移与 CSS 对齐
// STICKY_OFFSET / MODTABS_OFFSET 是 JS 常量，--nav-h / --modtabs-h 是 CSS 变量，
// 两者只能手动同步 —— 逐个对一遍。
const css = readFileSync(path.join(frontendRoot, 'src/styles.css'), 'utf8')
const cssVar = (name) => {
  const m = css.match(new RegExp(`--${name}:\\s*(\\d+)px`))
  return m ? Number(m[1]) : null
}
const navH = cssVar('nav-h')
const modtabsH = cssVar('modtabs-h')

check('STICKY_OFFSET 等于 --nav-h（二级导航现在只有子模块条，不再有第二层）',
  navH !== null && STICKY_OFFSET === navH,
  `STICKY_OFFSET=${STICKY_OFFSET} · css=${navH}`)

check('MODTABS_OFFSET 等于 --modtabs-h',
  modtabsH !== null && MODTABS_OFFSET === modtabsH,
  `MODTABS_OFFSET=${MODTABS_OFFSET} · css=${modtabsH}`)

// --subnav-h 已被 --modtabs-h 取代：留着它会让「到底叠了几层」有两个说法
check('styles.css 里不再有 --subnav-h（旧的二级导航高度）',
  cssVar('subnav-h') === null)

// 有子模块条的模块：锚点要给子模块条也留出高度
check('有子模块条的模块，锚点偏移多留一条的高度',
  stickyOffsetFor(loc('/discover#post-1', discoverMeta)) === STICKY_OFFSET + MODTABS_OFFSET,
  `discover → ${stickyOffsetFor(loc('/discover#post-1', discoverMeta))}`)
check('「我的」同样多留一条的高度',
  stickyOffsetFor(loc('/me#x', { module: 'me', tab: 'profile' })) === STICKY_OFFSET + MODTABS_OFFSET)
// 校园只有一个子模块（论坛），但那条子模块条照样渲染 —— 它是页面上唯一的层级提示
check('校园只有一个子模块，但那一条仍然渲染，因此也要留高度',
  stickyOffsetFor(loc('/campus#x', { module: 'campus', tab: 'forum' })) === STICKY_OFFSET + MODTABS_OFFSET)

// 没有子模块的模块不渲染子模块条，偏移不能多加
for (const [mod, label] of [['messages', '消息'], ['admin', '管理台']]) {
  check(`${label}没有子模块条，锚点偏移不加高度（加了会停在标题上方一截）`,
    stickyOffsetFor(loc('/x#y', { module: mod })) === STICKY_OFFSET,
    `${mod} → ${stickyOffsetFor(loc('/x#y', { module: mod }))}`)
}
check('meta 缺失时退回基础偏移（不因缺字段算出 NaN）',
  stickyOffsetFor(loc('/discover#a')) === STICKY_OFFSET
  && stickyOffsetFor(undefined) === STICKY_OFFSET)

// 判定必须与子模块条本身的渲染条件同源：modules.js 说有几个 tab，偏移就按几个算
check('offset 的判定与 modules.js 的 tab 数量同源',
  tabsOf('discover').length > 1 && tabsOf('me').length > 1
  && tabsOf('campus').length === 1
  && tabsOf('messages').length === 0 && tabsOf('admin').length === 0)

// 每个模块（含子模块）都必须有小图标 —— 漏一个的表现是导航里出现一个没有图形的空位
{
  const mods = await import('../src/router/modules.js')
  const missing = []
  for (const m of mods.MODULES) {
    if (!m.icon) missing.push(`模块 ${m.key}`)
    for (const t of m.tabs) if (!t.icon) missing.push(`子模块 ${m.key}/${t.key}`)
  }
  check('每个模块与子模块都配了图标', missing.length === 0, missing.join(', '))
}

// ---------------------------------------------------------------- 路由真的在用这个策略
const routerSource = readFileSync(path.join(frontendRoot, 'src/router/index.js'), 'utf8')
check('router/index.js 从 ./scroll 引入 scrollBehavior',
  /import\s*\{\s*scrollBehavior\s*\}\s*from\s*'\.\/scroll'/.test(routerSource))
check('router/index.js 已不再写死 () => ({ top: 0 })',
  !/scrollBehavior\s*:\s*\(\s*\)\s*=>\s*\(\s*\{\s*top\s*:\s*0\s*\}\s*\)/.test(routerSource))

// ---------------------------------------------------------------- 信息架构与路由表一致
// 子模块的归属写在路由 meta 里，tab 条与锚点偏移都读它。这里断言路由表
// 真的为每个子模块标了 module/tab —— 漏标的表现是「tab 条在该页消失」，
// 属于那种不报错、只是慢慢变得不对的退化。
const routeMetaFor = (name) => {
  const m = routerSource.match(
    new RegExp(`name:\\s*'${name}'[\\s\\S]{0,400}?meta:\\s*\\{([^}]*)\\}`)
  )
  return m ? m[1] : ''
}
for (const [name, moduleKey, tab] of [
  ['discover', 'discover', 'plaza'],
  ['discover-market', 'discover', 'market'],
  ['discover-circle', 'discover', 'circle'],
  ['discover-recycle', 'discover', 'recycle'],
  ['discover-neighbor', 'discover', 'neighbor'],
  ['campus', 'campus', 'forum'],
  ['me', 'me', 'profile'],
  ['me-settings', 'me', 'settings'],
  ['me-ad', 'me', 'ad']
]) {  const meta = routeMetaFor(name)
  check(`路由 ${name} 标了 module=${moduleKey} / tab=${tab}`,
    meta.includes(`module: '${moduleKey}'`) && meta.includes(`tab: '${tab}'`),
    meta.trim() || '未找到 meta')
}
check('校园路由标了 requiresStudent（门禁的依据）',
  routeMetaFor('campus').includes('requiresStudent: true'))
check('认证页不受门禁限制，且显式关掉子模块条',
  !routeMetaFor('campus-verify').includes('requiresStudent')
  && routeMetaFor('campus-verify').includes('hideTabs: true'))
check('关系列表页挂在「个人主页」这个 tab 下（它不是子模块）',
  routeMetaFor('me-following').includes("tab: 'profile'"))
// 「设置」= 应用偏好，「个人主页」= 别人看到我什么样。资料编辑跟着后者走。
check('「设置」指向偏好设置页，不再是资料表单',
  /name:\s*'me-settings'[\s\S]{0,200}?views\/SettingsView\.vue/.test(routerSource))
check('资料编辑挪到「个人主页」tab 下',
  routeMetaFor('me-profile-edit').includes("module: 'me'")
  && routeMetaFor('me-profile-edit').includes("tab: 'profile'")
  && /name:\s*'me-profile-edit'[\s\S]{0,200}?views\/ProfileEditView\.vue/.test(routerSource))
// 旧路径必须逐条留着，否则通知里的链接与收藏夹会在改版后 404
for (const legacy of ['/plaza', '/market', '/circle', '/recycle', '/neighbor', '/aids', '/ad/apply']) {
  check(`旧路径 ${legacy} 仍重定向`, routerSource.includes(`path: '${legacy}'`))
}

// ---------------------------------------------------------------- 「我的」与「个人主页」是同一页
check('自己的 /users/{id} 重定向到 /me',
  JSON.stringify(ownProfileRedirect({ name: 'user-home', params: { id: '7' } }, 7))
  === JSON.stringify({ name: 'me', replace: true }))
check('参数是字符串也认（路由参数本来就是字符串）',
  ownProfileRedirect({ name: 'user-home', params: { id: '7' } }, '7') !== null)
check('别人的主页不重定向',
  ownProfileRedirect({ name: 'user-home', params: { id: '8' } }, 7) === null)
check('未登录（没有我的 id）不重定向',
  ownProfileRedirect({ name: 'user-home', params: { id: '7' } }, undefined) === null)
check('别的路由不受影响',
  ownProfileRedirect({ name: 'discover', params: {} }, 7) === null)
check('router/index.js 的守卫里用上了 ownProfileRedirect',
  /import\s*\{\s*ownProfileRedirect\s*\}\s*from\s*'\.\/guards'/.test(routerSource)
  && /ownProfileRedirect\(to,\s*auth\.user\?\.id\)/.test(routerSource))
check('顶栏头像指向 /me（不再拼 /users/{id}）',
  /const selfPath = '\/me'/.test(readFileSync(path.join(frontendRoot, 'src/layouts/AppShell.vue'), 'utf8')))

const failed = results.filter((r) => !r.ok)
console.log('')
console.log(`# total ${results.length} checks, ${results.length - failed.length} passed, ${failed.length} failed`)
if (failed.length) {
  for (const f of failed) console.log(`  - ${f.name} -- ${f.detail}`)
  process.exit(1)
}
