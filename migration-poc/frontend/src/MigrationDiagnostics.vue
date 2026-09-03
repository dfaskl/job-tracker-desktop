<script setup lang="ts">
import { onMounted, ref } from 'vue'

type AppStatus = { javaVersion: string; databaseConfigured: boolean; frontendBundled: boolean }
type DatabaseStatus = {
  connected: boolean
  schemaCompatible: boolean
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
  try {
    const response = await fetch('/api/poc/status', { cache: 'no-store' })
    status.value = await response.json()
  } catch {
    error.value = '无法读取运行状态'
  }
})

async function probeDatabase() {
  loading.value = true
  error.value = ''
  database.value = null
  try {
    const response = await fetch('/api/poc/database', { method: 'POST', headers: { 'X-POC-Token': accessToken.value } })
    const body = await response.json().catch(() => ({}))
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
  <section class="card">
    <h2>运行诊断</h2>
    <p>供迁移期间检查 Java、Vue 和旧 PostgreSQL 兼容性；数据库检查始终只读。</p>
    <dl v-if="status">
      <div><dt>Spring Boot</dt><dd class="success">运行正常</dd></div>
      <div><dt>Java</dt><dd>{{ status.javaVersion }}</dd></div>
      <div><dt>Vue 已打入 JAR</dt><dd :class="status.frontendBundled ? 'success' : 'danger'">{{ status.frontendBundled ? '是' : '否' }}</dd></div>
      <div><dt>生产数据库只读连接已配置</dt><dd>{{ status.databaseConfigured ? '是' : '否' }}</dd></div>
    </dl>
    <div class="form-row">
      <input v-model="accessToken" type="password" autocomplete="off" placeholder="输入迁移检查令牌" />
      <button :disabled="loading || !accessToken" @click="probeDatabase">{{ loading ? '检查中…' : '执行只读兼容检查' }}</button>
    </div>
    <dl v-if="database">
      <div><dt>连接</dt><dd :class="database.connected ? 'success' : 'danger'">{{ database.connected ? '成功' : '失败' }}</dd></div>
      <div><dt>八张核心表</dt><dd :class="database.schemaCompatible ? 'success' : 'danger'">{{ database.schemaCompatible ? '完整' : '存在缺失' }}</dd></div>
      <div><dt>业务 JSON</dt><dd :class="database.businessJsonCompatible ? 'success' : 'danger'">{{ database.samplePresent ? (database.businessJsonCompatible ? '兼容' : '不兼容') : '没有样本' }}</dd></div>
    </dl>
    <p v-if="error" class="danger">{{ error }}</p>
  </section>
</template>
