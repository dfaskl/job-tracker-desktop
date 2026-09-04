<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, apiCached, ApiError } from './api'
import { useJobTrackerStore, type BusinessData } from './jobTrackerStore'

type CompanyLink = { company: string; url: string }
type CompanyLinkResponse = { items: CompanyLink[]; updatedAt: string }
type SandboxStatus = { enabled: boolean; configured: boolean; isolated: boolean; message: string }
type ImportResult = { applicationCount: number; eventCount: number; currentUpdatedAt: string }

const store = useJobTrackerStore()
const links = ref<CompanyLink[]>([])
const linksUpdatedAt = ref('')
const linkQuery = ref('')
const newCompany = ref('')
const newUrl = ref('')
const sandbox = ref<SandboxStatus | null>(null)
const importConfirmation = ref('')
const clearConfirmation = ref('')
const importFileName = ref('')
const importData = ref<BusinessData | null>(null)
const loading = ref(false)
const message = ref('')
const error = ref('')

const filteredLinks = computed(() => {
  const q = linkQuery.value.trim().toLowerCase()
  if (!q) return links.value
  return links.value.filter((item) => item.company.toLowerCase().includes(q) || item.url.toLowerCase().includes(q))
})

const exportName = computed(() => {
  const email = store.user.value?.email.replace(/[^a-z0-9._-]+/gi, '_') || 'job-tracker'
  const day = new Date().toISOString().slice(0, 10)
  return `${email}-business-data-${day}.json`
})

onMounted(() => {
  loadLinks()
  loadSandbox()
})

async function loadLinks() {
  error.value = ''
  try {
    const result = await apiCached<CompanyLinkResponse>('/api/poc/company-links')
    links.value = result.items || []
    linksUpdatedAt.value = result.updatedAt || ''
  } catch (cause) {
    if (cause instanceof ApiError && cause.status === 401) return
    error.value = cause instanceof Error ? cause.message : '读取公司链接失败'
  }
}

async function loadSandbox() {
  try {
    sandbox.value = await apiCached<SandboxStatus>('/api/poc/backup-sandbox/status')
  } catch (cause) {
    sandbox.value = { enabled: false, configured: false, isolated: false, message: cause instanceof Error ? cause.message : '无法读取数据状态' }
  }
}

