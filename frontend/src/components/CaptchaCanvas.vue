<template>
  <button
    type="button"
    class="captcha-btn"
    :disabled="loading"
    title="看不清？点击换一张"
    :aria-label="`图形验证码，点击刷新。当前为 ${challenge || '加载中'}`"
    @click="$emit('refresh')"
  >
    <canvas ref="canvasRef" :width="width" :height="height" />
    <span v-if="loading" class="mask">加载中</span>
    <span v-else class="refresh-hint" aria-hidden="true">
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.7"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <path d="M20 11a8 8 0 1 0-.9 4.2" />
        <path d="M20 5v6h-6" />
      </svg>
    </span>
  </button>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'

/**
 * 图形验证码画布。
 *
 * 改动：字体从写死的 "Noto Sans SC"（外链字体，实际未加载）改为系统字体栈，
 * 保证字形稳定；补上刷新提示与无障碍标签——此前只有一个无语义的 canvas。
 */
const props = defineProps({
  challenge: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  width: { type: Number, default: 128 },
  height: { type: Number, default: 46 }
})

defineEmits(['refresh'])

const canvasRef = ref(null)

const FONT_STACK = 'system-ui, "Segoe UI", "Microsoft YaHei", "PingFang SC", sans-serif'

function rand(min, max) {
  return min + Math.random() * (max - min)
}

function draw(text) {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const { width, height } = props

  ctx.clearRect(0, 0, width, height)

  // 底色用扁平米白：全站没有装饰性渐变，验证码图也不该例外。
  // 干扰线与噪点一律取中性灰，字符取近黑——对比度留给「人看得清」，
  // 干扰靠形状与旋转，而不是靠另加一套暖棕色系。
  ctx.fillStyle = '#f5f5f7'
  ctx.fillRect(0, 0, width, height)

  for (let i = 0; i < 4; i += 1) {
    ctx.strokeStyle = `rgba(0, 0, 0, ${rand(0.1, 0.18)})`
    ctx.lineWidth = rand(0.8, 1.4)
    ctx.beginPath()
    ctx.moveTo(rand(0, width), rand(0, height))
    ctx.lineTo(rand(0, width), rand(0, height))
    ctx.stroke()
  }

  for (let i = 0; i < 26; i += 1) {
    ctx.fillStyle = `rgba(0, 0, 0, ${rand(0.14, 0.28)})`
    ctx.fillRect(rand(0, width), rand(0, height), 1.6, 1.6)
  }

  const chars = text.split('')
  const step = width / (chars.length + 1)
  chars.forEach((ch, index) => {
    const x = step * (index + 1)
    const y = height / 2 + rand(-4, 5)
    ctx.save()
    ctx.translate(x, y)
    ctx.rotate(rand(-0.32, 0.32))
    ctx.font = `700 23px ${FONT_STACK}`
    ctx.fillStyle = `rgb(${rand(24, 62)}, ${rand(26, 64)}, ${rand(30, 68)})`
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
/* 只写这块画布的尺寸与描边；悬停换描边为强调色，按下沿用 scale(0.95) */
.captcha-btn {
  position: relative;
  width: 128px;
  height: 46px;
  padding: 0;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-md);
  background: var(--canvas);
  cursor: pointer;
  overflow: hidden;
  transition: border-color 0.16s var(--ease), transform var(--press-out);
}

.captcha-btn:hover:not(:disabled) {
  border-color: var(--accent);
}

.captcha-btn:active:not(:disabled) {
  transform: scale(0.96);
  transition: border-color var(--press-in), transform var(--press-in);
}

.captcha-btn:disabled {
  cursor: wait;
  opacity: 0.6;
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
  background: rgba(255, 255, 255, 0.86);
  font-size: 12px;
  letter-spacing: -0.12px;
  color: var(--muted);
}

.refresh-hint {
  position: absolute;
  right: 4px;
  bottom: 4px;
  width: 16px;
  height: 16px;
  display: grid;
  place-items: center;
  border-radius: var(--r-pill);
  background: rgba(255, 255, 255, 0.9);
  color: var(--muted-2);
  opacity: 0;
  transition: opacity 0.16s var(--ease);
}

.refresh-hint svg {
  width: 11px;
  height: 11px;
}

.captcha-btn:hover .refresh-hint {
  opacity: 1;
}
</style>
