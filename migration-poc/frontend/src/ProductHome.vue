<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

type Application = { id: string; company: string; position: string; stage: string; status: string; appliedDate: string; updatedAt: string }
type Page = 'applications' | 'calendar' | 'mail' | 'stats'

const emit = defineEmits<{ navigate: [page: Page] }>()
const applications = ref<Application[]>([])
const total = ref(0)
const authenticated = ref(false)
const loading = ref(true)
const error = ref('')

const recent = computed(() => applications.value.slice(0, 5))
const interviews = computed(() => applications.value.filter((item) => item.stage === '面试').length)
const offers = computed(() => applications.value.filter((item) => item.stage === 'Offer' || item.status === '已通过').length)

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const response = await fetch('/api/poc/applications', { cache: 'no-store' })
    const body = await response.json().catch(() => ({}))
    if (response.status === 401) return
    if (!response.ok) throw new Error(body.message || '读取首页数据失败')
    applications.value = body.applications || []
    total.value = Number(body.total || 0)
    authenticated.value = true
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '读取首页数据失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="welcome">
    <div><span>JOB SEARCH WORKSPACE</span><h2>把每一次投递，都变成清晰的下一步。</h2><p>职位、日程、邮件和数据分析已经进入新的 Vue + Java 工作台。</p></div>
    <button type="button" @click="emit('navigate', 'applications')">管理投递记录</button>
  </section>

  <div v-if="authenticated" class="metrics">
    <article><span>投递总数</span><strong>{{ total }}</strong></article>
    <article><span>面试阶段</span><strong>{{ interviews }}</strong></article>
    <article><span>Offer / 已通过</span><strong>{{ offers }}</strong></article>
  </div>

  <section v-if="authenticated" class="card recent-card">
    <div class="section-head"><h2>最近投递</h2><button type="button" class="link-button" @click="emit('navigate', 'applications')">查看全部</button></div>
    <div v-if="recent.length" class="recent-list">
      <article v-for="item in recent" :key="item.id">
        <div><strong>{{ item.company || '未填写公司' }}</strong><span>{{ item.position || '未填写岗位' }}</span></div>
        <div><b>{{ item.stage || '未标记阶段' }}</b><small>{{ item.status || '未标记状态' }}</small></div>
      </article>
    </div>
    <p v-else>还没有投递记录。</p>
  </section>

  <section v-else-if="!loading" class="card sign-in-card">
    <h2>登录后查看你的求职进展</h2>
    <p>前往“投递记录”使用现有账号登录。旧账号、密码哈希和业务 JSON 均保持兼容。</p>
    <button type="button" @click="emit('navigate', 'applications')">前往登录</button>
  </section>

  <div class="quick-grid">
    <button type="button" @click="emit('navigate', 'calendar')"><span>日程</span><strong>查看待办与历史安排 →</strong></button>
    <button type="button" @click="emit('navigate', 'mail')"><span>邮件识别</span><strong>提取面试与 Offer 信息 →</strong></button>
    <button type="button" @click="emit('navigate', 'stats')"><span>统计</span><strong>查看阶段与渠道分布 →</strong></button>
  </div>
  <p v-if="error" class="danger">{{ error }}</p>
</template>

<style scoped>
.welcome { display: flex; align-items: flex-end; justify-content: space-between; gap: 30px; margin-top: 28px; padding: 34px; border-radius: 22px; color: #fff; background: linear-gradient(135deg, #293e83, #566fd4); box-shadow: 0 18px 55px rgba(41, 62, 131, .2); }
.welcome span { font-size: 11px; font-weight: 800; letter-spacing: .14em; opacity: .72; }
.welcome h2 { max-width: 650px; margin: 11px 0 10px; font-size: clamp(26px, 4vw, 40px); line-height: 1.2; }
.welcome p { margin: 0; color: #dce4ff; }
.welcome button { flex: none; color: #304481; background: #fff; }
.metrics { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-top: 18px; }
.metrics article { display: grid; gap: 7px; padding: 20px; border: 1px solid #e4e9f2; border-radius: 15px; background: #fff; }
.metrics span { color: #667085; font-size: 13px; }
.metrics strong { font-size: 28px; }
.section-head, .recent-list article { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.section-head h2 { margin: 0; }
.link-button { padding: 8px 10px; color: #4461d8; background: transparent; }
.recent-list { display: grid; gap: 0; margin-top: 12px; }
.recent-list article { padding: 15px 0; border-top: 1px solid #edf0f5; }
.recent-list article > div { display: grid; gap: 4px; }
.recent-list span, .recent-list small { color: #667085; }
.recent-list b { color: #3d55bd; }
.quick-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-top: 18px; }
.quick-grid button { display: grid; gap: 8px; padding: 20px; color: #172033; border: 1px solid #e4e9f2; background: #fff; text-align: left; }
.quick-grid span { color: #667085; font-size: 12px; }

@media (max-width: 720px) {
  .welcome { align-items: flex-start; flex-direction: column; padding: 25px; }
  .metrics, .quick-grid { grid-template-columns: 1fr; }
}
</style>
