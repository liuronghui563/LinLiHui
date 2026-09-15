// Behavioural verification of the manual captcha gate, without a browser.
//
// A. `useCaptcha` state machine — the logic that drives `captchaVerified`:
//    wrong code must keep the gate shut, right code must open it for holdSeconds,
//    and expiry / manual refresh must shut it again.
// B. Server-render LoginView / RegisterView in their initial state:
//    phone is visible, SMS input is absent, captcha gate waits until the phone
//    is a complete 11-digit number.
// C. Server-render CaptchaGate in both states to prove they differ.
//
// 为什么不用浏览器跑：闸门最关键的性质——未填完手机号时图形验证码与短信框
// 都不存在——在 SSR 下就能直接断言；状态机是纯逻辑，用 Node 跑得更快也更稳。
// 唯一没覆盖的是「在真实浏览器里点确认按钮后输入框出现」这一交互，
// 它由 A8（确认后 captchaVerified 变 true）与 B（未确认时不渲染）两头夹住。
//
// Run from the frontend directory: npm run test:gate

import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'

const here = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(here, '..')
const stubAuth = path.join(here, 'stub-auth.mjs')

// stores/auth.js reads localStorage while building its initial state.
// Node has no such API, so provide a minimal in-memory stand-in rather than
// changing application code to suit the test.
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
}

const results = []
function check(name, ok, detail = '') {
  results.push({ name, ok: !!ok, detail })
  console.log(`[${ok ? 'PASS' : 'FAIL'}] ${name}${detail ? ` -- ${detail}` : ''}`)
}

// ---------------------------------------------------------------- stub control

const calls = { fetchCaptcha: 0, verifyCaptcha: 0 }
let mode = 'ok'

globalThis.__gateStub = {
  fetchCaptcha: async () => {
    calls.fetchCaptcha += 1
    return { code: 0, data: { captchaId: `cap-${calls.fetchCaptcha}`, challenge: 'ABCD', expireSeconds: 120 } }
  },
  verifyCaptcha: async () => {
    calls.verifyCaptcha += 1
    if (mode === 'wrong') throw new Error('验证码错误')
    return { code: 0, data: { verified: true, holdSeconds: globalThis.__holdSeconds ?? 60 } }
  }
}

// ---------------------------------------------------------------- vite server

const server = await createServer({
  root,
  configFile: false,
  // 必须显式挂 vue 插件：configFile:false 不会读 vite.config.js，
  // 少了它 Vite 会把 .vue 当普通 JS 解析并在模板第一行就报语法错误。
  plugins: [vue()],
  logLevel: 'error',
  appType: 'custom',
  server: { middlewareMode: true, hmr: false },
  resolve: {
    alias: [{ find: /^\.\.\/api\/auth$/, replacement: stubAuth }]
  }
})

const bridge = await server.ssrLoadModule('/tests/bridge.mjs')
const { createSSRApp, reactive, ref, renderToString, createPinia, createRouter, createMemoryHistory } = bridge

// suppress the "onUnmounted outside setup" warning: useCaptcha is exercised
// directly here rather than inside a component instance.
const origWarn = console.warn
console.warn = () => {}

// ================================================================ A. state machine

const { useCaptcha, isPhoneReady } = await server.ssrLoadModule('/src/composables/useCaptcha.js')

check('A0 未填完的手机号不算就绪', isPhoneReady('') === false && isPhoneReady('1380000000') === false)
check('A0 11 位手机号才算就绪', isPhoneReady('13800000000') === true)

const form = reactive({ captchaId: '', captchaCode: '' })
const error = ref('')
const gate = useCaptcha(form, error)

check('A1 初始为未验证（闸门关闭）', gate.captchaVerified.value === false)

await gate.loadCaptcha()
check('A2 加载验证码后拿到 captchaId', form.captchaId === 'cap-1', `captchaId=${form.captchaId}`)
check('A3 加载后仍为未验证', gate.captchaVerified.value === false)

const emptyResult = await gate.confirmCaptcha()
check('A4 空输入确认被拒绝', emptyResult === false && gate.captchaVerified.value === false, `error=${error.value}`)

mode = 'wrong'
form.captchaCode = 'ZZZZ'
const wrongResult = await gate.confirmCaptcha()
check('A5 错误验证码确认失败，闸门保持关闭',
  wrongResult === false && gate.captchaVerified.value === false, `error=${error.value}`)
