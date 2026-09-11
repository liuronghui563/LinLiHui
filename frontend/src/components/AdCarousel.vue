<template>
  <section class="carousel" @mouseenter="pause" @mouseleave="resume">
    <div v-if="loading" class="placeholder">广告加载中…</div>
    <div v-else-if="!ads.length" class="placeholder">暂无广告</div>
    <template v-else>
      <a
        class="slide"
        :href="current.linkUrl || 'javascript:void(0)'"
        :target="current.linkUrl ? '_blank' : undefined"
        rel="noopener"
        @click="onClick"
      >
        <img :src="current.imageUrl" :alt="current.title" :key="current.imageUrl" />
        <div class="caption">
          <h3>{{ current.title }}</h3>
          <p v-if="current.subtitle">{{ current.subtitle }}</p>
        </div>
      </a>
      <button type="button" class="nav prev" aria-label="上一张" @click.stop="prev">‹</button>
      <button type="button" class="nav next" aria-label="下一张" @click.stop="next">›</button>
      <div class="dots">
        <button
          v-for="(ad, i) in ads"
          :key="ad.id"
          type="button"
          :class="{ active: i === index }"
          :aria-label="`广告 ${i + 1}`"
          @click.stop="go(i)"
        />
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { clickAd, fetchAdCarousel } from '../api/ad'
import { attachWebImages } from '../utils/webImages'

const ads = ref([])
const index = ref(0)
const loading = ref(true)
let timer = null

const current = computed(() => ads.value[index.value] || {})

async function load() {
  loading.value = true
  try {
    const res = await fetchAdCarousel()
    ads.value = await attachWebImages(res.data || [])
    index.value = 0
    resume()
  } catch {
    ads.value = []
  } finally {
    loading.value = false
  }
}

function go(i) {
  index.value = i
}

function next() {
  if (!ads.value.length) return
  index.value = (index.value + 1) % ads.value.length
}

function prev() {
  if (!ads.value.length) return
  index.value = (index.value - 1 + ads.value.length) % ads.value.length
}

function pause() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function resume() {
  pause()
  if (ads.value.length > 1) {
    timer = setInterval(next, 4500)
  }
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
.carousel {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--line);
  background: #243044;
  min-height: 220px;
  margin-bottom: 22px;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

.slide {
  display: block;
  position: relative;
  color: #fff;
  min-height: 220px;
}

.slide img {
  width: 100%;
  height: 300px;
  object-fit: cover;
  display: block;
  filter: brightness(0.78);
}

.caption {
  position: absolute;
  left: 28px;
  bottom: 28px;
  right: 28px;
  max-width: 520px;
}

.caption h3 {
  margin: 0;
  font-family: "ZCOOL XiaoWei", serif;
  font-size: 32px;
  font-weight: 400;
}

.caption p {
  margin: 8px 0 0;
  opacity: 0.9;
  line-height: 1.5;
}

.nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 36px;
  height: 36px;
  border: none;
  background: rgba(0, 0, 0, 0.35);
  color: #fff;
  font-size: 24px;
  cursor: pointer;
}

.prev {
  left: 12px;
}

.next {
  right: 12px;
}

.dots {
  position: absolute;
  left: 50%;
  bottom: 12px;
  transform: translateX(-50%);
  display: flex;
  gap: 8px;
}

.dots button {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.45);
  padding: 0;
  cursor: pointer;
}

.dots button.active {
  background: #fff;
}

.placeholder {
  min-height: 180px;
  display: grid;
  place-items: center;
  color: rgba(255, 255, 255, 0.75);
}

@media (max-width: 640px) {
  .slide img {
    height: 200px;
  }

  .caption h3 {
    font-size: 24px;
  }
}
</style>
