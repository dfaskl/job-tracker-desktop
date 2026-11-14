<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onDeactivated, onMounted, reactive, ref, watch } from 'vue'
import { api, apiCached, ApiError } from './api'
import { type JobApplication, useJobTrackerStore } from './jobTrackerStore'
import BaseSelect from './BaseSelect.vue'
import ScheduleTimeModeNotice from './ScheduleTimeModeNotice.vue'
import { formatShanghaiDateTime, shanghaiDateKey } from './shanghaiTime'
import { validateScheduleTime } from './scheduleValidation'
import MailFuseButton from './MailFuseButton.vue'

type AiStatus = { callsEnabled: boolean; message: string }
type Recognition = { company: string; position: string; noticeType: string; scheduleTitle: string; suggestedStage: string; suggestedStatus: string; startsAt: string; endsAt: string; location: string; notes: string; summary: string }
type CollectedMail = { id: number; sender: string; subject: string; body: string; receivedAt: string; accountEmail: string }
const noticeTypes = ['测评', '笔试', '面试', 'Offer', '未通过', '其他']
const store = useJobTrackerStore()
const status = ref<AiStatus | null>(null)
const mailBody = ref('')
const result = reactive({ company: '', position: '', noticeType: '其他', scheduleTitle: '', suggestedStage: '已投递', suggestedStatus: '等待结果', startsAt: '', endsAt: '', location: '', summary: '', notes: '' })
const hasResult = ref(false)
const recognitionDetails = ref<Recognition | null>(null)
const recognitionDetailsDialog = ref<HTMLDialogElement | null>(null)
const recognitionFieldLabels: Record<string, string> = {
  company: '公司', position: '岗位', noticeType: '通知类型', scheduleTitle: '安排名称',
  suggestedStage: '建议阶段', suggestedStatus: '建议状态', startsAt: '开始时间',
  endsAt: '结束时间', location: '地点 / 视频链接', notes: '备注', summary: '摘要'
}
const timeMode = ref<'point' | 'range'>('point')
const checkingStatus = ref(false)
const recognizing = ref(false)
const saving = ref(false)
const mailStep = ref<'inbox' | 'compose' | 'review'>('inbox')
const noteMode = ref<'keep' | 'append' | 'replace'>('keep')
let recognitionRequest: AbortController | null = null
const inboxHeading = ref<HTMLElement | null>(null)
const composeHeading = ref<HTMLElement | null>(null)
const reviewHeading = ref<HTMLElement | null>(null)
async function openStep(step: typeof mailStep.value, focus = false) {
  mailStep.value = step
  if (focus && window.matchMedia('(max-width: 720px)').matches) {
    await nextTick()
    const heading = { inbox: inboxHeading, compose: composeHeading, review: reviewHeading }[step].value
    heading?.focus()
  }
}
const error = ref('')
const scheduleError = ref('')
const message = ref('')
const selectedApplicationId = ref('')
const inbox = store.mailInbox
const syncing = ref(false)
const selectingMail = ref(false)
const batch = ref<{ ids: number[]; all: boolean } | null>(null)
const batchIds = computed(() => new Set(batch.value?.ids))
const preparingBatch = ref(false)
const processingBatch = ref(false)
const batchBusy = computed(() => Boolean(batch.value) || preparingBatch.value || processingBatch.value)
const moreMailActions = ref<HTMLDetailsElement | null>(null)
const batchFuseLabel = computed(() => `撤销处理 ${batch.value?.ids.length ?? 0} 封`)
const checkedMailIds = ref<number[]>([])
const listedMailIds = computed(() => inbox.value.messages.map(mail => mail.id))
const allListedMailChecked = computed(() => listedMailIds.value.length > 0 && checkedMailIds.value.length === listedMailIds.value.length)
watch(listedMailIds, ids => {
  const listed = new Set(ids)
  checkedMailIds.value = checkedMailIds.value.filter(id => listed.has(id))
})
const selectedMailId = ref<number | null>(null)
const processNotice = ref('')
let processNoticeTimer = 0
const pendingMail = reactive(new Map<number, CollectedMail>())
const visibleMail = computed(() => [...inbox.value.messages, ...[...pendingMail.values()].filter(mail => !listedMailIds.value.includes(mail.id))])
const processingMailIds = reactive(new Set<number>())
const previewMail = ref<CollectedMail | null>(null)
const previewDialog = ref<HTMLDialogElement | null>(null)
const previewHtml = ref('')
const previewLoading = ref(false)
const previewError = ref('')
const previewDarkTheme = ref(document.documentElement.dataset.theme === 'dark')
let previewThemeObserver: MutationObserver | null = null
let disposed = false
const previewSrcdoc = computed(() => previewHtml.value
  ? `${previewHtml.value}${previewDarkTheme.value ? '<style>html{background:#fff!important;filter:invert(1) hue-rotate(180deg)!important}</style>' : ''}`
  : '')

const matchedApplication = computed(() => store.applications.value.find(item => item.id === selectedApplicationId.value))
const terminalNotice = computed(() => result.noticeType === '未通过')
const terminalStatus = computed(() => ['未通过','已放弃','已结束'].includes(result.suggestedStatus) ? result.suggestedStatus : result.noticeType === '未通过' ? '未通过' : '已结束')
const rankedApplications = computed(() => store.applications.value.slice().sort((a,b) =>
  applicationMatchScore(b, result.company, result.position) - applicationMatchScore(a, result.company, result.position)
))
const recommendedApplications = computed(() => rankedApplications.value.filter(isRecommendedApplication))
const otherApplications = computed(() => rankedApplications.value.filter(item => !isRecommendedApplication(item)))
const canCreateSchedule = computed(() => Boolean(result.startsAt))
const actionSummary = computed(() => hasResult.value
  ? terminalNotice.value
    ? (matchedApplication.value ? `只更新“${matchedApplication.value.company} · ${matchedApplication.value.position}”的结束状态，不创建日程` : '结束类通知不能新建投递或日程')
    : `${matchedApplication.value ? '为已有投递添加日程' : '新建一条投递并添加日程'}`
  : '')

const duplicateEvent = computed(() => {
  if (terminalNotice.value || !matchedApplication.value || !result.startsAt) return
  const type = result.noticeType
  const start = apiTime(result.startsAt)
  const end = timeMode.value === 'range' ? apiTime(result.endsAt) : ''
  return store.events.value.find(event => event.applicationId === matchedApplication.value?.id && event.type === type
    && apiTime(String(event.startsAt || event.start || event.date || '')) === start
    && apiTime(String(event.endsAt || event.end || '')) === end)
})
const duplicateNote = computed(() => {
  const oldNote = String(duplicateEvent.value?.notes || '').trim()
  const newNote = result.notes.trim()
  if (noteMode.value === 'replace') return newNote
  if (noteMode.value === 'append' && newNote && oldNote !== newNote) return [oldNote, newNote].filter(Boolean).join('\n\n')
  return oldNote
})
watch(duplicateEvent, () => { noteMode.value = 'keep' })
watch([mailBody, selectedMailId], () => {
  recognitionRequest?.abort()
  recognitionRequest = null
  recognizing.value = false
  hasResult.value = false
  recognitionDetails.value = null
  scheduleError.value = ''
  error.value = ''
  message.value = ''
  if (mailStep.value === 'review') void openStep('compose')
}, { flush: 'sync' })
watch(selectedApplicationId, () => {
  const matched = matchedApplication.value
  if (!matched) return
  result.company = String(matched.company || '')
  result.position = String(matched.position || '')
})
watch(timeMode, mode => { if (mode === 'point') result.endsAt = ''; scheduleError.value = '' })
watch([()=>result.startsAt,()=>result.endsAt],()=>{scheduleError.value=''})

