<script setup lang="ts">
import { computed, onActivated, ref, watch } from 'vue'
import { api, ApiError } from './api'
import { classifyInterviewPositions } from './interviewClassification'
import { useJobTrackerStore, type JobApplication, type JobEvent } from './jobTrackerStore'

type Category = { id: string; name: string; applicationIds: string[] }
type Topic = { name: string; count: number; kind: 'project' | 'knowledge' | 'other'; summary: string; questions: string[] }
type Summary = { sourceKey: string; stale?: boolean; topics: Topic[] }
type Workbench = { sourceKey: string; classification?: { sourceKey: string; categories: Category[] }; summaries?: Record<string, Summary> }
type Review = { event: JobEvent; application: JobApplication; company: string; position: string; title: string; questions: string }

const emit = defineEmits<{ navigate: [page: 'profile'] }>()
const store = useJobTrackerStore()
const state = ref<Workbench | null>(null)
const selectedCategoryId = ref('')
const loading = ref(false)
const classifying = ref(false)
const summarizing = ref(false)
const error = ref('')
const message = ref('')
let autoAttemptedKey = ''

async function aiPost<T>(url: string): Promise<T> {
  try { return await api<T>(url, { method: 'POST' }) }
  catch (cause) {
    if (!(cause instanceof ApiError) || cause.status !== 429) throw cause
    await new Promise(resolve => window.setTimeout(resolve, 3200))
    return api<T>(url, { method: 'POST' })
  }
}

const applicationsById = computed(() => new Map(store.applications.value.map(item => [item.id, item])))
const reviews = computed<Review[]>(() => store.events.value.flatMap(event => {
  const questions = String(event.interviewQuestions || '').trim()
  const application = applicationsById.value.get(String(event.applicationId || ''))
  if (!questions || !event.completed || event.missed || event.abandoned || !application) return []
  return [{ event, application, company: String(application.company || '未填写公司'), position: String(application.position || '未填写岗位'), title: String(event.title || event.type || '面试'), questions }]
}).sort((a, b) => String(b.event.startsAt || b.event.date || '').localeCompare(String(a.event.startsAt || a.event.date || ''))))
const reviewSignature = computed(() => reviews.value.map(item => `${item.event.id}|${item.application.id}|${item.company}|${item.position}`).sort().join('\n'))
const categories = computed(() => state.value?.classification?.categories || [])
const classificationCurrent = computed(() => !!state.value?.sourceKey && state.value.classification?.sourceKey === state.value.sourceKey)
const selectedCategory = computed(() => categories.value.find(item => item.id === selectedCategoryId.value) || null)
const categoryReviews = computed(() => {
  const ids = new Set(selectedCategory.value?.applicationIds || [])
  return reviews.value.filter(item => ids.has(item.application.id))
})
const selectedSummary = computed(() => selectedCategoryId.value ? state.value?.summaries?.[selectedCategoryId.value] : undefined)
const hasResume = computed(() => {
  const resume = (store.data.value.settings?.interviewWorkbench as { resume?: { internships?: unknown[]; projects?: unknown[] } } | undefined)?.resume
  return !!(resume?.internships?.length || resume?.projects?.length)
})

async function loadState(autoClassify = true) {
  if (loading.value) return
  loading.value = true
  try {
    state.value = await api<Workbench>('/api/poc/interview-workbench')
    if (categories.value.length && !categories.value.some(item => item.id === selectedCategoryId.value)) selectedCategoryId.value = categories.value[0].id
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '读取面试总结失败' }
  finally { loading.value = false }
  if (autoClassify && state.value && reviews.value.length && !classificationCurrent.value && state.value.sourceKey !== autoAttemptedKey) {
    autoAttemptedKey = state.value.sourceKey
    await classify(true)
  }
}

async function classify(automatic = false) {
  if (classifying.value || !reviews.value.length) return
  classifying.value = true; error.value = ''; message.value = ''
  try {
    state.value = await classifyInterviewPositions<Workbench>()
    if (!categories.value.some(item => item.id === selectedCategoryId.value)) selectedCategoryId.value = categories.value[0]?.id || ''
    autoAttemptedKey = state.value.sourceKey
    message.value = automatic ? '新面试回顾已自动重新分类' : '岗位分类已更新'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '岗位分类失败' }
  finally { classifying.value = false }
}

