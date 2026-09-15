<template>
  <AppShell
    title="设置"
    eyebrow="我的"
  >
    <template #actions>
      <router-link class="btn btn--ghost btn--sm" :to="selfPath">查看我的主页</router-link>
    </template>

    <!-- 通栏白瓦片：这一页只有一个任务，瓦片只负责说明它 -->
    <template #hero>
      <PageHero
        tone="parchment"
        align="left"
        eyebrow="账号 · 个人资料"
        title="展示自己"
        lead="这里填写的内容会展示在你的个人主页上，帮助邻居更快了解你。"
      />
    </template>

    <form class="profile-form" @submit.prevent="onSaveProfile">
      <!-- 对外展示 -->
      <section class="panel">
        <div class="panel-head">
          <h3>对外展示</h3>
          <span class="panel-note">邻居在动态、求助里看到的就是这些</span>
        </div>
        <div class="preview">
          <UserAvatar :user="previewUser" size="md" />
          <div class="preview-text">
            <strong>{{ form.nickname || '未设置昵称' }}</strong>
            <small class="caption">{{ form.bio || '还没有填写简介' }}</small>
          </div>
        </div>
        <div class="fields">
          <div class="field">
            <label for="p-nickname">昵称</label>
            <input id="p-nickname" v-model.trim="form.nickname" class="input" maxlength="50" placeholder="邻居怎么称呼你" />
          </div>
          <div class="field">
            <label for="p-presence">当前状态</label>
            <select id="p-presence" v-model="form.presenceStatus" class="select">
              <option v-for="item in PRESENCE_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </div>
          <div class="field field--wide">
            <label for="p-bio">简介</label>
            <input id="p-bio" v-model.trim="form.bio" class="input" maxlength="200" placeholder="一句话介绍自己" />
          </div>
          <div class="field field--wide">
            <span class="field-label">头像</span>
            <ImageUploader v-model="form.avatar" purpose="avatar" :max="1" />
          </div>
          <div class="field field--wide">
            <span class="field-label">主页封面图</span>
            <ImageUploader v-model="form.coverImage" purpose="cover" :max="1" />
            <span class="field-hint">显示在个人主页顶部那块深色封面上；不设置就用默认纹理底。</span>
          </div>
        </div>
      </section>

      <!-- 身份信息 -->
      <section class="panel">
        <div class="panel-head">
          <h3>身份信息</h3>
          <span class="panel-note">真实姓名仅自己可见</span>
        </div>
        <div class="fields">
          <div class="field">
            <label for="p-realname">真实姓名</label>
            <input id="p-realname" v-model.trim="form.realName" class="input" maxlength="30" placeholder="仅自己可见" />
          </div>
          <div class="field">
            <label for="p-gender">性别</label>
            <select id="p-gender" v-model="form.gender" class="select">
              <option v-for="item in GENDER_OPTIONS" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </div>
          <!-- 学生身份不是自选开关。
               原来这里是一个「我是在校学生」复选框，勾上就直接展示学校与专业——
               等于让用户自己声明一个需要凭证的身份。现在它是
               「提交材料 → 管理员审核通过」的结果（校园模块的门禁读的正是它），
               因此设置页只展示状态并给出入口；学校/专业/年级由认证通过时写回，
               要改就得去认证页重新提交。 -->
          <div class="field field--wide">
            <span class="field-label">在校学生</span>
            <div class="student-status">
              <StatusTag
                :label="auth.user?.student ? '已认证' : '未认证'"
                :tone="auth.user?.student ? 'done' : 'outline'"
                dot
              />
              <span v-if="studentMeta" class="student-meta">{{ studentMeta }}</span>
              <router-link class="student-link" to="/campus/verify">
                {{ auth.user?.student ? '查看认证' : '去认证' }}
              </router-link>
            </div>
          </div>
        </div>
      </section>

      <!-- 居住与联系 -->
      <section class="panel">
        <div class="panel-head">
          <h3>居住与联系</h3>
          <span class="panel-note">有助于邻居判断距离与联系成本</span>
        </div>
        <div class="fields">
          <div class="field">
            <label for="p-city">城市</label>
            <input id="p-city" v-model.trim="form.city" class="input" maxlength="50" placeholder="所在城市" />
          </div>
          <div class="field">
            <label for="p-neighborhood">小区 / 社区</label>
            <input id="p-neighborhood" v-model.trim="form.neighborhood" class="input" maxlength="80" placeholder="例如：梧桐里" />
          </div>
          <div class="field">
            <label for="p-wechat">微信号</label>
            <input id="p-wechat" v-model.trim="form.wechat" class="input" maxlength="50" placeholder="方便接单后联系" />
          </div>
        </div>
      </section>

      <!-- 隐私 -->
      <section class="panel">
        <div class="panel-head"><h3>隐私</h3></div>
        <label class="checkbox-row">
          <input v-model="form.privateAccount" type="checkbox" />
          <span>开启隐私账户：其他用户将无法进入我的个人主页</span>
        </label>
      </section>

      <p v-if="error" class="form-error caption text-danger">{{ error }}</p>
      <p v-if="profileMsg" class="form-ok caption text-success">{{ profileMsg }}</p>

      <div class="form-actions">
        <button type="submit" class="btn btn--primary" :disabled="saving">
          {{ saving ? '保存中…' : '保存资料' }}
        </button>
        <router-link class="btn btn--quiet" to="/">返回首页</router-link>
      </div>
    </form>

    <!-- 账号安全：米白瓦片收尾，危险只用一个红色描边按钮和一枚状态标签表达 -->
    <section class="panel panel--flat security">
      <div class="panel-head">
        <h3>账号安全</h3>
        <span class="tag tag--outline"><i class="tag-dot" />影响所有设备</span>
      </div>
      <p class="security-desc lead-body">
        退出所有设备会立即吊销该账号在全部设备上的登录状态（访问令牌与刷新令牌一并失效），
        其他设备需要重新登录。怀疑账号被盗用时建议使用。
      </p>
      <button type="button" class="btn btn--danger" :disabled="loggingOut" @click="onLogoutAll">
        {{ loggingOut ? '处理中…' : '退出所有设备' }}
      </button>
    </section>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '../layouts/AppShell.vue'
