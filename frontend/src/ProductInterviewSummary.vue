<script setup lang="ts">
import { computed, nextTick, onActivated, onDeactivated, ref, watch } from 'vue'
import { api } from './api'
import { summarizeInterviewReviews } from './interviewSummary'
import { useJobTrackerStore, type JobApplication, type JobEvent } from './jobTrackerStore'

type Topic = { name: string; count: number; kind: 'project' | 'knowledge'; summary: string; questions: string[] }
type Summary = { sourceKey: string; stale?: boolean; topics: Topic[] }
type Workbench = { sourceKey: string; overallSummary?: Summary }
type Review = { event: JobEvent; application: JobApplication; company: string; position: string; title: string; questions: string }

const emit = defineEmits<{ navigate: [page: 'profile'] }>()
const store = useJobTrackerStore()
const state = ref<Workbench | null>(null)
const loading = ref(false)
const summarizing = ref(false)
const error = ref('')
const message = ref('')
const selectedReviewId = ref('')
const reviewDialog = ref<HTMLDialogElement | null>(null)
let autoAttemptedKey = ''

const applicationsById = computed(() => new Map(store.applications.value.map(item => [item.id, item])))
const reviews = computed<Review[]>(() => store.events.value.flatMap(event => {
  const questions = String(event.interviewQuestions || '').trim()
  const application = applicationsById.value.get(String(event.applicationId || ''))
  if (!questions || !event.completed || event.missed || event.abandoned || !application) return []
  return [{ event, application, company: String(application.company || '未填写公司'), position: String(application.position || '未填写岗位'), title: String(event.title || event.type || '面试'), questions }]
}).sort((a, b) => String(b.event.startsAt || b.event.date || '').localeCompare(String(a.event.startsAt || a.event.date || ''))))
const reviewSignature = computed(() => reviews.value.map(item => `${item.event.id}|${item.application.id}|${item.company}|${item.position}|${item.title}|${item.questions}`).sort().join('\n'))
const summary = computed(() => state.value?.overallSummary)
const projectTopics = computed(() => (summary.value?.topics || []).filter(item => item.kind === 'project').sort((a, b) => b.count - a.count))
const knowledgeTopics = computed(() => (summary.value?.topics || []).filter(item => item.kind === 'knowledge').sort((a, b) => b.count - a.count))
const topicGroups = computed(() => [
  { kind: 'project', label: '项目考点', topics: projectTopics.value },
  { kind: 'knowledge', label: '八股考点', topics: knowledgeTopics.value }
])
const selectedReview = computed(() => reviews.value.find(item => item.event.id === selectedReviewId.value) || null)
const hasResume = computed(() => {
  const resume = (store.data.value.settings?.interviewWorkbench as { resume?: { internships?: unknown[]; projects?: unknown[] } } | undefined)?.resume
  return !!(resume?.internships?.length || resume?.projects?.length)
})

async function loadState(autoSummarize = true) {
  if (loading.value) return
  loading.value = true
  try {
    state.value = await api<Workbench>('/api/poc/interview-workbench')
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '读取面试总结失败' }
  finally { loading.value = false }
  if (autoSummarize && state.value && reviews.value.length && (!summary.value || summary.value.stale) && state.value.sourceKey !== autoAttemptedKey) {
    autoAttemptedKey = state.value.sourceKey
    await summarize(true)
  }
}

async function summarize(automatic = false) {
  if (!reviews.value.length || summarizing.value) return
  summarizing.value = true; error.value = ''; message.value = ''
  try {
    state.value = await summarizeInterviewReviews<Workbench>()
    autoAttemptedKey = state.value.sourceKey
    message.value = automatic ? '面试回顾已自动汇总' : '考点汇总已更新'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '考点汇总失败' }
  finally { summarizing.value = false }
}

function activate() { void store.refresh(false, false, false).then(() => loadState()) }
async function openReview(id: string) {
  selectedReviewId.value = id
  await nextTick()
  reviewDialog.value?.showModal()
}
function closeReview() { reviewDialog.value?.close() }
function onReviewDialogClick(event: MouseEvent) {
  if (event.target === event.currentTarget) closeReview()
}
onActivated(activate)
onDeactivated(closeReview)
watch(reviewSignature, (next, previous) => { if (previous && next !== previous) void loadState() })
</script>

