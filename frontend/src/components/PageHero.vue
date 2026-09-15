<template>
  <section class="hero tile" :class="[toneClass, `hero--${align}`, `hero--${size}`, { 'hero--cover': !!cover }]">
    <!-- 封面图：只在设置了背景图时出现。它压在表面之上、内容之下，
         并带一层可读性遮罩——文字始终压在图上，没有遮罩就读不清。 -->
    <div v-if="cover" class="hero-cover" :style="{ backgroundImage: `url(${cover})` }" aria-hidden="true" />

    <div class="hero-inner tile-inner">
      <p v-if="eyebrow" class="eyebrow hero-eyebrow">{{ eyebrow }}</p>
      <h1 v-if="title" class="hero-title">{{ title }}</h1>
      <p v-if="lead" class="hero-lead">{{ lead }}</p>

      <div v-if="$slots.actions" class="hero-actions">
        <slot name="actions" />
      </div>

      <div v-if="$slots.default" class="hero-extra">
        <slot />
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

/**
 * 通栏瓦片英雄区。
 *
 * 这是整套设计里唯一「大声说话」的地方：一个瓦片、一个大标题、一句副文案、
 * 两枚胶囊按钮。其余界面全部后退。瓦片没有圆角、没有描边、没有阴影——
 * 白 / 米白 / 近黑之间的色差本身就是分隔线。
 *
 * tone 决定表面：light（白）→ parchment（米白）→ dark（近黑）。
 * 相邻页面若音量大，就在同一页里交替 tone，形成「光的脉冲」。
 *
 * cover 给个人主页这类需要「一张自己的图」的场景：传了就在表面之上铺一层
 * 背景图 + 可读性遮罩；不传则是纯色 + 全站纹路（令牌层给的）。
 */
const props = defineProps({
  title: { type: String, default: '' },
  lead: { type: String, default: '' },
  eyebrow: { type: String, default: '' },
  tone: { type: String, default: 'light' }, // light | parchment | dark
  size: { type: String, default: 'display' }, // display | hero
  align: { type: String, default: 'center' }, // center | left
  /** 封面图地址（本站相对路径）。为空表示不设封面。 */
  cover: { type: String, default: '' }
})

const toneClass = computed(() => (props.tone === 'dark' ? 'tile--dark' : props.tone === 'parchment' ? 'tile--parchment' : ''))
</script>

<style scoped>
.hero {
  position: relative;
  overflow: hidden;               /* 封面图不越出瓦片 */
  padding-top: var(--sp-8);
  padding-bottom: var(--sp-8);
}

/* 封面层：压在瓦片底色之上、内容之下。
   遮罩用一层半透明黑（不是渐变）——只为可读性，不是装饰。
   没有封面时这一层不存在，表面就回到纯色 + 令牌里的细线网格。 */
.hero-cover {
  position: absolute;
  inset: 0;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}

/* 封面层的可读性遮罩。深浅两套：文字压在照片上，没有遮罩就一定读不清。
   浅底用白色遮罩（正文是墨色），深底用黑色遮罩（正文是白色）。 */
.hero--cover::after {
  content: "";
  position: absolute;
  inset: 0;
  background: rgba(255, 255, 255, 0.72);
  pointer-events: none;
}

.hero--cover.tile--dark::after {
  background: rgba(0, 0, 0, 0.46);
}

.hero-inner {
  position: relative;             /* 压在封面层之上 */
  display: grid;
  gap: var(--sp-4);
  z-index: 1;
}

/* 居中：产品瓦片的默认站姿（Apple 的目录页几乎都是居中） */
.hero--center .hero-inner {
  justify-items: center;
  text-align: center;
}

.hero--left .hero-inner {
  justify-items: start;
  text-align: left;
}

.hero-eyebrow {
  margin: 0;
}

.hero-title {
  font-family: var(--font-display);
  font-weight: 600;
  font-size: 40px;
  line-height: 1.1;
  letter-spacing: -0.374px;   /* 负字距是这个腔调的核心 */
  max-width: 22ch;
}

.hero--center .hero-title { max-width: 26ch; }

/* size="hero"：56px / 1.07 / -0.28px，留给首页级别的瓦片 */
.hero--hero .hero-title {
  font-size: 56px;
  line-height: 1.07;
  letter-spacing: -0.28px;
}

.hero-lead {
  font-size: 21px;
  font-weight: 400;
  line-height: 1.45;
  letter-spacing: 0.231px;
  color: var(--muted);
  max-width: 46ch;
}

.hero--center .hero-lead { max-width: 52ch; }

.tile--dark .hero-lead { color: var(--on-dark-muted); }

.hero-actions {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex-wrap: wrap;
  margin-top: var(--sp-3);
}

.hero-extra {
  width: 100%;
  margin-top: var(--sp-6);
}

@media (max-width: 734px) {
  .hero-title,
  .hero--hero .hero-title { font-size: 34px; letter-spacing: -0.28px; }
  .hero-lead { font-size: 17px; line-height: 1.47; letter-spacing: -0.374px; }
  .hero { padding-top: var(--sp-7); padding-bottom: var(--sp-7); }
}

@media (max-width: 419px) {
  .hero-title,
  .hero--hero .hero-title { font-size: 28px; }
}
</style>
