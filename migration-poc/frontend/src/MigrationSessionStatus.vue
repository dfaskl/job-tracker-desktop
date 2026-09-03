<script setup lang="ts">
import { onMounted, ref } from 'vue'

type SessionStatus = {
  requested: boolean
  persistent: boolean
  databaseIsolated: boolean
  sessionDays: number
  message: string
}

const status = ref<SessionStatus | null>(null)
const error = ref('')

onMounted(load)

async function load() {
  error.value = ''
  try {
    const response = await fetch('/api/poc/session-mode', { cache: 'no-store' })
    const body = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(body.message || '读取会话模式失败')
    status.value = body as SessionStatus
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '读取会话模式失败'
  }
}
</script>

<template>
  <section class="card session-card">
    <div class="section-head">
      <div><span class="section-kicker">第 3 阶段</span><h2>Java 持久化登录会话</h2></div>
      <span :class="['mode-badge', status?.persistent ? 'enabled' : 'fallback']">
        {{ status?.persistent ? 'PostgreSQL 会话' : '签名 Cookie' }}
      </span>
    </div>
    <p>持久化模式使用 32 字节随机令牌，数据库仅保存 SHA-256 哈希；注销会立即删除对应会话。</p>
    <dl v-if="status">
      <div><dt>当前模式</dt><dd :class="status.persistent ? 'success' : ''">{{ status.persistent ? 'Java + PostgreSQL' : '原型签名 Cookie' }}</dd></div>
      <div><dt>会话有效期</dt><dd>{{ status.persistent ? `${status.sessionDays} 天` : '4 小时' }}</dd></div>
      <div><dt>业务数据库隔离</dt><dd>{{ status.databaseIsolated ? '已确认' : '尚未启用' }}</dd></div>
    </dl>
    <div v-if="status && !status.persistent" class="notice">
      <strong>{{ status.message }}</strong>
      <span>准备好独立业务数据库后，设置 <code>POC_PERSISTENT_SESSION_ENABLED=true</code>；不会写入生产 sessions 表。</span>
    </div>
    <p v-if="error" class="danger">{{ error }}</p>
  </section>
</template>

<style scoped>
.section-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.section-kicker { display: block; margin-bottom: 5px; color: #4461d8; font-size: 12px; font-weight: 800; letter-spacing: .08em; }
.section-head h2 { margin-bottom: 0; }
.mode-badge { padding: 7px 10px; border-radius: 999px; font-size: 12px; font-weight: 800; white-space: nowrap; }
.mode-badge.enabled { color: #167647; background: #e9f8ef; }
.mode-badge.fallback { color: #7a4d0b; background: #fff3d6; }
.notice { display: grid; gap: 7px; margin-top: 18px; padding: 18px; border: 1px solid #dbe3f1; border-radius: 12px; background: #f7f9fc; }
.notice span { color: #667085; }
code { padding: 2px 5px; border-radius: 5px; background: #e9edf5; }
</style>
