<script setup lang="ts">
import { ref } from 'vue'
import { useJobTrackerStore } from './jobTrackerStore'

const store = useJobTrackerStore()
const email = ref('')
const password = ref('')
const submitting = ref(false)
const message = ref('')

async function login() {
  submitting.value = true
  message.value = ''
  try {
    await store.login(email.value, password.value)
    password.value = ''
  } catch (cause) {
    message.value = cause instanceof Error ? cause.message : '登录失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section v-if="store.initialized.value && !store.user.value" class="account-access">
    <div><strong>登录旧系统账号</strong><span>登录后可在所有页面查看同一份完整业务数据</span></div>
    <form @submit.prevent="login">
      <input v-model="email" type="email" autocomplete="username" placeholder="邮箱" required />
      <input v-model="password" type="password" autocomplete="current-password" placeholder="密码" required />
      <button :disabled="submitting">{{ submitting ? '登录中…' : '登录' }}</button>
    </form>
    <p v-if="message" class="danger">{{ message }}</p>
  </section>
</template>

<style scoped>
.account-access { display: grid; grid-template-columns: minmax(190px, 1fr) minmax(360px, 1.5fr); align-items: center; gap: 18px; margin-top: 22px; padding: 18px 20px; border: 1px solid #dbe3f1; border-radius: 15px; background: #fff; }
.account-access > div { display: grid; gap: 4px; }
.account-access span { color: #667085; font-size: 12px; }
form { display: grid; grid-template-columns: 1fr 1fr auto; gap: 9px; }
p { grid-column: 1 / -1; margin: 0; }
@media (max-width: 760px) { .account-access, form { grid-template-columns: 1fr; } }
</style>
