// 「广告位申请 → 管理员审核 → 上线轮播」的行为验证（不需要浏览器）。
//
// 这条链路有三处特别容易做错，而且都不会在编译期暴露：
//
//   A. 规则顺序：ad-service 的 SecurityConfig 里，用户提交申请的规则必须排在
//      「"/api/ad/**" 只有 ADMIN」之前——顺序反了，普通用户提交申请会被判 403，
//      表现成「功能没实现」。这里把顺序也断言上。
//   B. 审核是唯一的闸门：只有 APPROVED 才进轮播。前端的申请页要如实反映三种状态，
//      并且**只允许撤回待审的**。
//   C. 审核通过后必须清掉轮播缓存，否则「通过了下线」最多要等 60 秒才可见。
//
// 从 frontend 目录运行：npm run test:ad

import path from 'node:path'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'

const here = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(here, '..')
const frontendRoot = root
const backendRoot = path.resolve(root, '..', 'backend')

const results = []
function check(name, ok, detail = '') {
  results.push({ name, ok: !!ok, detail })
  console.log(`[${ok ? 'PASS' : 'FAIL'}] ${name}${detail ? ` -- ${detail}` : ''}`)
}

// AppShell → SiteFooter → stores/auth 在构造初始状态时会读 localStorage；
// Node 没有这个 API，用内存实现顶上，而不是为了测试去改业务代码。
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
  globalThis.IntersectionObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}

// ---------------------------------------------------------------- 网络层替身
const AD_ITEMS = [
  {
    id: 11,
    title: '楼下咖啡新店开业',
    subtitle: '本小区住户第二杯半价',
    imageUrl: '/api/file/objects/adapply/1/20260101/aaaaaaaa-1111-4111-8111-111111111111.jpg',
    linkUrl: 'https://example.com',
    status: 'PENDING',
    statusLabel: '待审核',
    reviewNote: null,
    applicantId: 7,
    applicantName: '小明',
    createdAt: '2026-01-01T10:00:00'
  },
  {
    id: 12,
    title: '周末家政保洁',
    subtitle: '',
    imageUrl: '/api/file/objects/adapply/1/20260101/bbbbbbbb-2222-4222-8222-222222222222.jpg',
    linkUrl: '',
    status: 'APPROVED',
    statusLabel: '已通过',
    reviewNote: '图片清晰，通过',
    applicantId: 7,
    applicantName: '小明',
    createdAt: '2026-01-01T09:00:00'
  },
  {
    id: 13,
    title: '贷款秒批',
    subtitle: '',
    imageUrl: '/api/file/objects/adapply/1/20260101/cccccccc-3333-4333-8333-333333333333.jpg',
    linkUrl: '',
    status: 'REJECTED',
    statusLabel: '已驳回',
    reviewNote: '不接金融借贷类广告',
    applicantId: 7,
    applicantName: '小明',
    createdAt: '2026-01-01T08:00:00'
  }
]

globalThis.__adStub = {
  mine: AD_ITEMS,
  carousel: [],
  calls: { apply: 0, withdraw: 0, pending: 0, approve: 0, reject: 0 }
}

const server = await createServer({
  root: frontendRoot,
  configFile: false,
  plugins: [vue()],
  logLevel: 'error',
  appType: 'custom',
  server: { middlewareMode: true, hmr: false },
  optimizeDeps: { noDiscovery: true, include: [] },
  resolve: {
    alias: [
      { find: /^\.\.\/router$/, replacement: path.join(here, 'stub-router.mjs') },
      { find: /^\.\.\/api\/ad$/, replacement: path.join(here, 'stub-ad.mjs') }
    ]
  }
})

const bridge = await server.ssrLoadModule('/tests/bridge.mjs')
const { createSSRApp, renderToString, createPinia, createRouter, createMemoryHistory } = bridge

function makeApp(component) {
  const app = createSSRApp(component)
  app.use(createPinia())
  app.use(createRouter({ history: createMemoryHistory(), routes: [{ path: '/:all(.*)', component: { template: '<div/>' } }] }))
  return app
}

