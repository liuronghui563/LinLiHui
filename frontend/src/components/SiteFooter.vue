<template>
  <footer class="site-foot">
    <div class="foot-inner">
      <!-- 页脚是唯一「刻意密集」的区域：让整套信息架构一眼可见。
           链接采用 17px / 2.41 的宽松行高，密集但不拥挤。 -->
      <div class="foot-cols">
        <nav v-for="col in columns" :key="col.title" class="foot-col" :aria-label="col.title">
          <h4>{{ col.title }}</h4>
          <ul>
            <li v-for="link in col.links" :key="link.to + link.label">
              <router-link :to="link.to">{{ link.label }}</router-link>
            </li>
          </ul>
        </nav>
      </div>

      <div class="foot-legal">
        <p class="foot-note">
          邻里汇是一个邻里互助信息平台：求助与接单由用户自行发布
          线下见面请选择公共场所，注意人身与财产安全。
        </p>
        <p class="foot-copy">© {{ year }} 邻里汇 · 城区互助</p>
      </div>
    </div>
  </footer>
</template>

<script setup>
import { computed } from 'vue'
import { useAuthStore } from '../stores/auth'

/**
 * 站尾。
 *
 * DESIGN.md 里页脚是唯一打破「低密度」原则的区域：这里刻意收紧、排满链接，
 * 让用户不必回到顶部就能跳到任何一个模块。底色用米白（canvas-parchment），
 * 与页面底同色，靠一条发丝线分隔，而不是靠深色块。
 */
const auth = useAuthStore()
const year = new Date().getFullYear()

const columns = computed(() => {
  // 列的分法跟着信息架构走：一列一个顶级模块，子模块按顺序排在模块列里。
  // 页脚是「不必回到顶部就能跳走」的兜底入口，因此它列出的是**全站可去处**，
  // 而不只是当前模块的子模块。
  const cols = [
    {
      title: '发现',
      links: [
        { to: '/discover', label: '生活广场' },
        { to: '/discover/market', label: '集市' },
        { to: '/discover/circle', label: '圈子' },
        { to: '/discover/recycle', label: '回收' },
        { to: '/discover/neighbor', label: '邻里互助' }
      ]
    },
    {
      title: '校园',
      links: [
        { to: '/campus', label: '论坛' },
        { to: '/campus/verify', label: '学生认证' }
      ]
    },
    {
      title: '我的',
      links: [
        { to: '/me', label: '个人主页' },
        { to: '/me/profile/edit', label: '编辑资料' },
        { to: '/me/settings', label: '设置' },
        { to: '/me/following', label: '我关注的人' },
        { to: '/me/followers', label: '关注我的人' },
        { to: '/me/blocked', label: '黑名单' },
        { to: '/me/ad', label: '广告位' }
      ]
    }
  ]
  const more = [
    { to: '/messages', label: '我的消息' },
    { to: '/discover/neighbor/create', label: '发布求助' }
  ]
  if (auth.isAdmin) {
    more.push({ to: '/admin', label: '管理台' })
  }
  cols.push({ title: '更多', links: more })
  return cols
})
</script>

<style scoped>
.site-foot {
  background: var(--parchment);
  border-top: 1px solid var(--line);
  margin-top: auto;
}

.foot-inner {
  width: min(var(--container), 100% - var(--gutter));
  margin: 0 auto;
  padding: var(--sp-7) 0 var(--sp-6);
}

.foot-cols {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--sp-6) var(--sp-7);
  padding-bottom: var(--sp-6);
  border-bottom: 1px solid var(--line);
}

.foot-col h4 {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: -0.224px;
  color: var(--ink);
  margin-bottom: var(--sp-2);
}

.foot-col ul {
  list-style: none;
  margin: 0;
  padding: 0;
}

/* typography.dense-link：17px / 400 / 2.41——宽松行高让密集的链接列可扫读 */
.foot-col a {
  display: inline-block;
  font-size: 17px;
  font-weight: 400;
  line-height: 2.41;
  letter-spacing: 0;
  color: var(--ink-2);
  -webkit-tap-highlight-color: transparent;
  transition: color 0.22s var(--ease), transform var(--press-out);
}

.foot-col a:hover { color: var(--accent); }
.foot-col a:active {
  transform: scale(0.98);
  transition: color var(--press-in), transform var(--press-in);
}

.foot-legal {
  display: grid;
  gap: var(--sp-3);
  padding-top: var(--sp-5);
}

.foot-note {
  font-size: 12px;
  line-height: 1.6;
  letter-spacing: -0.12px;
  color: var(--muted-2);
  max-width: 78ch;
}

.foot-copy {
  font-size: 12px;
  letter-spacing: -0.12px;
  color: var(--muted);
}

@media (max-width: 734px) {
  .foot-inner { padding: var(--sp-7) 0 var(--sp-6); }
  .foot-cols { grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); }
  .foot-col a { font-size: 14px; line-height: 2.2; }
}
</style>
