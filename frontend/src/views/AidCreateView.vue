<template>
  <AppShell :title="pageTitle">
    <form class="form" @submit.prevent="onSubmit">
      <label>
        <span>标题</span>
        <input v-model.trim="form.title" maxlength="100" :placeholder="isCampus ? '例如：食堂门口捡到一张校园卡' : '例如：帮忙搬一下快递'" />
      </label>
      <label>
        <span>分类</span>
        <select v-model="form.category">
          <option v-for="c in categories" :key="c" :value="c">{{ c }}</option>
        </select>
      </label>
      <label>
        <span>{{ isCampus ? '地点（可选）' : '地点' }}</span>
        <input
          v-model.trim="form.address"
          maxlength="200"
          :placeholder="isCampus ? '宿舍 / 教学楼 / 可不填' : '小区 / 楼栋 / 路口'"
        />
      </label>
      <label>
        <span>详情</span>
        <textarea
          v-model.trim="form.content"
          rows="6"
          maxlength="2000"
          :placeholder="isCampus ? '说明物品特征、丢失或捡到的地点和时间' : '说明需要什么帮助、时间等'"
        />
      </label>
      <p v-if="error" class="error">{{ error }}</p>
      <div class="actions">
        <router-link class="ghost" :to="backTo">返回列表</router-link>
        <button class="primary" type="submit" :disabled="submitting">
          {{ submitting ? '提交中…' : '发布' }}
        </button>
      </div>
    </form>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '../layouts/AppShell.vue'
import { createAid } from '../api/aid'

const NEIGHBOR_CATEGORIES = ['搬运', '代购', '陪护', '维修', '其他']
const CAMPUS_CATEGORIES = ['失物招领']

const router = useRouter()
const route = useRoute()
const isCampus = computed(() => String(route.query.board || '').toUpperCase() === 'CAMPUS')
const categories = computed(() => (isCampus.value ? CAMPUS_CATEGORIES : NEIGHBOR_CATEGORIES))
const pageTitle = computed(() => (isCampus.value ? '发布校园互助' : '发布求助'))
const backTo = computed(() => (isCampus.value ? { path: '/campus', query: { tab: 'aid' } } : '/aids'))

const form = reactive({
  title: '',
  category: '搬运',
  address: '',
  content: ''
})
const submitting = ref(false)
const error = ref('')

function applyQuery() {
  const incoming = String(route.query.category || '')
  if (categories.value.includes(incoming)) {
    form.category = incoming
  } else {
    form.category = categories.value[0]
  }
}

onMounted(applyQuery)

watch(() => [route.query.board, route.query.category], applyQuery)

async function onSubmit() {
  error.value = ''
  if (!form.title) {
    error.value = '标题不能为空'
    return
  }
  if (!form.content) {
    error.value = '内容不能为空'
    return
  }
  if (!isCampus.value && !form.address) {
    error.value = '地址不能为空'
    return
  }
  submitting.value = true
  try {
    const payload = {
      title: form.title,
      category: form.category,
      address: form.address,
      content: form.content,
      board: isCampus.value ? 'CAMPUS' : 'NEIGHBORHOOD'
    }
    const res = await createAid(payload)
    router.replace(`/aids/${res.data.id}`)
  } catch (e) {
    error.value = e.message
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.form {
  max-width: 640px;
  display: grid;
  gap: 14px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid var(--line);
  padding: 24px;
  border-radius: var(--radius);
  box-shadow: var(--shadow-soft);
}

label {
  display: grid;
  gap: 8px;
}

label span {
  font-size: 13px;
  color: var(--muted);
}

input,
select,
textarea {
  width: 100%;
  border: 1px solid var(--line);
  background: #fff;
  padding: 12px 14px;
  font: inherit;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.primary,
.ghost {
  padding: 12px 18px;
  border: none;
  cursor: pointer;
  text-decoration: none;
}

.primary {
  background: var(--accent);
  color: #fff;
  font-weight: 600;
}

.ghost {
  border: 1px solid var(--line);
  background: #fff;
  color: var(--ink);
}

.error {
  margin: 0;
  color: var(--danger);
}
</style>