onMounted(async () => {
  previewThemeObserver = new MutationObserver(() => { previewDarkTheme.value = document.documentElement.dataset.theme === 'dark' })
  previewThemeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })
  await store.initialize()
  await Promise.all([checkStatus(), loadInbox(true)])
})
onBeforeUnmount(() => {
  disposed = true
  window.clearTimeout(processNoticeTimer)
  previewThemeObserver?.disconnect()
  recognitionRequest?.abort()
})
onDeactivated(() => {
  recognitionDetailsDialog.value?.close()
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
function today() { return shanghaiDateKey() }
function failure(cause: unknown, fallback: string) {
  if (cause instanceof ApiError && cause.status === 401) return '请先登录旧账号'
  return cause instanceof Error ? cause.message : fallback
}
async function checkStatus() {
  checkingStatus.value = true; error.value = ''
  try {
    status.value = await apiCached<AiStatus>('/api/poc/ai-sandbox/status')
  } catch (cause) { error.value = failure(cause, '检查 AI 服务失败') }
  finally { checkingStatus.value = false }
}
async function loadInbox(sync = false) {
  syncing.value = sync
  if (sync) error.value = ''
  try {
    await store.refreshMailInbox(sync, true)
    if (sync) {
      const failed = inbox.value.accounts.filter(account => account.lastError)
      if (failed.length) error.value = failed.map(account => `${account.email}：${account.lastError}`).join('；')
    }
  } catch (cause) {
    if (!(cause instanceof ApiError && cause.status === 401)) error.value = failure(cause, '读取邮件收集箱失败')
  } finally { syncing.value = false }
}
function selectMail(mail: CollectedMail) {
  if (saving.value) return
  selectedMailId.value = mail.id
  mailBody.value = [mail.subject ? `主题：${mail.subject}` : '', mail.sender ? `发件人：${mail.sender}` : '', '', mail.body].join('\n').trim()
  void openStep('compose', true)
}
function queueMail(mail: CollectedMail) {
  if (batchBusy.value || pendingMail.has(mail.id) || processingMailIds.has(mail.id)) return
  if (!listedMailIds.value.includes(mail.id)) return
  pendingMail.set(mail.id, mail)
  if (previewMail.value?.id === mail.id) closeMailPreview()
}
async function processMail(id: number) {
  if (!pendingMail.has(id) || processingMailIds.has(id)) return
  processingMailIds.add(id)
  await commitMail([id])
  pendingMail.delete(id)
  processingMailIds.delete(id)
}
function showProcessNotice(value: string) {
  processNotice.value = value
  window.clearTimeout(processNoticeTimer)
  processNoticeTimer = window.setTimeout(() => { processNotice.value = '' }, 2600)
}
function toggleMailSelection() {
  selectingMail.value = !selectingMail.value
  checkedMailIds.value = []
}
function toggleAllListedMail() {
  checkedMailIds.value = allListedMailChecked.value ? [] : [...listedMailIds.value]
}
function queueSelectedMail() {
  if (batchBusy.value || pendingMail.size || !checkedMailIds.value.length) return
  batch.value = { ids: [...checkedMailIds.value], all: false }
}
async function queueAllMail() {
  if (batchBusy.value || pendingMail.size || !inbox.value.pendingCount || syncing.value) return
  preparingBatch.value = true
  error.value = ''
  try {
    const { ids } = await api<{ ids: number[] }>('/api/poc/mail-inbox/messages/pending-ids')
    if (disposed) return
    if (ids.length) {
      batch.value = { ids, all: true }
    } else {
      await loadInbox()
    }
  } catch (cause) {
    error.value = failure(cause, '读取待处理邮件失败')
  } finally {
    preparingBatch.value = false
  }
}
function undoBatch() {
  batch.value = null
}
async function processBatch() {
  if (!batch.value || processingBatch.value) return
  processingBatch.value = true
  const saved = await commitMail(batch.value.ids)
  batch.value = null
  processingBatch.value = false
  if (saved && selectingMail.value) toggleMailSelection()
}
async function commitMail(ids: number[]) {
  error.value = ''
  try {
    const { processed } = await api<{ processed: number }>('/api/poc/mail-inbox/messages/batch-processed', {
      method: 'PATCH', body: JSON.stringify({ ids })
    })
    if (disposed) return true
    const submitted = new Set(ids)
    inbox.value.messages = inbox.value.messages.filter(mail => !submitted.has(mail.id))
    inbox.value.pendingCount = Math.max(0, inbox.value.pendingCount - processed)
    if (selectedMailId.value !== null && submitted.has(selectedMailId.value)) selectedMailId.value = null
    showProcessNotice(processed ? `已标记 ${processed} 封邮件为已处理` : '邮件已不在待处理列表')
    await store.refreshMailInbox(false, false, true)
    return true
  } catch (cause) {
    if (!disposed) error.value = failure(cause, '处理邮件失败，请重试')
    return false
  }
}
async function openMailPreview(mail: CollectedMail) {
  previewMail.value = mail
  previewHtml.value = ''
  previewError.value = ''
  previewLoading.value = !previewHtml.value
  await nextTick()
  previewDialog.value?.showModal()
  if (!previewHtml.value) {
    try {
      const original = await api<{ bodyHtml: string }>(`/api/poc/mail-inbox/messages/${mail.id}/original`)
      previewHtml.value = original.bodyHtml || ''
    } catch (cause) {
      previewError.value = failure(cause, '暂时无法读取邮箱中的原始排版，当前显示收取时保存的正文。')
    } finally { previewLoading.value = false }
  }
}
function closeMailPreview() {
  previewDialog.value?.close()
  previewMail.value = null
  previewHtml.value = ''
  previewLoading.value = false
  previewError.value = ''
}
function mailDate(value: string) {
  if (!value) return '时间未知'
  return formatShanghaiDateTime(value, { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }, value)
}
async function recognize() {
  if (recognizing.value || !status.value?.callsEnabled || !mailBody.value.trim()) return
  const request = new AbortController()
  recognitionRequest = request
  recognizing.value = true; error.value = ''; scheduleError.value = ''; message.value = ''; hasResult.value = false
  try {
    const value = await api<Recognition>('/api/poc/ai-sandbox/recognize', { method: 'POST', body: JSON.stringify({ body: mailBody.value }), signal: request.signal })
    if (recognitionRequest !== request) return
    recognitionDetails.value = value
    Object.assign(result, value, { scheduleTitle: value.scheduleTitle?.trim() || value.noticeType || '其他', startsAt: inputTime(value.startsAt), endsAt: inputTime(value.endsAt), notes: value.notes || '' })
    if (value.noticeType === '未通过' || value.suggestedStage === '已结束' || ['未通过','已放弃','已结束'].includes(value.suggestedStatus)) {
      result.noticeType = '未通过'
      result.startsAt = ''; result.endsAt = ''; result.location = ''
    }
    selectedApplicationId.value = suggestApplication(value.company,value.position)?.id || ''
    hasResult.value = true
    timeMode.value = value.endsAt ? 'range' : 'point'
    message.value = '识别完成，请核对后确认录入'
    void openStep('review', true)
  } catch (cause) {
    if (recognitionRequest === request) error.value = failure(cause, '邮件识别失败')
  } finally {
    if (recognitionRequest === request) {
      recognitionRequest = null
      recognizing.value = false
    }
  }
}
function applicationPayload(item?: JobApplication) {
  const payload = {
    company: String(item?.company || result.company).trim(), position: String(item?.position || result.position).trim(),
    city: String(item?.city || ''), channel: String(item?.channel || '邮件识别'),
    appliedDate: String(item?.appliedDate || today()), stage: String(item?.stage || '已投递'),
    status: String(item?.status || '等待结果'), notes: String(item?.notes || ''),
    expectedUpdatedAt: String(item?.updatedAt || '')
  }
  if(item)return payload
  const eventType=result.noticeType==='其他'||result.noticeType==='未通过'?'其他':result.noticeType
  return {...payload,scheduleType:eventType,scheduleTitle:result.scheduleTitle.trim()||result.noticeType||'邮件通知',scheduleStartsAt:apiTime(result.startsAt),scheduleEndsAt:timeMode.value==='range'?apiTime(result.endsAt):'',scheduleLocation:result.location.trim(),scheduleNotes:result.notes.trim()}
}
async function saveResult() {
  if (saving.value || !hasResult.value) return
  if (duplicateEvent.value && duplicateNote.value.length > 4000) { scheduleError.value = '合并备注超过 4000 字，请缩短新备注或选择其他方式。'; return }
  const matched = matchedApplication.value
  if (!String(matched?.company || result.company).trim() || !String(matched?.position || result.position).trim()) { error.value = '请补全公司和岗位后再录入'; return }
  if (terminalNotice.value && !matched) { error.value = '这是结束类通知，不会新建投递或日程。请先关联一条已有投递。'; return }
  if (!terminalNotice.value) {
    scheduleError.value=validateScheduleTime(timeMode.value,result.startsAt,result.endsAt)
    if(scheduleError.value){error.value='';return}
  }
  const existingSchedule = duplicateEvent.value
  const notesToSave = duplicateNote.value
  saving.value = true; error.value = ''; message.value = ''
  try {
    if (terminalNotice.value && matched) {
      const payload = { ...applicationPayload(matched), stage: '已结束', status: terminalStatus.value }
      await api<{ application: JobApplication }>(`/api/poc/application-sandbox/applications/${encodeURIComponent(matched.id)}`, { method: 'PUT', body: JSON.stringify(payload) })
      await store.refresh()
      mailBody.value = ''; hasResult.value = false; selectedApplicationId.value = ''
      void openStep('inbox', true)
      message.value = `已将 ${matched.company} · ${matched.position} 标记为${terminalStatus.value}，未创建日程`
      return
    }
    const response = matched
      ? { application: matched }
      : await api<{ application: JobApplication }>('/api/poc/application-sandbox/applications', { method: 'POST', body: JSON.stringify(applicationPayload()) })
    let duplicateSchedule=false
    let updatedScheduleNote=false
    if (matched) {
      const eventType=result.noticeType==='其他'||result.noticeType==='未通过'?'其他':result.noticeType
      const startsAt=apiTime(result.startsAt),endsAt=timeMode.value==='range'?apiTime(result.endsAt):''
      duplicateSchedule=Boolean(existingSchedule)
      if(existingSchedule && noteMode.value !== 'keep' && notesToSave !== String(existingSchedule.notes || '').trim()){
        await api(`/api/poc/event-sandbox/events/${encodeURIComponent(existingSchedule.id)}`,{method:'PUT',body:JSON.stringify({
          applicationId:response.application.id,type:String(existingSchedule.type||eventType),title:String(existingSchedule.title||result.scheduleTitle.trim()||eventType),
          startsAt:apiTime(String(existingSchedule.startsAt||existingSchedule.start||existingSchedule.date||'')),
          endsAt:apiTime(String(existingSchedule.endsAt||existingSchedule.end||'')),location:String(existingSchedule.location||''),
          notes:notesToSave,expectedUpdatedAt:String(existingSchedule.updatedAt||existingSchedule.createdAt||'')
        })})
        updatedScheduleNote=true
      }else if(!existingSchedule)await api('/api/poc/event-sandbox/events', { method: 'POST', body: JSON.stringify({
        applicationId: response.application.id, type: eventType,
        title: result.scheduleTitle.trim()||(eventType==='其他'?'邮件通知':eventType),
        startsAt, endsAt,
        location: result.location.trim(), notes: result.notes.trim(), expectedUpdatedAt: ''
      }) })
    }
    await store.refresh()
    mailBody.value = ''; hasResult.value = false; selectedApplicationId.value = ''
    void openStep('inbox', true)
    message.value = duplicateSchedule ? (updatedScheduleNote?'已更新现有日程备注':'相同日程已存在，已保留原日程') : `已${matched ? '更新投递' : '新建投递'}并自动创建关联日程，写入前备份已自动生成`
  } catch (cause) { error.value = failure(cause, '录入识别结果失败') }
  finally { saving.value = false }
}
function skipTerminalUpdate() {
  hasResult.value = false
  selectedApplicationId.value = ''
  void openStep('inbox', true)
  message.value = '未修改投递，也未创建日程；邮件仍保留在待处理列表中'
  error.value = ''
}
function preventImplicitSubmit(event: KeyboardEvent) {
  if (event.isComposing || event.keyCode === 229) return
  if (event.target instanceof HTMLInputElement) event.preventDefault()
}
</script>

<template>
  <section class="mail-page">
    <nav class="mail-steps" aria-label="邮件识别步骤">
      <button type="button" class="secondary" :aria-pressed="mailStep === 'inbox'" :disabled="saving" @click="openStep('inbox')">邮件</button>
      <button type="button" class="secondary" :aria-pressed="mailStep === 'compose'" :disabled="saving" @click="openStep('compose')">识别</button>
      <button type="button" class="secondary" :aria-pressed="mailStep === 'review'" :disabled="!hasResult || saving" @click="openStep('review')">核对</button>
    </nav>
    <div class="mail-grid" :data-step="mailStep">
      <section class="card inbox-panel workspace-light-surface" :inert="saving">
        <div class="inbox-heading"><div><span class="step inbox-step" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M3.5 6.5h17v12h-17z"/><path d="m4 7 8 6 8-6"/></svg></span><div><h3 ref="inboxHeading" tabindex="-1">待处理邮件</h3><small>{{inbox.pendingCount}} 封</small></div></div>
        <div class="inbox-actions">
          <button class="secondary sync-button icon-button" type="button" aria-label="立即收取新邮件" :title="syncing?'正在收取邮件':'收取新邮件'" :disabled="syncing || batchBusy || pendingMail.size > 0 || !inbox.accounts.length" @click="loadInbox(true)"><AppIcon name="refresh" /></button>
          <button class="secondary" type="button" :disabled="syncing || batchBusy || pendingMail.size > 0 || (!selectingMail && !inbox.messages.length)" @click="toggleMailSelection">{{selectingMail?'取消选择':'选择'}}</button>
          <details ref="moreMailActions" class="mail-more" @keydown.esc.stop="moreMailActions?.removeAttribute('open'); moreMailActions?.querySelector('summary')?.focus()">
            <summary title="更多邮件操作" aria-label="更多邮件操作">···</summary>
            <div class="mail-more-panel">
              <MailFuseButton class="secondary" progress-bar
                :label="preparingBatch ? '正在读取…' : `处理全部 ${inbox.pendingCount} 封`"
                :undo-label="batchFuseLabel" :window-ms="10000"
                :pending="batch?.all" :submitting="batch?.all && processingBatch"
                :disabled="syncing || Boolean(batch) || pendingMail.size > 0 || !inbox.pendingCount"
                :aria-busy="preparingBatch"
                @press="queueAllMail" @undo="undoBatch" @expire="processBatch" />
            </div>
          </details>
        </div></div>
        <div class="mail-list-region" :aria-busy="syncing||processingBatch">
          <div v-if="syncing" class="inbox-loading" role="status" aria-live="polite"><span class="inbox-spinner" aria-hidden="true"></span><strong>正在收取邮件</strong><small>新邮件会自动出现在这里</small></div>
          <div v-else class="mail-cards" aria-label="待处理邮件">
            <article v-for="mail in visibleMail" :key="mail.id" :class="{selected:!selectingMail&&selectedMailId===mail.id,'mail-card-queued': batchIds.has(mail.id)}">
              <label v-if="selectingMail" class="mail-check-row">
                <input v-model="checkedMailIds" type="checkbox" :value="mail.id" :disabled="batchBusy" :aria-label="`勾选邮件：${mail.subject||'无主题'}`" />
                <span class="mail-card-copy"><strong>{{mail.subject||'（无主题）'}}</strong><span>{{mail.sender||mail.accountEmail}}</span><small>{{mailDate(mail.receivedAt)}}</small></span>
              </label>
              <template v-else>
                <button class="mail-select" :aria-label="'选择邮件：'+(mail.subject||'无主题')" @click="selectMail(mail)"><span class="mail-card-copy"><strong>{{mail.subject||'（无主题）'}}</strong><span>{{mail.sender||mail.accountEmail}}</span><small>{{mailDate(mail.receivedAt)}}</small></span></button>
                <div class="mail-card-actions"><button class="secondary icon-button compact-icon" type="button" :aria-label="`查看邮件原文：${mail.subject||'无主题'}`" title="查看原文" @click="openMailPreview(mail)"><AppIcon name="eye" /></button><MailFuseButton class="processed" label="标记为已处理"
                    :action-label="`标记邮件为已处理：${mail.subject || '无主题'}`"
                    progress-bar :window-ms="4000" :pending="pendingMail.has(mail.id)"
                    :disabled="batchBusy" :submitting="processingMailIds.has(mail.id)"
                    @press="queueMail(mail)" @undo="pendingMail.delete(mail.id)" @expire="processMail(mail.id)">
                    <AppIcon name="check" />
                  </MailFuseButton></div>
              </template>
            </article>
          </div>
          <div v-if="!syncing&&!visibleMail.length" class="inbox-empty">{{inbox.accounts.length?'暂无待处理邮件':'请先在设置页面连接 QQ 或网易邮箱'}}</div>
        </div>
        <div v-if="selectingMail" class="mail-batch-bar">
          <button v-if="selectingMail && !batch" class="secondary" type="button" title="选择当前列表中的邮件"
            :disabled="syncing || batchBusy || !listedMailIds.length" @click="toggleAllListedMail">{{ allListedMailChecked ? '取消全选' : '全选' }}</button>
          <small v-if="selectingMail && !batch" role="status">已选 {{ checkedMailIds.length }} 封</small>
          <MailFuseButton class="secondary"
            :label="`处理 ${checkedMailIds.length} 封`" :undo-label="batchFuseLabel"
            :window-ms="8000" :pending="Boolean(batch && !batch.all)" :submitting="Boolean(batch && !batch.all && processingBatch)"
            :disabled="syncing || batchBusy || pendingMail.size > 0 || !checkedMailIds.length"
            @press="queueSelectedMail" @undo="undoBatch" @expire="processBatch" />
        </div>
      </section>

      <section class="card compose-panel workspace-light-surface" :inert="saving">
        <div class="panel-title"><div><span class="step">1</span><h3 ref="composeHeading" tabindex="-1">粘贴通知正文</h3></div><button class="text-button icon-button compact-icon" type="button" :disabled="!mailBody" aria-label="清空通知正文" title="清空正文" @click="mailBody = ''"><AppIcon name="broom" /></button></div>
        <textarea v-model="mailBody" maxlength="100000" rows="18" placeholder="将笔试、面试、测评或 Offer 通知完整粘贴到这里……" />
        <div class="privacy-note">正文只用于本次识别，不会作为邮件原文写入投递记录。</div>
        <div v-if="status && !status.callsEnabled" class="service-unavailable">{{ status.message || '邮件识别服务当前不可用' }}</div>
        <button class="primary-action workspace-light-action" :disabled="recognizing || checkingStatus || !status?.callsEnabled || !mailBody.trim()" :aria-busy="recognizing" @click="recognize"><span v-if="recognizing" class="recognition-spinner" aria-hidden="true"></span><span v-else aria-hidden="true">✦</span>{{ recognizing ? '正在识别…' : checkingStatus ? '正在检查服务…' : '开始识别' }}</button>
      </section>

      <section class="card review-panel workspace-light-surface">
        <div class="panel-title">
          <div>
            <span class="step">2</span><h3 ref="reviewHeading" tabindex="-1">核对并录入</h3>
            <button v-if="hasResult" class="secondary icon-button compact-icon" type="button" aria-label="查看邮件识别字段" aria-haspopup="dialog" title="查看邮件识别字段" @click="recognitionDetailsDialog?.showModal()">
              <AppIcon name="info" />
            </button>
          </div>
          <span v-if="hasResult" class="match-badge">{{ matchedApplication ? '已匹配现有投递' : '将新建投递' }}</span>
        </div>
        <form v-if="hasResult" class="result-form" :inert="saving" @submit.prevent="saveResult" @keydown.enter="preventImplicitSubmit">
          <label class="wide application-match"><span>{{terminalNotice?'关联要结束的已有投递':'关联已有投递'}}</span><BaseSelect v-model="selectedApplicationId" :options="terminalNotice ? [{value:'',label:'请选择已有投递（不会新建）'},...rankedApplications.map(item=>({value:item.id,label:`${item.company} · ${item.position}`}))] : [{value:'',label:'不关联，新建一条投递'},...recommendedApplications.map(item=>({value:item.id,label:`★ ${matchPercent(item)}%｜${item.company} · ${item.position}`,group:'★ 高匹配推荐'})),...otherApplications.map(item=>({value:item.id,label:`${item.company} · ${item.position}`,group:'其他已有投递'}))]" /><small>{{terminalNotice ? (matchedApplication ? '确认后只会把这条投递标记为已结束，不会创建日程。' : '结束类通知不能新建投递或日程，请选择一条已有投递。') : matchedApplication ? '按所选类型添加日程，投递阶段随日程自动计算。' : '未自动匹配时可手动选择；新建投递后，有效时间也会自动生成关联日程。'}}</small></label>
          <div v-if="terminalNotice" class="terminal-notice wide" role="status"><AppIcon name="info" :size="18" /><div><strong>邮件可能表示招聘流程已结束或岗位不匹配</strong><span>已关闭日程录入。请确认是否要结束关联投递。</span></div></div>
          <label><span>公司 *</span><input v-model="result.company" maxlength="120" required /></label>
          <label><span>岗位 *</span><input v-model="result.position" maxlength="160" required /></label>
          <label><span>通知类型</span><BaseSelect v-model="result.noticeType" :options="noticeTypes" /></label>
          <label v-if="!terminalNotice"><span>安排名称</span><input v-model="result.scheduleTitle" maxlength="160" placeholder="如：一面、二面、HR面试" /></label>
          <ScheduleTimeModeNotice v-if="!terminalNotice" class="wide" :mode="timeMode" :detected="canCreateSchedule" selectable @select="timeMode=$event" />
          <label v-if="!terminalNotice"><span>{{timeMode==='range'?'开始时间 *':'时间 *'}}</span><input v-model="result.startsAt" type="datetime-local" required /></label>
          <label v-if="!terminalNotice&&timeMode==='range'"><span>结束时间 *</span><input v-model="result.endsAt" type="datetime-local" :min="result.startsAt" required /></label>
          <label v-if="!terminalNotice"><span>地点 / 视频链接</span><input v-model="result.location" maxlength="1000" /></label>
          <label v-if="!terminalNotice" class="wide"><span>备注</span><textarea v-model="result.notes" rows="3" maxlength="4000" placeholder="账号密码、考试码或其他需要记住的信息" /></label>
          <label v-if="duplicateEvent" class="wide duplicate-note">
            <span>已有相同日程，选择如何处理备注</span>
            <BaseSelect v-model="noteMode" aria-label="备注处理方式" :options="[{value:'keep',label:'保留原备注'},{value:'append',label:'追加新备注'},{value:'replace',label:'替换为新备注'}]" />
            <small v-if="duplicateEvent.notes">原备注：{{ duplicateEvent.notes }}</small>
          </label>
          <p v-if="scheduleError" class="schedule-error wide" role="alert">{{scheduleError}}</p>
          <div class="commit-box wide"><template v-if="terminalNotice"><span>{{actionSummary}}</span><div class="terminal-actions"><button type="button" class="secondary" :disabled="saving" @click="skipTerminalUpdate">暂不修改</button><button type="submit" class="workspace-light-action" :disabled="saving||!matchedApplication">{{saving?'正在更新…':'标记投递已结束'}}</button></div></template><template v-else><span>{{ actionSummary }}</span><button type="submit" class="workspace-light-action" :disabled="saving">{{ saving ? '正在录入…' : '确认录入' }}</button></template></div>
        </form>
        <div v-else-if="recognizing" class="recognition-state" role="status" aria-live="polite" aria-busy="true"><span class="recognition-spinner large" aria-hidden="true"></span><strong>正在分析邮件内容</strong></div>
        <div v-else class="empty-state review-empty">选择邮件或粘贴通知正文，再点击「开始识别」。</div>
      </section>
    </div>
    <p v-if="message" class="feedback success">{{ message }}</p>
    <p v-if="error" class="feedback danger" role="alert">{{ error }}</p>
    <Transition name="process-notice"><div v-if="processNotice" class="process-notice" role="status"><span aria-hidden="true">✓</span>{{processNotice}}</div></Transition>

    <dialog ref="recognitionDetailsDialog" class="recognition-details" aria-labelledby="recognition-details-title">
      <header>
        <div>
          <h2 id="recognition-details-title">邮件识别结果</h2>
          <p>识别接口返回的字段，可与当前表单核对。</p>
        </div>
        <button class="secondary icon-button" type="button" aria-label="关闭识别结果" title="关闭" autofocus @click="recognitionDetailsDialog?.close()"><AppIcon name="close" /></button>
      </header>
      <dl class="recognition-fields" tabindex="0" aria-label="识别字段">
        <div v-for="(value, key) in recognitionDetails" :key="key">
          <dt>{{ recognitionFieldLabels[key] ?? key }}</dt>
          <dd>{{ value === '' || value == null ? '未返回' : typeof value === 'string' ? value : JSON.stringify(value, null, 2) }}</dd>
        </div>
      </dl>
    </dialog>

    <dialog ref="previewDialog" class="mail-preview" aria-labelledby="mail-preview-title" @close="previewMail = null; previewHtml = ''; previewLoading = false">
      <article v-if="previewMail" class="mail-preview-card">
        <header>
          <div class="mail-preview-heading"><span class="preview-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M3.5 6.5h17v12h-17z"/><path d="m4 7 8 6 8-6"/></svg></span><div><span>邮件原文</span><h2 id="mail-preview-title">{{ previewMail.subject || '（无主题）' }}</h2></div></div>
          <button class="preview-close icon-button" type="button" aria-label="关闭邮件原文" title="关闭" @click="closeMailPreview"><AppIcon name="close" /></button>
        </header>
        <dl class="mail-preview-meta">
          <div><dt>发件人</dt><dd>{{ previewMail.sender || '未知发件人' }}</dd></div>
          <div><dt>收件邮箱</dt><dd>{{ previewMail.accountEmail }}</dd></div>
          <div><dt>收取时间</dt><dd>{{ mailDate(previewMail.receivedAt) }}</dd></div>
        </dl>
        <div class="mail-preview-body" tabindex="0"><div v-if="previewLoading" class="mail-preview-loading" role="status"><span class="recognition-spinner" aria-hidden="true"></span>正在读取邮件原始排版…</div><iframe v-else-if="previewHtml" class="mail-preview-iframe" :srcdoc="previewSrcdoc" title="邮件原文内容" sandbox="allow-popups allow-popups-to-escape-sandbox" referrerpolicy="no-referrer"></iframe><div v-else class="mail-preview-plain">{{ previewMail.body || '这封邮件没有可显示的正文。' }}</div></div>
        <footer><small>{{ previewError || (previewHtml ? '邮件中的外部图片已隐藏，以保护隐私。' : '这里展示系统通过邮箱服务收取并整理后的正文内容。') }}</small><div><button type="button" class="secondary" @click="closeMailPreview">关闭</button><button type="button" class="processed" :disabled="batchBusy || pendingMail.has(previewMail.id)" @click="queueMail(previewMail)">标记为已处理</button></div></footer>
      </article>
    </dialog>
  </section>
</template>

<style scoped>
.mail-more { position:relative; }
.mail-more > summary {
  display:grid; min-width:44px; min-height:44px; place-items:center;
  border:1px solid var(--color-border); border-radius:6px; list-style:none;
  color:var(--color-foreground); background:var(--color-muted); cursor:pointer;
  font-weight:700; letter-spacing:1px;
}
.mail-more > summary::-webkit-details-marker { display:none; }
.mail-more-panel {
  position:absolute; z-index:3; inset-inline-end:0; top:calc(100% + 6px);
  width:max-content; padding:4px; border:1px solid var(--color-border);
  border-radius:6px; background:var(--color-card);
}
#app .mail-more-panel button.mail-fuse { min-width:150px; white-space:nowrap; }
#app .mail-more-panel button.mail-fuse.is-pending { padding-bottom:12px; }
.mail-cards article.mail-card-queued .mail-card-copy { opacity:.58; }
.mail-check-row { display:flex; min-height:44px; align-items:center; gap:10px; padding-inline:4px; cursor:pointer; }
#app .mail-check-row input[type="checkbox"] {
  flex:none; width:17px; height:17px; min-height:0; margin:0;
  accent-color:var(--color-primary);
}
#app .mail-check-row input[type="checkbox"]:focus { box-shadow:none; }
#app .mail-check-row input[type="checkbox"]:focus-visible {
  outline:2px solid var(--color-primary);
  outline-offset:2px;
}
.mail-batch-bar {
  display:flex; flex:none; flex-wrap:wrap; align-items:center; gap:8px;
  margin-top:10px; padding:8px; border:1px solid var(--color-border); border-radius:6px;
  background:var(--color-muted);
}
.mail-batch-bar small { margin-inline-end:auto; color:var(--color-muted-foreground); }
.mail-batch-bar button { padding-inline:8px; font-size:13px; white-space:nowrap; }
.mail-page { display: grid; height: 100%; min-height: 0; grid-template-rows: minmax(0, 1fr); padding-top: 18px; box-sizing: border-box; overflow: hidden; }
.panel-title, .panel-title > div, .commit-box { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.panel-title p { margin: 0; }
.match-badge { padding: 7px 10px; border-radius: 999px; font-size: 12px; font-weight: 800; }
.match-badge { color: #4259bd; background: #edf1ff; }
.text-button { padding: 7px 10px; color: var(--color-card-foreground); background: transparent; }
.card { margin-top: 0 !important; }
.panel-title { min-height: 44px; }
.panel-title h3 { margin: 0; font-size: 17px; }
.recognition-details {
  width: min(600px, calc(100vw - 24px));
  max-height: calc(100dvh - 32px);
  padding: 0;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  color: var(--color-card-foreground);
  background: var(--color-card);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}
.recognition-details[open] { display: flex; flex-direction: column; }
.recognition-details::backdrop { background: rgb(15 23 42 / 48%); }
.recognition-details > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
}
.recognition-details h2 { margin: 0; font-size: 18px; }
.recognition-details p { margin: 4px 0 0; color: var(--color-muted-foreground); font-size: 13px; }
.recognition-fields { min-height: 0; margin: 0; padding: 4px 20px; overflow: auto; }
.recognition-fields > div {
  display: grid;
  grid-template-columns: minmax(5.5rem, 24%) minmax(0, 1fr);
  gap: 10px;
  padding: 10px 0;
  font-size: 14px;
}
.recognition-fields > div + div { border-top: 1px solid var(--color-border); }
.recognition-fields dt { color: var(--color-muted-foreground); overflow-wrap: anywhere; }
.recognition-fields dd { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
.result-form { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-top: 18px; }
.result-form label { display: grid; gap: 7px; color: var(--color-muted-foreground); font-size: 13px; font-weight: 700; }
.result-form .wide { grid-column: 1 / -1; }
.application-match{padding:12px;border:1px solid #dbe3f4;border-radius:10px;background:#f7f9ff}.application-match small{color:var(--color-muted-foreground);font-weight:400}
.mail-grid { display: grid; min-height: 0; grid-template-columns: minmax(260px, .82fr) minmax(300px, 1fr) minmax(380px, 1.28fr); gap: 16px; padding-bottom: 18px; }
.inbox-panel, .compose-panel, .review-panel { min-width: 0; min-height: 0; height: 100%; box-sizing: border-box; }
.inbox-panel, .compose-panel { display: flex; flex-direction: column; }
.review-panel { display: flex; flex-direction: column; overflow-y: auto; overscroll-behavior: contain; }
.step { display: grid; width: 28px; height: 28px; border-radius: 9px; color: #fff; background: var(--color-primary); place-items: center; font-size: 13px; font-weight: 800; }
textarea, select { width: 100%; padding: 12px 14px; border: 1px solid #d4dbea; border-radius: 10px; background: #fff; font: inherit; resize: vertical; }
.compose-panel > textarea { min-height: 0; flex: 1 1 auto; margin: 18px 0 10px; line-height: 1.65; resize: none; }
.inbox-heading,.inbox-heading>div,.inbox-actions,.mail-cards article,.mail-card-actions{display:flex;align-items:center}.inbox-heading{justify-content:space-between;gap:10px;margin-bottom:10px}.inbox-heading>div{min-width:0;gap:10px}.inbox-heading>div:first-child{flex:1}.inbox-heading h3{margin:0;font-size:16px}.inbox-heading small{display:block;margin-top:2px;color:var(--color-muted-foreground)}.inbox-step{color:var(--color-primary);background:#e8f4fa}.inbox-step svg{width:16px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}.inbox-actions{min-width:max-content;flex:none;gap:6px}.sync-button{min-height:38px;flex:none;padding:7px 11px;font-size:13px}.mail-list-region{display:flex;min-height:120px;flex:1 1 auto;flex-direction:column}.mail-cards{display:grid;min-height:0;flex:1 1 auto;align-content:start;gap:8px;padding:3px;overflow-y:auto;overflow-x:hidden;overscroll-behavior:contain}.mail-cards:empty{display:none}.mail-cards article{position:relative;align-items:stretch;flex-direction:column;gap:8px;min-width:0;padding:8px;border:1px solid var(--color-border);border-left:4px solid #7aa5bb;border-radius:10px;background:#f8fbfd;transition:transform .15s ease,border-color .15s ease,box-shadow .15s ease}.mail-cards article:hover{transform:translateY(1px);box-shadow:inset 0 2px 4px rgba(4,31,49,.08)}.mail-cards article:focus-within,.mail-cards article.selected{border-color:var(--color-primary);box-shadow:0 0 0 3px color-mix(in srgb,var(--color-primary) 14%,transparent)}.mail-select{display:block;min-width:0;flex:1;padding:2px;color:inherit;background:transparent;text-align:left}.mail-card-copy{display:grid;min-width:0;gap:2px}.mail-card-copy strong,.mail-card-copy span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.mail-card-copy span,.mail-card-copy small{color:var(--color-muted-foreground);font-size:12px}.mail-card-actions{flex:none;gap:8px}.mail-card-actions :is(button,a){display:flex;min-height:38px;flex:1;align-items:center;justify-content:center;padding:7px 10px;border:1px solid var(--color-border);border-radius:9px;font-family:var(--font-button);font-weight:700;text-decoration:none}.mailbox-button{color:var(--color-primary);background:#edf7fc}.processed{color:#176b4b;background:#eaf8f1}.inbox-empty{padding:12px;border:1px dashed var(--color-border);border-radius:10px;color:var(--color-muted-foreground);background:#fafcfd;text-align:center}.inbox-loading{display:grid;min-height:140px;flex:1 1 auto;align-content:center;justify-items:center;gap:7px;color:var(--color-primary);text-align:center}.inbox-loading strong{font-size:14px}.inbox-loading small{color:var(--color-muted-foreground)}.inbox-spinner{width:34px;height:34px;margin-bottom:4px;border:3px solid color-mix(in srgb,var(--color-primary) 18%,transparent);border-top-color:var(--color-primary);border-right-color:color-mix(in srgb,var(--color-primary) 62%,#fff);border-radius:50%;animation:inbox-spin .8s linear infinite}
.privacy-note { margin-bottom: 14px; color: var(--color-muted-foreground); font-size: 12px; }
.service-unavailable { margin: 0 0 12px; padding: 9px 12px; border: 1px solid #f4c7c7; border-radius: 9px; color: #b42318; background: #fff4f2; font-size: 12px; }
.primary-action { width: 100%; }
.empty-state { display: grid; min-height: 280px; gap: 8px; padding: 24px; border: 1px dashed #d4dbea; border-radius: 12px; color: var(--color-muted-foreground); background: #fafbfc; place-content: center; text-align: center; }
.review-panel > .empty-state { min-height: 0; flex: 1 1 auto; margin-top: 18px; }
.empty-state.small { min-height: 100px; }
.commit-box { padding: 14px; border-radius: 12px; color: var(--color-muted-foreground); background: #f4f6fb; }
.schedule-error{margin:0;padding:9px 12px;border:1px solid color-mix(in srgb,var(--color-destructive) 35%,var(--color-border));border-radius:9px;color:var(--color-destructive);background:color-mix(in srgb,var(--color-destructive) 9%,var(--color-card));font-size:12px;font-weight:700}
.feedback { margin: 0; padding: 13px 16px; border-radius: 11px; background: #fff; }
.process-notice{position:fixed;z-index:1200;top:22px;left:50%;display:flex;align-items:center;gap:9px;max-width:calc(100vw - 32px);padding:12px 18px;border:1px solid #a9d7c0;border-radius:12px;color:#145c43;background:#f0fbf5;box-shadow:0 12px 32px rgba(14,75,55,.18);font-family:var(--font-button);font-weight:700;transform:translateX(-50%)}.process-notice span{display:grid;width:22px;height:22px;border-radius:50%;color:#fff;background:#26956b;place-items:center}.process-notice-enter-active,.process-notice-leave-active{transition:opacity .18s ease,transform .18s ease}.process-notice-enter-from,.process-notice-leave-to{opacity:0;transform:translate(-50%,-8px)}
.mail-preview{width:min(780px,calc(100vw - 32px));max-width:none;height:min(760px,calc(100dvh - 48px));max-height:none;padding:0;border:1px solid var(--color-border);border-radius:18px;color:var(--color-card-foreground);background:var(--color-card);box-shadow:0 24px 70px rgba(4,31,49,.24);overflow:hidden}.mail-preview::backdrop{background:rgba(10,35,51,.5);backdrop-filter:blur(4px)}.mail-preview-card{display:grid;height:100%;grid-template-rows:auto auto minmax(0,1fr) auto;background:var(--color-card)}.mail-preview-card>header,.mail-preview-card>footer{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:20px 24px}.mail-preview-card>header{border-bottom:1px solid var(--color-border)}.mail-preview-heading{display:flex;min-width:0;align-items:center;gap:13px}.mail-preview-heading>div{min-width:0}.mail-preview-heading span{color:var(--color-primary);font-size:12px;font-weight:800;letter-spacing:.1em}.mail-preview-heading h2{margin:4px 0 0;overflow-wrap:anywhere;font-size:20px;line-height:1.35}.preview-icon{display:grid;width:42px;height:42px;flex:none;border-radius:12px;color:var(--color-on-primary);background:var(--color-primary);place-items:center}.preview-icon svg{width:21px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}.preview-close{display:grid;width:44px;height:44px;flex:none;padding:0;border:1px solid var(--color-border);border-radius:12px;color:var(--color-primary);background:var(--color-muted);place-items:center;font-size:27px;line-height:1}.mail-preview-meta{display:grid;grid-template-columns:1.4fr 1fr .7fr;gap:0;margin:0;padding:14px 24px;border-bottom:1px solid var(--color-border);background:var(--color-muted)}.mail-preview-meta div{min-width:0;padding-right:16px}.mail-preview-meta dt{margin-bottom:4px;color:var(--color-muted-foreground);font-size:12px;font-weight:700}.mail-preview-meta dd{margin:0;color:var(--color-card-foreground);overflow-wrap:anywhere;font-size:13px}.mail-preview-body{min-height:0;margin:20px 24px;padding:20px;border:1px solid var(--color-border);border-radius:12px;color:var(--color-card-foreground);background:var(--color-background);overflow:auto;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.75}.mail-preview-card>footer{border-top:1px solid var(--color-border)}.mail-preview-card>footer small{color:var(--color-muted-foreground)}.mail-preview-card>footer>div{display:flex;gap:10px}.mail-preview-card>footer button{min-height:44px}.mail-preview-card>footer .processed{padding:9px 15px;border:1px solid color-mix(in srgb,var(--color-success) 35%,var(--color-border));border-radius:10px;color:var(--color-success);background:color-mix(in srgb,var(--color-success) 10%,var(--color-card))}
.mail-preview-body{padding:0;color:#202330;background:#fff;overflow:hidden}.mail-preview-iframe{display:block;width:100%;height:100%;min-height:320px;border:0;background:#fff}.mail-preview-plain{height:100%;padding:20px;color:#202330;white-space:pre-wrap;overflow:auto;overflow-wrap:anywhere}.mail-preview-loading{display:flex;height:100%;min-height:180px;align-items:center;justify-content:center;gap:10px;color:#626879}.mail-preview-loading .recognition-spinner{flex:none;border-color:color-mix(in srgb,var(--color-primary) 30%,transparent);border-top-color:var(--color-primary)}
@keyframes inbox-spin{to{transform:rotate(360deg)}}
.compose-panel > textarea{border-color:#aeb9cb;box-shadow:inset 0 1px 2px rgba(24,39,75,.06);transition:border-color .18s ease,box-shadow .18s ease}.compose-panel > textarea:focus{border-color:var(--color-primary);box-shadow:inset 0 1px 3px rgba(24,39,75,.1),0 0 0 3px color-mix(in srgb,var(--color-primary) 15%,transparent);outline:none}
.mail-card-actions{align-self:flex-end}.mail-card-actions :is(button,a){width:38px;flex:0 0 38px}
#app .mail-card-actions button.mail-fuse.is-pending {
  width:auto; min-width:78px; flex:0 0 auto; padding:6px 14px 12px; white-space:nowrap;
}
.primary-action{display:inline-flex;width:auto;min-width:168px;min-height:46px;align-items:center;justify-content:center;align-self:flex-end;gap:9px;padding:11px 18px;border:1px solid color-mix(in srgb,var(--color-primary) 72%,#202d61);border-radius:10px;color:var(--color-on-primary);background:var(--color-primary);box-shadow:0 5px 14px color-mix(in srgb,var(--color-primary) 24%,transparent);font-weight:800;transition:transform .16s ease,box-shadow .16s ease,background-color .16s ease}.primary-action:not(:disabled):hover{background:color-mix(in srgb,var(--color-primary) 94%,var(--color-foreground))}.primary-action:disabled{border-color:var(--color-border);color:var(--color-muted-foreground);background:var(--color-muted);box-shadow:none;opacity:.68;cursor:not-allowed}.recognition-spinner{width:17px;height:17px;border:2px solid color-mix(in srgb,currentColor 34%,transparent);border-top-color:currentColor;border-radius:50%;animation:inbox-spin .72s linear infinite}.recognition-spinner.large{width:32px;height:32px;border-width:3px}.recognition-state,.empty-state{display:grid;min-height:280px;gap:10px;padding:22px;border:1px dashed #aab7cb;border-radius:14px;color:var(--color-muted-foreground);background:color-mix(in srgb,var(--color-card) 88%,var(--color-muted));place-content:center;text-align:center}.recognition-state{align-content:center;justify-items:center;border-color:color-mix(in srgb,var(--color-primary) 55%,var(--color-border));background:color-mix(in srgb,var(--color-primary) 5%,var(--color-card))}.empty-state strong,.recognition-state strong{color:var(--color-card-foreground);font-size:14px}
@media (min-width: 901px) and (max-width: 1200px) { .mail-grid { grid-template-columns: minmax(0, .9fr) minmax(0, 1.1fr); grid-template-rows:minmax(150px,.34fr) minmax(0,1fr); } .inbox-panel { grid-column: 1 / -1; height:100%; overflow:hidden; } .compose-panel, .review-panel { min-height:0; height:100%; } }
@media (max-width: 900px) { .mail-grid { grid-template-columns: 1fr; } .inbox-panel { grid-column: auto; } .inbox-panel, .compose-panel, .review-panel { min-height: 0; height: auto; } .compose-panel > textarea { flex: none; height: clamp(160px, 24dvh, 240px); min-height: 160px; overflow-y: auto; resize: vertical; } }
@media (max-width: 650px) {
  .result-form { grid-template-columns: 1fr; }
  .result-form .wide { grid-column: auto; }
  .commit-box { align-items: stretch; flex-direction: column; }
  .inbox-heading{align-items:stretch;flex-direction:column}.inbox-actions{display:grid;grid-template-columns:44px minmax(0,1fr) 44px}.sync-button{width:100%}.mail-cards{max-height:300px}.mail-card-actions button{min-height:44px}
  .mail-preview{width:calc(100vw - 20px);height:calc(100dvh - 20px);border-radius:14px}.mail-preview-card>header,.mail-preview-card>footer{padding:14px}.mail-preview-heading h2{font-size:17px}.mail-preview-meta{grid-template-columns:1fr;padding:12px 14px;gap:10px}.mail-preview-body{margin:12px 14px;padding:14px}.mail-preview-card>footer{align-items:stretch;flex-direction:column}.mail-preview-card>footer>div{display:grid;grid-template-columns:1fr 1fr}
}
</style>

<style scoped>
.terminal-notice{display:flex;align-items:flex-start;gap:10px;padding:12px 14px;border:1px solid color-mix(in srgb,#cf8b22 35%,var(--color-border));border-radius:10px;color:#76500d;background:color-mix(in srgb,#f3bf54 12%,var(--color-card))}
.terminal-notice>svg{flex:none;margin-top:1px;color:#ae7415}
.terminal-notice>div{display:grid;gap:4px}
.terminal-notice strong{font-size:12px}
.terminal-notice span{color:var(--color-muted-foreground);font-size:11px;line-height:1.5}
.terminal-actions{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:8px}
:global(:root[data-theme="dark"] .terminal-notice){border-color:#6e5428;color:#f0d093;background:#342a19}
:global(:root[data-theme="dark"] .terminal-notice>svg){color:#e5ae4b}
@media(max-width:650px){.terminal-actions{width:100%}.terminal-actions button{flex:1}}
</style>
<style scoped>
:global(:root[data-theme="dark"] .step) {
  border: 1px solid var(--color-border-strong);
  color: var(--color-foreground);
  background: #2a2e34;
}

:global(:root[data-theme="dark"] .inbox-step) {
  color: var(--color-foreground);
  background: #24282e;
}

:global(:root[data-theme="dark"] .compose-panel > textarea) { border-color:#505967; color:var(--color-card-foreground); background:#191c22; box-shadow:inset 0 1px 3px rgba(0,0,0,.24); }
:global(:root[data-theme="dark"] .compose-panel > textarea:focus) { border-color:var(--color-primary); box-shadow:inset 0 1px 3px rgba(0,0,0,.28),0 0 0 3px color-mix(in srgb,var(--color-primary) 18%,transparent); }
:global(:root[data-theme="dark"] .empty-state),:global(:root[data-theme="dark"] .recognition-state) { border-color:#4a5362; background:#1b1e25; }
:global(:root[data-theme="dark"] .primary-action:disabled) { border-color:#414650; color:#9299a6; background:#262a32; opacity:1; }
:global(:root[data-theme="dark"] .mail-preview-body) { color:#e8eaf1; background:#171a21; }
:global(:root[data-theme="dark"] .mail-preview-plain) { color:#e8eaf1; background:#171a21; }
 .mail-steps { display: none; }
@media (max-width: 720px) {
  .mail-steps { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 8px; margin-bottom: 12px; }
  .mail-steps button[aria-pressed="true"] { color: var(--color-primary); border-color: var(--color-primary); }
  .mail-grid:not([data-step="inbox"]) .inbox-panel,
  .mail-grid:not([data-step="compose"]) .compose-panel,
  .mail-grid:not([data-step="review"]) .review-panel { display: none !important; }
}
 .review-empty { color:var(--color-muted-foreground); text-align:center; }
:global(#app .review-panel .review-empty) { border:0; background:transparent; }
.duplicate-note { padding-inline-start:12px; border-inline-start:2px solid var(--color-warning); }
.result-form input[type="datetime-local"] { font-variant-numeric:tabular-nums; }
</style>
