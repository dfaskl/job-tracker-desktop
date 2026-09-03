<script setup lang="ts">
import { onMounted, ref } from 'vue'

type AdminStatus = { enabled: boolean; requested: boolean; sandboxEnabled: boolean; message: string }
type Summary = {
  totalUsers: number
  enabledUsers: number
  totalApplications: number
  activeSessions: number
  configuredApiKeys: number
  registrationOpen: boolean
}
type User = {
  id: string
  email: string
  isAdmin: boolean
  disabled: boolean
  createdAt: string
  lastLoginAt: string
  applicationCount: number
  eventCount: number
  hasApiKey: boolean
}
type Audit = { id: string; action: string; targetEmail: string; createdAt: string }
type ApplicationDetail = {
  id: string
  company: string
  position: string
  stage: string
  status: string
  flow: { at: string; title: string }[]
}
type UserDetails = {
  user: { id: string; email: string }
  applications: ApplicationDetail[]
  totalApplications: number
  truncated: boolean
}
type Overview = {
  currentUser: { id: string; email: string }
  summary: Summary
  users: User[]
  usersTruncated: boolean
  audit: Audit[]
}

const status = ref<AdminStatus | null>(null)
const overview = ref<Overview | null>(null)
const details = ref<Record<string, UserDetails>>({})
const expanded = ref<Record<string, boolean>>({})
const loading = ref(false)
const busyUser = ref('')
const error = ref('')
const message = ref('')

const actionLabels: Record<string, string> = {
  'disable-user': '停用了账号',
  'enable-user': '启用了账号',
  'delete-user': '永久删除了账号',
  'open-registration': '开放了用户注册',
  'close-registration': '关闭了用户注册',
  'view-user-details': '查看了用户数据'
}

onMounted(checkStatus)

async function requestJson(url: string, init?: RequestInit) {
  const response = await fetch(url, { cache: 'no-store', ...init })
  const body = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(body.message || '操作失败')
  return body
}

async function checkStatus() {
  loading.value = true
  error.value = ''
  try {
    status.value = await requestJson('/api/poc/admin-sandbox/status') as AdminStatus
    if (status.value.enabled) await loadOverview()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '管理员沙箱检查失败'
  } finally {
    loading.value = false
  }
}

async function loadOverview() {
  overview.value = await requestJson('/api/poc/admin-sandbox/overview') as Overview
}

async function refresh() {
  loading.value = true
  error.value = ''
  message.value = ''
  try {
    await loadOverview()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '刷新失败'
  } finally {
    loading.value = false
  }
}

async function toggleRegistration() {
  if (!overview.value) return
  loading.value = true
  error.value = ''
  message.value = ''
  const enabled = !overview.value.summary.registrationOpen
  try {
    await requestJson('/api/poc/admin-sandbox/settings/registration', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ enabled })
    })
    await loadOverview()
    message.value = enabled ? '独立测试库已开放注册' : '独立测试库已关闭注册'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '修改注册状态失败'
  } finally {
    loading.value = false
  }
}

async function toggleDetails(user: User) {
  expanded.value[user.id] = !expanded.value[user.id]
  if (!expanded.value[user.id] || details.value[user.id]) return
  busyUser.value = user.id
  error.value = ''
  try {
    details.value[user.id] = await requestJson(`/api/poc/admin-sandbox/users/${user.id}/details`) as UserDetails
    await loadOverview()
  } catch (cause) {
    expanded.value[user.id] = false
    error.value = cause instanceof Error ? cause.message : '读取用户详情失败'
  } finally {
    busyUser.value = ''
  }
}

