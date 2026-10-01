<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useJobTrackerStore } from './jobTrackerStore'
import { jsonFetch } from './api'
import { formatShanghaiDateTime } from './shanghaiTime'

type SandboxStatus = { enabled: boolean; configured: boolean; isolated: boolean; message: string }
type BackupItem = { id: number; reason: string; createdAt: string; size: number; applicationCount: number; eventCount: number }

const store = useJobTrackerStore()
const sandbox = ref<SandboxStatus | null>(null)
const backups = ref<BackupItem[]>([])
const currentUpdatedAt = ref('')
const selected = ref<BackupItem | null>(null)
const confirmation = ref('')
const loading = ref(false)
const restoring = ref(false)
const message = ref('')
const error = ref('')
const backupDialog = ref<HTMLDialogElement | null>(null)
const closeButton = ref<HTMLButtonElement | null>(null)

onMounted(() => checkSandbox(false))

async function requestJson(url: string, init?: RequestInit) {
  return jsonFetch<Record<string, any>>(url, { cache: 'no-store', ...init })
}

async function checkSandbox(loadItems = true) {
  loading.value = true
  error.value = ''
  try {
    const statusResult = await requestJson('/api/poc/backup-sandbox/status')
    if (!statusResult.response.ok) throw new Error(statusResult.body.message || '无法检查备份数据安全')
    sandbox.value = statusResult.body as SandboxStatus
    if (loadItems && sandbox.value.enabled) await loadBackups()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '检查备份数据安全失败'
  } finally {
    loading.value = false
  }
}

async function loadBackups() {
  const result = await requestJson('/api/poc/backup-sandbox/backups')
  if (!result.response.ok) throw new Error(result.response.status === 401 ? '请重新登录后再读取历史备份' : (result.body.message || '读取备份失败'))
  backups.value = result.body.items as BackupItem[]
  currentUpdatedAt.value = String(result.body.currentUpdatedAt || '')
  if (selected.value) selected.value = backups.value.find(item => item.id === selected.value?.id) || null
}

async function openDialog() {
  selected.value = null
  confirmation.value = ''
  message.value = ''
  error.value = ''
  backupDialog.value?.showModal()
  await nextTick()
  closeButton.value?.focus()
  await checkSandbox(true)
}
function closeDialog() {
  if (!restoring.value) backupDialog.value?.close()
}
function closeFromBackdrop(event: MouseEvent) {
  if (event.target === backupDialog.value) closeDialog()
}
function handleCancel(event: Event) {
  if (restoring.value) event.preventDefault()
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
  restoring.value = true
  error.value = ''
  message.value = ''
  try {
    const result = await requestJson(`/api/poc/backup-sandbox/backups/${selected.value.id}/restore`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ expectedCurrentUpdatedAt: currentUpdatedAt.value, confirmation: confirmation.value })
    })
    if (!result.response.ok) throw new Error(result.body.message || '恢复失败')
    message.value = `已恢复备份 #${selected.value.id}：${result.body.applicationCount} 条投递、${result.body.eventCount} 项日程`
    selected.value = null
    confirmation.value = ''
    await Promise.all([loadBackups(), store.refresh()])
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '恢复失败'
  } finally {
    loading.value = false
    restoring.value = false
  }
}

function formatDate(value: string) { return formatShanghaiDateTime(value, {}, value) }
function formatSize(value: number) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}
</script>

