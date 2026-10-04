<script setup lang="ts">
import { computed, nextTick, onActivated, onDeactivated, ref, watch } from 'vue'
import { api } from './api'
import { summarizeInterviewReviewsStream } from './interviewSummary'
import { useJobTrackerStore, type JobApplication, type JobEvent } from './jobTrackerStore'

type Topic = { name: string; count: number; kind: 'project' | 'knowledge' | 'other'; summary: string; questionAnswers: { question: string; answer: string; answerStatus?: string; frequency?: number; sourceQuestions?: { question: string; eventId?: string }[] }[] }
type Summary = { sourceKey: string; stale?: boolean; topics: Topic[] }
type Workbench = { sourceKey: string; overallSummary?: Summary; summaryJob?: { stage?: string; classificationBatches?: { status: string }[] } }
type Review = { event: JobEvent; application: JobApplication; company: string; position: string; title: string; questions: string }

const emit = defineEmits<{ navigate: [page: 'profile'] }>()
const store = useJobTrackerStore()
const state = ref<Workbench | null>(null)
const loading = ref(false)
const summarizing = ref(false)
const streamingQuestions = ref<{ question: string; answer: string; frequency: number }[]>([])
const streamingStatus = ref('')
const error = ref('')
const message = ref('')
const selectedReviewId = ref('')
const reviewDialog = ref<HTMLDialogElement | null>(null)
const selectedTopicKey = ref('')
const activeTopicKind = ref<'project' | 'knowledge' | 'other'>('project')
const activePane = ref<'reviews' | 'workbench'>('reviews')
const workbenchScroll = ref<HTMLElement | null>(null)
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
const hasIncompleteAnswers = computed(() => (summary.value?.topics || []).some(topic => topic.questionAnswers.some(item => !item.answer)))
const projectTopics = computed(() => (summary.value?.topics || []).filter(item => item.kind === 'project').sort((a, b) => b.count - a.count))
const knowledgeTopics = computed(() => (summary.value?.topics || []).filter(item => item.kind === 'knowledge').sort((a, b) => b.count - a.count))
const otherTopics = computed(() => (summary.value?.topics || []).filter(item => item.kind === 'other').sort((a, b) => b.count - a.count))
const topicGroups = computed(() => [
  { kind: 'project', label: '项目考点', topics: projectTopics.value },
  { kind: 'knowledge', label: '八股考点', topics: knowledgeTopics.value },
  { kind: 'other', label: '其他问题', topics: otherTopics.value }
])
const activeTopicGroup = computed(() => topicGroups.value.find(group => group.kind === activeTopicKind.value)!)
const activeTopics = computed(() => activeTopicGroup.value.topics)
const topicEntries = computed(() => summary.value?.stale ? [] : activeTopics.value.map((topic, index) => ({
  key: `${activeTopicKind.value}:${index}:${topic.name}`, group: activeTopicGroup.value.label, topic
})))
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
  const unfinishedJob = !!state.value?.summaryJob && state.value.summaryJob.stage !== 'completed'
  if (autoSummarize && state.value && reviews.value.length && (unfinishedJob || !summary.value || summary.value.stale) && state.value.sourceKey !== autoAttemptedKey) {
    autoAttemptedKey = state.value.sourceKey
    await summarize(true)
  }
}

