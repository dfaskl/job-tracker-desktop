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
const selectedTopicKey = ref('')
const activePane = ref<'reviews' | 'topics' | 'detail'>('reviews')
const detailScroll = ref<HTMLElement | null>(null)
const detailHeading = ref<HTMLElement | null>(null)
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
const topicEntries = computed(() => summary.value?.stale ? [] : topicGroups.value.flatMap(group =>
  group.topics.map((topic, index) => ({ key: `${group.kind}:${index}:${topic.name}`, group: group.label, topic }))
))
const selectedTopic = computed(() => topicEntries.value.find(item => item.key === selectedTopicKey.value) || null)
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
async function selectTopic(key: string) {
  selectedTopicKey.value = key
  activePane.value = 'detail'
  await nextTick()
  detailScroll.value?.scrollTo({ top: 0 })
  detailHeading.value?.focus()
}
onActivated(activate)
onDeactivated(closeReview)
watch(reviewSignature, (next, previous) => { if (previous && next !== previous) void loadState() })
watch(topicEntries, entries => {
  if (!entries.some(item => item.key === selectedTopicKey.value)) selectedTopicKey.value = ''
})
</script>

<template>
  <section class="interview-summary" aria-label="面试总结">
    <div class="page-notices">
      <p v-if="error" class="feedback error" role="alert">{{ error }}</p><p v-if="message" class="feedback success" role="status">{{ message }}</p>
      <p class="resume-hint"><span>考点汇总可以参考你的实习和项目经历。</span><button type="button" @click="emit('navigate', 'profile')">{{ hasResume ? '查看简历配置' : '先去个人主页设置简历 →' }}</button></p>
      <button type="button" class="primary-action summarize-action" :disabled="summarizing || !reviews.length" @click="summarize(false)">{{ summarizing ? '正在汇总…' : summary ? '重新汇总全部考点' : 'AI 汇总全部考点' }}</button>
    </div>
    <nav class="mobile-pane-tabs" aria-label="面试总结栏目"><button type="button" :aria-pressed="activePane === 'reviews'" @click="activePane = 'reviews'">面试回顾</button><button type="button" :aria-pressed="activePane === 'topics'" @click="activePane = 'topics'">考点分类</button><button type="button" :aria-pressed="activePane === 'detail'" @click="activePane = 'detail'">考点详情</button></nav>
    <div class="summary-columns">
      <section class="review-column summary-pane" :class="{ 'mobile-pane-active': activePane === 'reviews' }" aria-labelledby="review-list-title">
        <div class="section-heading"><div><small>原始记录</small><h2 id="review-list-title">面试回顾</h2></div><span>{{ reviews.length }} 条</span></div>
        <div class="pane-scroll"><p v-if="!reviews.length" class="empty-state">还没有已完成且写有面试回顾的日程。完成面试后可在日程编辑中记录面试官的问题。</p>
          <ol v-else class="review-list"><li v-for="item in reviews" :key="item.event.id"><button type="button" class="review-card" @click="openReview(item.event.id)"><span class="review-card-heading"><strong>{{ item.company }}</strong><span>{{ item.title }}</span></span><span class="review-card-position">{{ item.position }}</span></button></li></ol></div>
      </section>
      <section class="topic-column summary-pane" :class="{ 'mobile-pane-active': activePane === 'topics' }" aria-labelledby="topic-title">
        <div class="section-heading"><div><small>AI 归纳</small><h2 id="topic-title">考点分类</h2></div><span>按频次排序</span></div>
        <div class="pane-scroll"><p v-if="!reviews.length" class="empty-state">记录面试回顾后，这里会归纳所有问题的考点。</p>
          <p v-else-if="summarizing && (!summary || summary.stale)" class="empty-state" role="status">正在归纳全部面试问题…</p>
          <p v-else-if="!summary || summary.stale" class="empty-state">{{ summary?.stale ? '面试回顾或简历已更新，请重新汇总全部考点。' : '点击“AI 汇总全部考点”开始归纳。' }}</p>
          <template v-else><section v-for="group in topicGroups" :key="group.kind" class="topic-section" :aria-label="group.label"><div class="section-heading"><h3>{{ group.label }}</h3><span>{{ group.topics.length }} 个考点</span></div><ol v-if="group.topics.length" class="topic-list"><li v-for="(topic, index) in group.topics" :key="`${group.kind}:${index}:${topic.name}`"><button type="button" class="topic-choice" :class="{ selected: selectedTopicKey === `${group.kind}:${index}:${topic.name}` }" :aria-pressed="selectedTopicKey === `${group.kind}:${index}:${topic.name}`" @click="selectTopic(`${group.kind}:${index}:${topic.name}`)"><span>{{ topic.name }}</span><strong>{{ topic.count }} 次</strong></button></li></ol><p v-else class="empty-state">暂无{{ group.label }}。</p></section></template>
        </div>
      </section>
      <section class="detail-column summary-pane" :class="{ 'mobile-pane-active': activePane === 'detail' }" aria-labelledby="detail-title">
        <div class="section-heading"><div><small>考点内容</small><h2 id="detail-title">考点详情</h2></div></div>
        <div ref="detailScroll" class="pane-scroll">
          <div v-if="selectedTopic" class="topic-detail"><span class="detail-kind">{{ selectedTopic.group }}</span><h3 ref="detailHeading" tabindex="-1">{{ selectedTopic.topic.name }}</h3><span class="detail-frequency">出现 {{ selectedTopic.topic.count }} 次</span><section v-if="selectedTopic.topic.summary" class="detail-section"><h4>考点总结</h4><p>{{ selectedTopic.topic.summary }}</p></section><section v-if="selectedTopic.topic.questions?.length" class="detail-section"><h4>相关面试问题</h4><ol><li v-for="(question, index) in selectedTopic.topic.questions" :key="`${index}:${question}`">{{ question }}</li></ol></section></div>
          <p v-else class="empty-state">{{ summary && !summary.stale ? '从中间选择一个考点，查看详细内容。' : '完成考点汇总后，在中间选择类别查看详细内容。' }}</p>
        </div>
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

