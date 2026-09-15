<template>
  <section class="entries panel">
    <div class="panel-head">
      <h2>校园专区</h2>
      <router-link class="panel-more" to="/campus">进入专区 →</router-link>
    </div>
    <p class="entries-desc caption">
      互动帖用来吐槽、表白、唠嗑；互助帖用来发失物招领。
    </p>
    <div class="zone-tiles">
      <router-link
        v-for="item in items"
        :key="item.title"
        class="zone-card"
        :to="item.to"
        :style="{ '--tile-tint': item.tint, '--tile-ink': item.ink }"
      >
        <span class="tile-icon" aria-hidden="true" v-html="item.icon" />
        <span class="tile-body">
          <strong>{{ item.title }}</strong>
          <small>{{ item.copy }}</small>
        </span>
        <span class="tile-arrow" aria-hidden="true">→</span>
      </router-link>
    </div>
  </section>
</template>

<script setup>
/**
 * 校园专区入口。
 *
 * 此前是 4 张 188px 高、依赖 picsum 外链大图的卡片——图片在受限网络下会裂，
 * 而且视觉权重盖过了站内真实内容（最新求助、邻里动态）。
 * 它们本质上是「导航入口」而非「内容」，所以改为紧凑图标磁贴：
 * 不依赖任何外部图片，同时把版面让给真实数据。
 */
const stroke = 'fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"'

const items = [
  {
    title: '校园吐槽',
    copy: '食堂、课程、宿舍',
    to: '/campus?tab=social&category=吐槽',
    tint: 'var(--accent-soft)',
    ink: 'var(--accent)',
    icon: `<svg viewBox="0 0 24 24" ${stroke}><path d="M20 12a7.5 7.5 0 0 1-11 6.6L4 20l1.4-4.2A7.5 7.5 0 1 1 20 12Z"/><path d="M9.5 12h.01M14.5 12h.01"/></svg>`
  },
  {
    title: '匿名表白墙',
    copy: '把想说的话放这里',
    to: '/campus?tab=social&category=表白',
    tint: 'var(--surface-muted)',
    ink: 'var(--ink-2)',
    icon: `<svg viewBox="0 0 24 24" ${stroke}><path d="M20.5 12.5 12 21l-8.5-8.5a4.95 4.95 0 0 1 7-7l1.5 1.5 1.5-1.5a4.95 4.95 0 0 1 7 7Z"/></svg>`
  },
  {
    title: '失物招领',
    copy: '校园卡、雨伞、耳机',
    to: '/campus?tab=aid',
    tint: 'var(--accent-soft)',
    ink: 'var(--accent)',
    icon: `<svg viewBox="0 0 24 24" ${stroke}><circle cx="11" cy="11" r="6.5"/><path d="m16 16 4 4"/></svg>`
  },
  {
    title: '分享日常',
    copy: '唠嗑、记录、随手一发',
    to: '/campus?tab=social&category=分享日常',
    tint: 'var(--surface-muted)',
    ink: 'var(--ink-2)',
    icon: `<svg viewBox="0 0 24 24" ${stroke}><path d="M4 6.5h16v11H4Z"/><path d="m4 7 8 6 8-6"/></svg>`
  }
]
</script>

<style scoped>
/* 说明文字的类型外观走全局 .caption，这里只留与卡头的负向间距 */
.entries-desc {
  margin: calc(var(--sp-3) * -1) 0 var(--sp-4);
}

.zone-tiles {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: var(--sp-3);
}

/* 入口卡统一成白底 + 发丝线，染色只出现在 36px 的图标磁贴里，
   避免四张整卡染色的「仪表盘感」。--tile-tint / --tile-ink 仍由模板按项下发。
   类名用 .zone-card 而不是 .tile——.tile 已是全局的「通栏瓦片」。 */
.zone-card {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  border-radius: var(--r-lg);
  background: var(--canvas);
  color: var(--ink);
  border: 1px solid var(--line);
  transition: border-color 0.16s var(--ease), transform var(--press-out);
}

.zone-card:hover {
  border-color: var(--accent);
}

.zone-card:active {
  transform: scale(0.96);
  transition: border-color var(--press-in), transform var(--press-in);
}

.tile-icon {
  width: 36px;
  height: 36px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--r-sm);
  background: var(--tile-tint, var(--pearl));
  color: var(--tile-ink, var(--ink-2));
}

.tile-icon :deep(svg) {
  width: 20px;
  height: 20px;
  display: block;
}

.tile-body {
  display: grid;
  gap: 1px;
  min-width: 0;
  flex: 1;
}

.tile-body strong {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: -0.224px;
  color: var(--ink);
}

.tile-body small {
  font-size: 12px;
  letter-spacing: -0.12px;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tile-arrow {
  color: var(--muted-2);
  opacity: 0;
  transform: translateX(-4px);
  transition: opacity 0.16s var(--ease), transform 0.16s var(--ease);
}

.zone-card:hover .tile-arrow {
  opacity: 1;
  transform: translateX(0);
}
</style>
