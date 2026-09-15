/**
 * 设计系统体检（可直接执行，不需要浏览器）
 *
 *   cd frontend && npm run test:design
 *
 * 为什么需要它：DESIGN.md 里的规矩（单一强调色、不给卡片加阴影、不用装饰渐变、
 * 体例里没有 500）在代码评审里靠肉眼守不住——上一轮 UI 就是在这种「随手加一点」
 * 里漂移掉的。所以把规则写成断言：
 *
 *   硬规则（失败即退出码 1）
 *     · 每个 .vue 能被 @vue/compiler-sfc 正常解析/编译
 *     · 不出现 font-weight: 500（体例只有 300 / 400 / 600 / 700）
 *     · 不出现 radial-gradient / conic-gradient（装饰性氛围一律用摄影或纯色表面）
 *     · box-shadow 只能是 none 或 0 0 0 这类「发丝环」，不允许带模糊半径的投影
 *
 *   软规则（只提示，不失败）
 *     · .vue 里出现 linear-gradient（全局只允许 styles.css 的骨架扫光与下拉箭头）
 *     · .vue 里写死十六进制颜色（应走 var(--*) 令牌）
 *     · AppShell 页面没有使用 PageHero
 *
 * 说明：这是一份「体检」而不是「格式化」。它只拦真正破坏设计语言的写法，
 * 不干预页面自己的布局差异。
 */
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse, compileScript, compileTemplate } from 'vue/compiler-sfc'

const here = dirname(fileURLToPath(import.meta.url))
const srcRoot = resolve(here, '..', 'src')

const errors = []
const warnings = []

/** Vue 内置 / 全局注册的标签，不需要本文件 import */
const VUE_GLOBALS = new Set([
  'RouterLink', 'RouterView', 'Transition', 'TransitionGroup',
  'KeepAlive', 'Teleport', 'Suspense', 'Component', 'Fragment'
])

function walk(dir) {
  const out = []
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) out.push(...walk(full))
    else out.push(full)
  }
  return out
}

const files = walk(srcRoot)
const vueFiles = files.filter((f) => f.endsWith('.vue')).sort()
const cssFiles = files.filter((f) => f.endsWith('.css')).sort()

const rel = (f) => relative(resolve(here, '..'), f).replace(/\\/g, '/')

/* --------------------------------------------------------------------------
   1. SFC 语法：能解析、能编译，才算「改完」
   -------------------------------------------------------------------------- */
for (const file of vueFiles) {
  const source = readFileSync(file, 'utf8')
  const id = rel(file)

  const { descriptor, errors: parseErrors } = parse(source, { filename: file })
  for (const e of parseErrors) errors.push(`${id}: SFC 解析失败 → ${e.message}`)
  if (parseErrors.length) continue

  try {
    // App.vue 这类只有模板的 SFC 没有 <script>，不是错误
    if (descriptor.script || descriptor.scriptSetup) {
      compileScript(descriptor, { id, inlineTemplate: true })
    }
  } catch (e) {
    errors.push(`${id}: <script setup> 编译失败 → ${e.message}`)
  }

  if (descriptor.template) {
    const { errors: tplErrors } = compileTemplate({
      id,
      filename: file,
      source: descriptor.template.content
    })
    for (const e of tplErrors) {
      errors.push(`${id}: 模板编译失败 → ${typeof e === 'string' ? e : e.message}`)
    }
  }

  /* ------------------------------------------------------------------------
     模板里用到的组件必须在本文件里 import 过。
     编译器对未解析的组件只报警告不报错，构建照样通过——结果是页面「少了一整块」
     却没人发现（真发生过：四个页面用了 <PageHero> 却没 import，大标题直接消失）。
     ------------------------------------------------------------------------ */
  if (descriptor.template) {
    const scriptSrc = `${descriptor.script?.content || ''}\n${descriptor.scriptSetup?.content || ''}`
    const tpl = descriptor.template.content.replace(/<!--[\s\S]*?-->/g, '')
    const tags = new Set([...tpl.matchAll(/<([A-Z][A-Za-z0-9]*)[\s/>]/g)].map((m) => m[1]))
    for (const tag of tags) {
      if (VUE_GLOBALS.has(tag)) continue
      const named = new RegExp(`import\\s+${tag}\\b`).test(scriptSrc)
      const braced = new RegExp(`import\\s*\\{[^}]*\\b${tag}\\b[^}]*\\}`).test(scriptSrc)
      if (!named && !braced) {
        errors.push(`${id}: 模板用到 <${tag}>，但本文件没有 import 它（会静默渲染成空）`)
      }
    }
  }
}

