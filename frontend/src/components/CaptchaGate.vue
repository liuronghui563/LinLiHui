<template>
  <section class="gate panel" :class="{ 'gate--passed': verified }">
    <header class="gate-head">
      <span class="gate-no" aria-hidden="true">{{ verified ? '✓' : step }}</span>
      <span class="gate-title">{{ verified ? '图形验证码已通过' : '确认图形验证码' }}</span>
      <span v-if="verified" class="gate-state nums">{{ holdLeft }}s</span>
    </header>

    <div class="gate-row">
      <div class="field">
        <label :for="inputId">{{ label }}</label>
        <input
          :id="inputId"
          class="input"
          :value="modelValue"
          maxlength="6"
          autocomplete="off"
          spellcheck="false"
          placeholder="不区分大小写"
          :disabled="verified || confirming"
          @input="$emit('update:modelValue', $event.target.value)"
          @keyup.enter="$emit('confirm')"
        />
      </div>
      <CaptchaCanvas :challenge="challenge" :loading="loading" @refresh="$emit('refresh')" />
    </div>

    <button
      v-if="!verified"
      type="button"
      class="btn btn--primary btn--block"
      :disabled="confirming || loading"
      @click="$emit('confirm')"
    >
      {{ confirming ? '校验中…' : '确认验证码' }}
    </button>
    <p v-else class="gate-note">
      已通过校验，{{ holdLeft }} 秒内无需重复验证；超时后需重新确认。
    </p>
  </section></template>

<script setup>
import CaptchaCanvas from './CaptchaCanvas.vue'

/**
 * 图形验证码闸门：**必须手动点「确认验证码」**，通过后才放行后续表单。
 *
 * 为什么单独抽成组件：登录页与注册页的闸门必须完全一致——
 * 两处各写一份「输入框 + 画布 + 确认按钮 + 倒计时」，
 * 改动一次就要同步两遍，迟早会出现「登录拦得住、注册拦不住」这种漏洞。
 *
 * 本组件只负责展示与转发事件，状态由 `useCaptcha` 持有，
 * 校验通过与否由后端决定，组件不做任何本地判断。
 */
defineProps({
  /** 当前输入的验证码，由 useCaptcha 绑定的 form.captchaCode 提供 */
  modelValue: { type: String, default: '' },
  label: { type: String, default: '图形验证码' },
  inputId: { type: String, required: true },
  /** 画布要绘制的字符，来自后端下发的 challenge */
  challenge: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  confirming: { type: Boolean, default: false },
  verified: { type: Boolean, default: false },
  /** 冻结期剩余秒数，由后端 holdSeconds 倒计而来 */
  holdLeft: { type: Number, default: 0 },
  /** 步骤序号。登录/注册里手机号是第 1 步，图形验证码是第 2 步。 */
  step: { type: [String, Number], default: '2' }
})

defineEmits(['update:modelValue', 'refresh', 'confirm'])
</script>

<style scoped>
/* 登录 / 注册的前置闸门：一张白瓦片卡（.panel 提供白底、1px 发丝线、18px 圆角、
   24px 内距，无阴影），这里只写内部节奏。通过态不整卡染绿——
   只用 --success 的一枚小圆点和一层细描边表达。 */
.gate {
  display: grid;
  gap: var(--sp-4);
}

.gate-head {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

/* 步骤序号：未通过是发丝描边的小圆，通过后换成 success 细描边（不填色） */
.gate-no {
  width: 20px;
  height: 20px;
  flex: none;
  display: grid;
  place-items: center;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-pill);
  color: var(--muted);
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  transition: color 0.2s var(--ease), border-color 0.2s var(--ease);
}

.gate--passed {
  border-color: rgba(47, 125, 79, 0.32);
}

.gate--passed .gate-no {
  border-color: rgba(47, 125, 79, 0.4);
  color: var(--success);
}

.gate-title {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: -0.224px;
  color: var(--ink-2);
}

/* 冻结期：小圆点 + 细描边胶囊，而不是一块绿色底 */
.gate-state {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border: 1px solid rgba(47, 125, 79, 0.32);
  border-radius: var(--r-pill);
  color: var(--success);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: -0.12px;
}

.gate-state::before {
  content: "";
  width: 6px;
  height: 6px;
  border-radius: var(--r-pill);
  background: var(--success);
}

.gate-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--sp-3);
  align-items: end;
}

.gate-note {
  margin: 0;
  font-size: 12px;
  line-height: 1.43;
  letter-spacing: -0.12px;
  color: var(--muted);
}
</style>
