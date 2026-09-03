<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, apiCached, ApiError } from './api'
import { type JobApplication, useJobTrackerStore } from './jobTrackerStore'

type AiStatus = { sandboxEnabled: boolean; callsEnabled: boolean; message: string }
type ConfigView = { apiUrl: string; model: string; hasApiKey: boolean; lastFour: string }
type Recognition = { company: string; position: string; noticeType: string; suggestedStage: string; suggestedStatus: string; startsAt: string; endsAt: string; location: string; summary: string }
const noticeTypes = ['测评', '笔试', '面试', 'Offer', '未通过', '其他']
const stages = ['已投递', '测评', '笔试', '面试', 'Offer', '已结束']
const statuses = ['等待结果', '已通过', '未通过', '已放弃', '已结束']
const store = useJobTrackerStore()
const status = ref<AiStatus | null>(null)
const config = ref<ConfigView | null>(null)
const configForm = reactive({ apiUrl: 'https://api.deepseek.com', model: 'deepseek-chat', apiKey: '', clearApiKey: false })
const mailBody = ref('')
const result = reactive({ company: '', position: '', noticeType: '其他', suggestedStage: '已投递', suggestedStatus: '等待结果', startsAt: '', endsAt: '', location: '', summary: '', notes: '' })
const hasResult = ref(false)
const createSchedule = ref(true)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const message = ref('')
const showConfig = ref(false)

const matchedApplication = computed(() => store.applications.value.find(item =>
  normalize(item.company) === normalize(result.company) && normalize(item.position) === normalize(result.position)
))
const canCreateSchedule = computed(() => Boolean(result.startsAt) && result.noticeType !== '未通过')
const actionSummary = computed(() => hasResult.value
  ? `${matchedApplication.value ? '更新已有投递' : '新建一条投递'}${createSchedule.value && canCreateSchedule.value ? '，并创建关联日程' : ''}`
  : '')

onMounted(async () => {
  await store.initialize()
  await Promise.all([checkStatus(false), store.user.value && !store.readOnly.value ? loadConfig() : Promise.resolve()])
})
function normalize(value: unknown) { return String(value || '').trim().toLocaleLowerCase() }
function inputTime(value: string) { return value ? value.replace(' ', 'T').slice(0, 16) : '' }
function apiTime(value: string) { return value ? value.replace('T', ' ').slice(0, 16) : '' }
function today() { const d=new Date(),pad=(v:number)=>String(v).padStart(2,'0'); return d.getFullYear()+'-'+pad(d.getMonth()+1)+'-'+pad(d.getDate()) }
function failure(cause: unknown, fallback: string) {
  if (cause instanceof ApiError && cause.status === 401) return '请先登录旧账号'
  return cause instanceof Error ? cause.message : fallback
}
async function checkStatus(loadDetails = true) {
  loading.value = true; error.value = ''
  try {
    status.value = await apiCached<AiStatus>('/api/poc/ai-sandbox/status')
    if (loadDetails && status.value.sandboxEnabled && store.user.value) await loadConfig()
  } catch (cause) { error.value = failure(cause, '检查 AI 服务失败') }
  finally { loading.value = false }
}
async function loadConfig() {
  config.value = await apiCached<ConfigView>('/api/poc/ai-sandbox/config')
  Object.assign(configForm, { apiUrl: config.value.apiUrl, model: config.value.model, apiKey: '', clearApiKey: false })
}
async function saveConfig() {
  loading.value = true; error.value = ''; message.value = ''
  try {
    config.value = await api<ConfigView>('/api/poc/ai-sandbox/config', { method: 'POST', body: JSON.stringify(configForm) })
    configForm.apiKey = ''; configForm.clearApiKey = false; showConfig.value = false
    message.value = 'AI 配置已安全保存'
  } catch (cause) { error.value = failure(cause, '保存 AI 配置失败') }
  finally { loading.value = false }
}
async function recognize() {
  loading.value = true; error.value = ''; message.value = ''; hasResult.value = false
  try {
    const value = await api<Recognition>('/api/poc/ai-sandbox/recognize', { method: 'POST', body: JSON.stringify({ body: mailBody.value }) })
    Object.assign(result, value, { startsAt: inputTime(value.startsAt), endsAt: inputTime(value.endsAt), notes: '' })
    hasResult.value = true
    createSchedule.value = Boolean(value.startsAt) && value.noticeType !== '未通过'
    message.value = '识别完成，请核对后确认录入'
  } catch (cause) { error.value = failure(cause, '邮件识别失败') }
  finally { loading.value = false }
}
function applicationPayload(item?: JobApplication) {
  return {
    company: result.company.trim(), position: result.position.trim(),
    city: String(item?.city || ''), channel: String(item?.channel || '邮件识别'),
    appliedDate: String(item?.appliedDate || today()), stage: result.suggestedStage,
    status: result.suggestedStatus, notes: result.notes.trim() || String(item?.notes || ''),
    expectedUpdatedAt: String(item?.updatedAt || '')
  }
}
async function saveResult() {
  if (!result.company.trim() || !result.position.trim()) { error.value = '请补全公司和岗位后再录入'; return }
  saving.value = true; error.value = ''; message.value = ''
  try {
    const matched = matchedApplication.value
    const response = matched
      ? await api<{ application: JobApplication }>(`/api/poc/application-sandbox/applications/${encodeURIComponent(matched.id)}`, { method: 'PUT', body: JSON.stringify(applicationPayload(matched)) })
      : await api<{ application: JobApplication }>('/api/poc/application-sandbox/applications', { method: 'POST', body: JSON.stringify(applicationPayload()) })
    if (createSchedule.value && canCreateSchedule.value) {
      await api('/api/poc/event-sandbox/events', { method: 'POST', body: JSON.stringify({
        applicationId: response.application.id, type: result.noticeType === '其他' ? '其他' : result.noticeType,
        title: result.noticeType === '其他' ? '邮件通知' : `${result.noticeType}安排`,
        startsAt: apiTime(result.startsAt), endsAt: apiTime(result.endsAt),
        location: result.location.trim(), notes: result.notes.trim(), expectedUpdatedAt: ''
      }) })
    }
    await store.refresh()
    message.value = `已${matched ? '更新投递' : '新建投递'}${createSchedule.value && canCreateSchedule.value ? '并创建日程' : ''}，写入前备份已自动生成`
    mailBody.value = ''; hasResult.value = false
  } catch (cause) { error.value = failure(cause, '录入识别结果失败') }
  finally { saving.value = false }
}
</script>

