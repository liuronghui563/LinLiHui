<template>
  <button
    type="button"
    class="captcha-canvas-btn"
    :disabled="loading"
    :title="'点击刷新验证码'"
    @click="$emit('refresh')"
  >
    <canvas ref="canvasRef" :width="width" :height="height" aria-label="图形验证码" />
    <span v-if="loading" class="mask">加载中</span>
  </button>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'

const props = defineProps({
  challenge: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  width: { type: Number, default: 128 },
  height: { type: Number, default: 46 }
})

defineEmits(['refresh'])

const canvasRef = ref(null)

function rand(min, max) {
  return min + Math.random() * (max - min)
}

function draw(text) {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const { width, height } = props

  ctx.clearRect(0, 0, width, height)
  ctx.fillStyle = '#f3f7f5'
  ctx.fillRect(0, 0, width, height)

  for (let i = 0; i < 5; i += 1) {
    ctx.strokeStyle = `rgba(${rand(80, 160)}, ${rand(120, 180)}, ${rand(100, 160)}, 0.55)`
    ctx.beginPath()
    ctx.moveTo(rand(0, width), rand(0, height))
    ctx.lineTo(rand(0, width), rand(0, height))
    ctx.stroke()
  }

  for (let i = 0; i < 28; i += 1) {
    ctx.fillStyle = `rgba(${rand(120, 200)}, ${rand(140, 210)}, ${rand(120, 200)}, 0.7)`
    ctx.fillRect(rand(0, width), rand(0, height), 2, 2)
  }

  const chars = text.split('')
  const step = width / (chars.length + 1)
  chars.forEach((ch, index) => {
    const x = step * (index + 1)
    const y = height / 2 + rand(-4, 6)
    ctx.save()
    ctx.translate(x, y)
    ctx.rotate(rand(-0.35, 0.35))
    ctx.font = 'bold 24px "Noto Sans SC", sans-serif'
    ctx.fillStyle = `rgb(${rand(20, 90)}, ${rand(60, 120)}, ${rand(40, 100)})`
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(ch, 0, 0)
    ctx.restore()
  })
}

watch(
  () => [props.challenge, props.loading],
  async ([challenge, loading]) => {
    if (loading || !challenge) return
    await nextTick()
    draw(challenge)
  },
  { immediate: true }
)
</script>

<style scoped>
.captcha-canvas-btn {
  position: relative;
  width: 128px;
  height: 46px;
  padding: 0;
  border: 1px solid var(--line);
  background: #fff;
  cursor: pointer;
  overflow: hidden;
}

.captcha-canvas-btn:disabled {
  cursor: wait;
  opacity: 0.75;
}

canvas {
  display: block;
  width: 100%;
  height: 100%;
}

.mask {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.72);
  font-size: 12px;
  color: var(--muted);
}
</style>
