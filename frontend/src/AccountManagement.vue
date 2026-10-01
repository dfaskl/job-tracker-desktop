<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { api } from './api'
import { useJobTrackerStore, type User } from './jobTrackerStore'

const store = useJobTrackerStore()
const profile = reactive({ displayName: '', avatar: '' })
const password = reactive({ current: '', next: '', confirm: '' })
const savingProfile = ref(false)
const savingPassword = ref(false)
const loggingOut = ref(false)
const processingAvatar = ref(false)
const showCurrent = ref(false)
const showNext = ref(false)
const message = ref('')
const profileError = ref('')
const passwordError = ref('')
const avatarInput = ref<HTMLInputElement | null>(null)
const passwordDialog = ref<HTMLDialogElement | null>(null)
const currentPasswordInput = ref<HTMLInputElement | null>(null)

const defaultName = computed(() => String(store.user.value?.email || '').split('@')[0] || '未命名用户')
const avatarInitial = computed(() => (profile.displayName || defaultName.value).trim().slice(0, 1).toUpperCase())
watch(() => store.user.value, user => {
  profile.displayName = user?.displayName || defaultName.value
  profile.avatar = user?.avatar || ''
}, { immediate: true })

async function saveProfile() {
  savingProfile.value = true; message.value = ''; profileError.value = ''
  try {
    const result = await api<{ user: User; message: string }>('/api/poc/auth/profile', {
      method: 'PUT', body: JSON.stringify({ displayName: profile.displayName, avatar: profile.avatar })
    })
    store.setUser(result.user)
    profile.displayName = result.user.displayName || defaultName.value
    profile.avatar = result.user.avatar || ''
    message.value = '个人资料已更新'
  } catch (cause) { profileError.value = cause instanceof Error ? cause.message : '个人资料更新失败' }
  finally { savingProfile.value = false }
}

function selectAvatar() { avatarInput.value?.click() }
async function handleAvatar(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  profileError.value = ''; message.value = ''
  if (!file.type.startsWith('image/')) { profileError.value = '请选择图片文件'; return }
  if (file.size > 12 * 1024 * 1024) { profileError.value = '原始图片不能超过 12 MB'; return }
  processingAvatar.value = true
  try { profile.avatar = await resizeAvatar(file) }
  catch (cause) {
    console.warn('Avatar processing failed', cause)
    profileError.value = '无法读取这张图片，请换用 PNG、JPEG 或 WebP 图片重试'
  } finally { processingAvatar.value = false }
}
async function resizeAvatar(file: File) {
  const canvas = document.createElement('canvas')
  canvas.width = 256
  canvas.height = 256
  const context = canvas.getContext('2d')
  if (!context) throw new Error('canvas unavailable')
  context.imageSmoothingEnabled = true
  context.imageSmoothingQuality = 'high'

  let source: CanvasImageSource
  let width = 0
  let height = 0
  let bitmap: ImageBitmap | null = null
  let objectUrl = ''
  try {
    if ('createImageBitmap' in window) {
      try {
        bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
        source = bitmap
        width = bitmap.width
        height = bitmap.height
      } catch {
        const decoded = await loadImage(file)
        source = decoded.image
        width = decoded.image.naturalWidth
        height = decoded.image.naturalHeight
        objectUrl = decoded.url
      }
    } else {
      const decoded = await loadImage(file)
      source = decoded.image
      width = decoded.image.naturalWidth
      height = decoded.image.naturalHeight
      objectUrl = decoded.url
    }
    if (!width || !height) throw new Error('empty image')
    const size = Math.min(width, height)
    context.drawImage(source, (width - size) / 2, (height - size) / 2, size, size, 0, 0, 256, 256)
    const blob = await new Promise<Blob>((resolve, reject) => canvas.toBlob(value => value ? resolve(value) : reject(new Error('image encoding failed')), 'image/jpeg', .88))
    return await blobToDataUrl(blob)
  } finally {
    bitmap?.close()
    if (objectUrl) URL.revokeObjectURL(objectUrl)
  }
}
function loadImage(file: File) {
  return new Promise<{ image: HTMLImageElement; url: string }>((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const image = new Image()
    image.onload = () => resolve({ image, url })
    image.onerror = () => { URL.revokeObjectURL(url); reject(new Error('image decode failed')) }
    image.src = url
  })
}
function blobToDataUrl(blob: Blob) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => typeof reader.result === 'string' ? resolve(reader.result) : reject(new Error('image read failed'))
    reader.onerror = () => reject(reader.error || new Error('image read failed'))
    reader.readAsDataURL(blob)
  })
}
function removeAvatar() { profile.avatar = ''; message.value = ''; profileError.value = '' }