<template>
  <section class="card backup-card">
    <div class="backup-icon" aria-hidden="true"><AppIcon name="database" :size="22" /></div>
    <div class="backup-copy"><span class="section-kicker">数据保护</span><h2>云端历史备份</h2><p>查看自动备份记录，需要时可将投递与日程恢复到历史版本。</p></div>
    <div class="backup-summary"><span v-if="sandbox && !sandbox.enabled">暂不可用</span><span v-else-if="backups.length">已读取 {{ backups.length }} 份</span><span v-else>最多保留 30 份</span><button type="button" @click="openDialog">查看备份</button></div>
  </section>

  <dialog ref="backupDialog" class="backup-dialog" aria-labelledby="backup-dialog-title" @click="closeFromBackdrop" @cancel="handleCancel">
    <div class="dialog-shell">
      <header>
        <div><span class="section-kicker">数据保护</span><h2 id="backup-dialog-title">云端历史备份</h2><p>选择历史版本并确认恢复。恢复前系统会自动保存当前数据。</p></div>
        <button ref="closeButton" type="button" class="modal-close icon-button" :disabled="restoring" aria-label="关闭历史备份窗口" title="关闭" @click="closeDialog"><AppIcon name="close" /></button>
      </header>

      <div v-if="selected" class="restore-panel">
        <div><strong>恢复备份 #{{ selected.id }}</strong><p>{{ formatDate(selected.createdAt) }} · {{ selected.applicationCount }} 条投递 · {{ selected.eventCount }} 项日程</p></div>
        <label for="restore-confirmation"><span>输入“恢复”确认</span><input id="restore-confirmation" v-model="confirmation" autocomplete="off" placeholder="恢复"></label>
        <div class="restore-actions"><button class="danger-button" :disabled="restoring || confirmation !== '恢复'" @click="restore">{{ restoring ? '恢复中…' : '确认恢复' }}</button><button class="secondary" :disabled="restoring" @click="selected = null">取消选择</button></div>
      </div>
      <div class="dialog-scroll">
        <div v-if="sandbox && !sandbox.enabled" class="notice"><strong>备份功能暂时不可用</strong><span>{{ sandbox.message }}</span></div>
        <template v-else-if="sandbox?.enabled">
          <div class="toolbar"><div><strong>最近 {{ backups.length }} 份备份</strong><span>最多保留最近 30 份</span></div><button class="secondary icon-button" type="button" :disabled="loading" aria-label="刷新备份列表" title="刷新列表" @click="checkSandbox(true)"><AppIcon name="refresh" /></button></div>

          <div v-if="loading && !backups.length" class="notice loading-note">正在读取历史备份…</div>
          <div v-else-if="backups.length" class="backup-list">
            <button v-for="item in backups" :key="item.id" type="button" :class="{ selected: selected?.id === item.id }" :aria-pressed="selected?.id === item.id" @click="choose(item)">
              <div><strong>#{{ item.id }} · {{ item.reason || '自动备份' }}</strong><span>{{ formatDate(item.createdAt) }}</span></div>
              <div><span>{{ item.applicationCount }} 条投递</span><span>{{ item.eventCount }} 项日程</span><span>{{ formatSize(item.size) }}</span></div>
            </button>
          </div>
          <div v-else class="notice">还没有可恢复的备份；修改投递或日程后会自动生成。</div>


          <p v-if="message" class="success" role="status">{{ message }}</p>
        </template>
        <div v-else class="notice loading-note">正在检查备份服务…</div>
        <p v-if="error" class="danger" role="alert">{{ error }}</p>
      </div>
    </div>
  </dialog>
</template>

