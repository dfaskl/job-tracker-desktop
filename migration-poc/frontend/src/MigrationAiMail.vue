<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, apiCached, ApiError } from './api'
import { type JobApplication, useJobTrackerStore } from './jobTrackerStore'

type AiStatus = { callsEnabled: boolean }
type Recognition = { company: string; position: string; noticeType: string; scheduleTitle: string; suggestedStage: string; suggestedStatus: string; startsAt: string; endsAt: string; location: string; summary: string }
const noticeTypes = ['测评', '笔试', '面试', 'Offer', '未通过', '其他']
const store = useJobTrackerStore()
const status = ref<AiStatus | null>(null)
const mailBody = ref('')
const result = reactive({ company: '', position: '', noticeType: '其他', scheduleTitle: '', suggestedStage: '已投递', suggestedStatus: '等待结果', startsAt: '', endsAt: '', location: '', summary: '', notes: '' })
const hasResult = ref(false)
const createSchedule = ref(true)
const timeMode = ref<'point' | 'range'>('point')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const message = ref('')
const selectedApplicationId = ref('')

const matchedApplication = computed(() => store.applications.value.find(item => item.id === selectedApplicationId.value))
const rankedApplications = computed(() => store.applications.value.slice().sort((a,b) =>
  applicationMatchScore(b, result.company, result.position) - applicationMatchScore(a, result.company, result.position)
))
const recommendedApplications = computed(() => rankedApplications.value.filter(isRecommendedApplication))
const otherApplications = computed(() => rankedApplications.value.filter(item => !isRecommendedApplication(item)))
const canCreateSchedule = computed(() => Boolean(result.startsAt) && result.noticeType !== '未通过')
const actionSummary = computed(() => hasResult.value
  ? `${matchedApplication.value ? '更新已有投递' : '新建一条投递'}${createSchedule.value && canCreateSchedule.value ? '，并创建关联日程' : ''}`
  : '')

