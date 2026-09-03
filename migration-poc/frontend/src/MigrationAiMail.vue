<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

type AiStatus = {
  sandboxEnabled: boolean
  encryptionConfigured: boolean
  callsRequested: boolean
  callsEnabled: boolean
  message: string
}
type ConfigView = { apiUrl: string; model: string; hasApiKey: boolean; lastFour: string }
type Recognition = {
  company: string
  position: string
  noticeType: string
  suggestedStage: string
  suggestedStatus: string
  startsAt: string
  endsAt: string
  location: string
  summary: string
}

const status = ref<AiStatus | null>(null)
const config = ref<ConfigView | null>(null)
const form = reactive({ apiUrl: 'https://api.deepseek.com', model: 'deepseek-chat', apiKey: '', clearApiKey: false })
const mailBody = ref('')
const result = ref<Recognition | null>(null)
const loading = ref(false)
const error = ref('')
const message = ref('')

onMounted(checkStatus)

async function requestJson(url: string, init?: RequestInit) {
  const response = await fetch(url, { cache: 'no-store', ...init })
  const body = await response.json().catch(() => ({}))
  return { response, body }
}

async function checkStatus() {
  loading.value = true
  error.value = ''
  try {
    const statusResult = await requestJson('/api/poc/ai-sandbox/status')
    if (!statusResult.response.ok) throw new Error(statusResult.body.message || '无法检查 AI 沙箱')
    status.value = statusResult.body as AiStatus
    if (status.value.sandboxEnabled) await loadConfig()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '检查 AI 沙箱失败'
  } finally {
    loading.value = false
  }
}

async function loadConfig() {
  const response = await requestJson('/api/poc/ai-sandbox/config')
  if (!response.response.ok) throw new Error(
    response.response.status === 401 ? '请先在上方登录旧账号，再重新检查 AI 沙箱' : (response.body.message || '读取测试配置失败')
  )
  config.value = response.body as ConfigView
  form.apiUrl = config.value.apiUrl
  form.model = config.value.model
  form.apiKey = ''
  form.clearApiKey = false
}

async function saveConfig() {
  loading.value = true
  error.value = ''
  message.value = ''
  try {
    const response = await requestJson('/api/poc/ai-sandbox/config', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(form)
    })
    if (!response.response.ok) throw new Error(response.body.message || '保存 AI 配置失败')
    config.value = response.body as ConfigView
    form.apiKey = ''
    form.clearApiKey = false
    message.value = 'AI 配置已加密保存到独立测试数据库'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '保存 AI 配置失败'
  } finally {
    loading.value = false
  }
}

