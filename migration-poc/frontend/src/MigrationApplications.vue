<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

type RuntimeStatus = {
  databaseConfigured: boolean
  sessionAuthConfigured: boolean
}

type User = {
  id: string
  email: string
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
  updatedAt: string
}

type ApplicationsResponse = {
  user: User
  applications: Application[]
  total: number
  truncated: boolean
  readOnly: boolean
}

const checking = ref(true)
const available = ref(false)
const user = ref<User | null>(null)
const email = ref('')
const password = ref('')
const query = ref('')
const applications = ref<Application[]>([])
const total = ref(0)
const truncated = ref(false)
const submitting = ref(false)
const error = ref('')

const visibleApplications = computed(() => {
  const keyword = query.value.trim().toLocaleLowerCase('zh-CN')
  if (!keyword) return applications.value
  return applications.value.filter((item) =>
    `${item.company} ${item.position} ${item.city} ${item.stage} ${item.status}`
      .toLocaleLowerCase('zh-CN')
      .includes(keyword)
  )
})

onMounted(checkSession)

async function requestJson(url: string, init?: RequestInit) {
  const response = await fetch(url, { cache: 'no-store', ...init })
  const body = await response.json().catch(() => ({}))
  return { response, body }
}

async function checkSession() {
  checking.value = true
  error.value = ''
  try {
    const statusResult = await requestJson('/api/poc/status')
    const status = statusResult.body as RuntimeStatus
    available.value = Boolean(status.databaseConfigured && status.sessionAuthConfigured)
    if (!available.value) return

    const sessionResult = await requestJson('/api/poc/auth/session')
    if (sessionResult.response.ok) {
      user.value = sessionResult.body.user as User
      await loadApplications()
    }
  } catch {
    error.value = '无法读取 Java 服务状态，请稍后重试'
  } finally {
    checking.value = false
  }
}

async function login() {
  submitting.value = true
  error.value = ''
  try {
    const result = await requestJson('/api/poc/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: email.value, password: password.value })
    })
    password.value = ''
    if (!result.response.ok) throw new Error(result.body.message || '登录失败')
    user.value = result.body.user as User
    await loadApplications()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '登录失败'
  } finally {
    submitting.value = false
  }
}

async function loadApplications() {
  const result = await requestJson('/api/poc/applications')
  if (result.response.status === 401) {
    user.value = null
    applications.value = []
    throw new Error('登录已过期，请重新登录')
  }
  if (!result.response.ok) throw new Error(result.body.message || '读取投递记录失败')
  const body = result.body as ApplicationsResponse
  applications.value = body.applications
  total.value = body.total
  truncated.value = body.truncated
}

async function logout() {
  await requestJson('/api/poc/auth/logout', { method: 'POST' })
  user.value = null
  applications.value = []
  total.value = 0
  query.value = ''
  error.value = ''
}
</script>

<template>
  <section class="card migration-data">
    <div class="section-head">
      <div>
        <span class="section-kicker">真实业务切片</span>
        <h2>旧账号登录与职位列表</h2>
      </div>
      <span class="readonly-badge">只读模式</span>
    </div>

    <p>Java 验证现有账号密码，并按当前用户读取 PostgreSQL 中的投递记录；不会新增、修改或删除数据。</p>

    <p v-if="checking">正在检查只读登录服务…</p>

    <div v-else-if="!available" class="notice">
      <strong>等待数据库连接配置</strong>
      <span>请在 Render 为 Demo 配置现有数据库的 <code>DATABASE_URL</code>。会话密钥由 Blueprint 自动生成。</span>
    </div>

    <form v-else-if="!user" class="login-form" @submit.prevent="login">
      <label>
        <span>现有账号邮箱</span>
        <input v-model="email" type="email" autocomplete="username" required placeholder="name@example.com" />
      </label>
      <label>
        <span>现有账号密码</span>
        <input v-model="password" type="password" autocomplete="current-password" required placeholder="输入旧系统密码" />
      </label>
      <button :disabled="submitting">{{ submitting ? '验证中…' : '登录并读取职位' }}</button>
    </form>

    <div v-else>
      <div class="signed-in">
        <div><small>当前账号</small><strong>{{ user.email }}</strong></div>
        <button type="button" class="secondary" @click="logout">退出只读登录</button>
      </div>

      <div class="list-toolbar">
        <div><strong>{{ total }}</strong> 条投递记录<span v-if="truncated">（显示最近 500 条）</span></div>
        <input v-model="query" type="search" placeholder="搜索公司、岗位或状态" />
      </div>

      <div v-if="visibleApplications.length" class="application-list">
        <article v-for="item in visibleApplications" :key="item.id" class="application-item">
          <div class="application-title">
            <div><strong>{{ item.company || '未填写公司' }}</strong><span>{{ item.position || '未填写岗位' }}</span></div>
            <span class="stage">{{ item.stage || '未标记阶段' }}</span>
          </div>
          <div class="application-meta">
            <span>{{ item.status || '未标记状态' }}</span>
            <span v-if="item.city">{{ item.city }}</span>
            <span v-if="item.channel">{{ item.channel }}</span>
            <span v-if="item.appliedDate">投递于 {{ item.appliedDate }}</span>
          </div>
        </article>
      </div>
      <div v-else class="empty-result">{{ query ? '没有符合搜索条件的记录' : '该账号暂无投递记录' }}</div>
    </div>

    <p v-if="error" class="danger">{{ error }}</p>
  </section>
</template>

<style scoped>
.section-head, .signed-in, .list-toolbar, .application-title { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.section-kicker { display: block; margin-bottom: 5px; color: #4461d8; font-size: 12px; font-weight: 800; letter-spacing: .08em; }
.section-head h2 { margin-bottom: 0; }
.readonly-badge { padding: 7px 10px; border-radius: 999px; color: #167647; background: #e9f8ef; font-size: 12px; font-weight: 800; white-space: nowrap; }
.notice, .empty-result { padding: 18px; border: 1px solid #dbe3f1; border-radius: 12px; background: #f7f9fc; }
.notice { display: grid; gap: 7px; }
.notice span, small { color: #667085; }
.login-form { display: grid; grid-template-columns: 1fr 1fr auto; align-items: end; gap: 12px; margin-top: 20px; }
.login-form label { display: grid; gap: 7px; color: #475467; font-size: 13px; font-weight: 700; }
.signed-in { margin: 20px 0; padding: 14px 0; border-top: 1px solid #edf0f5; border-bottom: 1px solid #edf0f5; }
.signed-in div { display: grid; gap: 4px; }
.secondary { color: #344054; background: #eef2f8; }
.list-toolbar { margin: 18px 0 12px; color: #475467; }
.list-toolbar input { max-width: 320px; }
.application-list { display: grid; gap: 10px; }
.application-item { padding: 16px; border: 1px solid #e4e9f2; border-radius: 13px; background: #fbfcfe; }
.application-title div { display: grid; gap: 4px; }
.application-title div span { color: #475467; }
.stage { padding: 5px 9px; border-radius: 999px; color: #3d55bd; background: #edf1ff; font-size: 12px; font-weight: 800; }
.application-meta { display: flex; flex-wrap: wrap; gap: 8px 14px; margin-top: 12px; color: #667085; font-size: 13px; }
code { padding: 2px 5px; border-radius: 5px; background: #e9edf5; }

@media (max-width: 720px) {
  .login-form { grid-template-columns: 1fr; }
  .list-toolbar { align-items: stretch; flex-direction: column; }
  .list-toolbar input { max-width: none; }
}
</style>
