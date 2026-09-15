// Verifies the second redundant-per-navigation request: the shell used to call
// `auth.loadProfile()` on every view mount, and AppShell is rendered inside every
// page, so each cross-page navigation fired `/auth/me` — which costs two
// u_r_sys_user lookups in auth-service (JWT filter + AuthService.me).
//
// The interesting risk in the fix is the opposite direction: the profile edit
// form MUST still get fresh data, so `loadProfile()` keeps "always fetch"
// semantics while only the background variant is throttled. Both halves are
// asserted here, because a test that only proved "fewer requests" would pass even
// if the edit form silently started showing stale values.
//
// Run from the frontend directory: npm run test:profile-frequency

import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'

const here = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(here, '..')
const stubAuth = path.join(here, 'stub-auth-profile.mjs')

const results = []
function check(name, ok, detail = '') {
  results.push({ name, ok: !!ok, detail })
  console.log(`[${ok ? 'PASS' : 'FAIL'}] ${name}${detail ? ` -- ${detail}` : ''}`)
}

// ---------------------------------------------------------------- stub + clock

let httpCalls = 0
let nickname = 'first'

globalThis.__authProfileStub = {
  fetchMe: async () => {
    httpCalls += 1
    await new Promise((r) => setTimeout(r, 5))
    return { code: 0, data: { id: 1, nickname, role: 'USER' } }
  }
}

const realDateNow = Date.now
let fakeNow = realDateNow()
Date.now = () => fakeNow
const advance = (ms) => { fakeNow += ms }

{
  const store = new Map()
  globalThis.localStorage = {
    getItem: (k) => (store.has(k) ? store.get(k) : null),
    setItem: (k, v) => store.set(k, String(v)),
    removeItem: (k) => store.delete(k),
    clear: () => store.clear(),
    key: (i) => [...store.keys()][i] ?? null,
    get length() { return store.size }
  }
}

// ---------------------------------------------------------------- vite server

const server = await createServer({
  root,
  configFile: false,
  plugins: [vue()],
  logLevel: 'error',
  appType: 'custom',
  server: { middlewareMode: true, hmr: false },
  resolve: { alias: [{ find: /^\.\.\/api\/auth$/, replacement: stubAuth }] }
})

const { createPinia, setActivePinia } = await server.ssrLoadModule('/tests/bridge.mjs')
const { useAuthStore } = await server.ssrLoadModule('/src/stores/auth.js')

setActivePinia(createPinia())
const auth = useAuthStore()

// 起始状态：已登录、本地已有 user（所以后台刷新有缓存可退）
auth.accessToken = 'token-a'
auth.user = { id: 1, nickname: 'cached', role: 'USER' }

// ================================================================ 1. 后台刷新限频

httpCalls = 0
for (let i = 0; i < 10; i += 1) {
  await auth.refreshProfileInBackground()
  advance(1_000) // 10 次「跨页面导航」共 10 秒，远小于 30 秒下限
}
check('1a 10 秒内 10 次页面导航只打 1 次 /auth/me', httpCalls === 1, `httpCalls=${httpCalls}`)

advance(30_001)
httpCalls = 0
await auth.refreshProfileInBackground()
check('1b 超过下限后重新刷新', httpCalls === 1, `httpCalls=${httpCalls}`)

// ================================================================ 2. 编辑页必须拿到最新值

// 这是本次改动最大的风险点：loadProfile 若也被限频，资料设置页会回填 30 秒前的旧值
httpCalls = 0
nickname = 'edited'
const fresh = await auth.loadProfile()
check('2a 间隔内 loadProfile 仍然真的请求（编辑页要最新值）', httpCalls === 1, `httpCalls=${httpCalls}`)
check('2b loadProfile 返回的是最新值而不是缓存', fresh?.nickname === 'edited', `nickname=${fresh?.nickname}`)

// ================================================================ 3. 并发合并

advance(30_001)
httpCalls = 0
await Promise.all([auth.loadProfile(), auth.refreshProfileInBackground(), auth.loadProfile()])
check('3 外壳的后台刷新与编辑页的加载合并成 1 次请求', httpCalls === 1, `httpCalls=${httpCalls}`)

// ================================================================ 4. 未登录不请求

const notLoggedIn = useAuthStore()
notLoggedIn.accessToken = ''
httpCalls = 0
await notLoggedIn.refreshProfileInBackground()
check('4 未登录时后台刷新不发请求', httpCalls === 0, `httpCalls=${httpCalls}`)

// ================================================================ 5. 退出后复位

auth.clear()
check('5a clear 清掉本地用户', auth.user === null && auth.accessToken === '')
httpCalls = 0
auth.accessToken = 'token-b'
auth.user = { id: 2, nickname: 'other', role: 'USER' }
await auth.refreshProfileInBackground()
check('5b 换账号后不被上一个账号的时间戳挡住', httpCalls === 1, `httpCalls=${httpCalls}`)

Date.now = realDateNow
await server.close()

const failed = results.filter((r) => !r.ok)
console.log('')
console.log(`# total ${results.length} checks, ${results.length - failed.length} passed, ${failed.length} failed`)
if (failed.length) {
  for (const f of failed) console.log(`  - ${f.name} -- ${f.detail}`)
  process.exit(1)
}