const origWarn = console.warn
console.warn = () => {}

// ================================================================ A. 安全规则顺序
const security = readFileSync(
  path.join(backendRoot, 'ad-service/src/main/java/com/chengqu/huzhu/ad/config/SecurityConfig.java'), 'utf8')

const idxUserSubmit = security.indexOf('HttpMethod.POST, "/api/ad/applications")')
const idxUserMine = security.indexOf('HttpMethod.GET, "/api/ad/applications/mine")')
const idxUserEdit = security.indexOf('HttpMethod.PUT, "/api/ad/applications/*")')
const idxAdminAny = security.indexOf('"/api/ad/applications/**").hasRole("ADMIN")')
const idxAdminPost = security.indexOf('HttpMethod.POST, "/api/ad")')

check('A1 用户提交申请的规则存在', idxUserSubmit > 0)
check('A2 「我的申请」的规则存在', idxUserMine > 0)
check('A3 审核相关规则要求 ADMIN', idxAdminAny > 0)
check('A4 提交申请的规则排在 ADMIN 规则之前（顺序反了用户就是 403）',
  idxUserSubmit > 0 && idxAdminAny > idxUserSubmit,
  `submit@${idxUserSubmit} admin@${idxAdminAny}`)
check('A5 「我的申请」也排在 ADMIN 规则之前',
  idxUserMine > 0 && idxAdminAny > idxUserMine)
check('A6 没被更早的 POST /api/ad 规则吞掉（那是精确路径，不影响子路径）',
  idxAdminPost < 0 || idxAdminPost > idxUserSubmit,
  `POST /api/ad @${idxAdminPost}`)
check('A7 修改申请的 PUT 规则存在，且同样排在 ADMIN 之前',
  idxUserEdit > 0 && idxAdminAny > idxUserEdit,
  `put@${idxUserEdit} admin@${idxAdminAny}`)

// ================================================================ B. 闸门与缓存
const adService = readFileSync(
  path.join(backendRoot, 'ad-service/src/main/java/com/chengqu/huzhu/ad/service/AdService.java'), 'utf8')

check('B1 轮播只查「审核通过」的广告（审核是真正的闸门，不是前端不渲染）',
  /findByStatusAndEnabledTrueOrderBySortOrderAscIdDesc\(AdStatus\.APPROVED\)/.test(adService))
check('B2 申请落库为 PENDING', /\.status\(AdStatus\.PENDING\)/.test(adService))
check('B3 通过后清掉轮播缓存（否则最多 60 秒才可见）',
  /public AdApplicationResponse approve[\s\S]{0,600}?redisCache\.evict\(RedisKeys\.adCarousel\(\)\)/.test(adService))
check('B4 只能撤回自己的申请（用 404 而不是 403，避免探测别人的申请）',
  /withdraw[\s\S]{0,500}?BizException\(404, "申请不存在"\)/.test(adService))
check('B5 只允许撤回待审的申请', /getStatus\(\) != AdStatus\.PENDING[\s\S]{0,80}不能撤回/.test(adService))
check('B6 已审核过的申请不能再审一次', /requirePending[\s\S]{0,200}已经审核过了/.test(adService))

// 迁移：status 默认必须是 APPROVED，否则线上已有广告会集体消失
const migration = readFileSync(
  path.join(backendRoot, 'ad-service/src/main/resources/db/migration/V3__ad_application.sql'), 'utf8')
check('B7 迁移里 status 默认 APPROVED（存量广告是管理员直接建的，默认 PENDING 会让它们下线）',
  /status\s+ENUM\('PENDING','APPROVED','REJECTED'\)\s+NOT NULL\s+DEFAULT\s+'APPROVED'/.test(migration))

