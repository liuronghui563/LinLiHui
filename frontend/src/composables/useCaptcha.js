import { onUnmounted, ref, watch } from 'vue'
import { fetchCaptcha, verifyCaptcha } from '../api/auth'

export function useCaptcha(form, error) {
  const captchaChallenge = ref('')
  const loadingCaptcha = ref(false)
  const captchaVerified = ref(false)
  const captchaHoldLeft = ref(0)
  let holdTimer = null
  let verifying = false

  function stopHold() {
    if (holdTimer) {
      clearInterval(holdTimer)
      holdTimer = null
    }
  }

  function startHold(seconds) {
    stopHold()
    captchaHoldLeft.value = seconds
    holdTimer = setInterval(() => {
      captchaHoldLeft.value -= 1
      if (captchaHoldLeft.value <= 0) {
        stopHold()
        captchaVerified.value = false
      }
    }, 1000)
  }

  async function loadCaptcha() {
    loadingCaptcha.value = true
    captchaVerified.value = false
    captchaHoldLeft.value = 0
    stopHold()
    try {
      const res = await fetchCaptcha()
      form.captchaId = res.data.captchaId
      form.captchaCode = ''
      captchaChallenge.value = res.data.challenge
    } catch (e) {
      error.value = e.message
    } finally {
      loadingCaptcha.value = false
    }
  }

  async function tryVerify() {
    const code = form.captchaCode
    const expected = captchaChallenge.value?.length || 4
    if (captchaVerified.value || verifying || loadingCaptcha.value) return
    if (!form.captchaId || !code || code.length < expected) return
    verifying = true
    try {
      const res = await verifyCaptcha({ captchaId: form.captchaId, captchaCode: code })
      captchaVerified.value = true
      error.value = ''
      startHold(res.data?.holdSeconds || 60)
    } catch (e) {
      captchaVerified.value = false
      error.value = e.message
    } finally {
      verifying = false
    }
  }

  watch(() => form.captchaCode, tryVerify)
  onUnmounted(stopHold)

  return {
    captchaChallenge,
    loadingCaptcha,
    captchaVerified,
    captchaHoldLeft,
    loadCaptcha
  }
}

export function phoneError(phone) {
  if (!phone) return '请输入手机号'
  if (!/^1\d{10}$/.test(phone)) return '手机号格式错误'
  return ''
}

export function shouldRefreshCaptcha(message = '', verified = false) {
  if (verified) return false
  return /验证码失效|请刷新/.test(message)
}
