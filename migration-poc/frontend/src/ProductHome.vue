<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from './api'
import { useJobTrackerStore } from './jobTrackerStore'

type Page = 'applications' | 'calendar' | 'mail' | 'stats'
type AiStatus = { callsEnabled: boolean; message: string }
type Quote = { date: string; quote: string; author: string; generated: boolean }
const emit = defineEmits<{ navigate: [page: Page] }>()
const store = useJobTrackerStore()
const quoteKey = 'job_tracker_daily_quote_vue_v1'
const quote = ref<Quote>(fallbackQuote())
const quoteLoading = ref(false)
const quoteHint = ref('')
const error = ref('')

const recent = computed(() => [...store.applications.value].sort((a, b) =>
  String(b.updatedAt || b.appliedDate || '').localeCompare(String(a.updatedAt || a.appliedDate || ''))
).slice(0, 5))
const interviews = computed(() => store.applications.value.filter(item => item.stage === '面试').length)
const offers = computed(() => store.applications.value.filter(item => item.stage === 'Offer' || item.status === '已通过').length)
const upcoming = computed(() => store.events.value.filter(item => !item.completed && eventTime(item) >= nowText()).length)

onMounted(async () => {
  await store.initialize()
  const cached = loadCachedQuote()
  if (store.user.value && !cached) await generateQuote(false)
})
function today() { return new Date().toISOString().slice(0, 10) }
function nowText() { return new Date().toISOString().slice(0, 16).replace('T', ' ') }
function eventTime(item: Record<string, unknown>) { return String(item.startsAt || item.start || item.date || '') }
function fallbackQuote(): Quote {
  const items = [
    '今天多走一步，明天就多一个选择。',
    '把注意力放在能推进的下一步上。',
    '每一次认真准备，都在靠近更合适的机会。',
    '慢一点没有关系，只要方向仍在向前。',
    '机会会迟到，但你的积累不会白费。',
    '先完成今天能完成的，再把答案交给时间。',
    '保持行动，好的结果往往在坚持之后出现。'
  ]
  return { date: today(), quote: items[new Date().getDay()], author: '', generated: false }
}
function loadCachedQuote(): boolean {
  try {
    const cached = JSON.parse(localStorage.getItem(quoteKey) || 'null') as Quote | null
    if (cached?.date === today() && cached.quote) { quote.value = cached; return true }
  } catch { /* invalid cache falls back to local copy */ }
  return false
}
async function generateQuote(force: boolean) {
  if (!store.user.value || quoteLoading.value) return
  quoteLoading.value = true; error.value = ''; quoteHint.value = ''
  try {
    const status = await api<AiStatus>('/api/poc/ai-sandbox/status')
    if (!status.callsEnabled) {
      quote.value = fallbackQuote()
      if (force) quoteHint.value = `${status.message}，已为你换成本地内容`
      return
    }
    const value = await api<{ quote: string; author: string }>('/api/poc/ai-sandbox/daily-quote', {
      method: 'POST', body: JSON.stringify({ date: today() })
    })
    quote.value = { date: today(), quote: value.quote, author: value.author || '', generated: true }
    localStorage.setItem(quoteKey, JSON.stringify(quote.value))
    quoteHint.value = force ? '已经换了一句' : ''
  } catch (cause) {
    quote.value = fallbackQuote()
    error.value = cause instanceof Error ? `每日一句生成失败：${cause.message}` : '每日一句生成失败'
  } finally { quoteLoading.value = false }
}
</script>