// ---------------------------------------------------------------- B8–B12 修改申请
// 「改了就要重新审」是这条业务最容易被做错的地方：如果改已通过的广告不退回待审，
// 审核就形同虚设——先提交一个好图过审，再换成任意内容。
const editBlock = adService.match(/public AdApplicationResponse updateApplication[\s\S]*?\n    \}/)
check('B8 修改申请的方法存在', !!editBlock)
check('B9 只有申请人自己能改（用同一个 404 口径，避免探测别人的申请）',
  !!editBlock && /BizException\(404, "申请不存在"\)/.test(editBlock[0]))
check('B10 改完一律退回待审（含已通过的），否则审核白做',
  !!editBlock && /setStatus\(AdStatus\.PENDING\)/.test(editBlock[0]))
check('B11 改的是正在轮播的那条时，立刻清缓存把它撤下来',
  !!editBlock && /wasLive[\s\S]{0,400}?redisCache\.evict\(RedisKeys\.adCarousel\(\)\)/.test(editBlock[0]))
check('B12 清掉上一次的审核意见与时间（它们是针对旧内容的）',
  !!editBlock && /setReviewNote\(null\)/.test(editBlock[0]) && /setReviewedAt\(null\)/.test(editBlock[0]));

// ---------------------------------------------------------------- B13 管理员日常管理
check('B13 管理端有「全部广告」列表（审核通过之后的日常管理入口）',
  /public List<AdApplicationResponse> allApplications\(\)/.test(adService))
check('B14 控制器暴露了 /applications/all（ADMIN，被 applications/** 规则覆盖）',
  /@GetMapping\("\/applications\/all"\)/.test(
    readFileSync(path.join(backendRoot, 'ad-service/src/main/java/com/chengqu/huzhu/ad/controller/AdController.java'), 'utf8')))
check('B15 列表 DTO 带上排序与启用状态（管理页要靠它渲染与回传）',
  /private Integer sortOrder;/.test(readFileSync(
    path.join(backendRoot, 'ad-service/src/main/java/com/chengqu/huzhu/ad/dto/AdApplicationResponse.java'), 'utf8'))
  && /private Boolean enabled;/.test(readFileSync(
    path.join(backendRoot, 'ad-service/src/main/java/com/chengqu/huzhu/ad/dto/AdApplicationResponse.java'), 'utf8')))

// ================================================================ C. 用户端页面
// SSR 不执行 onMounted，所以列表在服务端渲染时必然是「加载中」——
// 这里断言页面结构，状态语义则由纯函数 adStatus 单独断言（见 C8–C12）。
const view = (await server.ssrLoadModule('/src/views/AdApplyView.vue')).default
const html = await renderToString(makeApp(view))

check('C1 申请页渲染出来了（含「我的申请」与提交入口）',
  html.includes('申请一个广告位') && html.includes('我的申请'))
check('C6 广告图用 adapply 用途上传（与管理员直投的 ad 分开）',
  /purpose="adapply"/.test(readFileSync(path.join(frontendRoot, 'src/views/AdApplyView.vue'), 'utf8')))
check('C7 页面不做权限判断（谁能审核由服务端说了算）',
  !/isAdmin/.test(readFileSync(path.join(frontendRoot, 'src/views/AdApplyView.vue'), 'utf8')))

const { adStatusMeta, adStatusHint, canWithdraw } = await server.ssrLoadModule('/src/utils/adStatus.js')

check('C2 待审：标签语气 + 「等待管理员审核」',
  adStatusMeta('PENDING').tone === 'tag--open'
  && adStatusHint({ status: 'PENDING' }) === '已提交，等待管理员审核。')
check('C3 已通过：标签语气 + 「正在首页轮播中」',
  adStatusMeta('APPROVED').tone === 'tag--done'
  && adStatusHint({ status: 'APPROVED' }) === '已通过，正在首页轮播中。')
check('C4 有审核意见时优先显示意见（那是管理员给用户的话）',
  adStatusHint({ status: 'REJECTED', reviewNote: '不接金融借贷类广告' }) === '审核意见：不接金融借贷类广告')
