# UI 重构规范（v2 · Apple 美术馆）

> 依据仓库根目录 `DESIGN.md` 落地。本文是**所有页面改版的唯一契约**：
> 先读本文，再读 `src/styles.css`、`src/layouts/AppShell.vue`、`src/components/PageHero.vue`，
> 然后按本文规则改写页面。

## 0. 一句话目标

把「仪表盘式的卡片堆叠」改成**美术馆策展**：大留白 + 通栏瓦片 + 发丝线 +
单一 Action Blue。界面后退，内容上前。

## 1. 三条不可动摇的规则

1. **只有一个强调色**：`--accent` (#0066cc)。深色瓦片上改用 `--accent-on-dark` (#2997ff)。
   不得引入第二个品牌色（紫色、金色、渐变都不行）。
2. **层级靠表面色差，不靠阴影**：白 `--canvas` / 米白 `--parchment` / 近黑 `--tile-1`。
   卡片、按钮、文字一律**不加 box-shadow**。全站唯一阴影 `--product-shadow`
   只留给「躺在表面上的图片」。
3. **体例只有 300 / 400 / 600 / 700**。没有 500。正文永远 17px / 1.47 / -0.374px；
   展示标题 600 + **负字距**（-0.28 → -0.374px）。

## 2. 页面结构（每个 AppShell 页面都长这样）

```
AppShell
├── #hero 插槽  …… 通栏瓦片（PageHero），可选但强烈建议
├── 默认插槽    …… 宽度 1120px 的内容区（.page 已内建 48px 上下、80px 下留白）
│    ├── subtitle（AppShell 的 prop）→ 一行 17px 灰引文
│    ├── 工具栏 / 分段控件
│    └── 内容：网格或双栏（主 1.6fr + 侧栏 1fr）
└── #band 插槽  …… 内容之后、页脚之前的通栏收口瓦片（近黑叙事 / 二次行动）
```

**#hero 与 #band 都在 `.page` 之外**，所以它们是真正通栏到屏幕边缘的瓦片。
默认插槽里的内容被 `.page` 收窄到 1120px——**不要在默认插槽里写 `.tile`**，
那会变成「内缩的巨卡」；需要通栏段落就放进 `#band`，需要卡片就用 `.panel--dark`。

顶层骨架的尺寸：全局导航 72px（`--nav-h`）、模块链接 16px、二级导航 60px
（`--subnav-h`，模块名 24px）。模块组用 `grid-template-columns: auto 1fr auto`
吃掉品牌与账号区之间的全部宽度，内部 `justify-content: space-evenly` 均匀分布——
不是挤成一小团挂在 Logo 后面；两侧区块宽度接近，整组因此仍是视觉居中的。
窄桌面 ≤1068px 收起昵称、链接降到 13px；≤833px 换回 `flex`（三列里有两列被隐藏后，
剩下的元素会停在错误的列上）。

`--nav-stack`（= `--nav-h + --subnav-h`）给吸顶侧栏与锚点留白用；
`src/router/scroll.js` 里的 `STICKY_OFFSET` 是 JS 常量，**必须手动同步**，
`npm run test:scroll` 会把两者对一遍，改了一处忘另一处会直接失败。

### 入场动画

三层，全部只做一次、不做循环：

| 层 | 位置 | 做法 |
|---|---|---|
| 整页淡入 | AppShell 的 `.main` | `animation: page-in`，**只动 opacity**——transform 会让 `.main` 成为后代 `fixed/sticky` 的包含块，页面里的吸顶侧栏会直接失效 |
| 卡片错峰 | 全局令牌层 | `:where(.aid-grid, .grid-auto, .goods-grid, .feed, .msg-list, .rel-list, .entry-grid, .circle-cards, .circle-grid, .info-grid, .tab-panel, .stat-strip) > *` 自动 `rise-in` 并按时序错峰，新页面用了这些容器就自带入场 |
| 显式编排 | `.enter-stagger` / `.enter-rise` | 需要自定义节奏时用 |

**fill-mode 只能是 `backwards`，不能用 `both`。** `both` 会让末帧的 `transform: none`
永久生效，把元素自己的 `:active scale(0.95)` 一起压掉——「按下去没有反馈」就是这么来的。
`npm run test:design` 会把这条规则当硬规则拦住。

**同一页里的筛选不要用「跳转」的语义。** 只换 query 的筛选（校园页切分区、
发现页切视角）必须保持滚动位置——策略在 `src/router/scroll.js`，
由 `npm run test:scroll` 钉住。曾经的 `scrollBehavior: () => ({ top: 0 })`
会让每一次筛选都把人从列表中间弹回页面顶端。

### 「我的」与「个人主页」是同一个页面

`/me` 是唯一的「我」页面：个人资料封面 + 我的动态 / 我发布的求助 + 常用入口。
访问自己的 `/users/{id}` 由守卫重定向到 `/me`
（`src/router/guards.js` 的 `ownProfileRedirect`，纯函数、可直接断言），
顶栏「我的」与右上角头像都指向 `/me`。看**别人**的主页仍然是 `/users/{别人的 id}`。

曾经两页的封面与统计高度重叠，顶栏「我的」走 `/me`、头像走 `/users/{自己}`，
两个入口落在不同 URL 上，看起来像两个页面。

```vue
<template #band>
  <section class="tile tile--dark">
    <div class="tile-inner">
      <h2 class="tile-title">…</h2>
      <p class="tile-lead">…</p>
      <router-link class="btn btn--on-dark" to="…">…</router-link>
    </div>
  </section>
</template>
```

**`title` 是模块名，不是页面大标题。** 二级导航里只放 2–4 个字的定位词
（如 `邻里`、`集市`、`我的`），页面的大标题交给 PageHero。这样二级导航
永远是一条安静的定位条，而不是每页都在喊同一句话。

### PageHero 用法

```vue
<template #hero>
  <PageHero
    eyebrow="邻里互助 · 城区"
    title="搭把手，让楼道更近一点"
    lead="把需要的、能给的写清楚，邻居就在隔壁。"
    tone="light"        <!-- light | parchment | dark -->
    size="display"      <!-- display 40px | hero 56px（仅首页级瓦片） -->
    align="center"      <!-- center（默认）| left -->
  >
    <template #actions>
      <router-link class="btn btn--primary" to="/neighbor/create">发布求助</router-link>
      <router-link class="btn btn--secondary" to="/neighbor">看看邻居需要什么</router-link>
    </template>

    <!-- 默认插槽放瓦片内的延伸内容，例如一条 stat-strip -->
    <div class="stat-strip">…</div>
  </PageHero>
</template>
```

**一页最多一个 PageHero。** 需要第二个瓦片时，用普通 `<section class="tile tile--dark">`
+ `.tile-inner`（见下方「通栏瓦片」）。

## 3. 节奏：交替音量的脉冲

同一页内部，区块之间的底色应交替（白 → 米白 → 近黑），**色差本身就是分隔线**，
不要再加圆角卡片去分段。典型脉冲：

```
米白英雄带 → 米白内容区（白卡）→ 近黑瓦片（数据/叙事）→ 米白页脚
```

列表页建议：`hero(parchment)` → 内容（米白底上的白卡）→ 结束。
详情页建议：`hero(parchment)` → 正文白卡 → 近黑瓦片放「联系方式/操作」。

### 英雄带只有两种：米白 / 近黑

**没有白色英雄带。** 页面顶部的白底英雄带会在内容之上形成一圈白框——被明确
反馈过两次后全部取消（发现 / 集市 / 回收 / 圈子 / 消息 / 广告位 / 设置 / 关系
都改成了米白）。

| tone | 用途 |
|---|---|
| `parchment` | **默认**。与页面底同色，等于「没有框」，标题直接坐在画布上 |
| `dark` | 有意的重音，只给「我的 / 个人主页 / 管理台」这类需要压场面的页面 |

白只作为**卡片**出现（`.panel`），不再作为**带**出现。`npm run test:shell`
会扫所有 `views/*.vue`，任何页面重新出现 `tone="light"` 直接判失败。

### 留白与纹路

竖向节奏收到 **瓦片 48px / 页面上下 24–48px / 卡片 24px**。原来的 80px 在只有
两三行内容的区块上会读成「空」而不是「安静」——留白要有内容配重才成立。

大面积空白再用一层**纹路**补质地，而不是继续塞内容。浓度分三档，因为
「看得见」和「不碍读」在两类表面上不是一回事：

| 令牌 | 浓度 | 用在哪 |
|---|---|---|
| `--texture-dots` | 墨点 **16%** / 20px 网格 | `body`、`.tile`（白瓦片）、`.tile--parchment` |
| `--texture-dots-soft` | 墨点 **9%** / 20px 网格 | `.panel`（工具卡里全是正文，纹路必须让位） |
| `--texture-grid` | 白线 **13%** / 28px 网格 | `.tile--dark` / `--dark-2` / `--dark-3` / `.tile--black`、`.panel--dark` |

**页面上不存在「没有纹路」的表面**——白块浮在纹路底上会像两张不同的纸。
用 SVG data-URI 而不是 CSS 渐变：1px 实边在高分屏上不糊，也不会触发
「禁止装饰性渐变」那条硬规则。

`npm run test:design` 对这条有**下限断言**：浓度低于 12%（浅底）/ 10%（深底）
直接判失败，并要求 `body` / `.tile` / `.tile--parchment` / `.panel` 都挂上纹路。
「几乎看不见」等于没做，这是被明确反馈过一次的事。

### 认证页（登录 / 注册）

**单栏居中 + 一枚字水印**，不是「半屏近黑 + 半屏表单」的两栏。

```
        [背景：页面米白 + 全站点阵 + 右上角一枚巨大的「邻」字水印 5%]
                        邻  邻里汇           ← 品牌行
                          欢迎回来           ← 全页唯一一句标题
        ┌──────────────────────────────┐
        │  密码登录 / 短信登录           │
        │  验证码闸门 → 凭据 → 提交      │   ← 一张白卡
        │  管理员内部码登录              │
        └──────────────────────────────┘
                     还没有账号？立即注册
                   演示账号 13800000000 / Admin+123
```

版式本身就是装饰：水印用 `clamp(240px, 34vw, 520px)` 出血在右侧，
`rgba(0,0,0,0.05)` 让它读起来是「质地」而不是「字」——因此不需要一句标语，
也不占版面。窄屏把水印缩小并挪到上方，否则它会从表单后面穿出来。

**认证页的文字量是刻意的下限**：品牌行 + 一句标题 + 表单标签 + 一行页脚 + 一行演示账号。
以前那半屏黑块里有一句大标语、一段三行说明、三行特性列表、两行演示账号卡，
信息量为零却占了半页。`npm run test:design` 会拦住「又加回 `hero-panel`」和
「水印被删掉」这两种回退。

### 封面图

`PageHero` 的 `cover` 属性给个人主页这类「需要一张自己的图」的场景：
传了就在表面之上铺背景图 + 可读性遮罩（浅底白遮罩、深底黑遮罩，
因为没有遮罩的文字压在照片上一定读不清），不传就是纯色 + 上面的纹路。

## 4. 全局类库（必须优先复用，不要再造轮子）

| 用途 | 类名 |
|---|---|
| 工具卡 | `.panel`（白底 1px hairline 18px 圆角 24px 内距，无阴影） |
| 近黑瓦片卡 | `.panel--dark`（内部标题/正文/链接已自动换成白与 Sky Link Blue） |
| 米白卡 | `.panel--flat` / `.panel--parchment` |
| 卡头 | `.panel-head`（左标题右 `.panel-more`） |
| 通栏瓦片 | `.tile` + `.tile--parchment/.tile--dark/.tile--black`，内层 `.tile-inner` |
| 瓦片标题 | `.tile-title`（40px/600）、`.tile-lead`（21px） |
| 按钮 | `.btn` + `.btn--primary`（蓝胶囊）/ `.btn--secondary`（描边胶囊）/ `.btn--ghost`（Pearl 胶囊）/ `.btn--quiet` / `.btn--danger` / `.btn--hero` / `.btn--sm` |
| 分段筛选 | `.segmented`（内含 `button[aria-pressed]`） |
| 标签 | `.tag` + `.tag--open/.tag--accepted/.tag--done/.tag--outline` |
| 统计 | `.stat` / `.stat-label` / `.stat-value` / `.stat-hint`；成组用 `.stat-strip`（可加 `.stat-strip--dark`） |
| 表单 | `.field` / `.field-label` / `.field-hint` / `.field-error` / `.input` / `.select` / `.textarea` / `.checkbox-row` |
| 元信息行 | `.meta-row` / `.meta-dot` |
| 列表 | `.list-plain` |
| 状态块 | `.state` + `.state-icon/.state-title/.state-desc/.state-action`，配 `StateBlock` 组件 |
| 布局 | `.stack` / `.row` / `.row-between` / `.grid-auto` / `.divider` |
| 文字 | `.eyebrow` / `.lead` / `.lead-airy` / `.lead-body` / `.caption` / `.fine-print` / `.link` |
| 动效 | 容器 `.enter-stagger`（子元素自动错峰）、元素 `.enter-rise` |

**允许新增页面级 scoped CSS，但只写「布局差异」**（列数、对齐、特殊排布）。
颜色、字号、圆角、间距、按钮外观一律走上面的全局类，不要重新定义。

## 5. 硬性禁止

- ❌ 渐变背景（`linear-gradient` 做装饰）、彩色光晕、玻璃拟态滥用（磨砂只给两条 sticky 栏）。
- ❌ 给卡片/按钮/文字加 `box-shadow`。
- ❌ `font-weight: 500`。
- ❌ 通栏瓦片加圆角（瓦片是矩形通栏的）。
- ❌ 引入第二个强调色；深色底上用 #0066cc 做链接（要用 #2997ff）。
- ❌ 用 emoji 当图标。图标统一：`viewBox="0 0 24 24"`、`fill="none"`、
  `stroke="currentColor"`、`stroke-width="1.7"`、`stroke-linecap/linejoin="round"`。
- ❌ 大面积染色卡片（整卡红/绿/黄）。状态用 `.tag` 或小圆点表达。
- ❌ 把正文改成 16px 或加大到 18px 以上。

## 6. 必须保持的行为契约（改版不等于重构）

- **脚本逻辑一行都不要动**：所有 `ref/reactive/computed/watch/onMounted`、
  API 调用、参数、路由跳转、校验、错误处理、`window.confirm` 一律原样保留。
- **模板里的事件绑定、`v-if` / `v-for` / `ref` / `:key` 一律保留**。
- **所有用户可见的既有功能文案保留其含义**（状态名、按钮动作、提示语）。
  可以润色语气，不可以改变含义或删除功能入口。
- **可访问性不得回退**：`aria-label`、`aria-pressed`、`aria-selected`、
  `role="tablist"` 等原样保留；`<button type="button">` 不要变成 `<div @click>`。
- **JS 依赖的钩子类名不得删除或改名**（例如 `PostFeed` 的 `.post-card`、
  `#post-{id}` 锚点、`.is-flash`、`DiscoverView` 的 `feedRef` 等）。
- **props 契约不变**：组件被调用时传入的 prop 名不要改（除非同时改所有调用方）。

## 7. 文案语气

- 大标题（PageHero.title）：短句、有人味、不喊口号。例：「搭把手，让楼道更近一点」。
- 引文（lead）：一句话说清这页能做什么。
- 二级导航 title：2–4 个字的名词（`邻里`、`集市`、`圈子`、`我的`、`消息`）。
- 不要用「立即」「马上」「快来」这类电商催促语。

## 8. 自检清单

- [ ] 页面有且仅有一个 PageHero（或一个明确的主瓦片）
- [ ] 二级导航 title 是 2–4 字模块名
- [ ] 没有新增颜色/字号/圆角的字面值（全部走 var(--*) 或全局类）
- [ ] 卡片无阴影、无渐变、无第二强调色
- [ ] 正文 17px、展示标题 600 + 负字距
- [ ] 深色区块内的链接是 `--accent-on-dark`
- [ ] 脚本逻辑、事件绑定、aria 属性与改版前一致
- [ ] 空态/加载/错误三态都还在（`StateBlock` / `SkeletonList`）
- [ ] 833px / 734px / 640px 三个断点下布局不溢出（网格降到 1 列）

## 9. 把规矩变成断言：`npm run test:design`、`test:shell`、`test:scroll`

肉眼守不住设计语言，所以规则写成了三套可执行体检。

```bash
cd frontend
npm run test:design   # 静态：SFC 编译 + 设计语言硬规则
npm run test:shell    # 运行时：SSR 渲染全部 14 个页面，断言骨架结构
npm run test:scroll   # 纯函数：路由滚动策略（同页筛选不弹回顶端）
```

### `test:design`（`tests/design-system.mjs`）

**硬规则（失败即退出码 1）**

| 规则 | 原因 |
|---|---|
| 每个 `.vue` 能被 `@vue/compiler-sfc` 解析并编译 | 「改完」的最低标准是能编译 |
| 不出现 `font-weight: 500` | 体例只有 300 / 400 / 600 / 700 |
| 不出现 `radial-gradient` / `conic-gradient` | 氛围由表面色差或摄影承担 |
| `box-shadow` 只能是 `none`、发丝环 `0 0 0 …` 或 `var(--product-shadow)` | 阴影只属于「躺在表面上的图片」 |
| 入场动画不得使用 `fill-mode: both` | 末帧的 `transform: none` 会永久压制 `:active scale(0.95)` |
| **模板里用到的组件必须在本文件 import 过** | 未解析的组件只报警告不报错，页面会静默少一整块 |

**软规则（提示，不失败）**

- `.vue` 里出现 `linear-gradient`（全局只允许 `styles.css` 的骨架扫光与下拉箭头）；
- `.vue` 里写死十六进制颜色（应走 `var(--*)`）；
- 某个 `views/` 页面没有使用 `PageHero`（页面大标题会缺席）。

`box-shadow: 0 0 0 1px` 这类**发丝环**是「描边」的替代写法，被当作合法值放行——
它没有模糊半径，不产生「浮起来」的观感，与投影是两件事。

### `test:shell`（`tests/shell-structure.mjs`）

用 Vite 的 SSR 模块图把每个页面**真渲染一遍**，断言骨架契约：

- 渲染不抛错（AppShell 是所有页面的地基，它一炸就是 14 个页面一起白屏）；
- 纯黑全局导航 / 二级导航 / `<main>` / 页脚各一份；
- **每页最多一个 `<h1>`**：页面大标题属于 `#hero` 里的 PageHero，
  二级导航里的模块名是 `<p class="subnav-title">`，不能也是 h1；
- 通栏瓦片真的渲染了出来，且 `tone` 映射到对应表面类
  （`dark → tile--dark`、`parchment → tile--parchment`、`light → 白`）；
- 详情页（`AidDetailView` / `UserHomeView`）无数据时 hero 由 `v-if` 守住，不渲染空瓦片；
- 校园页的分区切换**只出现一处**（曾经上半页一排分区、下半页又排一遍同样的
  「按种类筛选」，同一组控件出现两次，把内容推得很远）；
- 校园发帖区**没有外层白框**——书写台自己就是唯一表面
  （曾经它包在一层白色瓦片里，白底 + 左右零内距，等于给发帖区套了一圈白边）。

SSR 不执行 `onMounted`，所以渲染过程不产生任何网络请求；
`tests/stub-router.mjs` 顶替应用 router（每个 `api/*` 都会经 `http.js` 拉进它，
而它在 import 期就 `createWebHistory()`，需要 `window`）。

### `test:scroll`（`tests/router-scroll.mjs`）

`scrollBehavior` 与 `ownProfileRedirect` 都是纯函数，直接在 Node 里断言；
再补静态断言，确保 `router/index.js` 真的在用它俩，并校验 JS 常量与 CSS 变量同步。

| 场景 | 期望 |
|---|---|
| 浏览器前进/后退 | 回到原位置（压过其它规则） |
| 带 `#hash` | 滚到锚点，并留出 `STICKY_OFFSET` 给两条 sticky 栏 |
| **同一路由只换 query**（筛选、分区、分页） | **不滚动**（`return false`） |
| 真正换页面 | 回到顶部 |
| 访问自己的 `/users/{id}` | 重定向到 `/me`（`replace`，后退不在两页间打转） |
| 访问别人的 `/users/{id}` | 正常渲染 |
| `STICKY_OFFSET` vs `--nav-h + --subnav-h` | 必须相等（JS 与 CSS 无法互相引用） |