<style scoped>
.backup-card{display:grid;grid-template-columns:48px minmax(0,1fr) auto;align-items:center;gap:14px;height:auto;min-height:0;padding:18px;overflow:visible}.backup-icon{display:grid;width:48px;height:48px;place-items:center;border:1px solid color-mix(in srgb,var(--color-primary) 28%,var(--color-border));border-radius:13px;color:var(--color-primary);background:color-mix(in srgb,var(--color-primary) 9%,var(--color-card))}.backup-copy{display:grid;min-width:0;gap:3px}.section-kicker{display:block;color:var(--color-primary);font-size:11px;font-weight:800;letter-spacing:.1em}.backup-copy h2,.backup-dialog h2{margin:0}.backup-copy p,.backup-dialog header p{margin:0;color:var(--color-muted-foreground);font-size:12px;line-height:1.55}.backup-summary{display:grid;justify-items:end;gap:8px}.backup-summary span{color:var(--color-muted-foreground);font-size:11px}.backup-summary button{min-width:104px}.backup-dialog{width:min(900px,calc(100vw - 32px));height:min(780px,88dvh);padding:0;overflow:hidden;border:1px solid var(--color-border);border-radius:18px;color:var(--color-card-foreground);background:var(--color-card);box-shadow:var(--shadow-lg)}.backup-dialog::backdrop{background:rgba(8,9,11,.68);backdrop-filter:blur(4px)}.dialog-shell{display:flex;height:100%;min-height:0;flex-direction:column}.backup-dialog header{display:flex;align-items:flex-start;justify-content:space-between;gap:20px;padding:24px 26px 18px;border-bottom:1px solid var(--color-border)}.backup-dialog header>div{display:grid;gap:5px}.modal-close{width:44px;height:44px;flex:none;padding:0}.dialog-scroll{display:flex;min-height:0;flex:1;flex-direction:column;gap:14px;overflow-y:auto;padding:18px 26px 26px;overscroll-behavior:contain;scrollbar-width:thin}.toolbar,.backup-list button,.restore-actions{display:flex;align-items:center;justify-content:space-between;gap:12px}.toolbar>div{display:grid;gap:3px}.toolbar span,.backup-list span{color:var(--color-muted-foreground);font-size:12px}.toolbar .icon-button{width:42px;height:42px;padding:0}.notice{display:grid;gap:7px;padding:18px;border:1px solid var(--color-border);border-radius:12px;background:var(--color-muted)}.notice span{color:var(--color-muted-foreground)}.loading-note{text-align:center}.backup-list{display:grid;align-content:start;gap:9px}.backup-list button{width:100%;min-height:70px;padding:12px 14px;color:var(--color-card-foreground);border:1px solid var(--color-border);background:var(--color-background);text-align:left}.backup-list button:hover{border-color:color-mix(in srgb,var(--color-primary) 45%,var(--color-border))}.backup-list button.selected{border-color:var(--color-primary);background:color-mix(in srgb,var(--color-primary) 8%,var(--color-card));box-shadow:0 0 0 1px color-mix(in srgb,var(--color-primary) 35%,transparent)}.backup-list button>div{display:flex;flex-wrap:wrap;gap:6px 12px}.backup-list button>div:first-child{flex-direction:column;align-items:flex-start}.restore-panel{display:grid;gap:13px;margin:18px 26px 0;padding:17px;border:1px solid color-mix(in srgb,var(--color-destructive) 35%,var(--color-border));border-radius:12px;background:color-mix(in srgb,var(--color-destructive) 6%,var(--color-card))}.restore-panel>div:first-child{display:grid;gap:4px}.restore-panel p{margin:0;color:var(--color-muted-foreground);font-size:12px}.restore-panel label{display:grid;gap:7px;color:var(--color-muted-foreground);font-size:13px;font-weight:700}.restore-actions{justify-content:flex-start}.secondary{color:var(--color-card-foreground);background:var(--color-muted)}.danger-button{color:#fff;background:var(--color-destructive)}.success,.danger{margin:0;padding:10px 12px;border-radius:9px}.success{color:#0f715e;background:#e9f8f3}.danger{color:#a52d2d;background:#fff1ef}:global(:root[data-theme="dark"] .success){color:#8be1c8;background:#123c35}:global(:root[data-theme="dark"] .danger){color:#ffaaa2;background:#48201f}
@media(max-width:720px){.backup-card{grid-template-columns:44px minmax(0,1fr)}.backup-icon{width:44px;height:44px}.backup-summary{grid-column:1/-1;width:100%;grid-template-columns:1fr auto;align-items:center;justify-items:start}.backup-dialog{width:calc(100vw - 20px);height:calc(100dvh - 20px)}.backup-dialog header{padding:20px 16px 15px}.dialog-scroll{padding:15px 16px 20px}.restore-panel{margin:15px 16px 0}.backup-list button{align-items:flex-start;flex-direction:column}.restore-actions{align-items:stretch;flex-direction:column}}
</style>