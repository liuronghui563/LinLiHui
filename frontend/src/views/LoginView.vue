<template>
  <div class="auth-page">
    <aside class="auth-stage">
      <header class="auth-stage-brand">
        <span class="brand-mark">@</span>
        <span class="brand-word">邻里汇</span>
      </header>
      <div class="auth-stage-copy">
        <p class="eyebrow">邻里之间</p>
        <h1>欢迎回来</h1>
        <p class="lead">welcome</p>
        <ul class="auth-points">
          <li>生活广场</li>
          <li>邻里互助</li>
          <li>校园论坛</li>
        </ul>
      </div>
      <p class="auth-stage-note">线下见面请选择公共场所，注意人身与财产安全。</p>
    </aside>

    <div class="auth-panel">
      <div class="auth-shell">
        <div class="form-card enter-rise">
          <div class="mode-tabs segmented" role="tablist">
            <button
              type="button"
              role="tab"
              :aria-selected="mode === 'password'"
              :aria-pressed="mode === 'password'"
              @click="mode = 'password'"
            >
              密码登录
            </button>
            <button
              type="button"
              role="tab"
              :aria-selected="mode === 'sms'"
              :aria-pressed="mode === 'sms'"
              @click="mode = 'sms'"
            >
              短信登录
            </button>
          </div>

          <form class="auth-form" @submit.prevent="onSubmit">
            <div class="field">
              <label for="login-phone">手机号</label>
              <input
                id="login-phone"
                v-model.trim="form.phone"
                class="input"
                inputmode="numeric"
                maxlength="11"
                placeholder="11 位手机号"
              />
            </div>

            <div v-if="mode === 'password'" class="field">
              <label for="login-password">密码</label>
              <input
                id="login-password"
                v-model="form.password"
                class="input"
                type="password"
                placeholder="密码"
              />
            </div>

            <p v-if="!phoneReady" class="gate-locked">
              请先填写 11 位手机号，再确认图形验证码。
            </p>

            <CaptchaGate
              v-if="phoneReady"
              v-model="form.captchaCode"
              input-id="login-captcha"
              step="2"
              :challenge="captchaChallenge"
              :loading="loadingCaptcha"
              :confirming="confirmingCaptcha"
              :verified="captchaVerified"
              :hold-left="captchaHoldLeft"
              @refresh="loadCaptcha"
              @confirm="onConfirmCaptcha"
            />

            <div v-if="mode === 'sms' && phoneReady && captchaVerified" class="sms-row">
              <div class="field">
                <label for="login-sms">短信验证码</label>
                <input
                  id="login-sms"
                  v-model.trim="form.smsCode"
                  class="input"
                  maxlength="8"
                  placeholder="短信验证码"
                />
              </div>
              <button
                type="button"
                class="btn btn--ghost sms-btn"
                :disabled="smsCooldown > 0 || sendingSms"
                @click="onSendSms"
              >
                {{ smsCooldown > 0 ? `${smsCooldown}s 后重发` : '获取验证码' }}
              </button>
            </div>

            <p v-if="error" class="form-error">{{ error }}</p>

            <button
              class="btn btn--primary btn--block"
              type="submit"
              :disabled="submitting || !captchaVerified"
            >
              {{ submitting ? '登录中…' : '登录' }}
            </button>
          </form>

          <button class="admin-entry" type="button" @click="openAdminLogin">
            管理员内部码登录
          </button>
        </div>

        <footer class="auth-foot">
          <p class="foot">
            <span>还没有账号？</span>
            <router-link to="/register">立即注册</router-link>
          </p>
          <p class="demo">演示账号 13800000000 / Admin@123</p>
        </footer>
      </div>
    </div>

    <div v-if="adminOpen" class="modal-mask" @click.self="closeAdminLogin">
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="admin-modal-title">
        <h3 id="admin-modal-title">管理员内部登录</h3>
        <input
          v-model.trim="adminCode"
          class="input"
          type="password"
          maxlength="8"
          placeholder="内部码"
          @keyup.enter="onAdminLogin"
        />
        <p v-if="adminError" class="form-error">{{ adminError }}</p>
        <div class="modal-ops">
          <button type="button" class="btn btn--quiet" @click="closeAdminLogin">取消</button>
          <button type="button" class="btn btn--primary" :disabled="adminSubmitting" @click="onAdminLogin">
            {{ adminSubmitting ? '登录中…' : '确认登录' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { sendSms } from '../api/auth'
import { useAuthStore } from '../stores/auth'
import CaptchaGate from '../components/CaptchaGate.vue'
import { isCaptchaError, isPhoneReady, phoneError, useCaptcha } from '../composables/useCaptcha'

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

const {
  captchaChallenge,
  loadingCaptcha,
  confirmingCaptcha,
  captchaVerified,
  captchaHoldLeft,
  loadCaptcha,
  confirmCaptcha
} = useCaptcha(form, error)

const phoneReady = computed(() => isPhoneReady(form.phone))

function goHome() {
  const redirect = route.query.redirect || '/'
  router.replace(String(redirect))
}

function onConfirmCaptcha() {
  confirmCaptcha()
}

async function recoverIfCaptchaError(message) {
  if (!isCaptchaError(message)) return
  await loadCaptcha()
  error.value = message
}

async function onSendSms() {
  error.value = phoneError(form.phone)
  if (error.value) return
  if (!captchaVerified.value) {
    error.value = '请先确认图形验证码'
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
    await recoverIfCaptchaError(e.message)
  } finally {
    sendingSms.value = false
  }
}

async function onSubmit() {
  if (!phoneReady.value) {
    error.value = phoneError(form.phone) || '请先填写 11 位手机号'
    return
  }
  if (!captchaVerified.value) {
    error.value = '请先确认图形验证码'
    return
  }
  if (mode.value === 'password') {
    if (!form.password) {
      error.value = '请输入密码'
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
    await recoverIfCaptchaError(e.message)
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
.form-card {
  display: grid;
  gap: var(--sp-5);
  padding: var(--sp-7) var(--sp-6) var(--sp-6);
  background-color: var(--canvas);
  background-image: var(--texture-dots-soft);
  background-size: var(--texture-dot-size);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
}

.mode-tabs { width: 100%; }
.mode-tabs button { flex: 1; }

.auth-form {
  display: grid;
  gap: var(--sp-4);
}

.sms-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--sp-3);
  align-items: end;
}

.gate-locked {
  margin: 0;
  padding: var(--sp-4);
  border-radius: var(--r-md);
  background: var(--parchment);
  font-size: 13px;
  line-height: 1.6;
  letter-spacing: -0.1px;
  color: var(--muted);
  text-align: center;
}

.sms-btn { min-height: 44px; }

.form-error {
  padding: var(--sp-3) var(--sp-4);
  border: 1px solid rgba(192, 69, 58, 0.28);
  border-radius: var(--r-md);
  color: var(--danger);
  font-size: 14px;
  line-height: 1.5;
  letter-spacing: -0.224px;
}

.admin-entry {
  width: 100%;
  padding: var(--sp-4) 0 0;
  border: 0;
  border-top: 1px solid var(--line);
  background: transparent;
  color: var(--muted);
  font-size: 14px;
  letter-spacing: -0.224px;
  cursor: pointer;
  transition: color 0.16s var(--ease), transform var(--press-out);
}

.admin-entry:hover { color: var(--accent); }
.admin-entry:active {
  transform: scale(0.98);
  transition: color var(--press-in), transform var(--press-in);
}

.auth-foot {
  display: grid;
  gap: var(--sp-2);
  justify-items: center;
}

.foot {
  font-size: 14px;
  letter-spacing: -0.224px;
  color: var(--muted);
}

.foot a {
  margin-left: 6px;
  color: var(--accent);
  font-weight: 600;
}

.foot a:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.demo {
  font-size: 12px;
  letter-spacing: -0.12px;
  color: var(--muted-2);
}

.modal-mask {
  position: fixed;
  inset: 0;
  z-index: var(--z-overlay);
  display: grid;
  place-items: center;
  padding: var(--sp-5);
  background: rgba(0, 0, 0, 0.48);
  backdrop-filter: saturate(180%) blur(20px);
}

.modal {
  width: min(100%, 360px);
  display: grid;
  gap: var(--sp-3);
  padding: var(--sp-7);
  background: var(--canvas);
  border-radius: var(--r-lg);
}

.modal h3 {
  font-size: 21px;
  font-weight: 600;
  letter-spacing: 0.231px;
}

.modal-ops {
  display: flex;
  justify-content: flex-end;
  gap: var(--sp-2);
  margin-top: var(--sp-2);
}

@media (max-width: 734px) {
  .form-card { padding: var(--sp-6) var(--sp-5) var(--sp-5); }
}
</style>
