<template>
  <AppShell
    title="发布"
    eyebrow="互助"
    subtitle="带 * 的为必填项。"
  >
    <!-- 米白瓦片：与页面底色同调，读起来像一张摊开的纸 -->
    <template #hero>
      <PageHero
        :eyebrow="isCampus ? '校园 · 发布' : '发现 · 发布'"
        title="把事情说清楚，邻居更容易搭上手"
        lead="写清要做什么、什么时间"
        tone="parchment"
        size="display"
        align="left"
      />
    </template>

    <template #actions>
      <router-link class="btn btn--ghost btn--sm" :to="backTo">返回列表</router-link>
    </template>

    <div class="create-grid">
      <form class="form panel" @submit.prevent="onSubmit">
        <div class="panel-head">
          <h3>{{ pageTitle }}</h3>
        </div>

        <div class="field">
          <label for="aid-title">标题 *</label>
          <input
            id="aid-title"
            v-model.trim="form.title"
            class="input"
            maxlength="100"
            :placeholder="isCampus ? '例如：食堂门口捡到一张校园卡' : '例如：帮忙把两箱书搬上 5 楼'"
          />
          <span class="field-hint">一句话说清「需要做什么」，比笼统的「求助」更容易被接单。</span>
        </div>

        <div class="field">
          <span class="field-label">分类 *</span>
          <div class="segmented" role="group" aria-label="选择分类">
            <button
              v-for="c in categories"
              :key="c"
              type="button"
              class="chip"
              :class="{ 'is-on': form.category === c }"
              :aria-pressed="form.category === c"
              @click="form.category = c"
            >
              {{ c }}
            </button>
          </div>
        </div>

        <div class="field">
          <label for="aid-address">{{ isCampus ? '地点（可选）' : '地点 *' }}</label>
          <input
            id="aid-address"
            v-model.trim="form.address"
            class="input"
            maxlength="200"
            :placeholder="isCampus ? '宿舍 / 教学楼，可不填' : '小区 / 楼栋 / 路口'"
          />
          <span class="field-hint">
            {{ isCampus ? '写清楚位置方便失主或拾到者找到你。' : '写到楼栋或路口级别，邻居好判断距离。' }}
          </span>
        </div>

        <div class="field">
          <label for="aid-content">详情 *</label>
          <textarea
            id="aid-content"
            v-model.trim="form.content"
            class="textarea"
            rows="6"
            maxlength="2000"
            :placeholder="isCampus ? '说明物品特征、丢失或捡到的地点与时间' : '说明具体需要什么帮助、希望的时间段'"
          />
          <span class="field-hint">
            已输入 {{ form.content.length }} / 2000 字。建议写清时间窗口，避免答应了却联系不上。
          </span>
        </div>

        <div class="field">
          <span class="field-label">配图（可选）</span>
          <ImageUploader v-model="form.images" purpose="aid" />
        </div>

        <p v-if="error" class="field-error">{{ error }}</p>

        <div class="form-actions">
          <button class="btn btn--primary" type="submit" :disabled="submitting">
            {{ submitting ? '提交中…' : '发布求助' }}
          </button>
          <router-link class="btn btn--quiet" :to="backTo">取消</router-link>
        </div>
      </form>

      <aside class="tips panel panel--flat">
        <div class="panel-head"><h3>发布小贴士</h3></div>
        <ul class="tip-list list-plain">
          <li>
            <strong>标题写动作</strong>
            <span>「帮忙搬两箱书上 5 楼」比「求帮忙」更容易被看懂。</span>
          </li>
          <li>
            <strong>写清时间</strong>
            <span>比如「今天 18:00 前」，邻居才知道自己来不来得及。</span>
          </li>
          <li>
            <strong>地点尽量具体</strong>
            <span>楼栋、单元、门口，越具体越容易被顺路的邻居接单。</span>
          </li>
          <li>
            <strong>完成后记得评价</strong>
            <span>对帮助方的评价会累积成信用，帮人也帮社区。</span>
          </li>
        </ul>
      </aside>
    </div>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '../layouts/AppShell.vue'
import PageHero from '../components/PageHero.vue'
import ImageUploader from '../components/ImageUploader.vue'
import { createAid } from '../api/aid'

const NEIGHBOR_CATEGORIES = ['搬运', '代购', '陪护', '维修', '其他']
const CAMPUS_CATEGORIES = ['失物招领']

const router = useRouter()
const route = useRoute()

const isCampus = computed(() => String(route.query.board || '').toUpperCase() === 'CAMPUS')
const categories = computed(() => (isCampus.value ? CAMPUS_CATEGORIES : NEIGHBOR_CATEGORIES))
const pageTitle = computed(() => (isCampus.value ? '发布校园互助' : '发布求助'))
const backTo = computed(() => (
  // 校园互助属于「校园 → 论坛」这一节，邻里互助属于「发现 → 邻里互助」
  isCampus.value ? { path: '/campus', query: { tab: 'aid' } } : '/discover/neighbor'
))

const form = reactive({
  title: '',
  category: '搬运',
  address: '',
  content: '',
  images: []
})
const submitting = ref(false)
const error = ref('')

function applyQuery() {
  const incoming = String(route.query.category || '')
  form.category = categories.value.includes(incoming) ? incoming : categories.value[0]
}

onMounted(applyQuery)
watch(() => [route.query.board, route.query.category], applyQuery)

async function onSubmit() {
  error.value = ''
  if (!form.title) {
    error.value = '请填写标题'
    return
  }
  if (!form.content) {
    error.value = '请填写详情'
    return
  }
  if (!isCampus.value && !form.address) {
    error.value = '邻里互助需要填写地点'
    return
  }
  submitting.value = true
  try {
    const payload = {
      title: form.title,
      category: form.category,
      address: form.address,
      content: form.content,
      images: form.images,
      board: isCampus.value ? 'CAMPUS' : 'NEIGHBORHOOD'
    }
    const res = await createAid(payload)
    router.replace(`/aids/${res.data.id}`)
  } catch (e) {
    error.value = e.message
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
/* 只写布局差异：颜色 / 字号 / 圆角 / 按钮外观一律来自全局类与 var(--*) */

.create-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(260px, 1fr);
  gap: var(--sp-6);
  align-items: start;
}

/* 白卡 + hairline 来自 .panel；这里只排字段之间的纵向节奏 */
.form {
  display: grid;
  gap: var(--sp-5);
}

/* 分类选择用全局分段控件，选中态由 button[aria-pressed] 负责 */
.form .segmented {
  justify-self: start;
  max-width: 100%;
}

/* .chip / .is-on 仅作为钩子保留（模板绑定与测试可能引用），外观完全走 .segmented button */

.form-actions {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding-top: var(--sp-4);
  border-top: 1px solid var(--line);
}

.tips {
  position: sticky;
  top: calc(var(--nav-stack) + var(--sp-5));
}

.tip-list li {
  display: grid;
  gap: 2px;
}

.tip-list strong {
  font-weight: 600;
}

.tip-list span {
  color: var(--muted);
}

@media (max-width: 1000px) {
  .create-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .tips {
    position: static;
  }
}
</style>
