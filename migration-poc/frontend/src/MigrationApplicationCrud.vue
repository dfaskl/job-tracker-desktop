<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

type SandboxStatus = {
  enabled: boolean
  configured: boolean
  isolated: boolean
  message: string
}

type Application = {
  id: string
  company: string
  position: string
  city: string
  channel: string
  appliedDate: string
  stage: string
  status: string
  notes: string
  createdAt: string
  updatedAt: string
}

type ApplicationForm = Omit<Application, 'id' | 'createdAt' | 'updatedAt'>

const stages = ['已投递', '测评', '笔试', '面试', 'Offer', '已结束']
const statuses = ['等待结果', '已通过', '未通过', '已放弃', '已结束']
const sandbox = ref<SandboxStatus | null>(null)
const applications = ref<Application[]>([])
const total = ref(0)
const editing = ref<Application | null>(null)
const loading = ref(false)
const message = ref('')
const error = ref('')
const form = reactive<ApplicationForm>(emptyForm())

onMounted(checkSandbox)

function emptyForm(): ApplicationForm {
  return {
    company: '',
    position: '',
    city: '',
    channel: '',
    appliedDate: new Date().toISOString().slice(0, 10),
    stage: '已投递',
    status: '等待结果',
    notes: ''
  }
}

async function requestJson(url: string, init?: RequestInit) {
  const response = await fetch(url, { cache: 'no-store', ...init })
  const body = await response.json().catch(() => ({}))
  return { response, body }
}

async function checkSandbox() {
  loading.value = true
  error.value = ''
  try {
    const result = await requestJson('/api/poc/application-sandbox/status')
    if (!result.response.ok) throw new Error(result.body.message || '无法检查测试写入状态')
    sandbox.value = result.body as SandboxStatus
    if (sandbox.value.enabled) await loadApplications()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '检查失败'
  } finally {
    loading.value = false
  }
}

async function loadApplications() {
  const result = await requestJson('/api/poc/application-sandbox/applications')
  if (!result.response.ok) throw new Error(
    result.response.status === 401 ? '请先在上方登录旧账号，再重新检查' : (result.body.message || '读取测试数据失败')
  )
  applications.value = result.body.applications as Application[]
  total.value = Number(result.body.total || 0)
}

function edit(item: Application) {
  editing.value = item
  Object.assign(form, {
    company: item.company,
    position: item.position,
    city: item.city,
    channel: item.channel,
    appliedDate: item.appliedDate,
    stage: item.stage || '已投递',
    status: item.status || '等待结果',
    notes: item.notes
  })
  message.value = ''
  error.value = ''
}

function resetForm() {
  editing.value = null
  Object.assign(form, emptyForm())
}

async function save() {
  loading.value = true
  error.value = ''
  message.value = ''
  try {
    const current = editing.value
    const result = await requestJson(
      current
        ? `/api/poc/application-sandbox/applications/${encodeURIComponent(current.id)}`
        : '/api/poc/application-sandbox/applications',
      {
        method: current ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...form, expectedUpdatedAt: current?.updatedAt || '' })
      }
    )
    if (!result.response.ok) throw new Error(result.body.message || '保存失败')
    message.value = current ? '测试记录已更新，并已生成变更前备份' : '测试记录已新增，并已生成变更前备份'
    resetForm()
    await loadApplications()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '保存失败'
  } finally {
    loading.value = false
  }
}

