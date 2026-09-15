// 「关注 / 拉黑」业务的行为验证（不需要浏览器）。
//
// 背景：后端（auth-service UserRelationService）与网关路由本来就是通的，但前端只有一个
// 关系列表页能用这组操作——**个人主页上根本没有关注 / 拉黑按钮**，而关注列表的空态
// 却写着「在个人主页点『关注』」。也就是说业务在界面上等于没实现。
//
// 现在这组行为收敛在 `useRelation` 里（幂等、并发锁、失败提示、换行重查），
// 它是纯逻辑 + 可注入的网络层，所以能直接在 Node 里跑成断言：
//
//   A. 关注 / 取关的状态翻转，以及「每个写操作都用后端返回值覆盖本地状态」
//   B. 拉黑必须先确认；取消确认不发请求
//   C. 失败要有提示，且状态不能乱跳
//   D. 批量给了 initialRelation 就不该再各自请求（这是列表页 N+1 优化的命门）
//   E. 并发点击只发一次请求
//
// 从 frontend 目录运行：npm run test:relation

import path from 'node:path'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'

const here = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(here, '..')
const stubRelation = path.join(here, 'stub-relation.mjs')

const results = []
function check(name, ok, detail = '') {
  results.push({ name, ok: !!ok, detail })
  console.log(`[${ok ? 'PASS' : 'FAIL'}] ${name}${detail ? ` -- ${detail}` : ''}`)
}

// ---------------------------------------------------------------- stub control
const calls = { follow: 0, unfollow: 0, block: 0, unblock: 0, relation: 0 }
let failMode = ''

const state = {
  following: false,
  followedBy: false,
  blocked: false,
  blockedBy: false,
  followerCount: 3,
  followingCount: 5
}

function snapshot(message = 'ok') {
  return { code: 0, message, data: { ...state } }
}

globalThis.__relationStub = {
  calls,
  setFail(mode) { failMode = mode },
  reset() {
    calls.follow = calls.unfollow = calls.block = calls.unblock = calls.relation = 0
    failMode = ''
    Object.assign(state, {
      following: false, followedBy: false, blocked: false, blockedBy: false,
      followerCount: 3, followingCount: 5
    })
  },
  follow: async () => {
    calls.follow += 1
    if (failMode === 'follow') throw new Error('暂时无法关注该用户')
    state.following = true
    state.followerCount += 1
    return snapshot('已关注')
  },
  unfollow: async () => {
    calls.unfollow += 1
    if (failMode === 'unfollow') throw new Error('取消失败')
    state.following = false
    state.followerCount -= 1
    return snapshot('已取消关注')
  },
  block: async () => {
    calls.block += 1
    if (failMode === 'block') throw new Error('拉黑失败')
    state.blocked = true
    // 后端语义：拉黑会解除双向关注
    state.following = false
    state.followedBy = false
    return snapshot('已拉黑，双方互不可见')
  },
  unblock: async () => {
    calls.unblock += 1
    state.blocked = false
    return snapshot('已取消拉黑')
  },
  relation: async () => {
    calls.relation += 1
    if (failMode === 'relation') throw new Error('关系状态加载失败')
    return snapshot()
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
  // 关掉依赖预构建：测试期间服务只存活几秒，跑一次 esbuild 优化纯属浪费，
  // 而且关闭时取消它会在汇总之后打一行 "build was canceled" 干扰输出。
  optimizeDeps: { noDiscovery: true, include: [] },
  resolve: {
    // 只换掉网络层，被测的是真实的业务逻辑
    alias: [{ find: /^\.\.\/api\/relation$/, replacement: stubRelation }]
  }
})

const bridge = await server.ssrLoadModule('/tests/bridge.mjs')
const { ref, nextTick } = bridge
const { useRelation } = await server.ssrLoadModule('/src/composables/useRelation.js')
const stub = globalThis.__relationStub

/** 固定确认结果，避免测试依赖 window.confirm */
function makeRelation(userId = 9, initial = null, confirm = true) {
  return useRelation(ref(userId), ref(initial), { confirmBlock: () => confirm })
}

// ================================================================ A. 关注 / 取关
stub.reset()
{
  const r = makeRelation()
  await r.load()
  check('A1 初始为未关注', r.relation.value.following === false && calls.relation === 1)

  const afterFollow = await r.toggleFollow()
  check('A2 点击后调用关注接口', calls.follow === 1, `follow=${calls.follow}`)
  check('A3 关注后状态翻转为已关注', r.relation.value.following === true)
  check('A4 关注后用后端返回的粉丝数覆盖本地', r.relation.value.followerCount === 4,
    `followerCount=${r.relation.value.followerCount}`)
  check('A5 toggleFollow 返回最新关系（供父组件同步）',
    afterFollow && afterFollow.following === true)

  await r.toggleFollow()
  check('A6 再点一次走的是取关', calls.unfollow === 1 && r.relation.value.following === false)
  check('A7 取关后粉丝数回落', r.relation.value.followerCount === 3)
}

// ================================================================ B. 拉黑确认
stub.reset()
{
  const r = makeRelation(9, null, false)
  await r.toggleBlock()
  check('B1 未确认时一个请求都不发',
    calls.block === 0 && r.relation.value.blocked === false, `block=${calls.block}`)
}