/* --------------------------------------------------------------------------
   2. 设计语言硬规则
   -------------------------------------------------------------------------- */
/**
 * 发丝环（0 0 0 1px）是「描边」的替代写法，不算投影；带模糊半径的一律算投影。
 * 唯一的例外是 --product-shadow：DESIGN.md 允许它出现在「躺在表面上的图片」上。
 */
function isHairlineRing(value) {
  const v = value.trim().toLowerCase()
  if (v === 'none') return true
  if (v.includes('var(--product-shadow)')) return true
  return /^0\s+0\s+0(\s|$)/.test(v) && !/\d+px\s+\d+px/.test(v)
}

const ALLOWED_HEX = new Set([
  '#fff', '#ffffff', '#000', '#000000',
  '#1d1d1f', '#0066cc', '#0071e3', '#2997ff',
  // Canvas 2D 读不到 CSS 变量，验证码底色只能写字面值（= --parchment）
  '#f5f5f7',
  // 在线状态的语义色（不是品牌色，不参与「单一强调色」规则）
  '#34c759', '#ff3b30', '#ff9f0a', '#8e8e93'
])

for (const file of [...vueFiles, ...cssFiles]) {
  const id = rel(file)
  const isCss = file.endsWith('.css')
  const lines = readFileSync(file, 'utf8').split(/\r?\n/)

  lines.forEach((line, i) => {
    const at = `${id}:${i + 1}`
    // 注释里的示例不算违规
    const code = line.replace(/\/\*.*?\*\//g, '').replace(/^\s*\/\/.*$/, '')

    if (/font-weight:\s*500\b/.test(code)) {
      errors.push(`${at} 出现 font-weight: 500（体例只有 300 / 400 / 600 / 700）`)
    }

    if (/(radial|conic)-gradient\(/.test(code)) {
      errors.push(`${at} 出现装饰性渐变（radial/conic），氛围应由表面色差或摄影承担`)
    }

    const shadow = code.match(/box-shadow:\s*([^;]+);?/)
    if (shadow && !isHairlineRing(shadow[1])) {
      errors.push(`${at} 出现投影 box-shadow: ${shadow[1].trim()}（卡片/按钮/文字不允许投影）`)
    }

    /* 入场动画只能用 fill-mode: backwards。
       both 会让末帧的 transform: none 永久生效，把元素自己的 :active scale(0.95)
       一起压掉——「按下去没有反馈」就是这么来的（曾经真的踩过）。 */
    if (/rise-in/.test(code) && /\bboth\b/.test(code)) {
      errors.push(`${at} 入场动画用了 fill-mode: both，会永久压制元素自身的 :active transform，应改用 backwards`)
    }

    if (/linear-gradient\(/.test(code) && !isCss) {
      warnings.push(`${at} 使用了 linear-gradient，请确认它不是装饰性渐变`)
    }

    for (const hex of code.match(/#[0-9a-fA-F]{3,8}\b/g) || []) {
      if (!isCss && !ALLOWED_HEX.has(hex.toLowerCase())) {
        warnings.push(`${at} 写死了颜色 ${hex}，请改用 var(--*) 令牌`)
      }
    }
  })
}

/* --------------------------------------------------------------------------
   2.1 纹路：必须「看得见」，且覆盖到每一种表面
   --------------------------------------------------------------------------
   用户明确反馈过「纹路太不明显」——浓度调低到几乎看不见等于没做，
   所以把下限写死在这里；同时要求浅色表面都挂上纹路（白块浮在纹路底上
   会像两张不同的纸）。
   -------------------------------------------------------------------------- */
{
  const css = readFileSync(resolve(srcRoot, 'styles.css'), 'utf8')

  const dotsOpacity = css.match(/--texture-dots:[\s\S]{0,400}?fill-opacity='([\d.]+)'/)
  const gridOpacity = css.match(/--texture-grid:[\s\S]{0,400}?stroke-opacity='([\d.]+)'/)
  if (!dotsOpacity) errors.push('styles.css: 缺少 --texture-dots（页面纹路）')
  if (!gridOpacity) errors.push('styles.css: 缺少 --texture-grid（深色表面纹路）')
  if (dotsOpacity && Number(dotsOpacity[1]) < 0.12) {
    errors.push(`styles.css: --texture-dots 浓度 ${dotsOpacity[1]} 太低，纹路会看不见（要求 ≥ 0.12）`)
  }
  if (gridOpacity && Number(gridOpacity[1]) < 0.1) {
    errors.push(`styles.css: --texture-grid 浓度 ${gridOpacity[1]} 太低（要求 ≥ 0.10）`)
  }

  // 每一种浅色表面都要带纹路：页面底、通栏瓦片、工具卡。
  // 解析成「选择器 → 声明块」再判断，而不是用字符串包含——
  // 字符串包含会被 `html,body,#app{margin:0}` 这种同名规则骗过。
  const rules = [...css.replace(/\/\*[\s\S]*?\*\//g, '').matchAll(/([^{}]+)\{([^{}]*)\}/g)]
    .map(([, selector, body]) => ({
      selectors: selector.split(',').map((s) => s.trim()),
      body
    }))

  const hasTexture = (selector, token) => rules.some((rule) =>
    rule.selectors.includes(selector)
    && new RegExp(`background-image:\\s*var\\(--texture-${token}\\)`).test(rule.body))

  for (const [label, selector, token] of [
    ['body', 'body', 'dots'],
    ['.tile', '.tile', 'dots'],
    ['.tile--parchment', '.tile--parchment', 'dots'],
    ['.panel', '.panel', 'dots-soft']
  ]) {
    if (!hasTexture(selector, token)) {
      errors.push(`styles.css: ${label} 没有挂上 ${token} 纹路（页面上不应存在无纹路的表面）`)
    }
  }
}

/* --------------------------------------------------------------------------
   2.2 认证页：近黑叙事栏 + 表单，不要回到空的半屏黑框
   -------------------------------------------------------------------------- */
for (const file of ['src/views/LoginView.vue', 'src/views/RegisterView.vue']) {
  const source = readFileSync(resolve(resolve(here, '..'), file), 'utf8')
  if (/hero-panel/.test(source)) {
    errors.push(`${file}: 又出现了 hero-panel（半屏黑框版式已废弃）`)
  }
  if (!/auth-stage/.test(source)) {
    errors.push(`${file}: 缺少 auth-stage（认证页身份感应由近黑叙事栏承担）`)
  }
  if (!/auth-page/.test(source)) {
    errors.push(`${file}: 缺少 auth-page 根布局`)
  }
}

/* --------------------------------------------------------------------------
   3. 页面骨架：用了 AppShell 的页面应当有一个通栏 PageHero
   -------------------------------------------------------------------------- */
const viewFiles = vueFiles.filter((f) => rel(f).includes('/views/'))
for (const file of viewFiles) {
  const id = rel(file)
  const source = readFileSync(file, 'utf8')
  const isAuthPage = /LoginView|RegisterView/.test(id)
  if (isAuthPage) continue

  if (!source.includes('AppShell')) {
    warnings.push(`${id} 没有使用 AppShell 骨架`)
    continue
  }
  if (!source.includes('PageHero')) {
    warnings.push(`${id} 没有通栏 PageHero：页面大标题会缺席`)
  }
}

/* --------------------------------------------------------------------------
   4. 汇总
   -------------------------------------------------------------------------- */
for (const w of warnings) console.log(`[提示] ${w}`)
console.log('')
for (const e of errors) console.log(`[失败] ${e}`)

console.log('')
console.log(`设计系统体检：${vueFiles.length} 个组件 · ${errors.length} 项失败 · ${warnings.length} 项提示`)

if (errors.length) {
  console.log('')
  console.log('规则见 docs/UI-REDESIGN.md 与 DESIGN.md。')
  process.exit(1)
}
