<template>
  <div class="uploader">
    <div class="thumbs">
      <div v-for="(url, index) in urls" :key="url" class="thumb">
        <img :src="url" alt="" @error="onThumbError($event)" />
        <button type="button" class="thumb-del" aria-label="删除图片" @click="removeAt(index)">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.7"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path d="M6 6l12 12M18 6 6 18" />
          </svg>
        </button>
      </div>
      <label v-if="urls.length < max" class="add" :class="{ 'is-busy': uploading }">
        <input
          type="file"
          accept="image/png,image/jpeg,image/webp"
          :multiple="max > 1"
          hidden
          :disabled="uploading"
          @change="onPick"
        />
        <span v-if="uploading">{{ progress }}%</span>
        <span v-else>{{ max === 1 ? '上传图片' : '添加图片' }}</span>
      </label>
    </div>
    <p v-if="hint" class="hint">{{ hint }}</p>
    <p v-if="error" class="err">{{ error }}</p>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { uploadFile } from '../api/file'

const props = defineProps({
  modelValue: { type: [Array, String], default: () => [] },
  purpose: { type: String, required: true },
  max: { type: Number, default: 9 }
})

const emit = defineEmits(['update:modelValue'])

/** 与 file-service 的 spring.servlet.multipart.max-file-size 保持一致 */
const MAX_BYTES = 8 * 1024 * 1024

const uploading = ref(false)
const progress = ref(0)
const error = ref('')

const urls = computed(() => {
  if (Array.isArray(props.modelValue)) return props.modelValue.filter(Boolean)
  return props.modelValue ? [props.modelValue] : []
})

const hint = computed(() => (
  `png / jpg / webp，单张不超过 8MB，最多 ${props.max} 张；大图会自动等比压缩后上传`
))

function commit(next) {
  emit('update:modelValue', props.max === 1 ? (next[0] || '') : next)
}

function removeAt(index) {
  const next = urls.value.filter((_, i) => i !== index)
  commit(next)
}

function onThumbError(event) {
  event.target.style.opacity = '0.35'
}

/** 各用途的等比压缩上限（长边像素）。头像不需要 1920px，封面才需要。 */
const MAX_EDGE = {
  avatar: 512,
  cover: 1920,
  post: 1600,
  aid: 1600,
  ad: 1920,
  adapply: 1920
}

/**
 * 上传前在浏览器里等比压缩。
 *
 * 为什么必须做：手机直出的照片动辄 3–8MB、4000px 宽，直接丢给后端会撞上
 * multipart 上限（表现为「主页背景图无法上传」），而且原图对展示毫无意义
 * ——封面最宽也就 1360px。
 *
 * 压不动就原样返回（老浏览器没有 createImageBitmap、或不是图片格式）：
 * 这一步是优化，不允许把「本来能传的文件」变成传不了。
 */
async function shrink(file, purpose) {
  const limit = MAX_EDGE[purpose] || 1600
  if (!file.type?.startsWith('image/') || typeof createImageBitmap !== 'function') return file
  try {
    const bitmap = await createImageBitmap(file)
    const scale = Math.min(1, limit / Math.max(bitmap.width, bitmap.height))
    // 本来就不大、也不超标：原样上传，避免无谓的重编码
    if (scale === 1 && file.size <= 1.2 * 1024 * 1024) {
      bitmap.close?.()
      return file
    }
    const width = Math.round(bitmap.width * scale)
    const height = Math.round(bitmap.height * scale)
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    canvas.getContext('2d').drawImage(bitmap, 0, 0, width, height)
    bitmap.close?.()
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.85))
    if (!blob || blob.size >= file.size) return file
    return new File([blob], 'upload.jpg', { type: 'image/jpeg' })
  } catch {
    return file
  }
}

async function onPick(event) {
  const files = [...(event.target.files || [])]
  event.target.value = ''
  if (!files.length) return
  error.value = ''
  const room = props.max - urls.value.length
  const picked = files.slice(0, room)
  uploading.value = true
  progress.value = 0
  const next = [...urls.value]
  try {
    for (const raw of picked) {
      const file = await shrink(raw, props.purpose)
      if (file.size > MAX_BYTES) {
        error.value = '图片太大（单张上限 8MB），请压缩后再上传'
        break
      }
      const res = await uploadFile(file, props.purpose, (p) => {
        progress.value = p
      })
      if (res.data?.url) next.push(res.data.url)
    }
    commit(next)
  } catch (e) {
    error.value = e.message || '上传失败'
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.uploader {
  display: grid;
  gap: var(--sp-2);
}

.thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2);
}

/* 缩略图统一 --r-sm 圆角 + 发丝线描边；虚线只留给「添加」这个上传入口 */
.thumb {
  position: relative;
  width: 76px;
  height: 76px;
  border-radius: var(--r-sm);
  border: 1px solid var(--line-strong);
  background: var(--pearl);
  overflow: hidden;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* 危险操作是「白底小圆钮 + 危险色图标」，不是红底色块 */
.thumb-del {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 22px;
  height: 22px;
  padding: 0;
  display: grid;
  place-items: center;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-pill);
  background: var(--canvas);
  color: var(--ink-2);
  cursor: pointer;
  transition: color 0.16s var(--ease), border-color 0.16s var(--ease),
    transform var(--press-out);
}

.thumb-del svg {
  width: 12px;
  height: 12px;
}

.thumb-del:hover {
  color: var(--danger);
  border-color: rgba(192, 69, 58, 0.35);
}

.thumb-del:active {
  transform: scale(0.96);
  transition: color var(--press-in), border-color var(--press-in),
    transform var(--press-in);
}

.add {
  width: 76px;
  height: 76px;
  display: grid;
  place-items: center;
  padding: 0;
  border-radius: var(--r-sm);
  border: 1px dashed var(--line-strong);
  background: transparent;
  color: var(--muted);
  font-size: 12px;
  letter-spacing: -0.12px;
  text-align: center;
  cursor: pointer;
  transition: color 0.16s var(--ease), border-color 0.16s var(--ease),
    transform var(--press-out);
}

.add:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.add:active {
  transform: scale(0.96);
  transition: color var(--press-in), border-color var(--press-in),
    transform var(--press-in);
}

.add.is-busy {
  cursor: wait;
  color: var(--accent);
  border-color: var(--accent);
}

.hint,
.err {
  margin: 0;
  font-size: 12px;
  line-height: 1.43;
  letter-spacing: -0.12px;
}

.hint {
  color: var(--muted-2);
}

.err {
  color: var(--danger);
}
</style>