async function summarize(automatic = false, force = false) {
  if (!reviews.value.length || summarizing.value) return
  summarizing.value = true; error.value = ''; message.value = ''
  streamingQuestions.value = []
  streamingStatus.value = '正在准备面试回顾…'
  try {
    await summarizeInterviewReviewsStream(event => {
      if (event.type === 'progress') streamingStatus.value = event.message
      else if (event.type === 'classified') {
        state.value = { ...state.value, sourceKey: state.value?.sourceKey || '', overallSummary: event.summary as Summary }
        streamingStatus.value = '分类已完成，正在逐批生成学习讲解…'
      }
      else streamingQuestions.value.push({ question: event.question, answer: event.answer, frequency: event.frequency || 1 })
    }, force)
    state.value = await api<Workbench>('/api/poc/interview-workbench')
    autoAttemptedKey = state.value.sourceKey
    message.value = automatic ? '面试回顾已自动汇总' : '考点汇总已更新'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '考点汇总失败' }
  finally { summarizing.value = false; streamingStatus.value = '' }
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
  await nextTick()
  workbenchScroll.value?.scrollTo({ top: 0 })
}
function switchTopicKind(kind: 'project' | 'knowledge' | 'other') {
  if (activeTopicKind.value === kind) return
  activeTopicKind.value = kind
  selectedTopicKey.value = ''
  workbenchScroll.value?.scrollTo({ top: 0 })
}
onActivated(activate)
onDeactivated(closeReview)
watch(reviewSignature, (next, previous) => { if (previous && next !== previous) void loadState() })
watch(topicEntries, entries => {
  if (!entries.some(item => item.key === selectedTopicKey.value)) selectedTopicKey.value = entries[0]?.key || ''
})
</script>