import PageHero from '../components/PageHero.vue'
import UserAvatar from '../components/UserAvatar.vue'
import ImageUploader from '../components/ImageUploader.vue'
import StatusTag from '../components/StatusTag.vue'
import { updateUserProfile } from '../api/user'
import { useAuthStore } from '../stores/auth'
import { GENDER_OPTIONS, PRESENCE_OPTIONS } from '../utils/presence'

const auth = useAuthStore()
const router = useRouter()

const saving = ref(false)
const profileMsg = ref('')
const error = ref('')
const loggingOut = ref(false)

const form = reactive({
  nickname: auth.user?.nickname || '',
  realName: auth.user?.realName || '',
  bio: auth.user?.bio || '',
  avatar: auth.user?.avatar || '',
  coverImage: auth.user?.coverImage || '',
  presenceStatus: auth.user?.presenceStatus || 'ONLINE',
  gender: auth.user?.gender || 'UNKNOWN',
  city: auth.user?.city || '',
  neighborhood: auth.user?.neighborhood || '',
  privateAccount: Boolean(auth.user?.privateAccount),
  wechat: auth.user?.wechat || ''
  // school / major / grade / student 已从这里移除：它们不再是可编辑资料，
  // 而是学生认证审核通过时由服务端写入的结果。留在这里会被一起 POST 出去，
  // 形成一条「设置页改了学校，认证记录却没变」的旁路。
})

const selfPath = computed(() => (auth.user?.id ? `/users/${auth.user.id}` : '/'))

/** 认证通过后写回的学校信息，只读展示 */
const studentMeta = computed(() => {
  if (!auth.user?.student) return ''
  return [auth.user.school, auth.user.major, auth.user.grade].filter(Boolean).join(' · ')
})

/** 实时预览：让用户立刻看到别人眼中的自己 */
const previewUser = computed(() => ({
  id: auth.user?.id,
  nickname: form.nickname,
  avatar: form.avatar,
  presenceStatus: form.presenceStatus
}))

function fillForm(user) {
  if (!user) return
  form.nickname = user.nickname || ''
  form.realName = user.realName || ''
  form.bio = user.bio || ''
  form.avatar = user.avatar || ''
  form.coverImage = user.coverImage || ''
  form.presenceStatus = user.presenceStatus || 'ONLINE'
  form.gender = user.gender || 'UNKNOWN'
  form.city = user.city || ''
  form.neighborhood = user.neighborhood || ''
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

async function onLogoutAll() {
  if (!window.confirm('确定要退出所有设备吗？其他设备上的登录会立即失效。')) return
  loggingOut.value = true
  error.value = ''
  try {
    await auth.logoutAll()
    router.push({ name: 'login' })
  } catch (e) {
    error.value = e.message
  } finally {
    loggingOut.value = false
  }
}
</script>

<style scoped>
.profile-form {
  display: grid;
  gap: var(--sp-4);
  max-width: 880px;
}

/* 预览行：卡内用一条发丝线与下面的字段分开，不套第二张卡 */
.preview {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  padding-bottom: var(--sp-4);
  margin-bottom: var(--sp-5);
  border-bottom: 1px solid var(--line);
}

.preview-text {
  display: grid;
  gap: 2px;
  min-width: 0;
}

/* 17px 沿用正文字号，只把体例抬到 600 */
.preview-text strong {
  font-weight: 600;
}

.preview-text small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fields {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--sp-4);
}

.field--wide {
  grid-column: 1 / -1;
}

/* 学生认证状态：一枚标签 + 只读的学校信息 + 去认证的入口。
   排成一行而不是堆成三行，是因为它整块都是「状态展示」，不是待填的表单。 */
.student-status {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex-wrap: wrap;
  min-height: 44px;
}

.student-meta {
  font-size: 15px;
  letter-spacing: -0.224px;
  color: var(--muted);
}

.student-link {
  font-size: 15px;
  letter-spacing: -0.224px;
  color: var(--accent);
}

.student-link:hover { text-decoration: underline; }

.form-actions {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex-wrap: wrap;
}

.security {
  max-width: 880px;
  margin-top: var(--sp-5);
}

.security-desc {
  margin-bottom: var(--sp-4);
  max-width: 62ch;
}
</style>
