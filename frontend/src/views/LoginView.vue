<template>
  <div class="auth-page">
    <section class="hero-panel">
      <div class="hero-bg" />
      <div class="hero-glow" />
      <div class="hero-overlay">
        <p class="eyebrow enter-rise">城市邻里 · 互助同行</p>
        <h1 class="brand-title enter-rise" style="--delay: .12s">邻里汇</h1>
        <p class="hero-copy enter-rise" style="--delay: .24s">
          发布求助、响应邻里、发现学生专区。
        </p>
        <ul class="highlights">
          <li class="enter-rise" style="--delay: .36s">邻里互助</li>
          <li class="enter-rise" style="--delay: .46s">校园专区</li>
          <li class="enter-rise" style="--delay: .56s">社区动态</li>
        </ul>
      </div>
      <div class="hero-mosaic" aria-hidden="true">
        <img class="enter-photo" style="--delay: .18s" src="https://picsum.photos/id/1018/900/500" alt="" />
        <img class="enter-photo" style="--delay: .32s" src="https://picsum.photos/id/1011/700/500" alt="" />
        <img class="enter-photo" style="--delay: .46s" src="https://picsum.photos/id/1036/800/500" alt="" />
      </div>
    </section>

    <section class="form-panel">
      <div class="form-card enter-slide" style="--delay: .2s">
        <header>
          <h2>欢迎回来</h2>
          <p>支持密码登录与短信登录，均受图形验证码保护。</p>
        </header>

        <div class="tabs">
          <button type="button" :class="{ active: mode === 'password' }" @click="mode = 'password'">密码登录</button>
          <button type="button" :class="{ active: mode === 'sms' }" @click="mode = 'sms'">短信登录</button>
        </div>

        <form class="auth-form" @submit.prevent="onSubmit">
          <label>
            <span>手机号</span>
            <input v-model.trim="form.phone" maxlength="11" placeholder="请输入 11 位手机号" />
          </label>

          <template v-if="mode === 'password'">
            <label>
              <span>密码</span>
              <input v-model="form.password" type="password" placeholder="请输入密码" />
            </label>

            <div class="captcha-row">
              <label>
                <span>图形验证码</span>
                <input
                  v-model.trim="form.captchaCode"
                  maxlength="6"
                  placeholder="不区分大小写"
                  :disabled="captchaVerified"
                />
              </label>
              <div class="captcha-side">
                <span v-if="captchaVerified" class="verified">已验证 {{ captchaHoldLeft }}s</span>
                <CaptchaCanvas
                  :challenge="captchaChallenge"
                  :loading="loadingCaptcha"
                  @refresh="loadCaptcha"
                />
              </div>
            </div>
          </template>

          <template v-else>
            <div class="sms-row">
              <label>
                <span>短信验证码</span>
                <input v-model.trim="form.smsCode" maxlength="8" placeholder="请输入短信验证码" />
              </label>
              <button type="button" class="ghost" :disabled="smsCooldown > 0 || sendingSms" @click="onSendSms">
                {{ smsCooldown > 0 ? `${smsCooldown}s` : '获取验证码' }}
              </button>
            </div>

            <div class="captcha-row">
              <label>
                <span>图形验证码（发短信前校验）</span>
                <input
                  v-model.trim="form.captchaCode"
                  maxlength="6"
                  placeholder="不区分大小写"
                  :disabled="captchaVerified"
                />
              </label>
              <div class="captcha-side">
                <span v-if="captchaVerified" class="verified">已验证 {{ captchaHoldLeft }}s</span>
                <CaptchaCanvas
                  :challenge="captchaChallenge"
                  :loading="loadingCaptcha"
                  @refresh="loadCaptcha"
                />
              </div>
            </div>
          </template>

          <p v-if="error" class="error">{{ error }}</p>
          <button class="primary" type="submit" :disabled="submitting">
            {{ submitting ? '登录中…' : '登录' }}
          </button>
          <button class="admin-btn" type="button" @click="openAdminLogin">一键登录管理员账户</button>
        </form>

        <footer class="foot">
          <span>还没有账号？</span>
          <router-link to="/register">立即注册</router-link>
        </footer>

        <aside class="tips">
          <strong>演示账号</strong>
          <p>管理员：13800000000 / Admin@123</p>
          <p>普通用户：13900000000 / User@123</p>
          <p>短信验证码请查看后端控制台日志。</p>
        </aside>
      </div>
    </section>

    <div v-if="adminOpen" class="admin-mask" @click.self="closeAdminLogin">
      <div class="admin-dialog" role="dialog" aria-modal="true">
        <h3>管理员内部登录</h3>
        <p>请输入内部码后直接进入管理员账户。</p>
        <input
          v-model.trim="adminCode"
          type="password"
          maxlength="8"
          placeholder="请输入内部码"
          @keyup.enter="onAdminLogin"
        />
        <p v-if="adminError" class="error">{{ adminError }}</p>
        <div class="admin-ops">
          <button type="button" class="ghost" @click="closeAdminLogin">取消</button>
          <button type="button" class="primary" :disabled="adminSubmitting" @click="onAdminLogin">
            {{ adminSubmitting ? '登录中…' : '确认登录' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { sendSms } from '../api/auth'
import { useAuthStore } from '../stores/auth'
import CaptchaCanvas from '../components/CaptchaCanvas.vue'
import { phoneError, shouldRefreshCaptcha, useCaptcha } from '../composables/useCaptcha'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const mode = ref('password')
const form = reactive({
  phone: '',
  password: '',
  smsCode: '',
  captchaId: '',
  captchaCode: ''
})

const submitting = ref(false)
const sendingSms = ref(false)
const smsCooldown = ref(0)
const error = ref('')
const adminOpen = ref(false)
const adminCode = ref('')
const adminError = ref('')
const adminSubmitting = ref(false)
let timer = null

const { captchaChallenge, loadingCaptcha, captchaVerified, captchaHoldLeft, loadCaptcha } = useCaptcha(form, error)

function goHome() {
  const redirect = route.query.redirect || '/'
  router.replace(String(redirect))
}

async function onSendSms() {
  error.value = phoneError(form.phone)
  if (error.value) return
  if (!form.captchaCode) {
    error.value = '请输入图形验证码'
    return
  }
  sendingSms.value = true
  try {
    await sendSms({
      phone: form.phone,
      scene: 'LOGIN',
      captchaId: form.captchaId,
      captchaCode: form.captchaCode
    })
    error.value = ''
    smsCooldown.value = 60
    timer = setInterval(() => {
      smsCooldown.value -= 1
      if (smsCooldown.value <= 0) {
        clearInterval(timer)
        timer = null
      }
    }, 1000)
  } catch (e) {
    error.value = e.message
    if (shouldRefreshCaptcha(e.message, captchaVerified.value)) {
      await loadCaptcha()
    }
  } finally {
    sendingSms.value = false
  }
}

async function onSubmit() {
  error.value = phoneError(form.phone)
  if (error.value) return
  if (mode.value === 'password') {
    if (!form.password) {
      error.value = '请输入密码'
      return
    }
    if (!form.captchaCode) {
      error.value = '请输入图形验证码'
      return
    }
  } else if (!form.smsCode) {
    error.value = '请输入短信验证码'
    return
  }
  submitting.value = true
  try {
    if (mode.value === 'password') {
      await auth.passwordLogin({
        phone: form.phone,
        password: form.password,
        captchaId: form.captchaId,
        captchaCode: form.captchaCode
      })
    } else {
      await auth.smsLogin({
        phone: form.phone,
        smsCode: form.smsCode
      })
    }
    goHome()
  } catch (e) {
    error.value = e.message
    if (shouldRefreshCaptcha(e.message, captchaVerified.value)) {
      await loadCaptcha()
    }
  } finally {
    submitting.value = false
  }
}

function openAdminLogin() {
  adminOpen.value = true
  adminCode.value = ''
  adminError.value = ''
}

function closeAdminLogin() {
  adminOpen.value = false
  adminCode.value = ''
  adminError.value = ''
}

async function onAdminLogin() {
  if (!adminCode.value) {
    adminError.value = '请输入内部码'
    return
  }
  adminSubmitting.value = true
  adminError.value = ''
  try {
    await auth.internalLogin(adminCode.value)
    closeAdminLogin()
    goHome()
  } catch (e) {
    adminError.value = e.message
  } finally {
    adminSubmitting.value = false
  }
}

onMounted(loadCaptcha)
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
}

.hero-panel {
  position: relative;
  overflow: hidden;
  background: #243044;
}

.hero-bg {
  position: absolute;
  inset: -8%;
  background:
    linear-gradient(145deg, rgba(36, 48, 68, 0.78), rgba(224, 122, 61, 0.38), rgba(61, 142, 166, 0.28)),
    url("https://picsum.photos/id/1015/1800/1200") center/cover;
  animation: kenburns 8s ease-out both;
}

.hero-glow {
  position: absolute;
  width: 420px;
  height: 420px;
  right: -80px;
  top: -80px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(243, 201, 139, 0.42), transparent 68%);
}

