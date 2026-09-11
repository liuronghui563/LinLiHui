<template>
  <section class="comments">
    <button type="button" class="toggle" @click="toggle">
      评论 {{ total }}
    </button>
    <div v-if="open" class="box">
      <p v-if="error" class="error">{{ error }}</p>
      <ul>
        <li v-for="item in comments" :key="item.id">
          <div class="head">
            <PostAuthor
              :user-id="item.authorId"
              :name="item.authorName"
              :avatar="item.authorAvatar"
            />
            <span>{{ formatTime(item.createdAt) }}</span>
          </div>
          <p>{{ item.content }}</p>
          <button v-if="canDelete(item)" type="button" class="del" @click="onDelete(item)">删除</button>
        </li>
        <li v-if="!comments.length && !loading" class="empty">还没有评论</li>
      </ul>
      <form @submit.prevent="onSubmit">
        <input v-model.trim="draft" maxlength="500" placeholder="写一条评论…" />
        <button type="submit" class="primary" :disabled="sending || !draft">发送</button>
      </form>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { addComment, deleteComment, fetchComments } from '../api/community'
import { useAuthStore } from '../stores/auth'
import PostAuthor from './PostAuthor.vue'

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

const total = computed(() => comments.value.length || props.count || 0)

function formatTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

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
  if (!confirm('删除这条评论？')) return
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
.toggle {
  border: 1px solid var(--line);
  background: #fff;
  padding: 6px 12px;
  border-radius: 999px;
  cursor: pointer;
  color: var(--ink);
}

.box {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

ul {
  list-style: none;
  margin: 0 0 12px;
  padding: 0;
  display: grid;
  gap: 10px;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--muted);
}

li p {
  margin: 4px 0 0;
  line-height: 1.6;
  white-space: pre-wrap;
}

.del {
  margin-top: 4px;
  border: none;
  background: transparent;
  color: var(--danger);
  padding: 0;
  cursor: pointer;
  font-size: 12px;
}

form {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
}

input {
  border: 1px solid var(--line);
  padding: 8px 12px;
  border-radius: 10px;
}

.primary {
  border: none;
  background: var(--accent);
  color: #fff;
  padding: 8px 14px;
  border-radius: 10px;
  cursor: pointer;
}

.empty,
.error {
  color: var(--muted);
  font-size: 13px;
}

.error {
  color: var(--danger);
}
</style>