async function saveLinks(next: CompanyLink[]) {
  loading.value = true; error.value = ''; message.value = ''
  try {
    const result = await api<CompanyLinkResponse>('/api/poc/company-links', { method: 'POST', body: JSON.stringify({ items: next }) })
    links.value = result.items || []; linksUpdatedAt.value = result.updatedAt || ''; message.value = '公司官网库已保存'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '保存公司链接失败' }
  finally { loading.value = false }
}
async function addLink() {
  const company = newCompany.value.trim(), url = newUrl.value.trim()
  if (!company || !/^https?:\/\//i.test(url)) { error.value = '请填写公司名称和以 http:// 或 https:// 开头的网址'; return }
  const next = links.value.filter(item => item.company.trim().toLowerCase() !== company.toLowerCase())
  await saveLinks([...next, { company, url }].sort((a,b)=>a.company.localeCompare(b.company,'zh-CN')))
  newCompany.value = ''; newUrl.value = ''
}
async function removeLink(item: CompanyLink) {
  if (confirm(`确认删除“${item.company}”的官网链接吗？`)) await saveLinks(links.value.filter(value => value !== item))
}
function exportData() {
  message.value = ''
  error.value = ''
  const payload = JSON.stringify(store.data.value, null, 2)
  const blob = new Blob([payload], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = exportName.value
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
  message.value = `已导出 ${store.applications.value.length} 条投递、${store.events.value.length} 项日程`
}

async function chooseImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  importData.value = null
  importFileName.value = file?.name || ''
  importConfirmation.value = ''
  message.value = ''
  error.value = ''
  if (!file) return
  try {
    const text = await file.text()
    const parsed = JSON.parse(text) as BusinessData
    if (!Array.isArray(parsed.applications) || !Array.isArray(parsed.events)) throw new Error('JSON 缺少 applications 或 events 数组')
    importData.value = parsed
    message.value = `已读取文件：${parsed.applications.length} 条投递、${parsed.events.length} 项日程`
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '文件解析失败'
  }
}

async function importIntoSandbox() {
  if (!sandbox.value?.enabled || !importData.value || importConfirmation.value !== '导入') return
  loading.value = true
  message.value = ''
  error.value = ''
  try {
    const result = await api<ImportResult>('/api/poc/backup-sandbox/import', {
      method: 'POST',
      body: JSON.stringify({ data: importData.value, confirmation: importConfirmation.value })
    })
    message.value = `已导入数据：${result.applicationCount} 条投递、${result.eventCount} 项日程`
    importConfirmation.value = ''
    await store.refresh()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '导入失败'
  } finally {
    loading.value = false
  }
}

async function clearSandbox() {
  if (!sandbox.value?.enabled || clearConfirmation.value !== '清空') return
  loading.value = true
  message.value = ''
  error.value = ''
  try {
    await api<ImportResult>('/api/poc/backup-sandbox/clear', {
      method: 'POST',
      body: JSON.stringify({ confirmation: clearConfirmation.value })
    })
    message.value = '已清空业务数据'
    clearConfirmation.value = ''
    await store.refresh()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '清空失败'
  } finally {
    loading.value = false
  }
}

function formatDate(value: string) {
  if (!value) return '尚无更新时间'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}
</script>

<template>
  <section class="card data-card">
    <div class="section-head">
      <div><span class="section-kicker">数据管理</span><h2>公司官网库与业务数据</h2></div>
      <button class="secondary" type="button" @click="loadLinks">刷新链接</button>
    </div>

    <div class="metrics-row">
      <div><strong>{{ links.length }}</strong><span>公司链接</span></div>
      <div><strong>{{ store.applications.value.length }}</strong><span>投递记录</span></div>
      <div><strong>{{ store.events.value.length }}</strong><span>日程事件</span></div>
    </div>

    <div class="link-panel">
      <div class="toolbar">
        <label><span>搜索公司或链接</span><input v-model="linkQuery" placeholder="公司名称 / careers URL" /></label>
        <small>{{ formatDate(linksUpdatedAt) }}</small>
      </div>
      <form v-if="sandbox?.enabled" class="link-editor" @submit.prevent="addLink"><input v-model="newCompany" placeholder="公司名称" required maxlength="120" /><input v-model="newUrl" type="url" placeholder="https://careers.example.com" required /><button :disabled="loading">添加 / 更新</button></form>
      <div v-if="filteredLinks.length" class="link-list">
        <article v-for="item in filteredLinks" :key="`${item.company}:${item.url}`"><a :href="item.url" target="_blank" rel="noreferrer"><strong>{{ item.company }}</strong><span>{{ item.url }}</span></a><button v-if="sandbox?.enabled" class="link-delete" @click="removeLink(item)">删除</button></article>
      </div>
      <div v-else class="notice">当前账号没有可展示的公司链接。</div>
    </div>

    <div class="data-grid">
      <div class="tool-box">
        <h3>导出数据</h3>
        <p>导出当前登录账号的完整业务数据，已去除旧系统保存的 AI 密钥字段。</p>
        <button type="button" :disabled="!store.user.value" @click="exportData">导出数据</button>
      </div>

      <div class="tool-box">
        <h3>导入数据</h3>
        <p>{{ sandbox?.enabled ? '导入前会自动备份当前数据。' : (sandbox?.message || '正在读取数据状态') }}</p>
        <input type="file" accept="application/json,.json" :disabled="!sandbox?.enabled" @change="chooseImportFile" />
        <label><span>输入“导入”确认</span><input v-model="importConfirmation" :disabled="!sandbox?.enabled || !importData" placeholder="导入" /></label>
        <button type="button" :disabled="loading || !sandbox?.enabled || !importData || importConfirmation !== '导入'" @click="importIntoSandbox">确认导入</button>
        <small v-if="importFileName">{{ importFileName }}</small>
      </div>

      <div class="tool-box danger-zone">
        <h3>清空业务数据</h3>
        <p>{{ sandbox?.enabled ? '清空前会自动备份当前数据。' : '当前数据暂时不可修改。' }}</p>
        <label><span>输入“清空”确认</span><input v-model="clearConfirmation" :disabled="!sandbox?.enabled" placeholder="清空" /></label>
        <button type="button" class="danger-button" :disabled="loading || !sandbox?.enabled || clearConfirmation !== '清空'" @click="clearSandbox">清空全部数据</button>
      </div>
    </div>

    <p v-if="message" class="success">{{ message }}</p>
    <p v-if="error" class="danger">{{ error }}</p>
  </section>
</template>

<style scoped>
.data-card { display: grid; gap: 18px; }
.section-head, .toolbar { display: flex; align-items: center; justify-content: space-between; gap: 14px; }
.section-kicker { display: block; margin-bottom: 5px; color: #4461d8; font-size: 12px; font-weight: 800; letter-spacing: .08em; }
.section-head h2 { margin: 0; }
.secondary { color: #344054; background: #eef2f8; }
.metrics-row { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.metrics-row div { padding: 14px; border: 1px solid #e4e9f2; border-radius: 8px; background: #fbfcfe; }
.metrics-row strong { display: block; font-size: 24px; }
.metrics-row span, .toolbar small, .tool-box small { color: #667085; font-size: 12px; }
.link-panel { display: grid; gap: 12px; }
.toolbar label, .tool-box label { display: grid; gap: 7px; color: #475467; font-size: 13px; font-weight: 700; }
.toolbar label { min-width: min(420px, 100%); }
.link-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 9px; }
.link-editor { display: grid; grid-template-columns: 1fr 2fr auto; gap: 9px; }
.link-list article { display: flex; align-items: center; gap: 8px; min-width: 0; padding: 13px; border: 1px solid #e4e9f2; border-radius: 8px; background: #fbfcfe; }
.link-list a { display: grid; flex: 1; gap: 5px; min-width: 0; color: inherit; text-decoration: none; }
.link-list article:hover { border-color: #4461d8; background: #f4f6ff; }
.link-delete { padding: 7px 9px; color: #a52d2d; background: #fceaea; }
.link-list span { overflow: hidden; color: #667085; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.notice { padding: 16px; border: 1px solid #dbe3f1; border-radius: 8px; background: #f7f9fc; color: #667085; }
.data-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.tool-box { display: grid; align-content: start; gap: 12px; padding: 16px; border: 1px solid #e4e9f2; border-radius: 8px; background: #fbfcfe; }
.tool-box h3 { margin: 0; font-size: 16px; }
.tool-box p { margin: 0; }
.tool-box input[type="file"] { padding: 10px; background: #fff; }
.danger-zone { border-color: #f0c7c7; background: #fff8f8; }
.danger-button { color: #fff; background: #b43232; }

@media (max-width: 900px) {
  .data-grid, .link-list, .link-editor { grid-template-columns: 1fr; }
}

@media (max-width: 640px) {
  .section-head, .toolbar { align-items: stretch; flex-direction: column; }
  .metrics-row { grid-template-columns: 1fr; }
}
</style>