<template>
  <section class="welcome">
    <div><span>JOB SEARCH WORKSPACE</span><h2>把每一次投递，都变成清晰的下一步。</h2><p>职位、日程、邮件和数据分析已经进入新的 Vue + Java 工作台。</p></div>
    <button type="button" @click="emit('navigate', 'applications')">管理投递记录</button>
  </section>

  <section class="daily-quote">
    <div><span>{{ quote.generated ? 'AI 今日一句' : '今日一句' }}</span><blockquote>{{ quote.quote }}<small v-if="quote.author">— {{ quote.author }}</small></blockquote></div>
    <button type="button" :disabled="quoteLoading" title="换一句" aria-label="重新生成每日一句" @click="generateQuote(true)">{{ quoteLoading ? '…' : '✦' }}</button>
  </section>
  <p v-if="quoteHint" class="quote-hint">{{ quoteHint }}</p>

  <div v-if="store.user.value" class="metrics">
    <article><span>投递总数</span><strong>{{ store.applications.value.length }}</strong></article>
    <article><span>面试阶段</span><strong>{{ interviews }}</strong></article>
    <article><span>Offer / 已通过</span><strong>{{ offers }}</strong></article>
    <article><span>待办日程</span><strong>{{ upcoming }}</strong></article>
  </div>

  <section v-if="store.user.value" class="card recent-card">
    <div class="section-head"><h2>最近投递</h2><button type="button" class="link-button" @click="emit('navigate', 'applications')">查看全部</button></div>
    <div v-if="recent.length" class="recent-list">
      <article v-for="item in recent" :key="item.id">
        <div><strong>{{ item.company || '未填写公司' }}</strong><span>{{ item.position || '未填写岗位' }}</span></div>
        <div><b>{{ item.stage || '未标记阶段' }}</b><small>{{ item.status || '未标记状态' }}</small></div>
      </article>
    </div>
    <p v-else>还没有投递记录。</p>
  </section>

  <section v-else-if="store.initialized.value" class="card sign-in-card">
    <h2>登录后查看你的求职进展</h2>
    <p>使用现有账号即可进入，新旧系统账号及业务数据保持兼容。</p>
    <button type="button" @click="emit('navigate', 'applications')">前往登录</button>
  </section>

  <div class="quick-grid">
    <button type="button" @click="emit('navigate', 'calendar')"><span>日程</span><strong>查看待办与历史安排 →</strong></button>
    <button type="button" @click="emit('navigate', 'mail')"><span>邮件识别</span><strong>提取面试与 Offer 信息 →</strong></button>
    <button type="button" @click="emit('navigate', 'stats')"><span>统计</span><strong>查看阶段与渠道分布 →</strong></button>
  </div>
  <p v-if="error || store.error.value" class="danger">{{ error || store.error.value }}</p>
</template>

<style scoped>
.welcome { display: flex; align-items: flex-end; justify-content: space-between; gap: 30px; margin-top: 28px; padding: 34px; border-radius: 22px; color: #fff; background: linear-gradient(135deg, #293e83, #566fd4); box-shadow: 0 18px 55px rgba(41,62,131,.2); }
.welcome span { font-size: 11px; font-weight: 800; letter-spacing: .14em; opacity: .72; }
.welcome h2 { max-width: 650px; margin: 11px 0 10px; font-size: clamp(26px,4vw,40px); line-height: 1.2; }
.welcome p { margin: 0; color: #dce4ff; }
.welcome button { flex: none; color: #304481; background: #fff; }
.daily-quote { position: relative; display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-top: 18px; padding: 22px 25px; overflow: hidden; border: 1px solid #e2e7f4; border-radius: 17px; background: linear-gradient(120deg,#fff,#f2f5ff); }
.daily-quote::after { position: absolute; right: 68px; width: 120px; height: 120px; border-radius: 50%; background: rgba(82,109,221,.06); content: ''; }
.daily-quote span { color: #526ddd; font-size: 11px; font-weight: 800; letter-spacing: .12em; }
.daily-quote blockquote { margin: 7px 0 0; color: #263453; font-size: 17px; font-weight: 650; line-height: 1.65; }
.daily-quote small { margin-left: 8px; color: #75809a; font-weight: 500; }
.daily-quote button { z-index: 1; display: grid; width: 40px; height: 40px; flex: none; padding: 0; border-radius: 50%; place-items: center; }
.quote-hint { margin: 6px 4px 0; font-size: 12px; }
.metrics { display: grid; grid-template-columns: repeat(4,1fr); gap: 14px; margin-top: 18px; }
.metrics article { display: grid; gap: 7px; padding: 20px; border: 1px solid #e4e9f2; border-radius: 15px; background: #fff; }
.metrics span { color: #667085; font-size: 13px; }
.metrics strong { font-size: 28px; }
.section-head,.recent-list article { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.section-head h2 { margin: 0; }
.link-button { padding: 8px 10px; color: #4461d8; background: transparent; }
.recent-list { display: grid; margin-top: 12px; }
.recent-list article { padding: 15px 0; border-top: 1px solid #edf0f5; }
.recent-list article > div { display: grid; gap: 4px; }
.recent-list span,.recent-list small { color: #667085; }
.recent-list b { color: #3d55bd; }
.quick-grid { display: grid; grid-template-columns: repeat(3,1fr); gap: 14px; margin-top: 18px; }
.quick-grid button { display: grid; gap: 8px; padding: 20px; color: #172033; border: 1px solid #e4e9f2; background: #fff; text-align: left; }
.quick-grid span { color: #667085; font-size: 12px; }
@media (max-width: 720px) {
  .welcome { align-items: flex-start; flex-direction: column; padding: 25px; }
  .metrics { grid-template-columns: repeat(2,1fr); }
  .quick-grid { grid-template-columns: 1fr; }
}
</style>