check('A6 错误后自动换一张验证码', form.captchaId === 'cap-2', `captchaId=${form.captchaId}`)
check('A7 错误提示保留在表单上', error.value === '验证码错误', `error=${error.value}`)

mode = 'ok'
form.captchaCode = 'abcd'
const okResult = await gate.confirmCaptcha()
check('A8 正确验证码确认通过，闸门打开',
  okResult === true && gate.captchaVerified.value === true, `error=${error.value}`)
check('A9 冻结期秒数取后端返回值', gate.captchaHoldLeft.value === 60, `holdLeft=${gate.captchaHoldLeft.value}`)

const secondConfirm = await gate.confirmCaptcha()
check('A10 已验证后重复确认不再打后端', secondConfirm === true && calls.verifyCaptcha === 2,
  `verifyCaptcha calls=${calls.verifyCaptcha}`)

await gate.loadCaptcha()
check('A11 手动换一张会作废已验证状态', gate.captchaVerified.value === false && gate.captchaHoldLeft.value === 0)

globalThis.__holdSeconds = 1
await gate.loadCaptcha()
form.captchaCode = 'abcd'
await gate.confirmCaptcha()
check('A12 短冻结期确认通过', gate.captchaVerified.value === true)
await new Promise((r) => setTimeout(r, 1400))
check('A13 冻结期到点自动收回闸门（不是只改角标文字）',
  gate.captchaVerified.value === false && gate.captchaHoldLeft.value === 0,
  `verified=${gate.captchaVerified.value} holdLeft=${gate.captchaHoldLeft.value}`)

// ================================================================ B. views render gated

function makeApp(component) {
  const app = createSSRApp(component)
  app.use(createPinia())
  app.use(createRouter({ history: createMemoryHistory(), routes: [{ path: '/:all(.*)', component: { template: '<div/>' } }] }))
  return app
}

async function renderView(file) {
  const mod = await server.ssrLoadModule(file)
  return renderToString(makeApp(mod.default))
}

for (const [label, file, captchaId, always, smsId] of [
  ['登录页', '/src/views/LoginView.vue', 'login-captcha', ['login-phone', 'login-password'], 'login-sms'],
  ['注册页', '/src/views/RegisterView.vue', 'reg-captcha', ['reg-phone', 'reg-nickname', 'reg-password'], 'reg-sms']
]) {
  const html = await renderView(file)
  for (const id of always) {
    check(`B ${label} 未填手机号时已有 #${id}`, html.includes(`id="${id}"`))
  }
  check(`B ${label} 未填完手机号时不展示图形验证码`, !html.includes(`id="${captchaId}"`))
  check(`B ${label} 未填完手机号时没有「确认验证码」`, !html.includes('确认验证码'))
  check(`B ${label} 提示先填手机号`, html.includes('请先填写 11 位手机号'))
  check(`B ${label} 未通过图形验证码时不存在短信框 #${smsId}`, !html.includes(`id="${smsId}"`))
  const submitDisabled = /type="submit"[^>]*disabled/.test(html)
  check(`B ${label} 未确认时提交按钮为禁用`, submitDisabled)
}

// ================================================================ C. gate component states

const gateMod = await server.ssrLoadModule('/src/components/CaptchaGate.vue')

const closedHtml = await renderToString(createSSRApp(gateMod.default, {
  inputId: 'probe', challenge: 'ABCD', verified: false, holdLeft: 0, modelValue: '', confirming: false, loading: false
}))
check('C 关闭态显示确认按钮', closedHtml.includes('确认验证码') && !closedHtml.includes('已通过校验'))

const openHtml = await renderToString(createSSRApp(gateMod.default, {
  inputId: 'probe', challenge: 'ABCD', verified: true, holdLeft: 42, modelValue: 'abcd', confirming: false, loading: false
}))
check('C 通过态不再显示确认按钮', !openHtml.includes('确认验证码'), 'found confirm button')
check('C 通过态显示已通过与剩余秒数', openHtml.includes('已通过校验') && openHtml.includes('42'))
check('C 通过态输入框被禁用', /id="probe"[^>]*disabled/.test(openHtml))

console.warn = origWarn
await server.close()

// ---------------------------------------------------------------- summary

const failed = results.filter((r) => !r.ok)
console.log('')
console.log(`# total ${results.length} checks, ${results.length - failed.length} passed, ${failed.length} failed`)
if (failed.length) {
  for (const f of failed) console.log(`  - ${f.name} -- ${f.detail}`)
  process.exit(1)
}