<template>
  <section class="interview-summary" aria-labelledby="interview-summary-title">
    <header class="page-heading"><div><span class="eyebrow">面试复盘</span><h1 id="interview-summary-title">面试总结</h1><p>汇总所有已完成日程中的面试问题，按出现频率整理项目考点和八股考点。</p></div><button type="button" class="primary-action" :disabled="summarizing || !reviews.length" @click="summarize(false)">{{ summarizing ? '正在汇总…' : summary ? '重新汇总全部考点' : 'AI 汇总全部考点' }}</button></header>
    <p v-if="error" class="feedback error" role="alert">{{ error }}</p><p v-if="message" class="feedback success" role="status">{{ message }}</p>
    <p class="resume-hint"><span>考点汇总可以参考你的实习和项目经历。</span><button type="button" @click="emit('navigate', 'profile')">{{ hasResume ? '查看简历配置' : '先去个人主页设置简历 →' }}</button></p>
    <div class="summary-columns">
      <section class="review-column" aria-labelledby="review-list-title">
        <div class="section-heading"><div><small>原始记录</small><h2 id="review-list-title">面试回顾</h2></div><span>{{ reviews.length }} 条</span></div>
        <p v-if="!reviews.length" class="empty-state">还没有已完成且写有面试回顾的日程。完成面试后可在日程编辑中记录面试官的问题。</p>
        <ol v-else class="review-list"><li v-for="item in reviews" :key="item.event.id"><button type="button" class="review-card" @click="openReview(item.event.id)"><span class="review-card-heading"><strong>{{ item.company }}</strong><span>{{ item.title }}</span></span><span class="review-card-position">{{ item.position }}</span></button></li></ol>
      </section>
      <section class="topic-column" aria-labelledby="topic-title">
        <div class="section-heading"><div><small>AI 归纳</small><h2 id="topic-title">高频考点</h2></div><span>各类按频率排序</span></div>
        <p v-if="!reviews.length" class="empty-state">记录面试回顾后，这里会归纳所有问题的考点。</p>
        <p v-else-if="summarizing && (!summary || summary.stale)" class="empty-state" role="status">正在归纳全部面试问题…</p>
        <p v-else-if="!summary || summary.stale" class="empty-state">{{ summary?.stale ? '面试回顾或简历已更新，请重新汇总全部考点。' : '点击“AI 汇总全部考点”开始归纳。' }}</p>
        <template v-else><section v-for="group in topicGroups" :key="group.kind" class="topic-section" :aria-label="group.label"><div class="section-heading"><h3>{{ group.label }}</h3><span>{{ group.topics.length }} 个考点</span></div><ol v-if="group.topics.length" class="topic-list"><li v-for="topic in group.topics" :key="topic.name"><div class="topic-heading"><strong>{{ topic.name }}</strong><span>{{ topic.count }} 次</span></div><p>{{ topic.summary }}</p><ul v-if="topic.questions?.length"><li v-for="question in topic.questions" :key="question">{{ question }}</li></ul></li></ol><p v-else class="empty-state">暂无{{ group.label }}。</p></section></template>
      </section>
    </div>
    <dialog v-if="selectedReview" ref="reviewDialog" class="review-dialog" aria-labelledby="review-dialog-title" @close="selectedReviewId = ''" @click="onReviewDialogClick"><div class="review-dialog-header"><div><span class="eyebrow">面试回顾 · 问题清单</span><h2 id="review-dialog-title">{{ selectedReview.company }} · {{ selectedReview.title }}</h2><p>{{ selectedReview.position }}</p></div><button type="button" class="secondary" @click="closeReview">关闭</button></div><div class="review-dialog-content">{{ selectedReview.questions }}</div></dialog>
  </section>
</template>

