<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api } from './api'
import AppIcon from './AppIcon.vue'

type ConfigView = { apiUrl: string; model: string; hasApiKey: boolean; lastFour: string }

const config = ref<ConfigView | null>(null)
const form = reactive({ apiUrl: 'https://api.deepseek.com', model: 'deepseek-chat', apiKey: '', clearApiKey: false })
const loading = ref(false)
const revealing = ref(false)
const showStoredKey = ref(false)
const showTypedKey = ref(false)
const storedKey = ref('')
const message = ref('')
const error = ref('')

onMounted(loadConfig)

function hideStoredKey() {
  showStoredKey.value = false
  storedKey.value = ''
}

async function loadConfig() {
  try {
    config.value = await api<ConfigView>('/api/poc/ai-sandbox/config')
    Object.assign(form, { apiUrl: config.value.apiUrl, model: config.value.model, apiKey: '', clearApiKey: false })
    hideStoredKey()
    showTypedKey.value = false
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '读取 AI 配置失败'
  }
}

async function toggleKeyVisibility() {
  error.value = ''
  if (showStoredKey.value) {
    hideStoredKey()
    return
  }
  if (showTypedKey.value) {
    showTypedKey.value = false
    return
  }
  if (form.apiKey || !config.value?.hasApiKey) {
    showTypedKey.value = true
    return
  }
  revealing.value = true
  try {
    const result = await api<{ apiKey: string }>('/api/poc/ai-sandbox/config/reveal', { method: 'POST' })
    storedKey.value = result.apiKey
    showStoredKey.value = true
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '读取 API Key 失败'
  } finally {
    revealing.value = false
  }
}

async function saveConfig() {
  loading.value = true
  message.value = ''
  error.value = ''
  try {
    config.value = await api<ConfigView>('/api/poc/ai-sandbox/config', { method: 'POST', body: JSON.stringify(form) })
    form.apiKey = ''
    form.clearApiKey = false
    hideStoredKey()
    showTypedKey.value = false
    message.value = '大模型 API 配置已保存'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '保存 AI 配置失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="card preference-card api-card">
    <div class="section-head"><div><span>智能能力</span><h2>大模型 API</h2></div><i title="用于邮件识别、投递信息规范化和每日一句">ⓘ</i></div>
    <p>支持 OpenAI 兼容接口。API Key 加密保存在服务器中，点击眼睛可查看已保存的密钥。</p>
    <form class="api-form" @submit.prevent="saveConfig">
      <label class="wide"><span>API 地址</span><input v-model="form.apiUrl" type="url" maxlength="2048" required /></label>
      <label><span>模型名称</span><input v-model="form.model" maxlength="200" required /></label>
      <label><span>API Key {{ config?.hasApiKey ? '（已配置，末四位 ' + config.lastFour + '）' : '' }}</span>
        <span class="api-key-field">
          <input v-if="showStoredKey" :value="storedKey" type="text" readonly aria-label="已保存的 API Key" autocomplete="off" />
          <input v-else v-model="form.apiKey" :type="showTypedKey ? 'text' : 'password'" :class="{ 'saved-key-mask': config?.hasApiKey && !form.apiKey }" autocomplete="off" :placeholder="config?.hasApiKey ? '••••••••••••' : '输入 API Key'" />
          <button type="button" class="key-visibility icon-button compact-icon" :disabled="revealing || loading" :aria-label="showStoredKey || showTypedKey ? '隐藏 API Key' : '显示 API Key'" :title="showStoredKey || showTypedKey ? '隐藏 API Key' : '显示 API Key'" :aria-pressed="showStoredKey || showTypedKey" @click="toggleKeyVisibility"><AppIcon :name="showStoredKey || showTypedKey ? 'eye-off' : 'eye'" /></button>
        </span>
      </label>
      <div class="api-actions wide"><label v-if="config?.hasApiKey" class="check"><input v-model="form.clearApiKey" type="checkbox" /><span>清除现有 API Key</span></label><span v-else></span><button :disabled="loading || revealing">{{ loading ? '保存中…' : '保存配置' }}</button></div>
    </form>
    <p v-if="message" class="success">{{ message }}</p>
    <p v-if="error" class="danger" role="alert">{{ error }}</p>
  </section>
</template>

<style scoped>
.preference-card { display: grid; gap: 14px; }
.api-card { grid-area: api; }
.section-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.section-head h2 { margin: 4px 0 0; }
.section-head span { color: var(--accent, var(--color-primary)); font-size: 11px; font-weight: 800; letter-spacing: .1em; }
.section-head > i { color: #758198; font-style: normal; }
.preference-card > p { margin: 0; }
.api-form { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.api-form label { display: grid; gap: 7px; color: var(--color-muted-foreground); font-size: 13px; font-weight: 700; }
.api-form .wide { grid-column: 1 / -1; }
.api-form .check { display: flex; align-items: center; }
.check input { flex: none; width: 18px; }
.api-key-field { display: flex; min-width: 0; }
.api-key-field input { min-width: 0; flex: 1; border-radius: 8px 0 0 8px; }
.api-key-field input.saved-key-mask::placeholder { color: var(--color-foreground); opacity: 1; letter-spacing: .12em; }
.api-key-field .key-visibility { width: 46px; min-height: 42px; flex: none; padding: 0; border: 1px solid var(--color-border); border-left: 0; border-radius: 0 8px 8px 0; color: var(--color-primary); background: var(--color-muted); }
.api-key-field .key-visibility:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 2px; }
.api-actions { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding-top: 2px; }
.api-actions button { flex: 0 0 auto; }
@media (max-width: 560px) { .api-form { grid-template-columns: 1fr; } .api-form .wide { grid-column: auto; } .api-actions { align-items: stretch; flex-direction: column; } .api-actions button { width: 100%; } }
</style>