async function summarize() {
  if (!selectedCategory.value || summarizing.value || !classificationCurrent.value) return
  summarizing.value = true; error.value = ''; message.value = ''
  try {
    state.value = await aiPost<Workbench>(`/api/poc/interview-workbench/categories/${encodeURIComponent(selectedCategory.value.id)}/summarize`)
    message.value = '考点汇总已更新'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '考点汇总失败' }
  finally { summarizing.value = false }
}

function activate() { void store.refresh(false, false, false).then(() => loadState()) }
onActivated(activate)
watch(reviewSignature, (next, previous) => { if (previous && next !== previous) void loadState() })
</script>

<template>
  <section class="interview-summary" aria-labelledby="interview-summary-title">
    <header class="page-heading"><div><span class="eyebrow">面试复盘</span><h1 id="interview-summary-title">面试总结</h1><p>从已完成日程中的面试回顾整理问题，按岗位方向归类并汇总考点。</p></div><button type="button" class="primary-action" :disabled="classifying || !reviews.length" @click="classify(false)">{{ classifying ? '正在分类…' : 'AI 整理岗位类别' }}</button></header>
    <p v-if="error" class="feedback error" role="alert">{{ error }}</p><p v-if="message" class="feedback success" role="status">{{ message }}</p>
    <p class="resume-hint">考点汇总可以参考你的实习和项目经历。<button type="button" @click="emit('navigate', 'profile')">{{ hasResume ? '查看简历配置' : '先去个人主页设置简历 →' }}</button></p>
    <div class="summary-columns">
      <section class="review-column" aria-labelledby="review-list-title">
        <div class="section-heading"><div><small>原始记录</small><h2 id="review-list-title">面试回顾</h2></div><span>{{ reviews.length }} 条</span></div>
        <p v-if="!reviews.length" class="empty-state">还没有已完成且写有面试回顾的日程。完成面试后可在日程编辑中记录面试官的问题。</p>
        <ol v-else class="review-list"><li v-for="item in reviews" :key="item.event.id" class="review-item"><div class="review-heading"><strong>{{ item.company }}</strong><span>{{ item.title }}</span></div><p class="position">{{ item.position }}</p><p class="questions">{{ item.questions }}</p></li></ol>
      </section>
      <section class="category-column" aria-labelledby="category-title">
        <div class="section-heading"><div><small>分类副本</small><h2 id="category-title">岗位类别</h2></div><span>{{ categories.length }} 类</span></div>
        <p v-if="!reviews.length" class="empty-state">有面试回顾后，这里会展示按岗位分类的记录。</p>
        <p v-else-if="!categories.length || !classificationCurrent" class="empty-state">{{ classifying ? '正在根据公司和岗位名称分类…' : '岗位分类待生成。点击上方按钮可重试。' }}</p>
        <template v-else><nav class="category-directory" aria-label="岗位类别"><button v-for="category in categories" :key="category.id" type="button" :class="{ selected: selectedCategoryId === category.id }" :aria-pressed="selectedCategoryId === category.id" @click="selectedCategoryId = category.id">{{ category.name }}<span>{{ reviews.filter(item => category.applicationIds.includes(item.application.id)).length }}</span></button></nav>
          <div v-if="selectedCategory" class="category-content"><div class="category-actions"><h3>{{ selectedCategory.name }}</h3><button type="button" class="primary-action" :disabled="summarizing || classifying || !categoryReviews.length" @click="summarize">{{ summarizing ? '正在汇总…' : selectedSummary ? '重新汇总考点' : 'AI 汇总考点' }}</button></div>
            <ol class="review-list compact"><li v-for="item in categoryReviews" :key="item.event.id" class="review-item"><div class="review-heading"><strong>{{ item.company }}</strong><span>{{ item.title }}</span></div><p class="position">{{ item.position }}</p><p class="questions">{{ item.questions }}</p></li></ol>
            <section v-if="selectedSummary && !selectedSummary.stale" class="topic-section" aria-label="考点汇总"><div class="section-heading"><div><small>AI 汇总</small><h3>高频考点</h3></div><span>按频率排序</span></div><ol class="topic-list"><li v-for="topic in selectedSummary.topics" :key="topic.name"><div class="topic-heading"><strong>{{ topic.name }}</strong><span>{{ topic.count }} 次 · {{ topic.kind === 'project' ? '简历项目 / 实习' : topic.kind === 'knowledge' ? '通用知识' : '其他' }}</span></div><p>{{ topic.summary }}</p><ul v-if="topic.questions?.length"><li v-for="question in topic.questions" :key="question">{{ question }}</li></ul></li></ol></section>
            <p v-else class="summary-note">{{ selectedSummary?.stale ? '回顾或简历已更新，请重新汇总考点。' : '选择此类别的“AI 汇总考点”，查看按出现频率排序的问题。' }}</p>
          </div>
        </template>
      </section>
    </div>
  </section>
