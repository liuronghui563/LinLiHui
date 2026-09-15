// Verifies that the unread-count badge cannot turn into a per-navigation
// `COUNT(*)` storm. The assertions are on the NUMBER OF HTTP REQUESTS, which is
// the thing that was wrong — not just on the final number shown in the badge.
//
// Why the store is the right thing to test: all three call sites in AppShell /
// MessagesView are deliberately written without their own frequency checks
// (`notify.refresh()` on mount, on every route change, on tab focus, on message
// page mount). The frequency control lives in exactly one place — the store —
// so testing it there covers every caller, including ones added later.
//
// Run from the frontend directory: npm run test:notify-frequency

import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'

const here = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(here, '..')
const stubNotify = path.join(here, 'stub-notify.mjs')

const results = []
function check(name, ok, detail = '') {
  results.push({ name, ok: !!ok, detail })
  console.log(`[${ok ? 'PASS' : 'FAIL'}] ${name}${detail ? ` -- ${detail}` : ''}`)
}

// ---------------------------------------------------------------- stub control

let httpCalls = 0
let shouldFail = false
let countToReturn = 7

globalThis.__notifyStub = {
  fetchUnreadCount: async () => {
    httpCalls += 1
    // 让请求真的异步，否则并发去重那段测不出东西
    await new Promise((r) => setTimeout(r, 5))
    if (shouldFail) throw new Error('notify-service down')
    return { code: 0, data: { count: countToReturn } }
  }
}

// ---------------------------------------------------------------- fake clock

// The store throttles on Date.now(). Patching it lets the test advance past the
// window without sleeping 30 real seconds; the store calls Date.now() at every
// refresh, so the fake clock is picked up with no test-only hook in the code.
const realDateNow = Date.now
let fakeNow = realDateNow()
Date.now = () => fakeNow
const advance = (ms) => {
  fakeNow += ms
}

// ---------------------------------------------------------------- vite server

const server = await createServer({
  root,
  configFile: false,
  plugins: [vue()],
  logLevel: 'error',
  appType: 'custom',
  server: { middlewareMode: true, hmr: false },
  resolve: {
    alias: [{ find: /^\.\.\/api\/notify$/, replacement: stubNotify }]
  }
})

const bridge = await server.ssrLoadModule('/tests/bridge.mjs')
const { createPinia, setActivePinia } = bridge
const { useNotifyStore, MIN_INTERVAL_MS } = await server.ssrLoadModule('/src/stores/notify.js')

setActivePinia(createPinia())
const notify = useNotifyStore()

check('常量：最短间隔为 30 秒', MIN_INTERVAL_MS === 30_000, `MIN_INTERVAL_MS=${MIN_INTERVAL_MS}`)

// ================================================================ 1. 路由风暴

// 模拟「10 秒内切了 10 个页面」：AppShell 的 route watch 每次都会调 refresh()
httpCalls = 0
for (let i = 0; i < 10; i += 1) {
  await notify.refresh()
  advance(1_000) // 每次切页间隔 1 秒，远小于 30 秒的最短间隔
}
check('1a 10 秒内 10 次路由变化只打 1 次后端', httpCalls === 1, `httpCalls=${httpCalls}`)
check('1b 角标仍是真实值', notify.unreadCount === 7, `unreadCount=${notify.unreadCount}`)

// ================================================================ 2. 并发去重

// 必须先越过最短间隔，否则测的是限频而不是并发去重。
// 这一段针对的场景：首次进入应用时 AppShell 与 MessagesView 会几乎同时挂载，
// 两边各调一次 refresh()——必须只产生一次请求。
advance(MIN_INTERVAL_MS + 1)
httpCalls = 0
await Promise.all([notify.refresh(), notify.refresh(), notify.refresh()])
check('2a 同一时刻 3 个调用方共享 1 次请求', httpCalls === 1, `httpCalls=${httpCalls}`)

// 再确认一次：请求刚结束、仍在间隔内的并发调用同样只会有 0 次请求
httpCalls = 0
await Promise.all([notify.refresh(), notify.refresh(), notify.refresh()])
check('2b 间隔内的并发调用完全不打扰后端', httpCalls === 0, `httpCalls=${httpCalls}`)

advance(MIN_INTERVAL_MS + 1)
httpCalls = 0
await Promise.all([notify.refresh(), notify.refresh(), notify.refresh()])
check('2c 超出最短间隔后并发调用仍只有 1 次请求', httpCalls === 1, `httpCalls=${httpCalls}`)

// ================================================================ 3. 间隔到期

advance(MIN_INTERVAL_MS + 1)
httpCalls = 0
countToReturn = 9
await notify.refresh()
check('3a 超过最短间隔后重新请求', httpCalls === 1, `httpCalls=${httpCalls}`)
check('3b 角标更新为新值', notify.unreadCount === 9, `unreadCount=${notify.unreadCount}`)

// ================================================================ 4. force

httpCalls = 0
await notify.refresh({ force: true })
check('4 force=true 立即穿透最短间隔', httpCalls === 1, `httpCalls=${httpCalls}`)

// ================================================================ 5. 失败也限频

shouldFail = true
advance(MIN_INTERVAL_MS + 1)
httpCalls = 0
await notify.refresh()
check('5a 请求失败不抛异常', notify.loaded === false, `loaded=${notify.loaded}`)
for (let i = 0; i < 5; i += 1) {
  await notify.refresh()
  advance(1_000)
}
check('5b 通知服务挂掉后，5 次路由变化不会变成 5 次重试', httpCalls === 1, `httpCalls=${httpCalls}`)
shouldFail = false

// ================================================================ 6. 本地更新不打后端

advance(MIN_INTERVAL_MS + 1)
httpCalls = 0
countToReturn = 5
await notify.refresh()
const beforeLocal = httpCalls

notify.decrease()
check('6a 标记单条已读就地扣减', notify.unreadCount === 4, `unreadCount=${notify.unreadCount}`)
notify.decrease(99)
check('6b 扣减不会变成负数', notify.unreadCount === 0, `unreadCount=${notify.unreadCount}`)
notify.setCount(12)
check('6c 用列表接口的 totalElements 覆盖', notify.unreadCount === 12, `unreadCount=${notify.unreadCount}`)
notify.clear()
check('6d 全部已读就地清零', notify.unreadCount === 0, `unreadCount=${notify.unreadCount}`)
check('6e 以上本地操作一次后端都没打', httpCalls === beforeLocal, `httpCalls=${httpCalls}`)

// ================================================================ 7. 退出登录

notify.setCount(3)
notify.reset()
check('7a 退出登录后角标归零', notify.unreadCount === 0, `unreadCount=${notify.unreadCount}`)
check('7b 退出登录后 loaded 复位', notify.loaded === false)
httpCalls = 0
await notify.refresh()
check('7c 复位后最短间隔不再拦截（换账号能立刻取到）', httpCalls === 1, `httpCalls=${httpCalls}`)

Date.now = realDateNow
await server.close()

// ---------------------------------------------------------------- summary

const failed = results.filter((r) => !r.ok)
console.log('')
console.log(`# total ${results.length} checks, ${results.length - failed.length} passed, ${failed.length} failed`)
if (failed.length) {
  for (const f of failed) console.log(`  - ${f.name} -- ${f.detail}`)
  process.exit(1)
}