<style scoped>
.interview-summary{width:min(1500px,100%);margin:0 auto;padding:32px 0 72px}.page-heading{display:flex;align-items:end;justify-content:space-between;gap:20px;padding-bottom:24px;border-bottom:1px solid var(--color-border)}.eyebrow,.section-heading small{color:var(--color-primary);font-size:11px;font-weight:700;letter-spacing:.07em}h1{margin:7px 0;font-size:clamp(27px,3vw,36px)}h2{margin:4px 0 0;font-size:19px}h3{margin:0;font-size:17px}.page-heading p,.resume-hint,.summary-note{color:var(--color-muted-foreground)}.page-heading p{margin:0;line-height:1.5}.page-heading>button,.category-actions>button{flex:none;color:var(--color-on-primary);background:var(--color-primary)}.resume-hint{display:flex;align-items:center;flex-wrap:wrap;gap:5px;margin:18px 0 24px;font-size:13px}.resume-hint button{padding:0;border:0;color:var(--color-primary);background:none;font-weight:700}.feedback{padding:10px 12px;margin:16px 0 0;border-radius:8px;font-size:13px}.error{color:var(--color-destructive);background:color-mix(in srgb,var(--color-destructive) 10%,var(--color-background))}.success{color:var(--color-primary);background:color-mix(in srgb,var(--color-primary) 9%,var(--color-background))}.summary-columns{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:32px}.review-column,.category-column{min-width:0}.category-column{padding-left:32px;border-left:1px solid var(--color-border)}.section-heading{display:flex;align-items:end;justify-content:space-between;gap:12px;margin-bottom:16px}.section-heading>span{color:var(--color-muted-foreground);font-size:12px}.review-list,.topic-list{margin:0;padding:0;list-style:none}.review-list{display:grid;gap:9px}.review-card{display:grid;width:100%;min-width:0;gap:5px;padding:14px 16px;border:1px solid var(--color-border);border-radius:10px;color:var(--color-foreground);background:var(--color-card);text-align:left}.review-card:hover{border-color:var(--color-border-strong);background:var(--surface-hover)}.review-card-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;min-width:0}.review-card-heading strong{min-width:0;overflow:hidden;font-size:15px;text-overflow:ellipsis;white-space:nowrap}.review-card-heading>span{flex:none;max-width:45%;overflow:hidden;color:var(--color-primary);font-size:12px;font-weight:700;text-overflow:ellipsis;white-space:nowrap}.review-card-position{overflow:hidden;color:var(--color-muted-foreground);font-size:13px;text-overflow:ellipsis;white-space:nowrap}.empty-state{padding:22px 0;border-top:1px solid var(--color-border);color:var(--color-muted-foreground);line-height:1.7}.category-directory{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:25px}.category-directory button{display:flex;align-items:center;gap:10px;padding:8px 12px;border:1px solid var(--color-border);border-radius:8px;color:var(--color-foreground);background:var(--color-card);font-size:13px}.category-directory button.selected{border-color:var(--color-primary);color:var(--color-primary);background:color-mix(in srgb,var(--color-primary) 9%,var(--color-card))}.category-directory span{color:var(--color-muted-foreground)}.category-actions{display:flex;align-items:center;justify-content:space-between;gap:12px;padding-bottom:14px}.topic-section{padding-top:22px;margin-top:20px;border-top:1px solid var(--color-border)}.topic-list>li{padding:16px 0;border-top:1px solid var(--color-border)}.topic-heading{display:flex;align-items:baseline;justify-content:space-between;gap:12px}.topic-heading span{color:var(--color-primary);font-size:12px}.topic-list p{margin:8px 0;color:var(--color-muted-foreground);line-height:1.6}.topic-list ul{padding-left:18px;margin:8px 0 0;line-height:1.6}.summary-note{margin:10px 0 0;font-size:13px;line-height:1.6}.review-dialog{width:min(760px,calc(100vw - 32px));max-height:min(82vh,850px);margin:auto;padding:26px;border:1px solid var(--color-border);border-radius:16px;color:var(--color-foreground);background:var(--color-card);box-shadow:var(--shadow-lg)}.review-dialog::backdrop{background:rgb(10 13 24 / 68%);backdrop-filter:blur(4px)}.review-dialog-header{display:flex;align-items:start;justify-content:space-between;gap:18px;padding-bottom:18px;border-bottom:1px solid var(--color-border)}.review-dialog-header h2{margin:8px 0 5px;overflow-wrap:anywhere}.review-dialog-header p{margin:0;color:var(--color-muted-foreground);font-size:13px}.review-dialog-header button{flex:none}.review-dialog-content{padding-top:20px;line-height:1.7;white-space:pre-wrap;overflow-wrap:anywhere}
@media(max-width:900px){.summary-columns{grid-template-columns:1fr;gap:35px}.category-column{padding:28px 0 0;border-left:0;border-top:1px solid var(--color-border)}}
@media(max-width:620px){.interview-summary{padding:22px 0 48px}.page-heading{align-items:start;flex-direction:column}.page-heading>button{width:100%}.category-actions{align-items:start;flex-direction:column}.category-actions>button{width:100%}}
.resume-hint button,.category-directory button,.category-actions>button{min-height:44px}.category-directory button{transition:background .18s ease,border-color .18s ease}
:global(#app) button.review-card{color:var(--color-foreground)}
.topic-column{min-width:0;padding-left:32px;border-left:1px solid var(--color-border)}.topic-column>.section-heading{margin-bottom:18px}.topic-column .topic-section{padding-top:0;margin-top:0;border-top:0}.topic-column .topic-section+.topic-section{padding-top:24px;margin-top:24px;border-top:1px solid var(--color-border)}.topic-column .topic-section .section-heading{align-items:center;margin-bottom:12px}.topic-column .topic-list>li:first-child{border-top:0}.topic-column .topic-list>li{padding:15px 0}.topic-column .topic-list strong{overflow-wrap:anywhere}
@media(max-width:900px){.topic-column{padding:28px 0 0;border-left:0;border-top:1px solid var(--color-border)}}
</style>
