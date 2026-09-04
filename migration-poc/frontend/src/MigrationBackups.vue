<script setup lang="ts">
import { onMounted, ref } from 'vue'

type SandboxStatus = { enabled: boolean; configured: boolean; isolated: boolean; message: string }
type BackupItem = {
  id: number
  reason: string
  createdAt: string
  size: number
  applicationCount: number
  eventCount: number
}

const sandbox = ref<SandboxStatus | null>(null)
const backups = ref<BackupItem[]>([])
const currentUpdatedAt = ref('')
const selected = ref<BackupItem | null>(null)
const confirmation = ref('')
const loading = ref(false)
const message = ref('')
const error = ref('')

onMounted(checkSandbox)

async function requestJson(url: string, init?: RequestInit) {
  const response = await fetch(url, { cache: 'no-store', ...init })
  const body = await response.json().catch(() => ({}))
  return { response, body }
}

async function checkSandbox() {
  loading.value = true
  error.value = ''
  try {
    const statusResult = await requestJson('/api/poc/backup-sandbox/status')
    if (!statusResult.response.ok) throw new Error(statusResult.body.message || '无法检查备份数据安全')
    sandbox.value = statusResult.body as SandboxStatus
    if (sandbox.value.enabled) await loadBackups()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '检查备份数据安全失败'
  } finally {
    loading.value = false
  }
}

async function loadBackups() {
  const result = await requestJson('/api/poc/backup-sandbox/backups')
  if (!result.response.ok) throw new Error(
    result.response.status === 401 ? '请先在上方登录旧账号，再重新检查备份数据安全' : (result.body.message || '读取备份失败')
  )
  backups.value = result.body.items as BackupItem[]
  currentUpdatedAt.value = String(result.body.currentUpdatedAt || '')
  if (selected.value) selected.value = backups.value.find((item) => item.id === selected.value?.id) || null
}

function choose(item: BackupItem) {
  selected.value = item
  confirmation.value = ''
  message.value = ''
  error.value = ''
}

async function restore() {
  if (!selected.value || confirmation.value !== '恢复') return
  loading.value = true
  error.value = ''
  message.value = ''
  try {
    const result = await requestJson(`/api/poc/backup-sandbox/backups/${selected.value.id}/restore`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ expectedCurrentUpdatedAt: currentUpdatedAt.value, confirmation: confirmation.value })
    })
    if (!result.response.ok) throw new Error(result.body.message || '恢复失败')
    message.value = `已恢复备份 #${selected.value.id}：${result.body.applicationCount} 条投递、${result.body.eventCount} 项日程`
    selected.value = null
    confirmation.value = ''
    await loadBackups()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '恢复失败'
  } finally {
    loading.value = false
  }
}

function formatDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}
function formatSize(value: number) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}
</script>

<template>
  <section class="card backup-card">
    <div class="section-head">
      <div><span class="section-kicker">数据保护</span><h2>云端历史备份</h2></div>
    </div>
    <p>最多保留最近 30 份；恢复历史版本前会自动备份当前数据。</p>

    <div v-if="sandbox && !sandbox.enabled" class="notice">
      <strong>备份功能暂时不可用</strong><span>{{ sandbox.message }}</span>
    </div>

    <template v-else-if="sandbox?.enabled">
      <div class="toolbar"><strong>最近 {{ backups.length }} 份备份</strong><button class="secondary" :disabled="loading" @click="checkSandbox">刷新列表</button></div>
      <div v-if="backups.length" class="backup-list">
        <button v-for="item in backups" :key="item.id" type="button" :class="{ selected: selected?.id === item.id }" @click="choose(item)">
          <div><strong>#{{ item.id }} · {{ item.reason || 'auto' }}</strong><span>{{ formatDate(item.createdAt) }}</span></div>
          <div><span>{{ item.applicationCount }} 条投递</span><span>{{ item.eventCount }} 项日程</span><span>{{ formatSize(item.size) }}</span></div>
        </button>
      </div>
      <div v-else class="notice">还没有可恢复的云端备份；修改投递或日程后会自动生成。</div>

      <div v-if="selected" class="restore-panel">
        <strong>准备恢复备份 #{{ selected.id }}</strong>
        <p>这会用所选版本替换当前数据。恢复前系统会自动保存当前版本，以便需要时撤销。</p>
        <label><span>输入“恢复”确认</span><input v-model="confirmation" autocomplete="off" placeholder="恢复" /></label>
        <div><button class="danger-button" :disabled="loading || confirmation !== '恢复'" @click="restore">{{ loading ? '恢复中…' : '确认恢复' }}</button><button class="secondary" @click="selected = null">取消</button></div>
      </div>
      <p v-if="message" class="success">{{ message }}</p>
    </template>

    <p v-else>正在读取云端备份…</p>
    <p v-if="error" class="danger">{{ error }}</p>
  </section>
</template>

<style scoped>
.section-head, .toolbar, .backup-list button, .restore-panel > div { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.section-kicker { display: block; margin-bottom: 5px; color: #4461d8; font-size: 12px; font-weight: 800; letter-spacing: .08em; }
.section-head h2 { margin-bottom: 0; }
.mode-badge { padding: 7px 10px; border-radius: 999px; font-size: 12px; font-weight: 800; white-space: nowrap; }
.mode-badge.enabled { color: #167647; background: #e9f8ef; }
.mode-badge.disabled { color: #7a4d0b; background: #fff3d6; }
.notice { display: grid; gap: 7px; padding: 18px; border: 1px solid #dbe3f1; border-radius: 12px; background: #f7f9fc; }
.notice span { color: #667085; }
.toolbar { margin: 20px 0 12px; }
.backup-list { display: grid; gap: 9px; }
.backup-list button { width: 100%; color: #344054; border: 1px solid #e4e9f2; background: #fbfcfe; text-align: left; }
.backup-list button.selected { border-color: #4461d8; background: #f1f4ff; }
.backup-list button > div { display: flex; flex-wrap: wrap; gap: 6px 12px; }
.backup-list button > div:first-child { flex-direction: column; align-items: flex-start; }
.backup-list span { color: #667085; font-size: 12px; }
.restore-panel { display: grid; gap: 12px; margin-top: 18px; padding: 18px; border: 1px solid #f0c7c7; border-radius: 12px; background: #fff8f8; }
.restore-panel p { margin: 0; }
.restore-panel label { display: grid; gap: 7px; color: #475467; font-size: 13px; font-weight: 700; }
.restore-panel > div { justify-content: flex-start; }
.secondary { color: #344054; background: #eef2f8; }
.danger-button { color: #fff; background: #b43232; }
code { padding: 2px 5px; border-radius: 5px; background: #e9edf5; }

@media (max-width: 720px) {
  .backup-list button { align-items: flex-start; flex-direction: column; }
}
</style>