async function changePassword() {
  message.value = ''; passwordError.value = ''
  if (password.next !== password.confirm) { passwordError.value = '两次输入的新密码不一致'; return }
  savingPassword.value = true
  try {
    await api('/api/poc/auth/password', { method: 'POST', body: JSON.stringify({ currentPassword: password.current, newPassword: password.next }) })
    resetPasswordForm(); passwordDialog.value?.close(); message.value = '密码已更新，其他设备上的登录已退出'
  } catch (cause) { passwordError.value = cause instanceof Error ? cause.message : '密码更新失败' }
  finally { savingPassword.value = false }
}
async function logout() { loggingOut.value = true; try { await store.logout() } finally { loggingOut.value = false } }
async function openPasswordDialog() { resetPasswordForm(); passwordError.value = ''; message.value = ''; passwordDialog.value?.showModal(); await nextTick(); currentPasswordInput.value?.focus() }
function closePasswordDialog() { if (!savingPassword.value) passwordDialog.value?.close() }
function closePasswordFromBackdrop(event: MouseEvent) { if (event.target === passwordDialog.value) closePasswordDialog() }
function handlePasswordCancel(event: Event) { if (savingPassword.value) event.preventDefault() }
function resetPasswordForm() { password.current = ''; password.next = ''; password.confirm = ''; showCurrent.value = false; showNext.value = false }
</script>

