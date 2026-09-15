<template>
  <AppShell
    title="设置"
    eyebrow="我的"
    subtitle="这两项偏好保存在本机，不跟随账号——退出登录不会把它们一起重置。"
  >
    <template #hero>
      <PageHero
        eyebrow="我的 · 设置"
        title="偏好设置"
        lead="深色模式与所在地区会影响整个界面的呈现方式。"
        tone="parchment"
        size="display"
        align="left"
      />
    </template>

    <!-- 国家和地区 / 语言 -->
    <section class="panel set-panel">
      <div class="panel-head">
        <h3>国家和地区 / 语言</h3>
        <span class="panel-note">当前：{{ prefs.localeLabel }}</span>
      </div>

      <div class="field">
        <label for="set-locale">所在地区与界面语言</label>
        <select
          id="set-locale"
          class="select"
          :value="prefs.locale"
          @change="prefs.setLocale($event.target.value)"
        >
          <option v-for="o in LOCALE_OPTIONS" :key="o.value" :value="o.value">
            {{ o.region }} · {{ o.language }}
          </option>
        </select>
      </div>

      <p class="set-note">
        选择会写入浏览器的 <code>lang</code> 属性（屏幕阅读器与浏览器翻译读它），
        并按地区决定时间与数字的呈现习惯。
        <strong>界面文案的多语言尚未接入</strong>——现在只记录偏好，不会把按钮文字换成英文。
      </p>
    </section>

    <!-- 深色模式 -->
    <section class="panel set-panel">
      <div class="panel-head">
        <h3>深色模式</h3>
        <span class="panel-note">
          当前生效：{{ prefs.resolvedTheme === 'dark' ? '深色' : '浅色' }}
        </span>
      </div>

      <div class="segmented" role="group" aria-label="选择深色模式">
        <button
          v-for="o in THEME_OPTIONS"
          :key="o.value"
          type="button"
          :aria-pressed="prefs.theme === o.value"
          @click="prefs.setTheme(o.value)"
        >
          {{ o.label }}
        </button>
      </div>

      <p class="set-note">
        「跟随系统」会随操作系统的深浅色设置自动切换——现在是
        <strong>{{ prefs.systemDark ? '深色' : '浅色' }}</strong>。
        整套界面只通过颜色令牌取色，因此切换是立即生效的，不需要刷新页面。
      </p>
    </section>

    <template #band>
      <section class="tile tile--dark set-band">
        <div class="tile-inner">
          <h2 class="band-title">为什么偏好不跟着账号走</h2>
          <ul class="band-list">
            <li>深浅色取决于你<strong>此刻用的这台设备</strong>：白天用手机、晚上用电脑的人，不该被同一个设置绑住。</li>
            <li>所以它们存在本机，退出登录不会把深色模式一起重置。</li>
            <li>换设备需要重新选一次，这是刻意的取舍。</li>
          </ul>
        </div>
      </section>
    </template>
  </AppShell>
</template>

<script setup>
import { onUnmounted } from 'vue'
import AppShell from '../layouts/AppShell.vue'
import PageHero from '../components/PageHero.vue'
import { LOCALE_OPTIONS, THEME_OPTIONS, usePrefsStore } from '../stores/prefs'

/**
 * 设置页。
 *
 * 目前只有两项：国家和地区 / 语言、深色模式。
 *
 * 它接替了原来放在「我的 → 设置」下的**资料编辑表单**——那张表单已经移到
 * 「个人主页 → 编辑资料」（/me/profile/edit）。这样分的原因：「设置」回答的是
 * 「应用怎么运行」，「个人主页」回答的是「别人看到我什么样」，后者属于个人内容，
 * 放在个人主页下面更顺——点进自己的主页想改资料，不必先绕到设置。
 */
const prefs = usePrefsStore()

// 订阅系统深浅色变化：设置页上那句「跟随系统 → 现在是深色」要实时正确
prefs.bindSystem()
onUnmounted(() => prefs.unbindSystem())
</script>

<style scoped>
.set-panel {
  display: grid;
  gap: var(--sp-4);
  margin-bottom: var(--sp-5);
}

.set-panel .select {
  max-width: 360px;
}

.set-note {
  font-size: 15px;
  line-height: 1.5;
  letter-spacing: -0.224px;
  color: var(--muted);
}

.set-note strong {
  font-weight: 600;
  color: var(--ink-2);
}

.set-note code {
  font-family: var(--font-num);
  font-size: 13px;
  padding: 1px 5px;
  border-radius: var(--r-xs);
  background: var(--pearl);
  color: var(--ink-2);
}

.set-band .band-title {
  margin-bottom: var(--sp-4);
}

.band-list {
  display: grid;
  gap: var(--sp-2);
  margin: 0;
  padding-left: var(--sp-5);
  font-size: 17px;
  line-height: 1.47;
  letter-spacing: -0.374px;
  color: rgba(255, 255, 255, 0.8);
}
</style>
