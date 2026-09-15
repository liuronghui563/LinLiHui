<template>
  <div class="comments">
    <button
      type="button"
      class="toggle btn btn--ghost btn--sm"
      :aria-expanded="open"
      @click="toggle"
    >
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.7"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
        class="toggle-icon"
      >
        <path d="M20 12a7.5 7.5 0 0 1-11 6.6L4 20l1.4-4.2A7.5 7.5 0 1 1 20 12Z" />
      </svg>
      评论 {{ total }}
    </button>

    <div v-if="open" class="box">
      <SkeletonList v-if="loading" :count="2" />

      <p v-else-if="error" class="error">{{ error }}</p>

      <p v-else-if="!comments.length" class="hint">还没有评论，来说点什么吧。</p>

      <ul v-else class="comment-list">
        <li v-for="item in comments" :key="item.id">
          <div class="comment-head">
            <PostAuthor
              :user-id="item.authorId"
              :name="item.authorName"
              :avatar="item.authorAvatar"
              size="xs"
            />
            <span class="comment-time">{{ relativeTime(item.createdAt) }}</span>
          </div>
          <p class="comment-body">{{ item.content }}</p>
          <button
            v-if="canDelete(item)"
            type="button"
            class="btn btn--quiet btn--sm link-danger"
            @click="onDelete(item)"
          >
            删除
          </button>
        </li>
      </ul>

      <form class="comment-form" @submit.prevent="onSubmit">
        <input
          v-model.trim="draft"
          class="input"
          maxlength="500"
          placeholder="写一条评论…"
          :disabled="sending"
        />
        <button type="submit" class="btn btn--primary btn--sm" :disabled="sending || !draft">
          {{ sending ? '发送中…' : '发送' }}
        </button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { addComment, deleteComment, fetchComments } from '../api/community'
import { useAuthStore } from '../stores/auth'
import { relativeTime } from '../utils/format'
import PostAuthor from './PostAuthor.vue'
import SkeletonList from './SkeletonList.vue'

const props = defineProps({
  postId: { type: [Number, String], required: true },
  count: { type: Number, default: 0 }
})

const emit = defineEmits(['change'])
const auth = useAuthStore()
const open = ref(false)
const loading = ref(false)
const sending = ref(false)
const comments = ref([])
const draft = ref('')
const error = ref('')

const total = computed(() => (comments.value.length || props.count || 0))

function canDelete(item) {
  return auth.isAdmin || item.authorId === auth.user?.id
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await fetchComments(props.postId)
    comments.value = res.data || []
    emit('change', comments.value.length)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function toggle() {
  open.value = !open.value
  if (open.value && !comments.value.length) load()
}

async function onSubmit() {
  sending.value = true
  error.value = ''
  try {
    const res = await addComment(props.postId, { content: draft.value })
    comments.value.push(res.data)
    draft.value = ''
    emit('change', comments.value.length, 10)
  } catch (e) {
    error.value = e.message
  } finally {
    sending.value = false
  }
}

async function onDelete(item) {
  if (!window.confirm('删除这条评论？')) return
  try {
    await deleteComment(item.id)
    comments.value = comments.value.filter((c) => c.id !== item.id)
    emit('change', comments.value.length, -10)
  } catch (e) {
    error.value = e.message
  }
}

watch(() => props.postId, () => {
  comments.value = []
  if (open.value) load()
})
</script>

<style scoped>
.comments {
  display: contents;
}

.toggle {
  font-variant-numeric: tabular-nums;
}

.toggle-icon {
  width: 16px;
  height: 16px;
}

/* 评论区是白卡里的一块「米白井」：靠表面色差分隔，不加描边也不加阴影 */
.box {
  flex-basis: 100%;
  margin-top: var(--sp-3);
  padding: var(--sp-4);
  background: var(--parchment);
  border-radius: var(--r-lg);
  display: grid;
  gap: var(--sp-3);
}

.comment-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: var(--sp-3);
}

.comment-list > li {
  display: grid;
  gap: 4px;
}

.comment-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
}

.comment-time {
  font-size: 12px;
  letter-spacing: -0.12px;
  color: var(--muted-2);
}

/* 评论内容属于正文：17px / 1.47 / -0.374px，不缩到 16px */
.comment-body {
  font-size: 17px;
  line-height: 1.47;
  letter-spacing: -0.374px;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 危险操作走「灰底小按钮 + 危险色文字」，不做红底大按钮 */
.link-danger,
.link-danger:hover {
  justify-self: start;
  color: var(--danger);
}

.link-danger:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.comment-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--sp-2);
}

.hint,
.error {
  font-size: 14px;
  letter-spacing: -0.224px;
  color: var(--muted);
}

.error {
  color: var(--danger);
}

@media (max-width: 520px) {
  .comment-form {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