onMounted(async () => {
  await store.initialize()
  await checkStatus()
})
function normalize(value: unknown) { return String(value || '').trim().toLocaleLowerCase().replace(/[^0-9a-z一-龥]/gi, '') }
function companyKey(value: unknown) { return normalize(value).replace(/股份有限公司|有限责任公司|有限公司|集团|公司$/g, '') }
function textSimilarity(left: unknown, right: unknown) {
  const a=normalize(left),b=normalize(right)
  if(!a||!b)return 0
  if(a===b)return 1
  if(a.includes(b)||b.includes(a))return .86
  const chars=new Set(a),other=new Set(b),common=[...other].filter(char=>chars.has(char)).length
  return common/Math.max(chars.size,other.size)
}
function applicationMatchScore(item:JobApplication,company:unknown,position:unknown) {
  return textSimilarity(companyKey(item.company),companyKey(company))*.68+textSimilarity(item.position,position)*.32
}
function isRecommendedApplication(item:JobApplication) {
  return textSimilarity(companyKey(item.company),companyKey(result.company))>=.72&&applicationMatchScore(item,result.company,result.position)>=.65
}
function matchPercent(item:JobApplication) { return Math.round(applicationMatchScore(item,result.company,result.position)*100) }
function suggestApplication(company:unknown,position:unknown) {
  const targetCompany=normalize(company),targetPosition=normalize(position)
  if(!targetCompany||!targetPosition)return undefined
  return store.applications.value.find(item=>normalize(item.company)===targetCompany&&normalize(item.position)===targetPosition)
}
function inputTime(value: string) { return value ? value.replace(' ', 'T').slice(0, 16) : '' }
function apiTime(value: string) { return value ? value.replace('T', ' ').slice(0, 16) : '' }
function today() { const d=new Date(),pad=(v:number)=>String(v).padStart(2,'0'); return d.getFullYear()+'-'+pad(d.getMonth()+1)+'-'+pad(d.getDate()) }
function failure(cause: unknown, fallback: string) {
  if (cause instanceof ApiError && cause.status === 401) return '请先登录旧账号'
  return cause instanceof Error ? cause.message : fallback
}
async function checkStatus() {
  loading.value = true; error.value = ''
  try {
    status.value = await apiCached<AiStatus>('/api/poc/ai-sandbox/status')
  } catch (cause) { error.value = failure(cause, '检查 AI 服务失败') }
  finally { loading.value = false }
}
async function recognize() {
  loading.value = true; error.value = ''; message.value = ''; hasResult.value = false
  try {
    const value = await api<Recognition>('/api/poc/ai-sandbox/recognize', { method: 'POST', body: JSON.stringify({ body: mailBody.value }) })
    Object.assign(result, value, { scheduleTitle: value.scheduleTitle || value.noticeType || '日程', startsAt: inputTime(value.startsAt), endsAt: inputTime(value.endsAt), notes: '' })
    selectedApplicationId.value = suggestApplication(value.company,value.position)?.id || ''
    hasResult.value = true
    timeMode.value = value.endsAt ? 'range' : 'point'
    createSchedule.value = Boolean(value.startsAt) && value.noticeType !== '未通过'
    message.value = '识别完成，请核对后确认录入'
  } catch (cause) { error.value = failure(cause, '邮件识别失败') }
  finally { loading.value = false }
}
function applicationPayload(item?: JobApplication) {
  return {
    company: String(item?.company || result.company).trim(), position: String(item?.position || result.position).trim(),
    city: String(item?.city || ''), channel: String(item?.channel || '邮件识别'),
    appliedDate: String(item?.appliedDate || today()), stage: result.suggestedStage,
    status: result.suggestedStatus, notes: result.notes.trim() || String(item?.notes || ''),
    expectedUpdatedAt: String(item?.updatedAt || '')
  }
}
async function saveResult() {
  if (!result.company.trim() || !result.position.trim()) { error.value = '请补全公司和岗位后再录入'; return }
  if(createSchedule.value&&canCreateSchedule.value&&timeMode.value==='range'&&!result.endsAt){error.value='时间段日程必须填写结束时间';return}
  const startTime=result.startsAt?new Date(result.startsAt).getTime():NaN,endTime=result.endsAt?new Date(result.endsAt).getTime():NaN
  if(createSchedule.value&&timeMode.value==='range'&&(!Number.isFinite(startTime)||!Number.isFinite(endTime)||endTime<=startTime)){error.value='结束时间必须晚于开始时间';return}
  saving.value = true; error.value = ''; message.value = ''
  try {
    const matched = matchedApplication.value
    const response = matched
      ? await api<{ application: JobApplication }>(`/api/poc/application-sandbox/applications/${encodeURIComponent(matched.id)}`, { method: 'PUT', body: JSON.stringify(applicationPayload(matched)) })
      : await api<{ application: JobApplication }>('/api/poc/application-sandbox/applications', { method: 'POST', body: JSON.stringify(applicationPayload()) })
    let duplicateSchedule=false
    if (createSchedule.value && canCreateSchedule.value) {
      const eventType=result.noticeType==='其他'?'其他':result.noticeType
      const startsAt=apiTime(result.startsAt),endsAt=timeMode.value==='range'?apiTime(result.endsAt):''
      duplicateSchedule=store.events.value.some(event=>event.applicationId===response.application.id&&String(event.type||'')===eventType&&apiTime(String(event.startsAt||event.start||event.date||''))===startsAt&&apiTime(String(event.endsAt||event.end||''))===endsAt)
      if(!duplicateSchedule)await api('/api/poc/event-sandbox/events', { method: 'POST', body: JSON.stringify({
        applicationId: response.application.id, type: eventType,
        title: result.scheduleTitle.trim()||(eventType==='其他'?'邮件通知':eventType),
        startsAt, endsAt,
        location: result.location.trim(), notes: result.notes.trim(), expectedUpdatedAt: ''
      }) })
    }
    await store.refresh()
    message.value = duplicateSchedule ? `已${matched ? '更新投递' : '新建投递'}；相同日程已存在，未重复创建` : `已${matched ? '更新投递' : '新建投递'}${createSchedule.value && canCreateSchedule.value ? '并创建日程' : ''}，写入前备份已自动生成`
    mailBody.value = ''; hasResult.value = false; selectedApplicationId.value = ''
  } catch (cause) { error.value = failure(cause, '录入识别结果失败') }
  finally { saving.value = false }
}
</script>

