/**
 * 路由守卫里的纯判断，抽出来是为了能直接断言（不需要 window / Pinia）。
 *
 * 第一件事：**「我的」与「个人主页」是同一个页面。**
 *
 * 以前 `/me`（个人中心）与 `/users/{id}`（个人主页）是两页，展示的内容高度重叠：
 * 都有一张「头像 + 昵称 + 简介 + 统计」的封面，只是一个偏设置入口、一个偏公开内容。
 * 顶栏「我的」走 /me，右上角头像却走 /users/{自己}，两个入口落到不同 URL，
 * 看起来像两个页面。
 *
 * 现在规范成：`/me` 是唯一的「我」页面（个人主页 + 我的内容 + 常用入口），
 * 访问自己的 `/users/{id}` 一律重定向过去。看别人的主页仍然是 /users/{别人的 id}。
 */
export function ownProfileRedirect(to, myId) {
  if (!to || to.name !== 'user-home') return null
  if (!myId) return null
  const target = to.params?.id
  if (target === undefined || target === null || target === '') return null
  if (String(target) !== String(myId)) return null
  // replace：不留一条「我刚从自己的主页跳过来」的历史，后退不会在两页之间打转
  return { name: 'me', replace: true }
}
