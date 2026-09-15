<template>
  <div class="auth-page">
    <aside class="auth-stage">
      <header class="auth-stage-brand">
        <span class="brand-mark">邻</span>
        <span class="brand-word">邻里汇</span>
      </header>
      <div class="auth-stage-copy">
        <p class="eyebrow">邻里之间</p>
        <h1>加入邻里</h1>
        <p class="lead">注册只需手机号与短信验证码，通过图形验证后再发送短信。</p>
        <ul class="auth-points">
          <li>生活广场，看看邻居在聊什么</li>
          <li>邻里互助，求助与帮手就在身边</li>
          <li>校园论坛，通过认证后才能进入</li>
        </ul>
      </div>
      <p class="auth-stage-note">线下见面请选择公共场所，注意人身与财产安全。</p>
    </aside>

    <div class="auth-panel">
      <div class="auth-shell">
        <div class="form-card enter-rise">
          <form class="auth-form" @submit.prevent="onSubmit">
            <div class="field">
              <label for="reg-phone">手机号</label>
              <input
                id="reg-phone"
                v-model.trim="form.phone"
                class="input"
                inputmode="numeric"
                maxlength="11"
                placeholder="11 位手机号"
              />
            </div>

            <div class="field">
              <label for="reg-nickname">昵称（可选）</label>
              <input
                id="reg-nickname"
                v-model.trim="form.nickname"
                class="input"
                maxlength="50"
                placeholder="城南小李"
              />
            </div>

            <div class="field">
              <label for="reg-password">密码</label>
              <input
                id="reg-password"
                v-model="form.password"
                class="input"
                type="password"
                placeholder="6-32 位"
              />
            </div>

            <p v-if="!phoneReady" class="gate-locked">
              请先填写 11 位手机号，再确认图形验证码。
            </p>

            <CaptchaGate
              v-if="phoneReady"
              v-model="form.captchaCode"
              input-id="reg-captcha"
              step="2"
              :challenge="captchaChallenge"
              :loading="loadingCaptcha"
              :confirming="confirmingCaptcha"
              :verified="captchaVerified"
              :hold-left="captchaHoldLeft"
              @refresh="loadCaptcha"
              @confirm="onConfirmCaptcha"
            />

            <div v-if="phoneReady && captchaVerified" class="sms-row">
              <div class="field">
                <label for="reg-sms">短信验证码</label>
                <input
                  id="reg-sms"
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
              {{ submitting ? '注册中…' : '注册并登录' }}
            </button>
          </form>
        </div>

        <footer class="auth-foot">
          <p class="foot">
            <span>已有账号？</span>
            <router-link to="/login">去登录</router-link>
          </p>
        </footer>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { sendSms } from '../api/auth'
import { useAuthStore } from '../stores/auth'
import CaptchaGate from '../components/CaptchaGate.vue'
import { isCaptchaError, isPhoneReady, phoneError, useCaptcha } from '../composables/useCaptcha'

const auth = useAuthStore()
const router = useRouter()

const form = reactive({
  phone: '',
  nickname: '',
  password: '',
  smsCode: '',
  captchaId: '',
  captchaCode: ''
})

const submitting = ref(false)
const sendingSms = ref(false)
const smsCooldown = ref(0)
const error = ref('')
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
      scene: 'REGISTER',
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
  if (!form.password) {
    error.value = '请输入密码'
    return
  }
  if (form.password.length < 6 || form.password.length > 32) {
    error.value = '密码长度需为 6-32 位'
    return
  }
  if (!form.smsCode) {
    error.value = '请输入短信验证码'
    return
  }
  submitting.value = true
  try {
    await auth.registerAccount({
      phone: form.phone,
      nickname: form.nickname,
      password: form.password,
      smsCode: form.smsCode
    })
    router.replace('/')
  } catch (e) {
    error.value = e.message
    await recoverIfCaptchaError(e.message)
  } finally {
    submitting.value = false
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

@media (max-width: 734px) {
  .form-card { padding: var(--sp-6) var(--sp-5) var(--sp-5); }
}
</style>