async function setDisabled(user: User) {
  busyUser.value = user.id
  error.value = ''
  message.value = ''
  try {
    await requestJson(`/api/poc/admin-sandbox/users/${user.id}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ disabled: !user.disabled })
    })
    await loadOverview()
    message.value = user.disabled ? '测试账号已重新启用' : '测试账号已停用，测试库会话已撤销'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '修改账号状态失败'
  } finally {
    busyUser.value = ''
  }
}

async function deleteUser(user: User) {
  const confirmEmail = window.prompt(`此操作只针对独立测试数据库。请输入 ${user.email} 确认永久删除：`, '')
  if (confirmEmail === null) return
  busyUser.value = user.id
  error.value = ''
  message.value = ''
  try {
    await requestJson(`/api/poc/admin-sandbox/users/${user.id}`, {
      method: 'DELETE',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ confirmEmail })
    })
    delete details.value[user.id]
    await loadOverview()
    message.value = '测试用户及其测试库数据已删除'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '删除测试用户失败'
  } finally {
    busyUser.value = ''
  }
}

function formatDate(value: string) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}
</script>

<template>
  <section class="card admin-card">
    <div class="section-head">
      <div><span class="section-kicker">第 6 阶段</span><h2>管理员后台迁移沙箱</h2></div>
      <span :class="['mode-badge', status?.enabled ? 'enabled' : 'disabled']">{{ status?.enabled ? '测试后台已开启' : '默认关闭' }}</span>
    </div>
    <p>管理员操作只能作用于独立测试数据库；页面不会显示密码、API Key 明文或其他秘密信息。</p>

    <div v-if="status && !status.enabled" class="notice">
      <strong>{{ status.message }}</strong>
      <span>需同时开启独立测试写入和 <code>POC_ADMIN_ENABLED=true</code>，生产数据库地址会被拒绝。</span>
    </div>

    <template v-else-if="overview">
      <div class="toolbar">
        <span>当前测试管理员：<strong>{{ overview.currentUser.email }}</strong></span>
        <button class="secondary" :disabled="loading" @click="refresh">{{ loading ? '刷新中…' : '刷新概览' }}</button>
      </div>

      <div class="summary-grid">
        <article><span>用户</span><strong>{{ overview.summary.enabledUsers }} / {{ overview.summary.totalUsers }}</strong></article>
        <article><span>投递记录</span><strong>{{ overview.summary.totalApplications }}</strong></article>
        <article><span>有效会话</span><strong>{{ overview.summary.activeSessions }}</strong></article>
        <article><span>已配 API Key</span><strong>{{ overview.summary.configuredApiKeys }}</strong></article>
      </div>

      <div class="registration-row">
        <div><strong>测试库注册入口</strong><span>{{ overview.summary.registrationOpen ? '当前开放' : '当前关闭' }}</span></div>
        <button :disabled="loading" @click="toggleRegistration">{{ overview.summary.registrationOpen ? '关闭注册' : '开放注册' }}</button>
      </div>

      <h3>用户管理</h3>
      <p v-if="overview.usersTruncated" class="hint">用户列表仅显示前 500 个账号，统计数字仍覆盖全部账号。</p>
      <div class="user-list">
        <article v-for="user in overview.users" :key="user.id" class="user-card">
          <div class="user-head">
            <div><strong>{{ user.email }}</strong><span>{{ user.isAdmin ? '管理员' : (user.disabled ? '已停用' : '正常') }}</span></div>
            <div class="actions">
              <button class="secondary" :disabled="busyUser === user.id" @click="toggleDetails(user)">{{ expanded[user.id] ? '收起' : '查看流程' }}</button>
              <button v-if="!user.isAdmin" class="secondary" :disabled="busyUser === user.id" @click="setDisabled(user)">{{ user.disabled ? '启用' : '停用' }}</button>
              <button v-if="!user.isAdmin" class="danger-button" :disabled="busyUser === user.id" @click="deleteUser(user)">删除</button>
            </div>
          </div>
          <div class="user-metrics">
            <span>投递 {{ user.applicationCount }}</span><span>日程 {{ user.eventCount }}</span><span>API Key {{ user.hasApiKey ? '已配置' : '未配置' }}</span><span>最近登录 {{ formatDate(user.lastLoginAt) }}</span>
          </div>
          <div v-if="expanded[user.id]" class="details">
            <p v-if="busyUser === user.id">正在读取测试数据…</p>
            <template v-else-if="details[user.id]">
              <p v-if="details[user.id].truncated" class="hint">共 {{ details[user.id].totalApplications }} 条，仅显示前 500 条。</p>
              <p v-if="!details[user.id].applications.length">该用户暂无投递记录。</p>
              <article v-for="application in details[user.id].applications" :key="application.id" class="application-row">
                <div><strong>{{ application.company || '未填写公司' }} · {{ application.position || '未填写岗位' }}</strong><span>{{ application.stage || '—' }} / {{ application.status || '—' }}</span></div>
                <ol><li v-for="step in application.flow" :key="`${step.at}-${step.title}`"><time>{{ step.at || '时间未知' }}</time>{{ step.title }}</li></ol>
              </article>
            </template>
          </div>
        </article>
      </div>

      <h3>最近 30 条操作记录</h3>
      <div class="audit-list">
        <div v-for="item in overview.audit" :key="item.id"><span>{{ actionLabels[item.action] || item.action }} · {{ item.targetEmail }}</span><time>{{ formatDate(item.createdAt) }}</time></div>
        <p v-if="!overview.audit.length">暂无管理操作。</p>
      </div>
    </template>

    <p v-else-if="status?.enabled && loading">正在读取管理员概览…</p>
    <p v-if="message" class="success">{{ message }}</p>
    <p v-if="error" class="danger">{{ error }}</p>
  </section>
</template>

<style scoped>
.section-head, .toolbar, .registration-row, .user-head, .actions, .user-metrics, .audit-list div { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.section-kicker { display: block; margin-bottom: 5px; color: #4461d8; font-size: 12px; font-weight: 800; letter-spacing: .08em; }
.section-head h2 { margin-bottom: 0; }
.mode-badge { padding: 7px 10px; border-radius: 999px; font-size: 12px; font-weight: 800; white-space: nowrap; }
.mode-badge.enabled { color: #167647; background: #e9f8ef; }
.mode-badge.disabled { color: #7a4d0b; background: #fff3d6; }
.notice { display: grid; gap: 7px; padding: 18px; border: 1px solid #dbe3f1; border-radius: 12px; background: #f7f9fc; }
.notice span, .hint { color: #667085; }
code { padding: 2px 5px; border-radius: 5px; background: #e9edf5; }
.toolbar { margin: 22px 0 14px; }
.secondary { color: #344054; background: #eef2f8; }
.danger-button { background: #bd3434; }
.summary-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.summary-grid article { display: grid; gap: 7px; padding: 15px; border: 1px solid #e4e9f2; border-radius: 12px; }
.summary-grid span, .registration-row span, .user-head span { color: #667085; font-size: 13px; }
.summary-grid strong { font-size: 22px; }
.registration-row { margin: 16px 0 24px; padding: 16px; border-radius: 12px; background: #f7f9fc; }
.registration-row div, .user-head > div, .application-row > div { display: grid; gap: 4px; }
h3 { margin: 26px 0 12px; font-size: 16px; }
.user-list { display: grid; gap: 10px; }
.user-card { padding: 16px; border: 1px solid #e4e9f2; border-radius: 12px; }
.actions { justify-content: flex-end; }
.actions button { padding: 8px 11px; font-size: 12px; }
.user-metrics { justify-content: flex-start; flex-wrap: wrap; margin-top: 12px; color: #667085; font-size: 12px; }
.details { margin-top: 16px; padding-top: 12px; border-top: 1px solid #edf0f5; }
.application-row { margin-top: 10px; padding: 12px; border-radius: 10px; background: #f7f9fc; }
.application-row ol { margin: 10px 0 0; padding-left: 22px; }
.application-row li { margin: 6px 0; color: #475467; }
.application-row time { display: inline-block; min-width: 145px; color: #667085; font-size: 12px; }
.audit-list { display: grid; gap: 0; }
.audit-list div { padding: 11px 0; border-top: 1px solid #edf0f5; }
.audit-list time { color: #667085; font-size: 12px; }

@media (max-width: 720px) {
  .summary-grid { grid-template-columns: 1fr 1fr; }
  .user-head, .toolbar, .registration-row { align-items: flex-start; flex-direction: column; }
  .actions { flex-wrap: wrap; justify-content: flex-start; }
  .application-row time { display: block; min-width: 0; }
}
</style>