.hero-overlay {
  position: relative;
  z-index: 1;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 64px;
  color: #f7fbf8;
}

.eyebrow {
  margin: 0 0 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  font-size: 13px;
  opacity: 0.85;
}

.brand-title {
  margin: 0;
  font-size: clamp(48px, 7vw, 84px);
  font-weight: 400;
}

.hero-copy {
  margin: 18px 0 0;
  max-width: 34ch;
  line-height: 1.8;
  opacity: 0.92;
}

.highlights {
  margin: 28px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.highlights li {
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(8px);
  font-size: 13px;
}

.highlights li:nth-child(1) {
  background: rgba(224, 122, 61, 0.28);
}

.highlights li:nth-child(2) {
  background: rgba(61, 142, 166, 0.32);
}

.highlights li:nth-child(3) {
  background: rgba(212, 160, 23, 0.28);
}

.hero-mosaic {
  position: absolute;
  right: 36px;
  top: 48px;
  width: 280px;
  display: grid;
  gap: 12px;
}

.hero-mosaic img {
  width: 100%;
  height: 110px;
  object-fit: cover;
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  box-shadow: var(--shadow);
}

.hero-mosaic img:nth-child(2) {
  width: 78%;
  margin-left: auto;
}

.form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 36px clamp(18px, 4vw, 48px);
}

