<template>
  <AppShell title="个人信息设置">
    <p class="lead">在这里更新对外展示的邻里资料。保存后可在个人主页查看分类信息。</p>
    <p v-if="error" class="error">{{ error }}</p>
    <form class="editor" @submit.prevent="onSaveProfile">
      <div class="fields">
        <label>
          <span>昵称</span>
          <input v-model.trim="form.nickname" maxlength="50" placeholder="邻里称呼" />
        </label>
        <label>
          <span>真实姓名</span>
          <input v-model.trim="form.realName" maxlength="30" placeholder="仅自己可见" />
        </label>
        <label>
          <span>当前状态</span>
          <select v-model="form.presenceStatus">
            <option v-for="item in PRESENCE_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </label>
        <label>
          <span>性别</span>
          <select v-model="form.gender">
            <option v-for="item in GENDER_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </label>
        <label class="wide">
          <span>简介</span>
          <input v-model.trim="form.bio" maxlength="200" placeholder="介绍一下自己" />
        </label>
        <label class="wide">
          <span>头像地址（可留空使用随机头像）</span>
          <input v-model.trim="form.avatar" maxlength="255" placeholder="https://..." />
        </label>
        <label>
          <span>城市</span>
          <input v-model.trim="form.city" maxlength="50" placeholder="所在城市" />
        </label>
        <label>
          <span>小区 / 社区</span>
          <input v-model.trim="form.neighborhood" maxlength="80" placeholder="例如：梧桐里" />
        </label>
        <label>
          <span>微信号</span>
          <input v-model.trim="form.wechat" maxlength="50" placeholder="方便邻居联系" />
        </label>
        <div class="check">
          <span>学生身份</span>
          <label class="switch">
            <input v-model="form.student" type="checkbox" />
            <span>我是在校学生</span>
          </label>
        </div>
        <div class="check wide">
          <span>隐私账户</span>
          <label class="switch">
            <input v-model="form.privateAccount" type="checkbox" />
            <span>开启后，其他用户无法进入我的个人主页</span>
          </label>
        </div>
        <label>
          <span>学校</span>
          <input v-model.trim="form.school" maxlength="80" placeholder="学校名称" />
        </label>
        <label>
          <span>专业</span>
          <input v-model.trim="form.major" maxlength="80" placeholder="所学专业" />
        </label>
        <label>
          <span>年级</span>
          <input v-model.trim="form.grade" maxlength="30" placeholder="例如：大三 / 研一" />
        </label>
      </div>
      <div class="actions">
        <button type="submit" class="primary" :disabled="saving">
          {{ saving ? '保存中…' : '保存资料' }}
        </button>
        <router-link class="ghost" to="/">返回首页</router-link>
      </div>
      <p v-if="profileMsg" class="ok">{{ profileMsg }}</p>
    </form>
  </AppShell>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import AppShell from '../layouts/AppShell.vue'
import { updateUserProfile } from '../api/user'
import { useAuthStore } from '../stores/auth'
import { GENDER_OPTIONS, PRESENCE_OPTIONS } from '../utils/presence'

const auth = useAuthStore()
const saving = ref(false)
const profileMsg = ref('')
const error = ref('')
const form = reactive({
  nickname: auth.user?.nickname || '',
  realName: auth.user?.realName || '',
  bio: auth.user?.bio || '',
  avatar: auth.user?.avatar || '',
  presenceStatus: auth.user?.presenceStatus || 'ONLINE',
  gender: auth.user?.gender || 'UNKNOWN',
  city: auth.user?.city || '',
  neighborhood: auth.user?.neighborhood || '',
  school: auth.user?.school || '',
  major: auth.user?.major || '',
  grade: auth.user?.grade || '',
  student: Boolean(auth.user?.student),
  privateAccount: Boolean(auth.user?.privateAccount),
  wechat: auth.user?.wechat || ''
})

function fillForm(user) {
  if (!user) return
  form.nickname = user.nickname || ''
  form.realName = user.realName || ''
  form.bio = user.bio || ''
  form.avatar = user.avatar || ''
  form.presenceStatus = user.presenceStatus || 'ONLINE'
  form.gender = user.gender || 'UNKNOWN'
  form.city = user.city || ''
  form.neighborhood = user.neighborhood || ''
  form.school = user.school || ''
  form.major = user.major || ''
  form.grade = user.grade || ''
  form.student = Boolean(user.student)
  form.privateAccount = Boolean(user.privateAccount)
  form.wechat = user.wechat || ''
}

onMounted(async () => {
  try {
    fillForm(await auth.loadProfile())
  } catch {
    fillForm(auth.user)
  }
})

async function onSaveProfile() {
  saving.value = true
  profileMsg.value = ''
  error.value = ''
  try {
    const res = await updateUserProfile({ ...form })
    auth.user = { ...auth.user, ...res.data }
    localStorage.setItem('cq_user', JSON.stringify(auth.user))
    profileMsg.value = '资料已更新'
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.lead {
  margin: 0 0 16px;
  color: var(--muted);
}

.editor {
  display: grid;
  gap: 14px;
  padding: 20px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow-soft);
  max-width: 760px;
}

.fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.wide {
  grid-column: 1 / -1;
}

.check {
  display: grid;
  gap: 6px;
}

label {
  display: grid;
  gap: 6px;
}

label span,
.check > span {
  font-size: 13px;
  color: var(--muted);
}

input,
select {
  border: 1px solid var(--line);
  padding: 10px 12px;
  border-radius: 10px;
  background: #fff;
}

.switch {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
}

.switch span {
  color: var(--ink);
}

.actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.primary,
.ghost {
  padding: 10px 16px;
  border-radius: 999px;
  cursor: pointer;
  text-decoration: none;
}

.primary {
  border: none;
  background: var(--accent);
  color: #fff;
  font-weight: 600;
}

.ghost {
  border: 1px solid var(--line);
  background: #fff;
  color: var(--ink);
}

.ok {
  color: var(--accent);
  margin: 0;
}

.error {
  color: var(--danger);
}

@media (max-width: 720px) {
  .fields {
    grid-template-columns: 1fr;
  }
}
</style>