/* The page stays fixed; each of its three columns owns its content scroll. */
.interview-summary{display:grid;grid-template-rows:auto auto minmax(0,1fr);width:100%;height:100%;min-height:0;margin:0;padding:12px 0 0;overflow:hidden}
.page-heading{align-items:center;padding-bottom:18px}
.page-heading h1{font-size:clamp(24px,2.4vw,32px)}
.page-notices{display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:8px 18px;min-height:0;padding:0 0 12px}
.page-notices .feedback{flex-basis:100%}
.resume-hint{flex:1;min-width:0;margin:0}
.summarize-action{flex:none;min-height:44px;color:var(--color-on-primary);background:var(--color-primary)}
.feedback{margin:8px 0 0}
.summary-columns{display:grid;grid-template-columns:minmax(230px,1.05fr) minmax(220px,.85fr) minmax(280px,1.4fr);gap:0;min-height:0;border-top:1px solid var(--color-border)}
.summary-pane{display:flex;flex-direction:column;min-width:0;min-height:0;padding:20px 20px 0 0}
.summary-pane+.summary-pane{padding-left:20px;border-left:1px solid var(--color-border)}
.topic-column{padding-left:20px}
.summary-pane>.section-heading{flex:none;margin-bottom:14px}
.pane-scroll{min-height:0;overflow-x:hidden;overflow-y:auto;overscroll-behavior:contain;padding:0 8px 22px 0;scrollbar-gutter:stable}
.review-list{gap:8px}
.review-card{min-height:64px;padding:11px 12px;transition:background .15s,border-color .15s}
.topic-section,.topic-column .topic-section{margin:0;padding:0;border:0}
.topic-section+.topic-section,.topic-column .topic-section+.topic-section{margin-top:22px;padding-top:20px;border-top:1px solid var(--color-border)}
.topic-list>li,.topic-column .topic-list>li,.topic-column .topic-list>li:first-child{padding:0;border:0}
.topic-list{display:grid;gap:5px}
.topic-choice{display:flex;align-items:center;justify-content:space-between;gap:12px;width:100%;min-height:46px;padding:9px 12px;border:1px solid transparent;border-radius:8px;color:var(--color-foreground);background:transparent;text-align:left;transition:background .15s,border-color .15s}
.topic-choice:hover{background:var(--surface-hover)}
.topic-choice.selected{border-color:var(--color-border-strong);background:color-mix(in srgb,var(--color-primary) 12%,var(--color-card))}
.topic-choice span{min-width:0;overflow-wrap:anywhere;font-size:14px;font-weight:600}
.topic-choice strong{flex:none;color:var(--color-primary);font-size:12px;white-space:nowrap}
.detail-kind{color:var(--color-primary);font-size:12px;font-weight:700}
.topic-detail h3{margin:8px 0 10px;overflow-wrap:anywhere;font-size:clamp(20px,2vw,26px);line-height:1.35}
.detail-frequency{display:inline-block;padding:5px 9px;border-radius:6px;color:var(--color-primary);background:color-mix(in srgb,var(--color-primary) 10%,var(--color-card));font-size:12px;font-weight:700}
.detail-section{margin-top:24px;padding-top:20px;border-top:1px solid var(--color-border)}
.detail-section h4{margin:0 0 12px;font-size:14px}
.detail-section p{margin:0;color:var(--color-muted-foreground);line-height:1.7;white-space:pre-wrap;overflow-wrap:anywhere}
.detail-section ol{display:grid;gap:10px;margin:0;padding-left:22px;line-height:1.65;overflow-wrap:anywhere}
.mobile-pane-tabs{display:none}
@media(max-width:900px){
  .interview-summary{padding-top:8px;grid-template-rows:auto auto minmax(0,1fr)}
  .page-heading{align-items:flex-start;gap:10px;padding-bottom:10px}
  .page-heading p{font-size:12px}
  .page-heading>button{width:auto;min-height:44px}
  .page-notices{padding-bottom:8px}
  .resume-hint{flex-basis:100%;margin:0}
  .summarize-action{width:100%}
  .mobile-pane-tabs{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:4px;padding:0 0 10px}
  .mobile-pane-tabs button{min-height:44px;padding:7px 4px;border:1px solid var(--color-border);border-radius:7px;color:var(--color-muted-foreground);background:var(--color-card);font-size:12px}
  .mobile-pane-tabs button[aria-pressed="true"]{border-color:var(--color-primary);color:var(--color-primary);background:color-mix(in srgb,var(--color-primary) 10%,var(--color-card))}
  .summary-columns{display:block;overflow:hidden}
  .summary-pane,.topic-column,.summary-pane+.summary-pane{display:none;height:100%;padding:14px 0 0;border-left:0;border-top:0}
  .summary-pane.mobile-pane-active{display:flex}
}
</style>
