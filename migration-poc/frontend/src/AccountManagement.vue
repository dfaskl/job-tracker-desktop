<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { api } from './api'
import { useJobTrackerStore, type User } from './jobTrackerStore'

const store = useJobTrackerStore()
const profile = reactive({ displayName: '' })
const password = reactive({ current: '', next: '', confirm: '' })
const savingProfile = ref(false)
const savingPassword = ref(false)
const showCurrent = ref(false)
const showNext = ref(false)
const message = ref('')
const error = ref('')

const defaultName = computed(() => String(store.user.value?.email || '').split('@')[0] || '未命名用户')
watch(() => store.user.value, user => { profile.displayName = user?.displayName || defaultName.value }, { immediate: true })

async function saveProfile() {
  savingProfile.value = true; message.value = ''; error.value = ''
  try {
    const result = await api<{ user: User; message: string }>('/api/poc/auth/profile', {
      method: 'PUT', body: JSON.stringify({ displayName: profile.displayName })
    })
    store.setUser(result.user)
    profile.displayName = result.user.displayName || defaultName.value
    message.value = '昵称已更新，首页日程会立即使用新昵称'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '昵称更新失败' }
  finally { savingProfile.value = false }
}

async function changePassword() {
  message.value = ''; error.value = ''
  if (password.next !== password.confirm) { error.value = '两次输入的新密码不一致'; return }
  savingPassword.value = true
  try {
    await api('/api/poc/auth/password', {
      method: 'POST', body: JSON.stringify({ currentPassword: password.current, newPassword: password.next })
    })
    password.current = ''; password.next = ''; password.confirm = ''
    message.value = '密码已更新，其他设备上的登录已退出'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '密码更新失败' }
  finally { savingPassword.value = false }
}
</script>

<template>
  <section class="card account-card">
    <div class="section-head">
      <div><span>个人账户</span><h2>账户管理</h2></div>
      <span class="account-mark" aria-hidden="true"><svg viewBox="0 0 24 24"><circle cx="12" cy="8" r="3.5"/><path d="M5.5 20a6.5 6.5 0 0 1 13 0"/></svg></span>
    </div>
    <p class="account-email">{{ store.user.value?.email }}</p>

    <form class="account-form" @submit.prevent="saveProfile">
      <label><span>日程昵称</span><input v-model.trim="profile.displayName" maxlength="32" autocomplete="nickname" required :placeholder="defaultName"></label>
      <small>用于首页多人日程展示；未设置时默认使用邮箱前缀。</small>
      <button :disabled="savingProfile">{{ savingProfile ? '正在保存…' : '保存昵称' }}</button>
    </form>

    <div class="form-divider"><span>修改密码</span></div>
    <form class="account-form password-form" @submit.prevent="changePassword">
      <label><span>当前密码</span><span class="password-field"><input v-model="password.current" :type="showCurrent?'text':'password'" autocomplete="current-password" required><button type="button" class="visibility" :aria-label="showCurrent?'隐藏当前密码':'显示当前密码'" @click="showCurrent=!showCurrent">{{showCurrent?'隐藏':'显示'}}</button></span></label>
      <label><span>新密码</span><span class="password-field"><input v-model="password.next" :type="showNext?'text':'password'" minlength="10" maxlength="128" autocomplete="new-password" required placeholder="至少 10 位"><button type="button" class="visibility" :aria-label="showNext?'隐藏新密码':'显示新密码'" @click="showNext=!showNext">{{showNext?'隐藏':'显示'}}</button></span></label>
      <label><span>确认新密码</span><input v-model="password.confirm" :type="showNext?'text':'password'" minlength="10" maxlength="128" autocomplete="new-password" required></label>
      <button :disabled="savingPassword">{{ savingPassword ? '正在更新…' : '更新密码' }}</button>
    </form>
    <p v-if="message" class="success" role="status">{{message}}</p>
    <p v-if="error" class="danger" role="alert">{{error}}</p>
  </section>
</template>

<style scoped>
.account-card{display:grid;grid-area:account;align-content:start;gap:14px}.section-head{display:flex;align-items:center;justify-content:space-between;gap:12px}.section-head>div>span{color:var(--color-primary);font-size:11px;font-weight:800;letter-spacing:.1em}.section-head h2{margin:4px 0 0}.account-mark{display:grid;width:42px;height:42px;place-items:center;border:1px solid color-mix(in srgb,var(--color-primary) 30%,#fff);border-radius:13px;color:var(--color-primary);background:color-mix(in srgb,var(--color-primary) 8%,#fff)}.account-mark svg{width:23px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round}.account-email{margin:0;padding:9px 12px;border-radius:9px;color:var(--color-muted-foreground);background:#f5f9fa;font-size:13px}.account-form{display:grid;gap:10px}.account-form label{display:grid;gap:6px;color:var(--color-muted-foreground);font-size:13px;font-weight:700}.account-form small{margin-top:-3px;color:var(--color-muted-foreground);line-height:1.55}.account-form>button{width:100%}.form-divider{display:flex;align-items:center;gap:10px;color:var(--color-muted-foreground);font-size:12px;font-weight:800}.form-divider::before,.form-divider::after{height:1px;flex:1;background:var(--color-border);content:""}.password-field{display:flex;min-width:0}.password-field input{min-width:0;flex:1;border-radius:8px 0 0 8px}.password-field .visibility{width:58px;min-height:42px;padding:0;border-left:0;border-radius:0 8px 8px 0;color:var(--color-primary);background:#eef8f8;font-size:12px}.success,.danger{margin:0;padding:9px 11px;border-radius:8px;font-size:13px}.success{color:#0f715e;background:#e9f8f3}.danger{color:#a52d2d;background:#fff1ef}
</style>
