<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
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
const profileError = ref('')
const passwordError = ref('')
const passwordDialog = ref<HTMLDialogElement | null>(null)
const currentPasswordInput = ref<HTMLInputElement | null>(null)

const defaultName = computed(() => String(store.user.value?.email || '').split('@')[0] || '未命名用户')
watch(() => store.user.value, user => { profile.displayName = user?.displayName || defaultName.value }, { immediate: true })

async function saveProfile() {
  savingProfile.value = true; message.value = ''; profileError.value = ''
  try {
    const result = await api<{ user: User; message: string }>('/api/poc/auth/profile', {
      method: 'PUT', body: JSON.stringify({ displayName: profile.displayName })
    })
    store.setUser(result.user)
    profile.displayName = result.user.displayName || defaultName.value
    message.value = '昵称已更新，首页日程会立即使用新昵称'
  } catch (cause) { profileError.value = cause instanceof Error ? cause.message : '昵称更新失败' }
  finally { savingProfile.value = false }
}

async function changePassword() {
  message.value = ''; passwordError.value = ''
  if (password.next !== password.confirm) { passwordError.value = '两次输入的新密码不一致'; return }
  savingPassword.value = true
  try {
    await api('/api/poc/auth/password', {
      method: 'POST', body: JSON.stringify({ currentPassword: password.current, newPassword: password.next })
    })
    resetPasswordForm()
    passwordDialog.value?.close()
    message.value = '密码已更新，其他设备上的登录已退出'
  } catch (cause) { passwordError.value = cause instanceof Error ? cause.message : '密码更新失败' }
  finally { savingPassword.value = false }
}

async function openPasswordDialog() {
  resetPasswordForm(); passwordError.value = ''; message.value = ''
  passwordDialog.value?.showModal()
  await nextTick()
  currentPasswordInput.value?.focus()
}
function closePasswordDialog() { if (!savingPassword.value) passwordDialog.value?.close() }
function closePasswordFromBackdrop(event: MouseEvent) { if (event.target === passwordDialog.value) closePasswordDialog() }
function handlePasswordCancel(event: Event) { if (savingPassword.value) event.preventDefault() }
function resetPasswordForm() {
  password.current = ''; password.next = ''; password.confirm = ''
  showCurrent.value = false; showNext.value = false
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

    <button type="button" class="password-launch" @click="openPasswordDialog">修改密码</button>
    <p v-if="message" class="success" role="status">{{message}}</p>
    <p v-if="profileError" class="danger" role="alert">{{profileError}}</p>
  </section>

  <dialog ref="passwordDialog" class="password-dialog" aria-labelledby="password-dialog-title" @click="closePasswordFromBackdrop" @cancel="handlePasswordCancel">
    <div class="password-dialog-content">
      <button type="button" class="modal-close icon-button" :disabled="savingPassword" aria-label="关闭修改密码窗口" title="关闭" @click="closePasswordDialog"><AppIcon name="close" /></button>
      <header><span>账户安全</span><h2 id="password-dialog-title">修改密码</h2><p>请输入当前密码，并设置一个至少 10 位的新密码。</p></header>
      <form class="account-form password-form" @submit.prevent="changePassword">
        <label><span>当前密码</span><span class="password-field"><input ref="currentPasswordInput" v-model="password.current" :type="showCurrent?'text':'password'" autocomplete="current-password" required><button type="button" class="visibility icon-button compact-icon" :aria-label="showCurrent?'隐藏当前密码':'显示当前密码'" :title="showCurrent?'隐藏密码':'显示密码'" @click="showCurrent=!showCurrent"><AppIcon :name="showCurrent?'eye-off':'eye'" /></button></span></label>
        <label><span>新密码</span><span class="password-field"><input v-model="password.next" :type="showNext?'text':'password'" minlength="10" maxlength="128" autocomplete="new-password" required placeholder="至少 10 位"><button type="button" class="visibility icon-button compact-icon" :aria-label="showNext?'隐藏新密码':'显示新密码'" :title="showNext?'隐藏密码':'显示密码'" @click="showNext=!showNext"><AppIcon :name="showNext?'eye-off':'eye'" /></button></span></label>
        <label><span>确认新密码</span><input v-model="password.confirm" :type="showNext?'text':'password'" minlength="10" maxlength="128" autocomplete="new-password" required></label>
        <p v-if="passwordError" class="danger" role="alert">{{passwordError}}</p>
        <div class="dialog-actions"><button type="button" class="secondary" :disabled="savingPassword" @click="closePasswordDialog">取消</button><button class="password-submit" :disabled="savingPassword">{{ savingPassword ? '正在更新…' : '确认修改' }}</button></div>
      </form>
    </div>
  </dialog>
</template>

<style scoped>
.account-card{display:grid;grid-area:account;align-content:start;gap:14px}.section-head{display:flex;align-items:center;justify-content:space-between;gap:12px}.section-head>div>span,.password-dialog header>span{color:var(--color-primary);font-size:11px;font-weight:800;letter-spacing:.1em}.section-head h2{margin:4px 0 0}.account-mark{display:grid;width:42px;height:42px;place-items:center;border:1px solid color-mix(in srgb,var(--color-primary) 30%,#fff);border-radius:13px;color:var(--color-primary);background:color-mix(in srgb,var(--color-primary) 8%,#fff)}.account-mark svg{width:23px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round}.account-email{margin:0;padding:9px 12px;border-radius:9px;color:var(--color-muted-foreground);background:#f5f9fa;font-size:13px}.account-form{display:grid;gap:10px}.account-form label{display:grid;gap:6px;color:var(--color-muted-foreground);font-size:13px;font-weight:700}.account-form small{margin-top:-3px;color:var(--color-muted-foreground);line-height:1.55}.account-form>button,.password-launch{width:100%}.password-launch{color:var(--color-primary);background:#eef8f8}.password-field{display:flex;min-width:0}.password-field input{min-width:0;flex:1;border-radius:8px 0 0 8px}.password-field .visibility{width:58px;min-height:42px;padding:0;border-left:0;border-radius:0 8px 8px 0;color:var(--color-primary);background:#eef8f8;font-size:12px}.success,.danger{margin:0;padding:9px 11px;border-radius:8px;font-size:13px}.success{color:#0f715e;background:#e9f8f3}.danger{color:#a52d2d;background:#fff1ef}.password-dialog{width:min(480px,calc(100vw - 32px));padding:0;border:1px solid var(--color-border);border-radius:18px;color:var(--color-card-foreground);background:var(--color-card);box-shadow:var(--shadow-lg)}.password-dialog::backdrop{background:rgba(7,37,58,.58);backdrop-filter:blur(4px)}.password-dialog-content{position:relative;display:grid;gap:20px;padding:28px}.password-dialog .modal-close{position:absolute;top:14px;right:14px;width:44px;height:44px;padding:0;font-size:24px}.password-dialog header{padding-right:46px}.password-dialog header h2{margin:4px 0 7px}.password-dialog header p{margin:0}.password-form{gap:14px}.password-form .danger{margin-top:-2px}.dialog-actions{display:grid;grid-template-columns:1fr 1.4fr;gap:10px;margin-top:4px}.password-submit{color:#fff;background:var(--color-primary)}@media(max-width:520px){.password-dialog{width:calc(100vw - 20px)}.password-dialog-content{padding:24px 16px}.dialog-actions{grid-template-columns:1fr}}
</style>