check('C5 撤回只对「待审」开放',
  canWithdraw('PENDING') === true
  && canWithdraw('APPROVED') === false
  && canWithdraw('REJECTED') === false)
check('C8 未知状态不会炸（标签回落到中性语气）',
  adStatusMeta('SOMETHING_NEW').tone === 'tag--outline'
  && adStatusHint({ status: 'SOMETHING_NEW' }) === '')

// ================================================================ D. 管理端接入
const adminSource = readFileSync(path.join(frontendRoot, 'src/views/AdminView.vue'), 'utf8')
const shellSource = readFileSync(path.join(frontendRoot, 'src/layouts/AppShell.vue'), 'utf8')
const footerSource = readFileSync(path.join(frontendRoot, 'src/components/SiteFooter.vue'), 'utf8')
check('D1 管理台有待审区块', adminSource.includes('广告位申请') && adminSource.includes('loadPendingAds'))
check('D2 通过 / 驳回都会把审核意见带上',
  /approveAdApplication\(item\.id, note\)/.test(adminSource)
  && /rejectAdApplication\(item\.id, note\)/.test(adminSource))
check('D3 审核后重新拉待审列表（列表状态必须与审核结果一致）',
  /onReview[\s\S]{0,900}?await loadPendingAds\(\)/.test(adminSource))
check('D4 驳回未填原因时会二次确认', /驳回时没有填写原因/.test(adminSource))
check('D5 顶部导航有「广告位」入口', /to: '\/ad\/apply'/.test(shellSource))
check('D6 页脚也有入口', /to: '\/ad\/apply'/.test(footerSource))

// ---------------------------------------------------------------- E. 审核通过之后的管理
check('E1 管理台有「广告管理」区块，并拉全部广告',
  adminSource.includes('广告管理') && /fetchAllAdApplications\(\)/.test(adminSource))
check('E2 上下架不需要再审（只改 enabled，其余字段原样回传）',
  /function onToggleEnabled[\s\S]{0,500}?enabled: !item\.enabled/.test(adminSource))
check('E3 改文案 / 排序都回传完整对象（后端是整体覆盖语义，漏字段会把标题清空）',
  ['onSaveAd', 'onSaveSort', 'onToggleEnabled'].every((fn) =>
    new RegExp(`${fn}\\(item\\)[\\s\\S]{0,600}?updateAd\\(item\\.id, \\{[\\s\\S]{0,400}?imageUrl: item\\.imageUrl`).test(adminSource)))
check('E4 审核通过后会同时刷新广告管理列表（同一件事不能两个列表两种说法）',
  /onReview[\s\S]{0,900}?await loadAds\(\)/.test(adminSource))
check('E5 删除广告要二次确认', /删除广告|删除「/.test(adminSource) && /window\.confirm\(`删除/.test(adminSource))

// ---------------------------------------------------------------- F. 用户端「修改申请」
const applySource = readFileSync(path.join(frontendRoot, 'src/views/AdApplyView.vue'), 'utf8')
check('F1 申请页有「修改」入口', /@click="startEdit\(item\)"/.test(applySource))
check('F2 修改走 PUT /ad/applications/{id}', /updateAdApplication\(editingId\.value, \{ \.\.\.form \}\)/.test(applySource))
check('F3 修改正在轮播的广告时，提交前就告知它会上线/下线',
  /editingWasLive/.test(applySource) && /这条广告正在首页轮播，提交修改后会先下线/.test(applySource))
check('F4 修改成功后回到「新建」状态，不会把表单留在编辑模式',
  /updateAdApplication[\s\S]{0,400}?cancelEdit\(\)/.test(applySource))

console.warn = origWarn
await server.close()

const failed = results.filter((r) => !r.ok)
console.log('')
console.log(`# total ${results.length} checks, ${results.length - failed.length} passed, ${failed.length} failed`)
if (failed.length) {
  for (const f of failed) console.log(`  - ${f.name} -- ${f.detail}`)
  process.exit(1)
}
process.exit(0)