.form-card {
  width: min(100%, 460px);
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid var(--line);
  border-radius: 28px;
  box-shadow: var(--shadow);
  padding: 36px 32px;
  backdrop-filter: blur(16px);
}

header h2 {
  margin: 0;
  font-size: 28px;
  font-weight: 500;
}

header p {
  margin: 10px 0 0;
  color: var(--muted);
}

.tabs {
  display: flex;
  gap: 8px;
  margin: 28px 0 18px;
}

.tabs button {
  border: 1px solid var(--line);
  background: transparent;
  padding: 10px 16px;
  cursor: pointer;
  color: var(--muted);
  border-radius: 999px;
}

.tabs button.active {
  background: var(--bg-deep);
  color: #fff;
  border-color: var(--bg-deep);
}

.auth-form {
  display: grid;
  gap: 14px;
}

label {
  display: grid;
  gap: 8px;
}

label span {
  font-size: 13px;
  color: var(--muted);
}

input {
  width: 100%;
  border: 1px solid var(--line);
  background: #fff;
  padding: 12px 14px;
  outline: none;
  border-radius: 12px;
}

input:focus {
  border-color: var(--accent);
}

.captcha-row,
.sms-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  align-items: end;
}

.captcha-side {
  display: grid;
  justify-items: end;
  gap: 6px;
}

.verified {
  font-size: 12px;
  color: #2d7388;
  font-weight: 700;
}

input:disabled {
  background: #f4eee6;
  color: var(--muted);
}

.admin-btn {
  border: 1px dashed var(--line);
  background: transparent;
  color: var(--ink);
  padding: 12px 18px;
  border-radius: 12px;
  cursor: pointer;
  font-weight: 600;
}

.admin-mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  background: rgba(29, 26, 23, 0.45);
  display: grid;
  place-items: center;
  padding: 24px;
}

.admin-dialog {
  width: min(100%, 380px);
  background: #fffaf4;
  border-radius: 20px;
  padding: 24px;
  box-shadow: var(--shadow);
}

.admin-dialog h3 {
  margin: 0;
}

.admin-dialog p {
  margin: 8px 0 14px;
  color: var(--muted);
}

.admin-ops {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

.admin-ops .primary {
  margin-top: 0;
}

.admin-dialog input {
  width: 100%;
}

.ghost,
.primary {
  border: none;
  cursor: pointer;
  padding: 12px 18px;
  border-radius: 12px;
}

.ghost {
  background: #efe4d6;
  color: var(--ink);
  height: 46px;
  white-space: nowrap;
}

.primary {
  margin-top: 8px;
  background: var(--accent);
  color: #fff;
  font-weight: 600;
}

.primary:disabled,
.ghost:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error {
  margin: 0;
  color: var(--danger);
  font-size: 14px;
}

.foot {
  margin-top: 18px;
  color: var(--muted);
}

.foot a {
  margin-left: 6px;
  color: var(--accent);
  font-weight: 600;
}

.tips {
  margin-top: 28px;
  padding-top: 18px;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}

.tips strong {
  color: var(--ink);
}

@media (max-width: 980px) {
  .auth-page {
    grid-template-columns: 1fr;
  }

  .hero-panel {
    min-height: 280px;
  }

  .hero-overlay {
    padding: 28px;
  }

  .hero-mosaic {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero-bg {
    animation: none;
  }
}
</style>