<template>
  <section class="card account-card">
    <div class="section-head"><div><span>个人资料</span><h2>账户信息</h2></div></div>
    <div class="identity-editor">
      <button type="button" class="avatar-button" :disabled="processingAvatar" aria-label="选择新头像" title="更换头像" @click="selectAvatar">
        <img v-if="profile.avatar" :src="profile.avatar" alt="当前头像">
        <span v-else aria-hidden="true">{{ avatarInitial }}</span>
        <i aria-hidden="true"><AppIcon name="edit" :size="14" /></i>
      </button>
      <div class="identity-copy"><strong>{{ profile.displayName || defaultName }}</strong><span>{{ store.user.value?.email }}</span><small>点击头像可上传并自动裁切为正方形</small></div>
      <input ref="avatarInput" class="avatar-input" type="file" accept="image/png,image/jpeg,image/webp,image/gif" @change="handleAvatar">
    </div>
    <div class="avatar-actions"><button type="button" class="secondary" :disabled="processingAvatar" @click="selectAvatar">{{ processingAvatar ? '正在处理…' : '更换头像' }}</button><button v-if="profile.avatar" type="button" class="text-button" @click="removeAvatar">移除头像</button></div>

    <form class="account-form" @submit.prevent="saveProfile">
      <label><span>昵称</span><input v-model.trim="profile.displayName" maxlength="32" autocomplete="nickname" required :placeholder="defaultName"></label>
      <small>昵称会用于首页、小组日程和个人主页展示。</small>
      <button :disabled="savingProfile || processingAvatar">{{ savingProfile ? '正在保存…' : '保存个人资料' }}</button>
    </form>

    <div class="account-actions"><button type="button" class="password-launch" @click="openPasswordDialog">修改密码</button><button type="button" class="logout-button" :disabled="loggingOut" @click="logout"><AppIcon name="logout" :size="17" />{{ loggingOut ? '正在退出…' : '退出登录' }}</button></div>
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
.account-card{display:grid;grid-area:account;align-content:start;gap:16px}.section-head{display:flex;align-items:center;justify-content:space-between;gap:12px}.section-head>div>span,.password-dialog header>span{color:var(--color-primary);font-size:11px;font-weight:800;letter-spacing:.1em}.section-head h2{margin:4px 0 0}.identity-editor{display:grid;grid-template-columns:76px minmax(0,1fr);align-items:center;gap:14px;padding:14px;border:1px solid var(--color-border);border-radius:14px;background:var(--color-muted)}.avatar-button{position:relative;display:grid;width:76px;min-width:76px;height:76px;place-items:center;padding:0;overflow:hidden;border:2px solid var(--color-card);border-radius:50%;color:var(--color-on-primary);background:var(--color-primary);box-shadow:0 7px 20px color-mix(in srgb,var(--color-primary) 22%,transparent);font-size:28px;font-weight:800}.avatar-button img{width:100%;height:100%;object-fit:cover}.avatar-button>i{position:absolute;right:1px;bottom:1px;display:grid;width:24px;height:24px;place-items:center;border:2px solid var(--color-card);border-radius:50%;color:var(--color-card-foreground);background:var(--color-card)}.identity-copy{display:grid;min-width:0;gap:4px}.identity-copy strong{overflow:hidden;font-size:18px;text-overflow:ellipsis;white-space:nowrap}.identity-copy span{overflow:hidden;color:var(--color-muted-foreground);font-size:13px;text-overflow:ellipsis;white-space:nowrap}.identity-copy small{color:var(--color-muted-foreground);line-height:1.45}.avatar-input{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}.avatar-actions{display:flex;align-items:center;gap:8px}.avatar-actions button{min-height:38px;padding:7px 12px}.text-button{border-color:transparent;color:var(--color-muted-foreground);background:transparent}.account-form{display:grid;gap:10px}.account-form label{display:grid;gap:6px;color:var(--color-muted-foreground);font-size:13px;font-weight:700}.account-form small{margin-top:-3px;color:var(--color-muted-foreground);line-height:1.55}.account-form>button{width:100%}.account-actions{display:grid;grid-template-columns:1fr 1fr;gap:10px}.password-launch{color:var(--color-primary);background:var(--color-muted)}.logout-button{display:flex;align-items:center;justify-content:center;gap:7px;color:var(--color-destructive);border-color:color-mix(in srgb,var(--color-destructive) 32%,var(--color-border));background:color-mix(in srgb,var(--color-destructive) 7%,var(--color-card))}.success,.danger{margin:0;padding:9px 11px;border-radius:8px;font-size:13px}.success{color:#0f715e;background:#e9f8f3}.danger{color:#a52d2d;background:#fff1ef}.password-dialog{width:min(480px,calc(100vw - 32px));padding:0;border:1px solid var(--color-border);border-radius:18px;color:var(--color-card-foreground);background:var(--color-card);box-shadow:var(--shadow-lg)}.password-dialog::backdrop{background:rgba(8,9,11,.64);backdrop-filter:blur(4px)}.password-dialog-content{position:relative;display:grid;gap:20px;padding:28px}.password-dialog .modal-close{position:absolute;top:14px;right:14px;width:44px;height:44px;padding:0;font-size:24px}.password-dialog header{padding-right:46px}.password-dialog header h2{margin:4px 0 7px}.password-dialog header p{margin:0}.password-form{gap:14px}.password-form .danger{margin-top:-2px}.password-field{display:flex;min-width:0}.password-field input{min-width:0;flex:1;border-radius:8px 0 0 8px}.password-field .visibility{width:58px;min-height:42px;padding:0;border-left:0;border-radius:0 8px 8px 0;color:var(--color-primary);background:var(--color-muted)}.dialog-actions{display:grid;grid-template-columns:1fr 1.4fr;gap:10px;margin-top:4px}.password-submit{color:var(--color-on-primary);background:var(--color-primary)}
:global(:root[data-theme="dark"] .success){color:#8be1c8;background:#123c35}:global(:root[data-theme="dark"] .danger){color:#ffaaa2;background:#48201f}
@media(max-width:520px){.identity-editor{grid-template-columns:62px minmax(0,1fr);padding:11px}.avatar-button{width:62px;min-width:62px;height:62px}.identity-copy small{display:none}.account-actions{grid-template-columns:1fr}.password-dialog{width:calc(100vw - 20px)}.password-dialog-content{padding:24px 16px}.dialog-actions{grid-template-columns:1fr}}
</style>