<template>
  <section class="mail-page">
    <div class="mail-toolbar">
      <div><span class="eyebrow">智能录入</span><h2>从招聘通知中提取投递与日程</h2><p>粘贴完整通知，识别后先核对，再由你确认写入。</p></div>
      <div class="toolbar-actions">
        <span :class="['status-pill', status?.callsEnabled ? 'ready' : 'waiting']">{{ status?.callsEnabled ? 'AI 可用' : status?.message || '检查中' }}</span>
        <button type="button" class="secondary" @click="showConfig = !showConfig">AI 设置</button>
      </div>
    </div>

    <div v-if="showConfig" class="card config-panel">
      <div class="panel-title"><div><h3>大模型 API</h3><p>密钥仅加密保存在当前业务数据库中。</p></div><button class="text-button" @click="showConfig = false">收起</button></div>
      <form v-if="status?.sandboxEnabled && store.user.value" class="config-form" @submit.prevent="saveConfig">
        <label class="wide"><span>API 地址</span><input v-model="configForm.apiUrl" type="url" maxlength="2048" required /></label>
        <label><span>模型名称</span><input v-model="configForm.model" maxlength="200" required /></label>
        <label><span>API Key {{ config?.hasApiKey ? `（末四位 ${config.lastFour}）` : '' }}</span><input v-model="configForm.apiKey" type="password" autocomplete="off" placeholder="留空保留现有密钥" /></label>
        <label class="check wide"><input v-model="configForm.clearApiKey" type="checkbox" /><span>清除现有 API Key</span></label>
        <button :disabled="loading">保存配置</button>
      </form>
      <div v-else class="empty-state small">{{ store.user.value ? status?.message : '登录后可查看和修改 AI 配置' }}</div>
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
          <label><span>公司 *</span><input v-model="result.company" maxlength="120" required /></label>
          <label><span>岗位 *</span><input v-model="result.position" maxlength="160" required /></label>
          <label><span>通知类型</span><select v-model="result.noticeType"><option v-for="item in noticeTypes" :key="item">{{ item }}</option></select></label>
          <label><span>投递阶段</span><select v-model="result.suggestedStage"><option v-for="item in stages" :key="item">{{ item }}</option></select></label>
          <label><span>当前状态</span><select v-model="result.suggestedStatus"><option v-for="item in statuses" :key="item">{{ item }}</option></select></label>
          <label><span>开始时间</span><input v-model="result.startsAt" type="datetime-local" /></label>
          <label><span>结束时间</span><input v-model="result.endsAt" type="datetime-local" :min="result.startsAt" /></label>
          <label><span>地点 / 视频链接</span><input v-model="result.location" maxlength="1000" /></label>
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
.mail-toolbar, .panel-title, .panel-title > div, .toolbar-actions, .commit-box { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.mail-toolbar h2 { margin: 5px 0 4px; font-size: 24px; }
.mail-toolbar p, .panel-title p { margin: 0; }
.eyebrow { color: #526ddd; font-size: 12px; font-weight: 800; letter-spacing: .12em; }
.status-pill, .match-badge { padding: 7px 10px; border-radius: 999px; font-size: 12px; font-weight: 800; }
.status-pill.ready { color: #157347; background: #e7f7ee; }
.status-pill.waiting { color: #83570c; background: #fff3d6; }
.match-badge { color: #4259bd; background: #edf1ff; }
.secondary { color: #344054; background: #eef2f8; }
.text-button { padding: 7px 10px; color: #344054; background: transparent; }
.card { margin-top: 0; }
.config-panel { padding: 20px 24px; }
.panel-title h3 { margin: 0; font-size: 17px; }
.config-form, .result-form { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-top: 18px; }
.config-form label, .result-form label { display: grid; gap: 7px; color: #475467; font-size: 13px; font-weight: 700; }
.config-form .wide, .result-form .wide { grid-column: 1 / -1; }
.check { display: flex !important; align-items: center; }
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
  .config-form, .result-form { grid-template-columns: 1fr; }
  .config-form .wide, .result-form .wide { grid-column: auto; }
  .commit-box { align-items: stretch; flex-direction: column; }
}
</style>
