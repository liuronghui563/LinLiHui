<template>
  <section v-if="ads.length" class="carousel" @mouseenter="pause" @mouseleave="resume">
    <a
      class="slide"
      :href="current.linkUrl || undefined"
      :target="current.linkUrl ? '_blank' : undefined"
      rel="noopener"
      @click="onClick"
    >
      <img
        v-if="current.imageUrl && !imageFailed"
        :key="current.imageUrl"
        :src="current.imageUrl"
        :alt="current.title"
        @error="imageFailed = true"
      />
      <span v-else class="slide-fallback" aria-hidden="true" />
      <div class="caption">
        <span class="tag tag--outline caption-tag">推广</span>
        <h3>{{ current.title }}</h3>
        <p v-if="current.subtitle">{{ current.subtitle }}</p>
      </div>
    </a>

    <button v-if="ads.length > 1" type="button" class="nav prev" aria-label="上一张" @click.stop="prev">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="m14 6-6 6 6 6" /></svg>
    </button>
    <button v-if="ads.length > 1" type="button" class="nav next" aria-label="下一张" @click.stop="next">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="m10 6 6 6-6 6" /></svg>
    </button>

    <div v-if="ads.length > 1" class="dots">
      <button
        v-for="(ad, i) in ads"
        :key="ad.id"
        type="button"
        :class="{ active: i === index }"
        :aria-label="`第 ${i + 1} 张`"
        @click.stop="go(i)"
      />
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { clickAd, fetchAdCarousel } from '../api/ad'

/**
 * 广告轮播。
 *
 * 改动要点：
 * 1. 去掉了此前通过 `attachWebImages` 在浏览器里请求 picsum 列表、再用随机图
 *    覆盖后端 imageUrl 的做法——既多了一次外网请求（网络受限时会卡住首屏），
 *    又让运营在后台配的图完全失效。
 * 2. 图片加载失败时回退为渐变底，不再出现裂图。
 * 3. 视觉降级为「窄条横幅」：广告不应比站内真实内容更抢眼。
 */
const ads = ref([])
const index = ref(0)
const imageFailed = ref(false)
let timer = null

const current = computed(() => ads.value[index.value] || {})

watch(current, () => {
  imageFailed.value = false
})

async function load() {
  try {
    const res = await fetchAdCarousel()
    ads.value = res.data || []
    index.value = 0
    resume()
  } catch {
    ads.value = []
  }
}

function go(i) {
  index.value = i
}

function next() {
  if (ads.value.length) index.value = (index.value + 1) % ads.value.length
}

function prev() {
  if (ads.value.length) index.value = (index.value - 1 + ads.value.length) % ads.value.length
}

function pause() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function resume() {
  pause()
  if (ads.value.length > 1) timer = setInterval(next, 5200)
}

async function onClick(e) {
  const ad = current.value
  if (!ad?.id) return
  if (!ad.linkUrl) e.preventDefault()
  try {
    await clickAd(ad.id)
  } catch {
    // 点击统计失败不影响跳转
  }
}

onMounted(load)
onUnmounted(pause)
</script>

<style scoped>
/* 广告瓦片：一条克制的横幅。广告不该比站内真实内容更抢眼，
   所以它没有阴影、没有渐变，只有一张照片 + 一段压在照片上的文案。 */
.carousel {
  position: relative;
  overflow: hidden;
  border-radius: var(--r-lg);
  background: var(--tile-1);
}

.slide {
  display: block;
  position: relative;
  min-height: 340px;
  color: #fff;
}

.slide img,
.slide-fallback {
  width: 100%;
  height: 340px;
  object-fit: cover;
  display: block;
  filter: brightness(0.78);
}

.slide-fallback {
  background: var(--tile-1);
  filter: none;
}

.caption {
  position: absolute;
  left: var(--sp-7);
  right: var(--sp-7);
  bottom: var(--sp-6);
  max-width: 40ch;
  display: grid;
  gap: var(--sp-2);
}

/* 压在图上的「推广」小胶囊：半透明灰片，读得出但不抢文案 */
.caption-tag {
  justify-self: start;
  color: var(--ink);
  background: rgba(210, 210, 215, 0.64);
  backdrop-filter: saturate(180%) blur(20px);
  border-color: transparent;
}

.caption h3 {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 600;
  line-height: 1.14;
  letter-spacing: -0.374px;
  color: #fff;
}

.caption p {
  font-size: 14px;
  line-height: 1.5;
  letter-spacing: -0.224px;
  color: rgba(255, 255, 255, 0.86);
}

/* 悬浮在照片上的圆形控件：44×44，半透明灰片（button-icon-circular） */
.nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: rgba(210, 210, 215, 0.64);
  backdrop-filter: saturate(180%) blur(20px);
  color: var(--ink);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s var(--ease), background-color 0.2s var(--ease),
    transform var(--press-out);
}

.carousel:hover .nav,
.nav:focus-visible {
  opacity: 1;
}

.nav:hover {
  background: rgba(210, 210, 215, 0.88);
}

.nav:active {
  transform: translateY(-50%) scale(0.96);
  transition: opacity var(--press-in), background-color var(--press-in),
    transform var(--press-in);
}

.nav svg {
  width: 18px;
  height: 18px;
}

.prev { left: var(--sp-4); }
.next { right: var(--sp-4); }

.dots {
  position: absolute;
  right: var(--sp-6);
  bottom: var(--sp-5);
  display: flex;
  gap: 6px;
}

.dots button {
  width: 18px;
  height: 4px;
  border: 0;
  padding: 0;
  border-radius: var(--r-pill);
  background: rgba(255, 255, 255, 0.42);
  cursor: pointer;
  transition: background-color 0.2s var(--ease), width 0.2s var(--ease);
}

.dots button.active {
  width: 30px;
  background: #fff;
}

@media (max-width: 734px) {
  .slide,
  .slide img,
  .slide-fallback {
    height: 240px;
    min-height: 240px;
  }

  .caption {
    left: var(--sp-5);
    right: var(--sp-5);
    bottom: var(--sp-5);
  }

  .caption h3 { font-size: 21px; }
  .dots { display: none; }
}
</style>
