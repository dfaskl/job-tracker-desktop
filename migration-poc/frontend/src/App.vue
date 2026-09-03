<script setup lang="ts">
import { onMounted, ref } from 'vue'
import MigrationApplications from './MigrationApplications.vue'
import MigrationApplicationCrud from './MigrationApplicationCrud.vue'
import MigrationEventCalendar from './MigrationEventCalendar.vue'
import MigrationSessionStatus from './MigrationSessionStatus.vue'
import MigrationBackups from './MigrationBackups.vue'
import MigrationAiMail from './MigrationAiMail.vue'

type AppStatus = {
  ok: boolean
  application: string
  javaVersion: string
  databaseConfigured: boolean
  databaseProbeProtected: boolean
  frontendBundled: boolean
}

type DatabaseStatus = {
  configured: boolean
  connected: boolean
  schemaCompatible: boolean
  missingTables: string[]
  samplePresent: boolean
  businessJsonCompatible: boolean
  message: string
}

const status = ref<AppStatus | null>(null)
const database = ref<DatabaseStatus | null>(null)
const accessToken = ref('')
const loading = ref(false)
const error = ref('')

onMounted(async () => {
  const response = await fetch('/api/poc/status', { cache: 'no-store' })
  status.value = await response.json()
})

async function probeDatabase() {
  loading.value = true
  error.value = ''
  database.value = null
  try {
    const response = await fetch('/api/poc/database', {
      method: 'POST',
      headers: { 'X-POC-Token': accessToken.value }
    })
    const body = await response.json()
    if (!response.ok) throw new Error(body.message || '检查失败')
    database.value = body
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '检查失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main>
    <section class="hero">
      <span class="eyebrow">MIGRATION PROOF OF CONCEPT</span>
      <h1>Vue + Java 迁移验证</h1>
      <p>一个 JAR 同时提供 Vue 页面、Java API 和只读 PostgreSQL 兼容性检查。</p>
    </section>

    <section class="card">
      <h2>运行状态</h2>
      <dl v-if="status">
        <div><dt>Spring Boot</dt><dd class="success">运行正常</dd></div>
        <div><dt>Java</dt><dd>{{ status.javaVersion }}</dd></div>
        <div><dt>Vue 已打入 JAR</dt><dd :class="status.frontendBundled ? 'success' : 'danger'">{{ status.frontendBundled ? '是' : '否' }}</dd></div>
        <div><dt>数据库已配置</dt><dd>{{ status.databaseConfigured ? '是' : '否' }}</dd></div>
      </dl>
      <p v-else>正在读取 Java 服务状态…</p>
    </section>

    <section class="card">
      <h2>现有 PostgreSQL 只读检查</h2>
      <p>只有服务器同时配置了 DATABASE_URL 和 POC_ACCESS_TOKEN 时才会连接数据库。</p>
      <div class="form-row">
        <input v-model="accessToken" type="password" autocomplete="off" placeholder="输入 POC_ACCESS_TOKEN" />
        <button :disabled="loading || !accessToken" @click="probeDatabase">{{ loading ? '检查中…' : '开始只读检查' }}</button>
      </div>
      <p v-if="error" class="danger">{{ error }}</p>
      <dl v-if="database">
        <div><dt>连接成功</dt><dd :class="database.connected ? 'success' : 'danger'">{{ database.connected ? '是' : '否' }}</dd></div>
        <div><dt>八张核心表</dt><dd :class="database.schemaCompatible ? 'success' : 'danger'">{{ database.schemaCompatible ? '完整' : '存在缺失' }}</dd></div>
        <div><dt>业务 JSON 结构</dt><dd :class="database.businessJsonCompatible ? 'success' : 'danger'">{{ database.samplePresent ? (database.businessJsonCompatible ? '兼容' : '不兼容') : '没有可抽样数据' }}</dd></div>
      </dl>
    </section>

    <MigrationApplications />
    <MigrationApplicationCrud />
    <MigrationEventCalendar />
    <MigrationSessionStatus />
    <MigrationBackups />
    <MigrationAiMail />
  </main>
</template>