</template>

<style scoped>
.interview-summary{width:min(1500px,100%);margin:0 auto;padding:32px 0 72px}.page-heading{display:flex;align-items:end;justify-content:space-between;gap:20px;padding-bottom:24px;border-bottom:1px solid var(--color-border)}.eyebrow,.section-heading small{color:var(--color-primary);font-size:11px;font-weight:700;letter-spacing:.07em}h1{margin:7px 0;font-size:clamp(27px,3vw,36px)}h2{margin:4px 0 0;font-size:19px}h3{margin:0;font-size:17px}.page-heading p,.resume-hint,.position,.summary-note{color:var(--color-muted-foreground)}.page-heading p{margin:0;line-height:1.5}.page-heading>button,.category-actions>button{flex:none;color:var(--color-on-primary);background:var(--color-primary)}.resume-hint{display:flex;flex-wrap:wrap;gap:5px;margin:18px 0 24px;font-size:13px}.resume-hint button{padding:0;border:0;color:var(--color-primary);background:none;font-weight:700}.feedback{padding:10px 12px;margin:16px 0 0;border-radius:8px;font-size:13px}.error{color:var(--color-destructive);background:color-mix(in srgb,var(--color-destructive) 10%,var(--color-background))}.success{color:var(--color-primary);background:color-mix(in srgb,var(--color-primary) 9%,var(--color-background))}.summary-columns{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:32px}.review-column,.category-column{min-width:0}.category-column{padding-left:32px;border-left:1px solid var(--color-border)}.section-heading{display:flex;align-items:end;justify-content:space-between;gap:12px;margin-bottom:16px}.section-heading>span{color:var(--color-muted-foreground);font-size:12px}.review-list,.topic-list{margin:0;padding:0;list-style:none}.review-item{padding:18px 0;border-top:1px solid var(--color-border)}.review-heading{display:flex;align-items:baseline;justify-content:space-between;gap:14px}.review-heading strong{font-size:15px}.review-heading span{flex:none;color:var(--color-primary);font-size:12px;font-weight:700}.position{margin:5px 0 12px;font-size:13px}.questions{margin:0;line-height:1.65;white-space:pre-wrap;overflow-wrap:anywhere}.empty-state{padding:22px 0;border-top:1px solid var(--color-border);color:var(--color-muted-foreground);line-height:1.7}.category-directory{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:25px}.category-directory button{display:flex;align-items:center;gap:10px;padding:8px 12px;border:1px solid var(--color-border);border-radius:8px;color:var(--color-foreground);background:var(--color-card);font-size:13px}.category-directory button.selected{border-color:var(--color-primary);color:var(--color-primary);background:color-mix(in srgb,var(--color-primary) 9%,var(--color-card))}.category-directory span{color:var(--color-muted-foreground)}.category-actions{display:flex;align-items:center;justify-content:space-between;gap:12px;padding-bottom:14px}.compact .review-item{padding:13px 0}.compact .questions{font-size:13px}.topic-section{padding-top:22px;margin-top:20px;border-top:1px solid var(--color-border)}.topic-list>li{padding:16px 0;border-top:1px solid var(--color-border)}.topic-heading{display:flex;align-items:baseline;justify-content:space-between;gap:12px}.topic-heading span{color:var(--color-primary);font-size:12px}.topic-list p{margin:8px 0;color:var(--color-muted-foreground);line-height:1.6}.topic-list ul{padding-left:18px;margin:8px 0 0;line-height:1.6}.summary-note{margin:10px 0 0;font-size:13px;line-height:1.6}
@media(max-width:900px){.summary-columns{grid-template-columns:1fr;gap:35px}.category-column{padding:28px 0 0;border-left:0;border-top:1px solid var(--color-border)}}
@media(max-width:620px){.interview-summary{padding:22px 0 48px}.page-heading{align-items:start;flex-direction:column}.page-heading>button{width:100%}.category-actions{align-items:start;flex-direction:column}.category-actions>button{width:100%}}
.resume-hint button,.category-directory button,.category-actions>button{min-height:44px}.review-heading strong{min-width:0;overflow-wrap:anywhere}.review-heading span{max-width:40%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.category-directory button{transition:background .18s ease,border-color .18s ease}
</style>