async function remove(item: Application) {
  if (!window.confirm(`确认从测试库删除“${item.company} / ${item.position}”吗？`)) return
  loading.value = true
  error.value = ''
  message.value = ''
  try {
    const result = await requestJson(
      `/api/poc/application-sandbox/applications/${encodeURIComponent(item.id)}`,
      {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ expectedUpdatedAt: item.updatedAt })
      }
    )
    if (!result.response.ok) throw new Error(result.body.message || '删除失败')
    message.value = '测试记录及其关联日程已删除，并已生成变更前备份'
    if (editing.value?.id === item.id) resetForm()
    await loadApplications()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '删除失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="card sandbox-card">
    <div class="section-head">
      <div>
        <span class="section-kicker">第 1 阶段</span>
        <h2>职位申请 CRUD 迁移沙箱</h2>
      </div>
      <span :class="['mode-badge', sandbox?.enabled ? 'enabled' : 'disabled']">
        {{ sandbox?.enabled ? '独立测试库' : '安全关闭' }}
      </span>
    </div>

    <p>该区域只允许写入单独配置的测试数据库；生产数据库连接仍保持只读。</p>
    <div v-if="sandbox && !sandbox.enabled" class="notice">
      <strong>{{ sandbox.message }}</strong>
      <span>准备好数据副本后，再配置 <code>POC_WRITE_DATABASE_URL</code> 和 <code>POC_WRITE_ENABLED=true</code>。</span>
    </div>

    <template v-else-if="sandbox?.enabled">
      <form class="crud-form" @submit.prevent="save">
        <label><span>公司 *</span><input v-model="form.company" maxlength="120" required /></label>
        <label><span>岗位 *</span><input v-model="form.position" maxlength="160" required /></label>
        <label><span>城市</span><input v-model="form.city" maxlength="120" /></label>
        <label><span>渠道</span><input v-model="form.channel" maxlength="80" /></label>
        <label><span>投递日期</span><input v-model="form.appliedDate" type="date" /></label>
        <label><span>阶段</span><select v-model="form.stage"><option v-for="item in stages" :key="item">{{ item }}</option></select></label>
        <label><span>状态</span><select v-model="form.status"><option v-for="item in statuses" :key="item">{{ item }}</option></select></label>
        <label class="wide"><span>备注</span><textarea v-model="form.notes" maxlength="4000" rows="3" /></label>
        <div class="form-actions wide">
          <button :disabled="loading">{{ loading ? '处理中…' : (editing ? '保存修改' : '新增测试记录') }}</button>
          <button v-if="editing" type="button" class="secondary" @click="resetForm">取消编辑</button>
          <button type="button" class="secondary" :disabled="loading" @click="checkSandbox">重新检查</button>
        </div>
      </form>

      <p v-if="message" class="success">{{ message }}</p>
      <div class="sandbox-list-head"><strong>测试库共 {{ total }} 条</strong><span>每次写入前自动备份</span></div>
      <div v-if="applications.length" class="sandbox-list">
        <article v-for="item in applications" :key="item.id">
          <div><strong>{{ item.company }}</strong><span>{{ item.position }} · {{ item.stage }} · {{ item.status }}</span></div>
          <div class="item-actions">
            <button type="button" class="secondary compact" @click="edit(item)">编辑</button>
            <button type="button" class="danger-button compact" @click="remove(item)">删除</button>
          </div>
        </article>
      </div>
      <div v-else-if="!error" class="notice">测试库中暂无投递记录。</div>
    </template>

    <p v-else>正在检查隔离写入状态…</p>
    <p v-if="error" class="danger">{{ error }}</p>
  </section>
</template>

<style scoped>
.section-head, .sandbox-list-head, .sandbox-list article, .form-actions, .item-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.section-kicker { display: block; margin-bottom: 5px; color: #4461d8; font-size: 12px; font-weight: 800; letter-spacing: .08em; }
.section-head h2 { margin-bottom: 0; }
.mode-badge { padding: 7px 10px; border-radius: 999px; font-size: 12px; font-weight: 800; white-space: nowrap; }
.mode-badge.enabled { color: #167647; background: #e9f8ef; }
.mode-badge.disabled { color: #7a4d0b; background: #fff3d6; }
.notice { display: grid; gap: 7px; padding: 18px; border: 1px solid #dbe3f1; border-radius: 12px; background: #f7f9fc; }
.notice span, .sandbox-list-head span { color: #667085; }
.crud-form { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; margin: 22px 0; }
.crud-form label { display: grid; gap: 7px; color: #475467; font-size: 13px; font-weight: 700; }
.crud-form .wide { grid-column: 1 / -1; }
select, textarea { width: 100%; padding: 12px 14px; border: 1px solid #d4dbea; border-radius: 10px; background: #fff; font: inherit; }
.form-actions { justify-content: flex-start; }
.sandbox-list-head { margin: 22px 0 12px; padding-top: 18px; border-top: 1px solid #edf0f5; }
.sandbox-list { display: grid; gap: 10px; }
.sandbox-list article { padding: 14px; border: 1px solid #e4e9f2; border-radius: 12px; background: #fbfcfe; }
.sandbox-list article > div:first-child { display: grid; gap: 4px; }
.sandbox-list article span { color: #667085; font-size: 13px; }
.secondary { color: #344054; background: #eef2f8; }
.danger-button { color: #a52d2d; background: #fceaea; }
.compact { padding: 8px 11px; }
code { padding: 2px 5px; border-radius: 5px; background: #e9edf5; }

@media (max-width: 720px) {
  .crud-form { grid-template-columns: 1fr; }
  .crud-form .wide { grid-column: auto; }
  .sandbox-list article { align-items: stretch; flex-direction: column; }
  .item-actions { justify-content: flex-start; }
}
</style>