async function recognize() {
  loading.value = true
  error.value = ''
  message.value = ''
  result.value = null
  try {
    const response = await requestJson('/api/poc/ai-sandbox/recognize', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ body: mailBody.value })
    })
    if (!response.response.ok) throw new Error(response.body.message || '邮件识别失败')
    result.value = response.body as Recognition
    message.value = '邮件识别完成；请人工核对后再录入职位或日程'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '邮件识别失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="card ai-card">
    <div class="section-head">
      <div><span class="section-kicker">第 5 阶段</span><h2>AI 配置与邮件识别沙箱</h2></div>
      <span :class="['mode-badge', status?.callsEnabled ? 'enabled' : 'disabled']">{{ status?.callsEnabled ? '测试调用已开启' : '外部调用关闭' }}</span>
    </div>
    <p>API Key 使用与旧 Node.js 服务兼容的 AES-256-GCM 格式加密；服务端限制 HTTPS、域名白名单并拒绝内网地址。</p>

    <div v-if="status && !status.sandboxEnabled" class="notice"><strong>{{ status.message }}</strong><span>AI 配置只允许存入独立测试数据库。</span></div>

    <template v-else-if="status?.sandboxEnabled">
      <form class="config-form" @submit.prevent="saveConfig">
        <label class="wide"><span>API 地址</span><input v-model="form.apiUrl" type="url" maxlength="2048" required /></label>
        <label><span>模型名称</span><input v-model="form.model" maxlength="200" required /></label>
        <label><span>API Key {{ config?.hasApiKey ? `（当前末四位 ${config.lastFour}）` : '' }}</span><input v-model="form.apiKey" type="password" maxlength="4096" autocomplete="off" placeholder="留空则保留现有密钥" /></label>
        <label class="clear-key wide"><input v-model="form.clearApiKey" type="checkbox" /><span>清除测试库中现有 API Key</span></label>
        <div class="form-actions wide"><button :disabled="loading">{{ loading ? '保存中…' : '保存测试配置' }}</button><button type="button" class="secondary" @click="checkStatus">重新检查</button></div>
      </form>

      <div class="mail-grid">
        <div>
          <h3>邮件正文</h3>
          <textarea v-model="mailBody" maxlength="100000" rows="12" placeholder="粘贴笔试、面试或 Offer 通知正文……" />
          <button :disabled="loading || !status.callsEnabled || !mailBody.trim()" @click="recognize">{{ loading ? '识别中…' : '使用测试 AI 识别' }}</button>
          <p v-if="!status.callsEnabled" class="hint">{{ status.message }}。需另外设置 <code>POC_AI_CALLS_ENABLED=true</code> 才会发出外部请求。</p>
        </div>
        <div>
          <h3>识别结果</h3>
          <dl v-if="result" class="result-list">
            <div><dt>公司</dt><dd>{{ result.company || '未识别' }}</dd></div>
            <div><dt>岗位</dt><dd>{{ result.position || '未识别' }}</dd></div>
            <div><dt>通知类型</dt><dd>{{ result.noticeType }}</dd></div>
            <div><dt>建议阶段 / 状态</dt><dd>{{ result.suggestedStage }} / {{ result.suggestedStatus }}</dd></div>
            <div><dt>开始时间</dt><dd>{{ result.startsAt || '未识别' }}</dd></div>
            <div><dt>结束时间</dt><dd>{{ result.endsAt || '时间点日程' }}</dd></div>
            <div><dt>地点 / 链接</dt><dd>{{ result.location || '未识别' }}</dd></div>
          </dl>
          <div v-else class="notice">识别结果会显示在这里；不会自动写入职位或日程。</div>
        </div>
      </div>
      <p v-if="message" class="success">{{ message }}</p>
    </template>

    <p v-else>正在检查 AI 沙箱…</p>
    <p v-if="error" class="danger">{{ error }}</p>
  </section>
</template>

<style scoped>
.section-head, .form-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.section-kicker { display: block; margin-bottom: 5px; color: #4461d8; font-size: 12px; font-weight: 800; letter-spacing: .08em; }
.section-head h2 { margin-bottom: 0; }
.mode-badge { padding: 7px 10px; border-radius: 999px; font-size: 12px; font-weight: 800; white-space: nowrap; }
.mode-badge.enabled { color: #167647; background: #e9f8ef; }
.mode-badge.disabled { color: #7a4d0b; background: #fff3d6; }
.notice { display: grid; gap: 7px; padding: 18px; border: 1px solid #dbe3f1; border-radius: 12px; background: #f7f9fc; }
.notice span, .hint { color: #667085; }
.config-form { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin: 22px 0; }
.config-form label { display: grid; gap: 7px; color: #475467; font-size: 13px; font-weight: 700; }
.config-form .wide { grid-column: 1 / -1; }
.clear-key { display: flex !important; align-items: center; grid-template-columns: none !important; }
.clear-key input { flex: none; width: 18px; }
.form-actions { justify-content: flex-start; }
.secondary { color: #344054; background: #eef2f8; }
.mail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; margin-top: 24px; padding-top: 20px; border-top: 1px solid #edf0f5; }
.mail-grid h3 { margin-top: 0; font-size: 16px; }
textarea { width: 100%; margin-bottom: 10px; padding: 12px 14px; border: 1px solid #d4dbea; border-radius: 10px; font: inherit; resize: vertical; }
.result-list { margin-top: 0; }
.result-list div { align-items: flex-start; }
.result-list dd { max-width: 62%; overflow-wrap: anywhere; }
code { padding: 2px 5px; border-radius: 5px; background: #e9edf5; }

@media (max-width: 720px) {
  .config-form, .mail-grid { grid-template-columns: 1fr; }
  .config-form .wide { grid-column: auto; }
}
</style>