<template>
  <section class="mail-page">
    <div class="mail-toolbar">
      <div><span class="eyebrow">智能录入</span><h2>从招聘通知中提取投递与日程</h2><p>粘贴完整通知，识别后先核对，再由你确认写入。</p></div>
    </div>

    <div class="mail-grid">
      <section class="card source-panel">
        <div class="panel-title"><div><span class="step">1</span><h3>粘贴通知正文</h3></div><button class="text-button" :disabled="!mailBody" @click="mailBody = ''">清空</button></div>
        <textarea v-model="mailBody" maxlength="100000" rows="18" placeholder="将笔试、面试、测评或 Offer 通知完整粘贴到这里……" />
        <div class="privacy-note">正文只用于本次识别，不会作为邮件原文写入投递记录。</div>
        <button class="primary-action" :disabled="loading || !status?.callsEnabled || !mailBody.trim()" @click="recognize">{{ loading ? '正在识别…' : '✦ 开始识别' }}</button>
      </section>

      <section class="card review-panel">
        <div class="panel-title"><div><span class="step">2</span><h3>核对并录入</h3></div><span v-if="hasResult" class="match-badge">{{ matchedApplication ? '已匹配现有投递' : '将新建投递' }}</span></div>
        <form v-if="hasResult" class="result-form" @submit.prevent="saveResult">
          <label class="wide application-match"><span>关联已有投递</span><select v-model="selectedApplicationId"><option value="">不关联，新建一条投递</option><optgroup v-if="recommendedApplications.length" label="★ 高匹配推荐"><option v-for="item in recommendedApplications" :key="item.id" :value="item.id">★ {{matchPercent(item)}}%｜{{item.company}} · {{item.position}}</option></optgroup><optgroup v-if="otherApplications.length" label="其他已有投递"><option v-for="item in otherApplications" :key="item.id" :value="item.id">{{item.company}} · {{item.position}}</option></optgroup></select><small>{{matchedApplication ? '将更新该投递的阶段和状态，并把识别出的日程关联到它。' : '未自动匹配时可手动选择；确实是新岗位再保留“不关联”。'}}</small></label>
          <label><span>公司 *</span><input v-model="result.company" maxlength="120" required /></label>
          <label><span>岗位 *</span><input v-model="result.position" maxlength="160" required /></label>
          <label><span>通知类型</span><select v-model="result.noticeType"><option v-for="item in noticeTypes" :key="item">{{ item }}</option></select></label>
          <label><span>安排名称</span><input v-model="result.scheduleTitle" maxlength="160" placeholder="如：一面、二面、HR面试" /></label>
          <label><span>时间类型</span><select v-model="timeMode" @change="timeMode==='point'&&(result.endsAt='')"><option value="point">时间点</option><option value="range">时间段</option></select></label>
          <label><span>{{timeMode==='range'?'开始时间':'时间'}}</span><input v-model="result.startsAt" type="datetime-local" /></label>
          <label><span>地点 / 视频链接</span><input v-model="result.location" maxlength="1000" /></label>
          <label v-if="timeMode==='range'"><span>结束时间</span><input v-model="result.endsAt" type="datetime-local" :min="result.startsAt" /></label>
          <label class="wide"><span>备注</span><textarea v-model="result.notes" rows="3" maxlength="4000" placeholder="可补充轮次、准备事项等" /></label>
          <label v-if="canCreateSchedule" class="check wide"><input v-model="createSchedule" type="checkbox" /><span>同时创建关联日程</span></label>
          <div class="commit-box wide"><span>{{ actionSummary }}</span><button :disabled="saving">{{ saving ? '正在录入…' : '确认录入' }}</button></div>
        </form>
        <div v-else class="empty-state"><strong>等待识别结果</strong><span>识别出的公司、岗位、通知类型和时间会显示在这里。</span></div>
      </section>
    </div>
    <p v-if="message" class="feedback success">{{ message }}</p>
    <p v-if="error" class="feedback danger">{{ error }}</p>
  </section>
</template>

<style scoped>
.mail-page { display: grid; gap: 20px; padding-top: 22px; }
.mail-toolbar, .panel-title, .panel-title > div, .commit-box { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.mail-toolbar h2 { margin: 5px 0 4px; font-size: 24px; }
.mail-toolbar p, .panel-title p { margin: 0; }
.eyebrow { color: #526ddd; font-size: 12px; font-weight: 800; letter-spacing: .12em; }
.match-badge { padding: 7px 10px; border-radius: 999px; font-size: 12px; font-weight: 800; }
.match-badge { color: #4259bd; background: #edf1ff; }
.text-button { padding: 7px 10px; color: #344054; background: transparent; }
.card { margin-top: 0; }
.panel-title h3 { margin: 0; font-size: 17px; }
.result-form { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-top: 18px; }
.result-form label { display: grid; gap: 7px; color: #475467; font-size: 13px; font-weight: 700; }
.result-form .wide { grid-column: 1 / -1; }
.application-match{padding:12px;border:1px solid #dbe3f4;border-radius:10px;background:#f7f9ff}.application-match small{color:#667085;font-weight:400}.check { display: flex !important; align-items: center; }
.check input { flex: none; width: 18px; }
.mail-grid { display: grid; grid-template-columns: .9fr 1.1fr; gap: 20px; }
.source-panel, .review-panel { min-width: 0; }
.step { display: grid; width: 28px; height: 28px; border-radius: 9px; color: #fff; background: #526ddd; place-items: center; font-size: 13px; font-weight: 800; }
textarea, select { width: 100%; padding: 12px 14px; border: 1px solid #d4dbea; border-radius: 10px; background: #fff; font: inherit; resize: vertical; }
.source-panel > textarea { min-height: 340px; margin: 18px 0 10px; line-height: 1.65; }
.privacy-note { margin-bottom: 14px; color: #667085; font-size: 12px; }
.primary-action { width: 100%; }
.empty-state { display: grid; min-height: 280px; gap: 8px; padding: 24px; border: 1px dashed #d4dbea; border-radius: 12px; color: #667085; background: #fafbfc; place-content: center; text-align: center; }
.empty-state.small { min-height: 100px; }
.commit-box { padding: 14px; border-radius: 12px; color: #475467; background: #f4f6fb; }
.feedback { margin: 0; padding: 13px 16px; border-radius: 11px; background: #fff; }
@media (max-width: 900px) { .mail-grid { grid-template-columns: 1fr; } }
@media (max-width: 650px) {
  .mail-toolbar { align-items: flex-start; flex-direction: column; }
  .result-form { grid-template-columns: 1fr; }
  .result-form .wide { grid-column: auto; }
  .commit-box { align-items: stretch; flex-direction: column; }
}
</style>
