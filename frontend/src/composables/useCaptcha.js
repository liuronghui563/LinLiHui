import { onUnmounted, ref } from 'vue'
import { fetchCaptcha, verifyCaptcha } from '../api/auth'

/**
 * 图形验证码状态机：必须**手动确认**，通过后才放行登录 / 注册表单。
 *
 * 为什么从「输满长度自动校验」改成显式按钮：自动校验时，校验结果与表单可见性无关，
 * 密码与短信验证码输入框始终摆在页面上——验证码拦不住任何人，只是个装饰。
 * 改成显式确认后，captchaVerified 成为后续输入框的**前置条件**。
 *
 * 冻结期（后端 holdSeconds，当前 60 秒）内不必重复校验：这是后端 requireVerified
 * 的口径，前端必须跟着走，否则用户会在「刚验过」和「又说没验」之间来回被拒。
 * 冻结期一到就立刻收回表单，而不只是改个角标文字——否则用户填到一半突然提交失败
 * 却看不出原因。
 */
export function useCaptcha(form, error) {
  const captchaChallenge = ref('')
  const loadingCaptcha = ref(false)
  const confirmingCaptcha = ref(false)
  const captchaVerified = ref(false)
  const captchaHoldLeft = ref(0)
  let holdTimer = null

  function stopHold() {
    if (holdTimer) {
      clearInterval(holdTimer)
      holdTimer = null
    }
  }

  /** 回到未验证状态。 */
  function lock() {
    stopHold()
    captchaVerified.value = false
    captchaHoldLeft.value = 0
  }

  function startHold(seconds) {
    stopHold()
    captchaHoldLeft.value = seconds
    holdTimer = setInterval(() => {
      captchaHoldLeft.value -= 1
      if (captchaHoldLeft.value <= 0) lock()
    }, 1000)
  }

  /**
   * 换一张验证码。刷新必然作废此前的验证结果——否则用户能靠旧验证码
   * 换取一张新图，闸门就漏了。
   */
  async function loadCaptcha() {
    loadingCaptcha.value = true
    lock()
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

  /**
   * 手动确认验证码，返回是否通过，便于调用方决定后续动作。
   *
   * 校验只能由后端裁决：`verified` 标记写在 Redis 里，登录与注册接口读的是同一份状态。
   * 前端本地比对等于把答案交到浏览器手里，改动一行代码就能绕过。
   */
  async function confirmCaptcha() {
    if (captchaVerified.value || confirmingCaptcha.value || loadingCaptcha.value) {
      return captchaVerified.value
    }
    if (!form.captchaId) {
      error.value = '验证码未加载，请点击图片换一张'
      return false
    }
    const code = (form.captchaCode || '').trim()
    if (!code) {
      error.value = '请输入图形验证码'
      return false
    }
    confirmingCaptcha.value = true
    try {
      const res = await verifyCaptcha({ captchaId: form.captchaId, captchaCode: code })
      captchaVerified.value = true
      error.value = ''
      startHold(res.data?.holdSeconds || 60)
      return true
    } catch (e) {
      // 先把消息存下来：loadCaptcha 会清空输入框并重新覆盖 error
      const message = e.message
      // 失败即换一张，否则用户只能对着同一张图反复猜
      await loadCaptcha()
      error.value = message
      return false
    } finally {
      confirmingCaptcha.value = false
    }
  }

  onUnmounted(stopHold)

  return {
    captchaChallenge,
    loadingCaptcha,
    confirmingCaptcha,
    captchaVerified,
    captchaHoldLeft,
    loadCaptcha,
    confirmCaptcha
  }
}

export function phoneError(phone) {
  if (!phone) return '请输入手机号'
  if (!/^1\d{10}$/.test(phone)) return '手机号格式错误'
  return ''
}

/** 11 位国内手机号填完后，才放出图形验证码（短信发送的前置条件）。 */
export function isPhoneReady(phone) {
  return /^1\d{10}$/.test(phone || '')
}

/**
 * 判断错误是否属于「图形验证码没通过」。
 *
 * 只匹配图形验证码本身，不匹配「短信验证码」：后者是业务错误，
 * 拿它去刷新图形验证码，用户会莫名其妙地丢掉刚填好的表单，
 * 还要多做一次验证码，问题却一点没解决。
 */
export function isCaptchaError(message = '') {
  return /图形验证码|验证码错误|验证码失效|验证码不正确|验证码已过期/.test(String(message))
}
