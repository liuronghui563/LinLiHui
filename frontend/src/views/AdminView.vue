<template>
  <AppShell title="管理台">
    <section class="panel">
      <p v-if="error" class="error">{{ error }}</p>
      <template v-else>
        <p>{{ data.greeting || '加载中…' }}</p>
        <p>当前注册用户数：{{ data.userCount ?? '-' }}</p>
      </template>
    </section>
  </AppShell>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import AppShell from '../layouts/AppShell.vue'
import { fetchAdminDashboard } from '../api/auth'

const data = reactive({ greeting: '', userCount: null })
const error = ref('')

onMounted(async () => {
  try {
    const res = await fetchAdminDashboard()
    Object.assign(data, res.data)
  } catch (e) {
    error.value = e.message
  }
})
</script>

<style scoped>
.panel {
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid var(--line);
  padding: 24px;
  line-height: 1.8;
}

.error {
  color: var(--danger);
}
</style>
