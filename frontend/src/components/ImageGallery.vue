<template>
  <div v-if="images.length" class="gallery">
    <button
      v-for="(url, index) in images"
      :key="url + index"
      type="button"
      class="thumb"
      :aria-label="`查看第 ${index + 1} 张图片`"
      @click="open = index"
    >
      <img :src="url" alt="" @error="onError" />
    </button>
  </div>

  <div v-if="open !== null" class="lightbox" @click.self="open = null">
    <img :src="images[open]" alt="" />
    <button type="button" class="btn btn--sm btn--on-dark close" @click="open = null">关闭</button>
  </div>
</template>

<script setup>
import { ref } from 'vue'

defineProps({
  images: { type: Array, default: () => [] }
})

const open = ref(null)

function onError(event) {
  event.target.style.opacity = '0.3'
}
</script>

<style scoped>
.gallery {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(88px, 1fr));
  gap: var(--sp-2);
}

/* 缩略图：内嵌配图走 --r-sm（圆角档位里最小的一档）+ 发丝线。
   类名用 .thumb 而不是 .tile——.tile 已经是全局的「通栏瓦片」，撞名会让
   通栏瓦片的 80px 内距悄悄落到缩略图上。 */
.thumb {
  padding: 0;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-sm);
  overflow: hidden;
  background: var(--pearl);
  color: var(--ink);
  cursor: zoom-in;
  aspect-ratio: 1;
  transition: border-color 0.16s var(--ease), transform var(--press-out);
}

.thumb:hover {
  border-color: var(--accent);
}

.thumb:active {
  transform: scale(0.96);
  transition: border-color var(--press-in), transform var(--press-in);
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* 全站唯一的 product-shadow 留给「躺在表面上的图片」——灯箱正是这个场景 */
.lightbox {
  position: fixed;
  inset: 0;
  z-index: var(--z-overlay);
  display: grid;
  place-items: center;
  background: rgba(0, 0, 0, 0.72);
  padding: var(--sp-6);
}

.lightbox img {
  max-width: min(920px, 92vw);
  max-height: 82vh;
  border-radius: var(--r-sm);
  box-shadow: var(--product-shadow);
}

.close {
  position: absolute;
  top: var(--sp-4);
  right: var(--sp-4);
}
</style>
