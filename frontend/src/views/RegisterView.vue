<template>
  <div class="auth-page">
    <section class="hero-panel">
      <div class="hero-overlay">
        <p class="eyebrow enter-rise">加入邻里网络</p>
        <h1 class="brand-title enter-rise" style="--delay: .12s">邻里汇</h1>
        <p class="hero-copy enter-rise" style="--delay: .24s">手机号 + 短信验证码完成注册。校园内外，都能找到愿意帮忙的人。</p>
        <ul class="highlights">
          <li class="enter-rise" style="--delay: .36s">三步注册</li>
          <li class="enter-rise" style="--delay: .46s">学生专区</li>
          <li class="enter-rise" style="--delay: .56s">邻里互助</li>
        </ul>
      </div>
    </section>

    <section class="form-panel">
      <div class="form-card enter-slide" style="--delay: .2s">
        <header>
          <h2>创建账号</h2>
          <p>先通过图形验证码，再获取短信验证码。</p>
        </header>

        <form class="auth-form" @submit.prevent="onSubmit">
          <label>
            <span>手机号</span>
            <input v-model.trim="form.phone" maxlength="11" placeholder="请输入 11 位手机号" />
          </label>

          <label>
            <span>昵称（可选）</span>
            <input v-model.trim="form.nickname" maxlength="50" placeholder="例如：城南小李" />
          </label>

          <label>
            <span>密码</span>
            <input v-model="form.password" type="password" placeholder="6-32 位密码" />
          </label>

          <div class="captcha-row">
            <label>
              <span>图形验证码</span>
              <input
                v-model.trim="form.captchaCode"
                maxlength="6"
                placeholder="发短信前必填"
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

          <div class="sms-row">
            <label>
              <span>短信验证码</span>
              <input v-model.trim="form.smsCode" maxlength="8" placeholder="请输入短信验证码" />
            </label>
            <button type="button" class="ghost" :disabled="smsCooldown > 0 || sendingSms" @click="onSendSms">
              {{ smsCooldown > 0 ? `${smsCooldown}s` : '获取验证码' }}
            </button>
          </div>

          <p v-if="error" class="error">{{ error }}</p>
          <button class="primary" type="submit" :disabled="submitting">
            {{ submitting ? '注册中…' : '注册并登录' }}
          </button>
        </form>

        <footer class="foot">
          <span>已有账号？</span>
          <router-link to="/login">去登录</router-link>
        </footer>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { sendSms } from '../api/auth'
import { useAuthStore } from '../stores/auth'
import CaptchaCanvas from '../components/CaptchaCanvas.vue'
import { phoneError, shouldRefreshCaptcha, useCaptcha } from '../composables/useCaptcha'

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

const { captchaChallenge, loadingCaptcha, captchaVerified, captchaHoldLeft, loadCaptcha } = useCaptcha(form, error)

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
  if (!form.password) {
    error.value = '请输入密码'
    return
  }
  if (form.password.length < 6 || form.password.length > 32) {
    error.value = '密码长度需为 6-32 位'
    return
  }
  if (!form.captchaCode) {
    error.value = '请输入图形验证码'
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
    if (shouldRefreshCaptcha(e.message, captchaVerified.value)) {
      await loadCaptcha()
    }
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

.hero-panel::before {
  content: "";
  position: absolute;
  inset: -8%;
  background:
    linear-gradient(145deg, rgba(36, 48, 68, 0.78), rgba(61, 142, 166, 0.4), rgba(224, 122, 61, 0.28)),
    url("https://picsum.photos/id/1011/1800/1200") center/cover;
  animation: kenburns 8s ease-out both;
}

.hero-overlay {
  position: absolute;
  inset: 0;
  z-index: 1;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 64px;
  color: #f7fbf8;
}

.eyebrow {
  margin: 0 0 12px;
  letter-spacing: 0.18em;
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
  max-width: 32ch;
  line-height: 1.8;
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

.auth-form {
  display: grid;
  gap: 14px;
  margin-top: 28px;
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

.ghost,
.primary {
  border: none;
  cursor: pointer;
  padding: 12px 18px;
  border-radius: 12px;
}

.ghost {
  background: #efe4d6;
  height: 46px;
  white-space: nowrap;
}

.primary {
  margin-top: 8px;
  background: var(--accent);
  color: #fff;
  font-weight: 600;
}

.error {
  margin: 0;
  color: var(--danger);
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

@media (max-width: 980px) {
  .auth-page {
    grid-template-columns: 1fr;
  }

  .hero-panel {
    min-height: 260px;
  }

  .hero-overlay {
    padding: 28px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero-panel::before {
    animation: none;
  }
}
</style>
