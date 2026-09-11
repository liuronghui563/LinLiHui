<template>
  <div class="shell">
    <aside class="side">
      <div class="brand brand-title">邻里汇</div>
      <nav>
        <router-link to="/">首页</router-link>
        <router-link to="/aids">邻里互助</router-link>
        <router-link to="/campus">校园专区</router-link>
        <router-link to="/community">邻里动态</router-link>
        <router-link to="/plaza">生活广场</router-link>
        <router-link v-if="auth.isAdmin" to="/admin">管理台</router-link>
      </nav>
      <button class="logout" type="button" @click="onLogout">退出登录</button>
    </aside>
    <main class="main">
      <header class="topbar">
        <div>
          <p class="eyebrow">邻里汇 · 互助同行</p>
          <h1>{{ title }}</h1>
        </div>
      </header>
      <slot />
    </main>
    <router-link
      class="user-entry"
      :to="auth.user?.id ? `/users/${auth.user.id}` : '/'"
      title="进入个人主页"
    >
      <UserAvatar :user="auth.user" size="sm" />
    </router-link>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import UserAvatar from '../components/UserAvatar.vue'

defineProps({
  title: { type: String, default: '首页' }
})

const auth = useAuthStore()
const router = useRouter()

onMounted(() => {
  if (auth.isLogin) {
    auth.loadProfile().catch(() => {})
  }
})

async function onLogout() {
  await auth.logout()
  router.replace('/login')
}
</script>

<style scoped>
.shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 228px 1fr;
  position: relative;
}

.side {
  background:
    radial-gradient(circle at 18% 0%, rgba(224, 122, 61, 0.32), transparent 42%),
    radial-gradient(circle at 90% 80%, rgba(61, 142, 166, 0.22), transparent 36%),
    linear-gradient(180deg, #243044, #2c3a52 58%, #3a2e38);
  color: #f7f1e8;
  padding: 28px 18px;
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.brand {
  font-size: 28px;
  padding: 0 10px;
}

nav {
  display: grid;
  gap: 6px;
}

nav a {
  padding: 12px 14px;
  color: rgba(243, 239, 230, 0.78);
  border-left: 3px solid transparent;
  border-radius: 0 12px 12px 0;
}

nav a.router-link-active {
  color: #fff;
  background: rgba(255, 255, 255, 0.1);
  border-left-color: #e07a3d;
}

.logout {
  margin-top: auto;
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: transparent;
  color: #fff;
  padding: 10px 12px;
  cursor: pointer;
  border-radius: 12px;
}

.main {
  padding: 28px clamp(18px, 4vw, 40px) 60px;
}

.topbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 28px;
  padding-right: 64px;
}

.eyebrow {
  margin: 0;
  color: var(--muted);
  letter-spacing: 0.12em;
  font-size: 12px;
}

h1 {
  margin: 8px 0 0;
  font-family: "ZCOOL XiaoWei", serif;
  font-size: 34px;
  font-weight: 400;
}

.user-entry {
  position: fixed;
  top: 14px;
  right: 16px;
  z-index: 50;
  display: flex;
  align-items: center;
  padding: 3px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid var(--line);
  border-radius: 50%;
  box-shadow: var(--shadow);
}

.user-meta,
.user-meta strong,
.user-meta span {
  display: none;
}

@media (max-width: 860px) {
  .shell {
    grid-template-columns: 1fr;
  }

  .side {
    gap: 14px;
  }

  nav {
    grid-auto-flow: column;
    grid-auto-columns: max-content;
    overflow-x: auto;
    padding-right: 56px;
  }
}
</style>
