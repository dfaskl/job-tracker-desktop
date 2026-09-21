<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { api, apiCached, ApiError } from './api'
import { type JobApplication, useJobTrackerStore } from './jobTrackerStore'
import BaseSelect from './BaseSelect.vue'
import ScheduleTimeModeNotice from './ScheduleTimeModeNotice.vue'

type AiStatus = { callsEnabled: boolean; message: string }
type Recognition = { company: string; position: string; noticeType: string; scheduleTitle: string; suggestedStage: string; suggestedStatus: string; startsAt: string; endsAt: string; location: string; summary: string }
type CollectedMail = { id: number; sender: string; subject: string; body: string; receivedAt: string; accountEmail: string }
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
const inbox = store.mailInbox
const syncing = ref(false)
const processingAll = ref(false)
const selectedMailId = ref<number | null>(null)
const processNotice = ref('')
let processNoticeTimer = 0
const previewMail = ref<CollectedMail | null>(null)
const previewDialog = ref<HTMLDialogElement | null>(null)

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

watch(selectedApplicationId, () => {
  const matched = matchedApplication.value
  if (!matched) return
  result.company = matched.company
  result.position = matched.position
})
watch(timeMode, mode => { if (mode === 'point') result.endsAt = '' })

onMounted(async () => {
  await store.initialize()
  await Promise.all([checkStatus(), loadInbox(true)])
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
async function loadInbox(sync = false) {
  syncing.value = sync
  try {
    await store.refreshMailInbox(sync)
  } catch (cause) {
    if (!(cause instanceof ApiError && cause.status === 401)) error.value = failure(cause, '读取邮件收集箱失败')
  } finally { syncing.value = false }
}
function selectMail(mail: CollectedMail) {
  selectedMailId.value = mail.id
  mailBody.value = [mail.subject ? `主题：${mail.subject}` : '', mail.sender ? `发件人：${mail.sender}` : '', '', mail.body].join('\n').trim()
  hasResult.value = false
}
async function processMail(mail: CollectedMail) {
  await api(`/api/poc/mail-inbox/messages/${mail.id}/processed`, { method: 'PATCH' })
  inbox.value.messages = inbox.value.messages.filter(item => item.id !== mail.id)
  inbox.value.pendingCount = Math.max(0, inbox.value.pendingCount - 1)
  await store.refreshMailInbox()
  if (selectedMailId.value === mail.id) selectedMailId.value = null
  if (previewMail.value?.id === mail.id) closeMailPreview()
  showProcessNotice('邮件已处理成功')
}
function showProcessNotice(value: string) {
  processNotice.value = value
  window.clearTimeout(processNoticeTimer)
  processNoticeTimer = window.setTimeout(() => { processNotice.value = '' }, 2600)
}
async function processAllMail() {
  const count=inbox.value.pendingCount
  if(!count||!confirm(`确认将当前 ${count} 封待处理邮件全部标记为已处理吗？`))return
  processingAll.value=true;error.value=''
  try {
    const result=await api<{processed:number}>('/api/poc/mail-inbox/messages/processed',{method:'PATCH'})
    selectedMailId.value=null;closeMailPreview();await store.refreshMailInbox()
    showProcessNotice(`已成功处理 ${Math.max(0,Number(result.processed)||count)} 封邮件`)
  } catch(cause) { error.value=failure(cause,'批量处理邮件失败') }
  finally { processingAll.value=false }
}
async function openMailPreview(mail: CollectedMail) {
  previewMail.value = mail
  await nextTick()
  previewDialog.value?.showModal()
}
function closeMailPreview() {
  previewDialog.value?.close()
  previewMail.value = null
}
function mailDate(value: string) {
  if (!value) return '时间未知'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false })
}
async function recognize() {
  loading.value = true; error.value = ''; message.value = ''; hasResult.value = false
  try {
    const value = await api<Recognition>('/api/poc/ai-sandbox/recognize', { method: 'POST', body: JSON.stringify({ body: mailBody.value }) })
    Object.assign(result, value, { scheduleTitle: value.noticeType || '其他', startsAt: inputTime(value.startsAt), endsAt: inputTime(value.endsAt), notes: '' })
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
  const matched = matchedApplication.value
  if (!String(matched?.company || result.company).trim() || !String(matched?.position || result.position).trim()) { error.value = '请补全公司和岗位后再录入'; return }
  if(createSchedule.value&&canCreateSchedule.value&&timeMode.value==='range'&&!result.endsAt){error.value='时间段日程必须填写结束时间';return}
  const startTime=result.startsAt?new Date(result.startsAt).getTime():NaN,endTime=result.endsAt?new Date(result.endsAt).getTime():NaN
  if(createSchedule.value&&timeMode.value==='range'&&(!Number.isFinite(startTime)||!Number.isFinite(endTime)||endTime<=startTime)){error.value='结束时间必须晚于开始时间';return}
  saving.value = true; error.value = ''; message.value = ''
  try {
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
      <section class="card inbox-panel">
        <div class="inbox-heading"><div><span class="step inbox-step" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M3.5 6.5h17v12h-17z"/><path d="m4 7 8 6 8-6"/></svg></span><div><h3>待处理邮件</h3><small>{{inbox.pendingCount}} 封 · 点击卡片填入通知正文</small></div></div><div class="inbox-actions"><button class="process-all-button" title="将所有待处理邮件标记为已处理" :disabled="syncing||processingAll||!inbox.pendingCount" @click="processAllMail">{{processingAll?'处理中…':'全部处理'}}</button><button class="secondary sync-button" title="立即收取新邮件" :disabled="syncing||!inbox.accounts.length" @click="loadInbox(true)">{{syncing?'收取中…':'收取'}}</button></div></div>
        <div class="mail-list-region" :aria-busy="syncing">
          <div v-if="syncing" class="inbox-loading" role="status" aria-live="polite"><span class="inbox-spinner" aria-hidden="true"></span><strong>正在收取邮件</strong><small>新邮件会自动出现在这里</small></div>
          <div v-else-if="inbox.messages.length" class="mail-cards" aria-label="待处理邮件">
            <article v-for="mail in inbox.messages" :key="mail.id" :class="{selected:selectedMailId===mail.id}">
              <button class="mail-select" :aria-label="'选择邮件：'+(mail.subject||'无主题')" @click="selectMail(mail)"><span class="mail-card-copy"><strong>{{mail.subject||'（无主题）'}}</strong><span>{{mail.sender||mail.accountEmail}}</span><small>{{mailDate(mail.receivedAt)}}</small></span></button>
              <div class="mail-card-actions"><button class="mailbox-button" @click.stop="openMailPreview(mail)">查看原文</button><button class="processed" @click.stop="processMail(mail)">已处理</button></div>
            </article>
          </div>
          <div v-else class="inbox-empty">{{inbox.accounts.length?'暂无待处理邮件':'请先在设置页面连接 QQ 或网易邮箱'}}</div>
        </div>
      </section>

      <section class="card compose-panel">
        <div class="panel-title"><div><span class="step">1</span><h3>粘贴通知正文</h3></div><button class="text-button" :disabled="!mailBody" @click="mailBody = ''">清空</button></div>
        <textarea v-model="mailBody" maxlength="100000" rows="18" placeholder="将笔试、面试、测评或 Offer 通知完整粘贴到这里……" />
        <div class="privacy-note">正文只用于本次识别，不会作为邮件原文写入投递记录。</div>
        <div v-if="status && !status.callsEnabled" class="service-unavailable">{{ status.message || '邮件识别服务当前不可用' }}</div>
        <button class="primary-action" :disabled="loading || !status?.callsEnabled || !mailBody.trim()" @click="recognize">{{ loading ? '正在识别…' : '✦ 开始识别' }}</button>
      </section>

      <section class="card review-panel">
        <div class="panel-title"><div><span class="step">2</span><h3>核对并录入</h3></div><span v-if="hasResult" class="match-badge">{{ matchedApplication ? '已匹配现有投递' : '将新建投递' }}</span></div>
        <form v-if="hasResult" class="result-form" @submit.prevent="saveResult">
          <label class="wide application-match"><span>关联已有投递</span><BaseSelect v-model="selectedApplicationId" :options="[{value:'',label:'不关联，新建一条投递'},...recommendedApplications.map(item=>({value:item.id,label:`★ ${matchPercent(item)}%｜${item.company} · ${item.position}`,group:'★ 高匹配推荐'})),...otherApplications.map(item=>({value:item.id,label:`${item.company} · ${item.position}`,group:'其他已有投递'}))]" /><small>{{matchedApplication ? '将更新该投递的阶段和状态，并把识别出的日程关联到它。' : '未自动匹配时可手动选择；确实是新岗位再保留“不关联”。'}}</small></label>
          <label><span>公司 *</span><input v-model="result.company" maxlength="120" required /></label>
          <label><span>岗位 *</span><input v-model="result.position" maxlength="160" required /></label>
          <label><span>通知类型</span><BaseSelect v-model="result.noticeType" :options="noticeTypes" /></label>
          <label><span>安排名称</span><input v-model="result.scheduleTitle" maxlength="160" placeholder="如：一面、二面、HR面试" /></label>
          <ScheduleTimeModeNotice v-if="canCreateSchedule" class="wide" :mode="timeMode" detected />
          <label><span>时间类型</span><BaseSelect v-model="timeMode" :options="[{value:'point',label:'时间点'},{value:'range',label:'时间段'}]" /></label>
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
    <p v-if="error" class="feedback danger" role="alert">{{ error }}</p>
    <Transition name="process-notice"><div v-if="processNotice" class="process-notice" role="status"><span aria-hidden="true">✓</span>{{processNotice}}</div></Transition>

    <dialog ref="previewDialog" class="mail-preview" aria-labelledby="mail-preview-title" @close="previewMail = null">
      <article v-if="previewMail" class="mail-preview-card">
        <header>
          <div class="mail-preview-heading"><span class="preview-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M3.5 6.5h17v12h-17z"/><path d="m4 7 8 6 8-6"/></svg></span><div><span>邮件原文</span><h2 id="mail-preview-title">{{ previewMail.subject || '（无主题）' }}</h2></div></div>
          <button class="preview-close" type="button" aria-label="关闭邮件原文" @click="closeMailPreview">×</button>
        </header>
        <dl class="mail-preview-meta">
          <div><dt>发件人</dt><dd>{{ previewMail.sender || '未知发件人' }}</dd></div>
          <div><dt>收件邮箱</dt><dd>{{ previewMail.accountEmail }}</dd></div>
          <div><dt>收取时间</dt><dd>{{ mailDate(previewMail.receivedAt) }}</dd></div>
        </dl>
        <div class="mail-preview-body" tabindex="0">{{ previewMail.body || '这封邮件没有可显示的正文。' }}</div>
        <footer><small>这里展示系统通过邮箱服务收取并整理后的正文内容。</small><div><button type="button" class="secondary" @click="closeMailPreview">关闭</button><button type="button" class="processed" @click="processMail(previewMail)">标记为已处理</button></div></footer>
      </article>
    </dialog>
  </section>
</template>

<style scoped>
.mail-page { display: grid; height: 100%; min-height: 0; grid-template-rows: auto minmax(0, 1fr); gap: 18px; padding-top: 18px; box-sizing: border-box; overflow: hidden; }
.mail-toolbar, .panel-title, .panel-title > div, .commit-box { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.mail-toolbar h2 { margin: 5px 0 4px; font-size: 24px; }
.mail-toolbar p, .panel-title p { margin: 0; }
.eyebrow { color: var(--color-primary); font-size: 12px; font-weight: 800; letter-spacing: .12em; }
.match-badge { padding: 7px 10px; border-radius: 999px; font-size: 12px; font-weight: 800; }
.match-badge { color: #4259bd; background: #edf1ff; }
.text-button { padding: 7px 10px; color: var(--color-card-foreground); background: transparent; }
.card { margin-top: 0 !important; }
.panel-title { min-height: 44px; }
.panel-title h3 { margin: 0; font-size: 17px; }
.result-form { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-top: 18px; }
.result-form label { display: grid; gap: 7px; color: var(--color-muted-foreground); font-size: 13px; font-weight: 700; }
.result-form .wide { grid-column: 1 / -1; }
.application-match{padding:12px;border:1px solid #dbe3f4;border-radius:10px;background:#f7f9ff}.application-match small{color:var(--color-muted-foreground);font-weight:400}.check { display: flex !important; align-items: center; }
.check input { flex: none; width: 18px; }
.mail-grid { display: grid; min-height: 0; grid-template-columns: minmax(260px, .82fr) minmax(300px, 1fr) minmax(380px, 1.28fr); gap: 16px; padding-bottom: 18px; }
.inbox-panel, .compose-panel, .review-panel { min-width: 0; min-height: 0; height: 100%; box-sizing: border-box; }
.inbox-panel, .compose-panel { display: flex; flex-direction: column; }
.review-panel { display: flex; flex-direction: column; overflow-y: auto; overscroll-behavior: contain; }
.step { display: grid; width: 28px; height: 28px; border-radius: 9px; color: #fff; background: var(--color-primary); place-items: center; font-size: 13px; font-weight: 800; }
textarea, select { width: 100%; padding: 12px 14px; border: 1px solid #d4dbea; border-radius: 10px; background: #fff; font: inherit; resize: vertical; }
.compose-panel > textarea { min-height: 0; flex: 1 1 auto; margin: 18px 0 10px; line-height: 1.65; resize: none; }
.inbox-heading,.inbox-heading>div,.inbox-actions,.mail-cards article,.mail-card-actions{display:flex;align-items:center}.inbox-heading{justify-content:space-between;gap:10px;margin-bottom:10px}.inbox-heading>div{min-width:0;gap:10px}.inbox-heading>div:first-child{flex:1}.inbox-heading h3{margin:0;font-size:16px}.inbox-heading small{display:block;margin-top:2px;color:var(--color-muted-foreground)}.inbox-step{color:var(--color-primary);background:#e8f4fa}.inbox-step svg{width:16px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}.inbox-actions{min-width:max-content;flex:none;gap:6px}.sync-button,.process-all-button{min-height:38px;flex:none;padding:7px 11px;font-size:13px}.process-all-button{color:#176b4b;background:#eaf8f1;border-color:#b9d9c9}.mail-list-region{display:flex;min-height:120px;flex:1 1 auto;flex-direction:column}.mail-cards{display:grid;min-height:0;flex:1 1 auto;align-content:start;gap:8px;padding:3px;overflow:auto;overscroll-behavior:contain}.mail-cards article{align-items:stretch;flex-direction:column;gap:8px;min-width:0;padding:8px;border:1px solid var(--color-border);border-left:4px solid #7aa5bb;border-radius:10px;background:#f8fbfd;transition:transform .15s ease,border-color .15s ease,box-shadow .15s ease}.mail-cards article:hover{transform:translateY(1px);box-shadow:inset 0 2px 4px rgba(4,31,49,.08)}.mail-cards article:focus-within,.mail-cards article.selected{border-color:var(--color-primary);box-shadow:0 0 0 3px color-mix(in srgb,var(--color-primary) 14%,transparent)}.mail-select{display:block;min-width:0;flex:1;padding:2px;color:inherit;background:transparent;text-align:left}.mail-card-copy{display:grid;min-width:0;gap:2px}.mail-card-copy strong,.mail-card-copy span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.mail-card-copy span,.mail-card-copy small{color:var(--color-muted-foreground);font-size:12px}.mail-card-actions{flex:none;gap:8px}.mail-card-actions :is(button,a){display:flex;min-height:38px;flex:1;align-items:center;justify-content:center;padding:7px 10px;border:1px solid var(--color-border);border-radius:9px;font-family:var(--font-button);font-weight:700;text-decoration:none}.mailbox-button{color:var(--color-primary);background:#edf7fc}.processed{color:#176b4b;background:#eaf8f1}.inbox-empty{padding:12px;border:1px dashed var(--color-border);border-radius:10px;color:var(--color-muted-foreground);background:#fafcfd;text-align:center}.inbox-loading{display:grid;min-height:140px;flex:1 1 auto;align-content:center;justify-items:center;gap:7px;color:var(--color-primary);text-align:center}.inbox-loading strong{font-size:14px}.inbox-loading small{color:var(--color-muted-foreground)}.inbox-spinner{width:34px;height:34px;margin-bottom:4px;border:3px solid color-mix(in srgb,var(--color-primary) 18%,transparent);border-top-color:var(--color-primary);border-right-color:color-mix(in srgb,var(--color-primary) 62%,#fff);border-radius:50%;animation:inbox-spin .8s linear infinite}
.privacy-note { margin-bottom: 14px; color: var(--color-muted-foreground); font-size: 12px; }
.service-unavailable { margin: 0 0 12px; padding: 9px 12px; border: 1px solid #f4c7c7; border-radius: 9px; color: #b42318; background: #fff4f2; font-size: 12px; }
.primary-action { width: 100%; }
.empty-state { display: grid; min-height: 280px; gap: 8px; padding: 24px; border: 1px dashed #d4dbea; border-radius: 12px; color: var(--color-muted-foreground); background: #fafbfc; place-content: center; text-align: center; }
.review-panel > .empty-state { min-height: 0; flex: 1 1 auto; margin-top: 18px; }
.empty-state.small { min-height: 100px; }
.commit-box { padding: 14px; border-radius: 12px; color: var(--color-muted-foreground); background: #f4f6fb; }
.feedback { margin: 0; padding: 13px 16px; border-radius: 11px; background: #fff; }
.process-notice{position:fixed;z-index:1200;top:22px;left:50%;display:flex;align-items:center;gap:9px;max-width:calc(100vw - 32px);padding:12px 18px;border:1px solid #a9d7c0;border-radius:12px;color:#145c43;background:#f0fbf5;box-shadow:0 12px 32px rgba(14,75,55,.18);font-family:var(--font-button);font-weight:700;transform:translateX(-50%)}.process-notice span{display:grid;width:22px;height:22px;border-radius:50%;color:#fff;background:#26956b;place-items:center}.process-notice-enter-active,.process-notice-leave-active{transition:opacity .18s ease,transform .18s ease}.process-notice-enter-from,.process-notice-leave-to{opacity:0;transform:translate(-50%,-8px)}
.mail-preview{width:min(780px,calc(100vw - 32px));max-width:none;height:min(760px,calc(100dvh - 48px));max-height:none;padding:0;border:1px solid var(--color-border);border-radius:18px;color:var(--color-card-foreground);background:#fff;box-shadow:0 24px 70px rgba(4,31,49,.24);overflow:hidden}.mail-preview::backdrop{background:rgba(10,35,51,.5);backdrop-filter:blur(4px)}.mail-preview-card{display:grid;height:100%;grid-template-rows:auto auto minmax(0,1fr) auto}.mail-preview-card>header,.mail-preview-card>footer{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:20px 24px}.mail-preview-card>header{border-bottom:1px solid var(--color-border)}.mail-preview-heading{display:flex;min-width:0;align-items:center;gap:13px}.mail-preview-heading>div{min-width:0}.mail-preview-heading span{color:var(--color-primary);font-size:12px;font-weight:800;letter-spacing:.1em}.mail-preview-heading h2{margin:4px 0 0;overflow-wrap:anywhere;font-size:20px;line-height:1.35}.preview-icon{display:grid;width:42px;height:42px;flex:none;border-radius:12px;color:#fff;background:var(--color-primary);place-items:center}.preview-icon svg{width:21px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}.preview-close{display:grid;width:44px;height:44px;flex:none;padding:0;border:1px solid var(--color-border);border-radius:12px;color:var(--color-primary);background:#f5f9fb;place-items:center;font-size:27px;line-height:1}.mail-preview-meta{display:grid;grid-template-columns:1.4fr 1fr .7fr;gap:0;margin:0;padding:14px 24px;border-bottom:1px solid var(--color-border);background:#f7fafc}.mail-preview-meta div{min-width:0;padding-right:16px}.mail-preview-meta dt{margin-bottom:4px;color:var(--color-muted-foreground);font-size:12px;font-weight:700}.mail-preview-meta dd{margin:0;overflow-wrap:anywhere;font-size:13px}.mail-preview-body{min-height:0;margin:20px 24px;padding:20px;border:1px solid #dce6eb;border-radius:12px;background:#fbfcfc;overflow:auto;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.75}.mail-preview-card>footer{border-top:1px solid var(--color-border)}.mail-preview-card>footer small{color:var(--color-muted-foreground)}.mail-preview-card>footer>div{display:flex;gap:10px}.mail-preview-card>footer button{min-height:44px}.mail-preview-card>footer .processed{padding:9px 15px;border:1px solid #b9d9c9;border-radius:10px}
@keyframes inbox-spin{to{transform:rotate(360deg)}}
@media (prefers-reduced-motion: reduce) { .inbox-spinner{animation:inbox-spin 1.8s linear infinite!important} }
@media (max-width: 1200px) { .mail-page { height: auto; overflow: visible; } .mail-grid { grid-template-columns: minmax(0, .9fr) minmax(0, 1.1fr); padding-bottom: 0; } .inbox-panel { grid-column: 1 / -1; height: auto; } .inbox-panel .mail-cards { max-height: 230px; } .compose-panel, .review-panel { min-height: 620px; height: auto; } }
@media (max-width: 900px) { .mail-grid { grid-template-columns: 1fr; } .inbox-panel { grid-column: auto; } .inbox-panel, .compose-panel, .review-panel { min-height: 0; height: auto; } .compose-panel > textarea { min-height: 340px; } }
@media (max-width: 650px) {
  .mail-toolbar { align-items: flex-start; flex-direction: column; }
  .result-form { grid-template-columns: 1fr; }
  .result-form .wide { grid-column: auto; }
  .commit-box { align-items: stretch; flex-direction: column; }
  .inbox-heading{align-items:stretch;flex-direction:column}.inbox-actions{display:grid;grid-template-columns:1fr 1fr}.sync-button,.process-all-button{width:100%}.mail-cards{max-height:300px}.mail-card-actions button{min-height:44px}
  .mail-preview{width:calc(100vw - 20px);height:calc(100dvh - 20px);border-radius:14px}.mail-preview-card>header,.mail-preview-card>footer{padding:14px}.mail-preview-heading h2{font-size:17px}.mail-preview-meta{grid-template-columns:1fr;padding:12px 14px;gap:10px}.mail-preview-body{margin:12px 14px;padding:14px}.mail-preview-card>footer{align-items:stretch;flex-direction:column}.mail-preview-card>footer>div{display:grid;grid-template-columns:1fr 1fr}
}
</style>
