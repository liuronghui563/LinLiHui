// src/api/http.js 在 401 时会用这个 router 跳登录页。
// SSR 渲染不发起任何请求（onMounted 不执行），所以给一个最小替身即可——
// 否则 `createWebHistory()` 会在 import 期就去摸 window，整个模块图直接炸掉。
export default {
  push: () => {},
  replace: () => {},
  currentRoute: { value: { fullPath: '/', name: 'discover', query: {}, params: {} } }
}
