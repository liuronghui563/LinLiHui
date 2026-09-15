/**
 * 路由滚动策略。
 *
 * 抽成独立模块有两个原因：它是纯函数（可以直接断言，不需要浏览器），
 * 而它本身又特别容易写错——原来的实现是 `scrollBehavior: () => ({ top: 0 })`，
 * 意思是**任何**导航都回到顶部，包括只换了 query 的那些。
 *
 * 后果是「筛选/切分区被弹回顶端」这个 bug：校园页切一个帖子分类、
 * 发现页切内容视角，都会走 router.replace({ query })，
 * 于是列表刚看完的位置被清掉，人被迫重新往下滚。
 *
 * 规则：
 *   1. 浏览器前进/后退 → 回到原来的位置（savedPosition 优先）；
 *   2. 带 #hash 的跳转 → 滚到锚点，并留出 sticky 栏的高度（top 偏移）；
 *   3. **同一路由只换 query**（筛选、分区、分页）→ 保持当前位置，不滚动；
 *   4. 真正换页面 → 回到顶部。
 */
// 必须写扩展名：这个模块会被 npm run test:scroll 用**纯 Node** 直接加载，
// Node 的 ESM 解析器不做扩展名补全（浏览器侧由 Vite 负责，所以别处可以省）。
import { tabsOf } from './modules.js'

/**
 * 全局导航的高度（styles.css 的 `--nav-h`）。
 *
 * 这是**没有子模块条**时的全部粘性高度：二级导航现在只由子模块条构成，
 * 消息 / 管理台这类没有子模块的模块只剩全局导航这一层。
 *
 * 必须与 styles.css 里的变量保持一致；那是 CSS，这里是 JS，两者无法互相引用，
 * 所以 `npm run test:scroll` 会把这两个常量与 CSS 变量对一遍，
 * 改了一处忘了另一处会直接失败。
 */
export const STICKY_OFFSET = 72

/**
 * 子模块条的高度（styles.css 的 `--modtabs-h`）。
 *
 * 「发现」「校园」「我的」都有子模块，因此这些页面叠起来更高。
 * `npm run test:scroll` 同样会校验这个常量与 CSS 变量一致。
 */
export const MODTABS_OFFSET = 56

/**
 * 目标路由上层叠的 sticky 栏总高。
 *
 * 用 `meta.module` 查出该模块是否有子模块 tab 条，而不是按路径前缀猜：
 * `/me/following` 这种「属于某个 tab 但不是 tab」的子页同样会渲染 tab 条，
 * 前缀猜法在这里会漏掉 44px。
 */
export function stickyOffsetFor(route) {
  return tabsOf(route?.meta?.module).length > 0
    ? STICKY_OFFSET + MODTABS_OFFSET
    : STICKY_OFFSET
}

export function scrollBehavior(to, from, savedPosition) {
  if (savedPosition) return savedPosition
  if (to.hash) return { el: to.hash, top: stickyOffsetFor(to), behavior: 'smooth' }
  // 只换了查询参数：这是「同一页里的筛选」，不是一次页面跳转
  if (to.path === from.path) return false
  return { top: 0 }
}
