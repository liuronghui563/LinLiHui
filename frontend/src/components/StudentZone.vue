<template>
  <section class="student-zone">
    <div class="zone-head">
      <div>
        <p class="eyebrow">Campus</p>
        <h2>校园专区</h2>
        <p class="desc">互动帖用来吐槽、表白、唠嗑和分享日常；互助帖用来发失物招领。</p>
      </div>
      <router-link class="more" to="/campus">进入校园专区</router-link>
    </div>
    <div class="cards">
      <router-link
        v-for="item in items"
        :key="item.title"
        class="card"
        :to="item.to"
        :style="{ backgroundImage: `linear-gradient(180deg, ${item.wash}, rgba(29,26,23,.72)), url(${item.image})` }"
      >
        <span class="tag">{{ item.tag }}</span>
        <h3>{{ item.title }}</h3>
        <p>{{ item.copy }}</p>
      </router-link>
    </div>
  </section>
</template>

<script setup>
const items = [
  {
    tag: '吐槽',
    title: '校园吐槽',
    copy: '食堂、课程、宿舍，说出来轻松一点',
    wash: 'rgba(224,122,61,.22)',
    to: '/campus?tab=social&category=吐槽',
    image: 'https://picsum.photos/id/1011/900/600'
  },
  {
    tag: '表白',
    title: '匿名表白墙',
    copy: '把想说的话放进校园专区',
    to: '/campus?tab=social&category=表白',
    wash: 'rgba(61,142,166,.22)',
    image: 'https://picsum.photos/id/24/900/600'
  },
  {
    tag: '失物',
    title: '失物招领',
    copy: '校园卡、雨伞、耳机，找回来更容易',
    to: '/campus?tab=aid',
    wash: 'rgba(212,160,23,.22)',
    image: 'https://picsum.photos/id/1015/900/600'
  },
  {
    tag: '发布',
    title: '分享日常',
    copy: '唠嗑、分享，一键发到校园专区',
    to: '/campus?tab=social&category=分享日常',
    wash: 'rgba(36,48,68,.28)',
    image: 'https://picsum.photos/id/1036/900/600'
  }
]
</script>

<style scoped>
.student-zone {
  margin: 8px 0 28px;
}

.zone-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
  margin-bottom: 16px;
}

.eyebrow {
  margin: 0;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  font-size: 12px;
  color: var(--accent);
}

.zone-head h2 {
  margin: 6px 0 0;
  font-family: "ZCOOL XiaoWei", serif;
  font-size: 28px;
  font-weight: 400;
}

.desc {
  margin: 8px 0 0;
  color: var(--muted);
}

.more {
  color: var(--accent);
  font-weight: 600;
  white-space: nowrap;
}

.cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}

.card {
  min-height: 188px;
  border-radius: var(--radius);
  padding: 18px;
  color: #f7fbf8;
  background-size: cover;
  background-position: center;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  box-shadow: var(--shadow-soft);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow);
}

.tag {
  align-self: flex-start;
  margin-bottom: auto;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.18);
  font-size: 12px;
  backdrop-filter: blur(8px);
}

.card:nth-child(1) .tag {
  background: rgba(224, 122, 61, 0.72);
}

.card:nth-child(2) .tag {
  background: rgba(61, 142, 166, 0.78);
}

.card:nth-child(3) .tag {
  background: rgba(212, 160, 23, 0.78);
  color: #1d1a17;
}

.card:nth-child(4) .tag {
  background: rgba(36, 48, 68, 0.72);
}

.card h3 {
  margin: 0;
  font-size: 20px;
}

.card p {
  margin: 6px 0 0;
  opacity: 0.88;
  font-size: 13px;
}

@media (max-width: 1080px) {
  .cards {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 640px) {
  .cards {
    grid-template-columns: 1fr;
  }
}
</style>