stub.reset()
{
  const r = makeRelation(9, null, true)
  await r.toggleBlock()
  check('B2 确认后调用拉黑接口', calls.block === 1 && r.relation.value.blocked === true)

  await r.toggleBlock()
  check('B3 已拉黑时取消拉黑不再二次确认', calls.unblock === 1, `unblock=${calls.unblock}`)
  check('B4 取消拉黑后回到未拉黑', r.relation.value.blocked === false)
}

stub.reset()
{
  const r = makeRelation(9, null, true)
  // 先关注再拉黑：后端会解除双向关注，前端必须跟着变
  await r.toggleFollow()
  await r.toggleBlock()
  check('B5 拉黑会解除双向关注（与后端语义一致）',
    r.relation.value.following === false && r.relation.value.blocked === true)
}

// ================================================================ C. 失败处理
stub.reset()
{
  const r = makeRelation()
  stub.setFail('follow')
  const res = await r.toggleFollow()
  check('C1 关注失败时返回 null', res === null)
  check('C2 关注失败时状态不乱跳', r.relation.value.following === false)
  check('C3 关注失败时把后端文案显示出来（而不是静默）',
    r.error.value === '暂时无法关注该用户', `error=${r.error.value}`)
  check('C4 失败后 busy 归位（按钮不会永远禁用）', r.busy.value === false)

  stub.setFail('')
  await r.toggleFollow()
  check('C5 失败后再点能成功，且错误提示被清掉',
    r.relation.value.following === true && r.error.value === '')
}

stub.reset()
{
  const r = makeRelation()
  stub.setFail('relation')
  await r.load()
  check('C6 关系状态取不到时只提示、不阻塞（按钮回落为未关注）',
    r.error.value === '关系状态加载失败' && r.relation.value.following === false)
}

// ================================================================ D. 批量给过就别再问
stub.reset()
{
  const preset = { following: true, followedBy: false, blocked: false, blockedBy: false, followerCount: 7, followingCount: 2 }
  const r = makeRelation(9, preset)
  check('D1 传了 initialRelation 就直接用，不请求接口',
    calls.relation === 0 && r.relation.value.following === true)
  check('D2 计数也来自批量结果', r.relation.value.followerCount === 7)
}

// ================================================================ E. 并发点击
stub.reset()
{
  const r = makeRelation()
  await r.load()
  const [a, b] = await Promise.all([r.toggleFollow(), r.toggleFollow()])
  check('E1 连点两次只发一次请求（busy 加锁）',
    calls.follow === 1 && (a === null || b === null), `follow=${calls.follow}`)
}

// ================================================================ F. 换行重查
stub.reset()
{
  const userId = ref(9)
  const r = useRelation(userId, ref(null), { confirmBlock: () => true })
  await r.load()
  const before = calls.relation
  userId.value = 10
  await nextTick()
  await nextTick()
  check('F1 换了一个用户会重新查关系（否则按钮显示上一行的状态）',
    calls.relation > before, `relation calls=${calls.relation}`)
}

// ================================================================ G. 组件渲染与入口存在
// 业务「实现了」的最低标准是界面上真有这组按钮。个人主页曾经一个都没有，
// 而关注列表的空态却写着「在个人主页点『关注』」——这里把入口本身也钉住。
{
  const { createSSRApp, renderToString, createPinia } = bridge
  const FollowButton = (await server.ssrLoadModule('/src/components/FollowButton.vue')).default

  const render = (props) => renderToString(createSSRApp(FollowButton, { userId: 9, ...props }))

  const idle = await render({ initialRelation: { following: false, followedBy: false, blocked: false, blockedBy: false, followerCount: 0, followingCount: 0 } })
  check('G1 未关注时渲染「+ 关注」与「拉黑」', idle.includes('+ 关注') && idle.includes('拉黑'))
  check('G2 未关注时不出现「已关注」', !idle.includes('已关注'))

  const following = await render({ initialRelation: { following: true, followedBy: false, blocked: false, blockedBy: false, followerCount: 1, followingCount: 1 } })
  check('G3 已关注时按钮文案翻转', following.includes('已关注') && !following.includes('+ 关注'))

  const blocked = await render({ initialRelation: { following: false, followedBy: false, blocked: true, blockedBy: false, followerCount: 1, followingCount: 1 } })
  check('G4 已拉黑时显示「已拉黑」，且隐藏关注按钮',
    blocked.includes('已拉黑') && !blocked.includes('+ 关注'))

  const blockedBy = await render({ initialRelation: { following: false, followedBy: false, blocked: false, blockedBy: true, followerCount: 1, followingCount: 1 } })
  check('G5 被对方拉黑时给出说明，且不显示关注按钮',
    blockedBy.includes('对方已将你拉黑') && !blockedBy.includes('+ 关注'))

  const dark = await render({ tone: 'dark', initialRelation: { following: false, followedBy: false, blocked: false, blockedBy: false, followerCount: 0, followingCount: 0 } })
  check('G6 tone="dark" 会带上深色表面变体（近黑封面上的按钮才不会掉色）',
    dark.includes('relation-actions--dark'))

  const homeSource = readFileSync(path.join(root, 'src/views/UserHomeView.vue'), 'utf8')
  check('G7 个人主页真的挂了这组按钮（曾整个缺失）',
    /<FollowButton[\s\S]{0,200}?tone="dark"/.test(homeSource))
  check('G8 个人主页从关系接口取状态，并把结果交给按钮（不重复请求）',
    /fetchRelation\(/.test(homeSource) && /:initial-relation="relation"/.test(homeSource))
}

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
