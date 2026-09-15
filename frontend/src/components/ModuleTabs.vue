<template>
  <nav class="mod-tabs" aria-label="子模块">
    <router-link
      v-for="tab in tabs"
      :key="tab.key"
      class="mod-tab"
      :class="{ 'is-on': tab.key === active }"
      :to="tab.to"
      :aria-current="tab.key === active ? 'page' : undefined"
    >
      <span v-if="tab.icon" class="mod-tab-icon" aria-hidden="true" v-html="tab.icon" />
      <span>{{ tab.label }}</span>
    </router-link>
  </nav>
</template>

<script setup>
/**
 * 子模块 tab 条 —— 二级导航现在**只有它**。
 *
 * 模块名与副标题那一栏已经去掉：那一栏写的是「你在大模块的哪一节」，
 * 而这句话 tab 条本身用高亮就说清楚了，再重复一遍只是占掉 60px 高度。
 *
 * 为什么按钮态用 `aria-current="page"` 而不是 `role="tab"`：
 * 这些 tab 是**真的导航链接**（各自有独立 URL、可收藏、可前进后退），
 * 不是切换同一页面内面板的 tablist。用 role="tab" 会要求给每项配 tabpanel
 * 并把键盘交互改成方向键切换，反而与真实行为不符。
 */
defineProps({
  tabs: { type: Array, required: true },
  /** 当前高亮的子模块 key；为 null 时不高亮任何一项（例如校园的认证页） */
  active: { type: String, default: null }
})
</script>

<style scoped>
.mod-tabs {
  display: flex;
  align-items: center;
  gap: var(--sp-6);
  /* 窄屏放不下时横向滚动，而不是换行——换行会让粘性栏高度变化，
     页面里的 sticky 侧栏与锚点偏移都是按固定高度算的 */
  overflow-x: auto;
  scrollbar-width: none;
}

.mod-tabs::-webkit-scrollbar { display: none; }

.mod-tab {
  position: relative;
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: var(--modtabs-h);
  font-size: 18px;
  font-weight: 400;
  letter-spacing: -0.374px;
  color: var(--muted);
  white-space: nowrap;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
  transition: color 0.22s var(--ease), transform var(--press-out);
}

.mod-tab::after {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  background: var(--accent);
  border-radius: 1px;
  transform: scaleX(0);
  transform-origin: center;
  transition: transform 0.32s var(--ease);
}

.mod-tab:active {
  transform: scale(0.97);
  transition: color var(--press-in), transform var(--press-in);
}

.mod-tab.is-on {
  color: var(--ink);
}

.mod-tab.is-on::after {
  transform: scaleX(1);
}

@media (hover: hover) and (pointer: fine) {
  .mod-tab:hover { color: var(--ink-2); }
  .mod-tab.is-on:hover { color: var(--ink); }
}

/* 图标只用 currentColor 描边，因此选中态不需要单独换色：
   文字变色时它跟着变，两者永远一致。 */
.mod-tab-icon {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  flex: none;
  opacity: 0.85;
  transition: opacity 0.22s var(--ease);
}

.mod-tab-icon :deep(svg) {
  width: 20px;
  height: 20px;
}

.mod-tab.is-on .mod-tab-icon { opacity: 1; }

@media (max-width: 833px) {
  .mod-tabs { gap: var(--sp-5); }
  .mod-tab { font-size: 17px; }
  .mod-tab-icon,
  .mod-tab-icon :deep(svg) { width: 18px; height: 18px; }
}
</style>