<template>
  <section class="interview-summary" aria-label="面试总结">
    <div class="page-notices">
      <p v-if="error" class="feedback error" role="alert">{{ error }}</p><p v-if="message" class="feedback success" role="status">{{ message }}</p>
      <p class="resume-hint"><span>考点汇总可以参考你的实习和项目经历。</span><button type="button" @click="emit('navigate', 'profile')">{{ hasResume ? '查看简历配置' : '先去个人主页设置简历 →' }}</button></p>
      <button type="button" class="primary-action summarize-action" :disabled="summarizing || !reviews.length" @click="summarize(false, !!summary && !summary.stale && !hasIncompleteAnswers)">{{ summarizing ? '正在分批整理…' : summary?.stale ? '重新汇总全部考点' : hasIncompleteAnswers ? '继续生成未完成回答' : summary ? '重新汇总全部考点' : 'AI 汇总全部考点' }}</button>
    </div>
    <nav class="mobile-pane-tabs" aria-label="面试总结栏目"><button type="button" :aria-pressed="activePane === 'reviews'" @click="activePane = 'reviews'">面试回顾</button><button type="button" :aria-pressed="activePane === 'workbench'" @click="activePane = 'workbench'">考点整理</button></nav>
    <div class="summary-columns">
      <section class="review-column summary-pane" :class="{ 'mobile-pane-active': activePane === 'reviews' }" aria-labelledby="review-list-title">
        <div class="section-heading"><div><small>原始记录</small><h2 id="review-list-title">面试回顾</h2></div><span>{{ reviews.length }} 条</span></div>
        <div class="pane-scroll"><p v-if="!reviews.length" class="empty-state">还没有已完成且写有面试回顾的日程。完成面试后可在日程编辑中记录面试官的问题。</p>
          <ol v-else class="review-list"><li v-for="item in reviews" :key="item.event.id"><button type="button" class="review-card" @click="openReview(item.event.id)"><span class="review-card-heading"><strong>{{ item.company }}</strong><span>{{ item.title }}</span></span><span class="review-card-position">{{ item.position }}</span></button></li></ol></div>
      </section>
      <section class="workbench-column summary-pane" :class="{ 'mobile-pane-active': activePane === 'workbench' }" aria-labelledby="topic-title">
        <div class="workbench-header">
          <div class="workbench-heading"><div><small>AI 归纳</small><h2 id="topic-title">考点整理</h2></div><div class="topic-kind-switch" role="group" aria-label="切换考点类型"><button type="button" :aria-pressed="activeTopicKind === 'project'" @click="switchTopicKind('project')">项目考点</button><button type="button" :aria-pressed="activeTopicKind === 'knowledge'" @click="switchTopicKind('knowledge')">八股考点</button><button type="button" :aria-pressed="activeTopicKind === 'other'" @click="switchTopicKind('other')">其他</button><span class="topic-kind-indicator" :class="{ 'is-knowledge': activeTopicKind === 'knowledge', 'is-other': activeTopicKind === 'other' }" aria-hidden="true"></span></div></div>
          <div v-if="summary && !summary.stale" class="topic-tags" role="group" :aria-label="`${activeTopicGroup.label}分类`"><button v-for="entry in topicEntries" :key="entry.key" type="button" class="topic-tag" :class="{ selected: selectedTopicKey === entry.key }" :aria-pressed="selectedTopicKey === entry.key" @click="selectTopic(entry.key)"><span>{{ entry.topic.name }}</span><strong>{{ entry.topic.count }}</strong></button><span v-if="!topicEntries.length" class="topic-tag-empty">暂无{{ activeTopicGroup.label }}</span></div>
          <div v-else class="topic-tags topic-tags-placeholder"><span>汇总后可按考点类别筛选</span></div>
        </div>
        <div ref="workbenchScroll" class="pane-scroll workbench-content">
          <section v-if="summarizing" class="stream-preview" aria-label="实时生成的复习内容">
            <div class="stream-status" role="status" aria-live="polite" aria-atomic="true"><span class="stream-spinner" aria-hidden="true"></span><span>{{ streamingStatus }}</span><span v-if="streamingQuestions.length" class="stream-count">已生成 {{ streamingQuestions.length }} 题</span></div>
            <ol v-if="streamingQuestions.length" class="stream-question-list"><li v-for="(item, index) in streamingQuestions" :key="`${index}:${item.question}`" class="question-answer"><h5><span>归纳题 {{ index + 1 }}</span>{{ item.question }}<small v-if="item.frequency > 1" class="merged-frequency">合并 {{ item.frequency }} 个问法</small></h5><div class="reference-answer"><strong>学习讲解</strong><p>{{ item.answer }}</p></div></li></ol>
            <p v-else class="stream-waiting">AI 正在整理问题并撰写详细讲解，完成一题后会立即显示在这里。</p>
          </section>
          <p v-if="!reviews.length" class="empty-state">记录面试回顾后，这里会归纳问题并生成详细参考回答。</p>
          <p v-else-if="(!summary || summary.stale) && !summarizing" class="empty-state">{{ summary?.stale ? '面试回顾或简历已更新，请重新汇总全部考点。' : '点击“AI 汇总全部考点”开始归纳。' }}</p>
          <div v-else-if="selectedTopic" class="topic-detail"><p v-if="selectedTopic.topic.kind === 'project' && !hasResume" class="project-resume-hint">项目类讲解可以结合你的真实实习和项目经历进一步个性化。<button type="button" @click="emit('navigate', 'profile')">去个人主页设置简历 →</button></p><section v-if="selectedTopic.topic.summary" class="detail-section"><h4>考点总结</h4><p>{{ selectedTopic.topic.summary }}</p></section><section v-if="selectedTopic.topic.questionAnswers?.length" class="detail-section"><h4>问题与详细讲解 <span>{{ selectedTopic.topic.questionAnswers.length }} 个归纳问题</span></h4><ol class="question-answer-list"><li v-for="(item, index) in selectedTopic.topic.questionAnswers" :key="`${index}:${item.question}`" class="question-answer"><h5><span>归纳题 {{ index + 1 }}</span>{{ item.question }}<small v-if="(item.frequency || 1) > 1" class="merged-frequency">合并 {{ item.frequency }} 个问法</small></h5><details v-if="item.sourceQuestions?.length" class="source-questions"><summary>查看原始问法（{{ item.sourceQuestions.length }}）</summary><ul><li v-for="(source, sourceIndex) in item.sourceQuestions" :key="`${source.eventId || ''}:${sourceIndex}`">{{ source.question }}</li></ul></details><div class="reference-answer"><strong>学习讲解</strong><p>{{ item.answer }}</p></div></li></ol></section></div>
          <p v-else-if="!summarizing" class="empty-state">{{ summary && !summary.stale ? '选择顶部的考点类别，查看详细问题和参考回答。' : '完成考点汇总后，在顶部选择类别查看详细内容。' }}</p>
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
.topic-column-heading{align-items:center}
.topic-kind-switch{position:relative;isolation:isolate;display:grid;grid-template-columns:repeat(3,minmax(76px,1fr));flex:none;min-width:246px;padding:4px;overflow:hidden;border:1px solid color-mix(in srgb,var(--color-foreground) 16%,transparent);border-radius:13px;background:color-mix(in srgb,var(--color-foreground) 5%,transparent);box-shadow:inset 0 1px 0 color-mix(in srgb,var(--color-foreground) 10%,transparent),0 5px 18px color-mix(in srgb,#000 12%,transparent);backdrop-filter:blur(16px);-webkit-backdrop-filter:blur(16px)}
.topic-kind-switch button{position:relative;z-index:1;min-width:76px;min-height:40px;padding:8px 8px;border:0;border-radius:10px;color:var(--color-muted-foreground);background:transparent;font-size:13px;font-weight:600;white-space:nowrap;transition:color .42s ease,font-weight .42s ease}
.topic-kind-switch button:hover{color:var(--color-foreground)}
.topic-kind-switch button[aria-pressed="true"]{color:var(--color-foreground);font-weight:800}
.topic-kind-indicator{position:absolute;z-index:0;top:4px;bottom:4px;left:4px;width:calc(33.333% - 2.67px);border:1px solid color-mix(in srgb,var(--color-primary) 55%,transparent);border-radius:10px;background:color-mix(in srgb,var(--color-primary) 22%,transparent);box-shadow:inset 0 1px 0 color-mix(in srgb,#fff 18%,transparent),0 3px 12px color-mix(in srgb,var(--color-primary) 20%,transparent);backdrop-filter:blur(12px);-webkit-backdrop-filter:blur(12px);transform:translateX(0);transition:transform .62s cubic-bezier(.2,.85,.25,1),background-color .38s ease,box-shadow .38s ease}
.topic-kind-indicator.is-knowledge{transform:translateX(100%)}
.topic-kind-indicator.is-other{transform:translateX(200%)}
.topic-kind-switch button:focus-visible{outline:2px solid var(--color-primary);outline-offset:-3px}
.topic-content-enter-active{transition:opacity .22s ease,transform .22s ease}
.topic-content-leave-active{transition:opacity .15s ease,transform .15s ease}
.topic-content-enter-from{opacity:0;transform:translateY(8px)}
.topic-content-leave-to{opacity:0;transform:translateY(-5px)}
@media(prefers-reduced-motion:reduce){.topic-kind-indicator,.topic-kind-switch button,.topic-content-enter-active,.topic-content-leave-active{transition:none}}
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
/* Keep reviews beside one unified topic workspace; only the workspace body scrolls. */
.summary-columns{grid-template-columns:minmax(240px,.78fr) minmax(0,2fr)}
.summary-pane+.summary-pane{padding-left:22px}
.workbench-column{padding-right:0!important}
.workbench-header{flex:none;padding:0 0 14px;border-bottom:1px solid var(--color-border)}
.workbench-heading{display:flex;align-items:center;justify-content:space-between;gap:16px;min-height:54px}
.workbench-heading h2{margin-top:4px}
.topic-tags{display:flex;align-items:center;gap:8px;max-width:100%;overflow-x:auto;overscroll-behavior-x:contain;padding:12px 1px 2px;scrollbar-width:thin}
.topic-tag{display:inline-flex;align-items:center;gap:9px;flex:none;max-width:240px;min-height:38px;padding:6px 11px;border:1px solid var(--color-border);border-radius:999px;color:var(--color-foreground);background:var(--color-card);font-size:13px;transition:color .16s,border-color .16s,background .16s}
.topic-tag:hover{border-color:var(--color-border-strong);background:var(--surface-hover)}
.topic-tag.selected{border-color:var(--color-primary);color:var(--color-primary);background:color-mix(in srgb,var(--color-primary) 12%,var(--color-card))}
.topic-tag span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.topic-tag strong{flex:none;font-size:12px}
.topic-tag-empty,.topic-tags-placeholder{color:var(--color-muted-foreground);font-size:13px}
.workbench-content{padding-right:12px}
.topic-detail{max-width:1000px;padding:2px 2px 28px}
.detail-frequency{flex:none}
.detail-section{margin-top:16px;padding-top:18px}
.detail-section h4{display:flex;align-items:center;justify-content:space-between;gap:10px}
.detail-section h4 span{color:var(--color-muted-foreground);font-size:12px;font-weight:500}
.question-answer-list{display:grid;gap:14px;margin:0;padding:0;list-style:none}
.question-answer{padding:16px;border:1px solid var(--color-border);border-radius:10px;background:color-mix(in srgb,var(--color-card) 85%,transparent)}
.question-answer h5{display:flex;align-items:flex-start;gap:10px;margin:0;font-size:15px;line-height:1.6;overflow-wrap:anywhere}
.question-answer h5 span{flex:none;padding:2px 7px;border-radius:5px;color:var(--color-primary);background:color-mix(in srgb,var(--color-primary) 11%,var(--color-card));font-size:11px}
.merged-frequency{flex:none;padding:2px 7px;border-radius:999px;color:var(--color-muted-foreground);background:var(--color-background);font-size:11px;font-weight:500}
.source-questions{margin:12px 0 0;color:var(--color-muted-foreground);font-size:13px}
.source-questions summary{width:fit-content;cursor:pointer;color:var(--color-primary)}
.source-questions ul{display:grid;gap:5px;padding-left:20px;margin:8px 0 0;line-height:1.6}
.reference-answer{margin:14px 0 0;padding:13px 15px;border-left:3px solid var(--color-primary);border-radius:0 8px 8px 0;background:color-mix(in srgb,var(--color-primary) 5%,var(--color-card))}
.reference-answer strong{color:var(--color-primary);font-size:12px}
.reference-answer p{margin:7px 0 0;color:var(--color-foreground);line-height:1.8;white-space:pre-wrap;overflow-wrap:anywhere}
.project-resume-hint{padding:12px 14px;border-radius:8px;color:var(--color-muted-foreground);background:color-mix(in srgb,var(--color-primary) 6%,var(--color-card));font-size:13px;line-height:1.6}
.project-resume-hint button{display:inline;padding:0;border:0;color:var(--color-primary);background:transparent;font-weight:700}
.stream-preview{padding:4px 0 18px}
.stream-status{position:sticky;top:0;z-index:1;display:flex;align-items:center;gap:10px;min-height:42px;padding:8px 12px;border:1px solid color-mix(in srgb,var(--color-primary) 28%,var(--color-border));border-radius:9px;color:var(--color-primary);background:color-mix(in srgb,var(--color-card) 94%,transparent);backdrop-filter:blur(10px);font-size:13px;font-weight:600}
.stream-spinner{width:15px;height:15px;flex:none;border:2px solid color-mix(in srgb,var(--color-primary) 25%,transparent);border-top-color:var(--color-primary);border-radius:50%;animation:interview-stream-spin .8s linear infinite}
.stream-count{margin-left:auto;color:var(--color-muted-foreground);font-size:12px;font-weight:500;white-space:nowrap}
.stream-waiting{padding:14px 4px;color:var(--color-muted-foreground);font-size:13px;line-height:1.7}
.stream-question-list{display:grid;gap:14px;margin:14px 0 0;padding:0;list-style:none}
@keyframes interview-stream-spin{to{transform:rotate(360deg)}}
@media(max-width:900px){
  .summary-columns{display:block;overflow:hidden}
  .summary-pane,.summary-pane+.summary-pane,.workbench-column{display:none;height:100%;padding:14px 0 0!important;border:0}
  .summary-pane.mobile-pane-active{display:flex}
  .mobile-pane-tabs{grid-template-columns:repeat(2,minmax(0,1fr))}
  .workbench-header{padding-bottom:10px}
  .topic-tags{padding-top:9px}
}
@media(max-width:620px){
  .workbench-heading{align-items:flex-start;flex-direction:column;gap:6px}
  .topic-kind-switch{align-self:stretch;grid-template-columns:repeat(2,minmax(0,1fr));min-width:0}
  .topic-kind-switch button{min-width:0}
  .question-answer{padding:13px}
}
@media(prefers-reduced-motion:reduce){.topic-tag{transition:none}}
@media(prefers-reduced-motion:reduce){.stream-spinner{animation:none}}
</style>
