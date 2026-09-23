<script setup lang="ts">
import { nextTick, ref } from 'vue'
import AuthIllustration from './AuthIllustration.vue'
import { useJobTrackerStore } from './jobTrackerStore'

const store = useJobTrackerStore()
const mode = ref<'login' | 'register'>('login')
const email = ref('')
const password = ref('')
const registrationCode = ref('')
const submitting = ref(false)
const message = ref('')
const emailInput = ref<HTMLInputElement | null>(null)
const showPassword = ref(false)
const emailFocused = ref(false)
const passwordFocused = ref(false)

async function submit() {
  submitting.value = true
  message.value = ''
  try {
    if (mode.value === 'login') await store.login(email.value, password.value)
    else await store.register(email.value, password.value, registrationCode.value)
  } catch (cause) {
    message.value = cause instanceof Error ? cause.message : '登录失败，请检查账号信息后重试'
  } finally {
    submitting.value = false
  }
}

async function switchMode() {
  mode.value = mode.value === 'login' ? 'register' : 'login'
  password.value = ''
  showPassword.value = false
  registrationCode.value = ''
  message.value = ''
  await nextTick()
  emailInput.value?.focus()
}
</script>

<template>
  <main id="login-page" class="login-page">
    <a class="source-link" href="https://github.com/dfaskl/job-tracker-desktop" target="_blank" rel="noreferrer" aria-label="查看 GitHub 仓库">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2a10 10 0 0 0-3.16 19.49c.5.09.68-.22.68-.48v-1.88c-2.78.6-3.37-1.18-3.37-1.18-.45-1.16-1.11-1.47-1.11-1.47-.91-.62.07-.61.07-.61 1 .07 1.53 1.03 1.53 1.03.9 1.53 2.35 1.09 2.92.83.09-.65.35-1.09.64-1.34-2.22-.25-4.55-1.11-4.55-4.94 0-1.09.39-1.98 1.03-2.68-.1-.25-.45-1.27.1-2.64 0 0 .84-.27 2.75 1.02A9.6 9.6 0 0 1 12 6.82a9.6 9.6 0 0 1 2.5.34c1.91-1.3 2.75-1.02 2.75-1.02.55 1.37.2 2.39.1 2.64.64.7 1.03 1.59 1.03 2.68 0 3.84-2.34 4.68-4.57 4.93.36.31.68.92.68 1.86v2.76c0 .27.18.58.69.48A10 10 0 0 0 12 2Z" /></svg>
      <span>GitHub</span>
    </a>

    <section class="login-story" aria-labelledby="welcome-title">
      <div class="login-brand"><span><img src="/favicon.svg" alt=""></span><strong>求职进度本</strong></div>
      <h1 id="welcome-title" class="sr-only">求职进度本登录</h1>
      <div class="illustration-wrap"><AuthIllustration :is-typing="emailFocused" :show-password="showPassword" :password-length="password.length" :password-active="passwordFocused" /></div>
      <div class="story-footer"><span>隐私保护</span><span>专注求职进度</span></div>
      <div class="story-grid" aria-hidden="true"></div>
      <div class="story-glow glow-top" aria-hidden="true"></div>
      <div class="story-glow glow-bottom" aria-hidden="true"></div>
    </section>

    <section class="login-entry" aria-labelledby="login-title">
      <div class="entry-inner">
        <div class="mobile-brand"><span><img src="/favicon.svg" alt=""></span><strong>求职进度本</strong></div>
        <header class="entry-heading">
          <h2 id="login-title">{{ mode === 'login' ? '欢迎回来' : '创建你的账户' }}</h2>
          <p>{{ mode === 'login' ? '继续查看你的求职计划' : '保存求职进度，开始规划下一步' }}</p>
        </header>

        <form @submit.prevent="submit">
          <label for="login-email">邮箱地址
            <input id="login-email" ref="emailInput" v-model.trim="email" type="email" autocomplete="username" inputmode="email" placeholder="name@example.com" required autofocus @focus="emailFocused=true" @blur="emailFocused=false">
          </label>
          <label for="login-password">{{ mode === 'login' ? '登录密码' : '设置密码' }}
            <span class="password-field">
              <input id="login-password" v-model="password" :type="showPassword?'text':'password'" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" placeholder="••••••••" minlength="10" required @focus="passwordFocused=true" @blur="passwordFocused=false">
              <button type="button" :aria-label="showPassword?'隐藏密码':'显示密码'" :title="showPassword?'隐藏密码':'显示密码'" @click="showPassword=!showPassword"><AppIcon :name="showPassword?'eye-off':'eye'" :size="19" /></button>
            </span>
          </label>
          <label v-if="mode === 'register'" for="registration-code">注册码 <small>如管理员已启用</small>
            <input id="registration-code" v-model.trim="registrationCode" autocomplete="one-time-code" placeholder="输入管理员提供的注册码" @focus="emailFocused=true" @blur="emailFocused=false">
          </label>
          <p v-if="message" class="login-error" role="alert">{{ message }}</p>
          <button class="login-submit" type="submit" :disabled="submitting">
            <span>{{ submitting ? '正在处理…' : mode === 'login' ? '进入账户' : '开始使用' }}</span>
            <span aria-hidden="true">{{ submitting ? '请稍候' : mode === 'login' ? '进入账户' : '开始使用' }}<AppIcon name="chevron-right" :size="17" /></span>
          </button>
        </form>

        <p class="mode-line">{{ mode === 'login' ? '还没有账户？' : '已经有账户？' }} <button class="mode-switch" type="button" :disabled="submitting" @click="switchMode">{{ mode === 'login' ? '立即创建' : '去登录' }}</button></p>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page{display:grid;min-height:100dvh;color:#f5f5f4;background:#0c0a09;font-family:Inter,"Noto Sans SC",ui-sans-serif,system-ui,sans-serif}.source-link{position:fixed;top:24px;right:24px;z-index:50;display:inline-flex;height:36px;align-items:center;justify-content:center;gap:8px;padding:0 13px;border:1px solid rgba(255,255,255,.1);border-radius:999px;color:#f5f5f4;background:rgba(12,10,9,.45);box-shadow:0 8px 24px rgba(0,0,0,.18);backdrop-filter:blur(12px);font-size:12px;font-weight:500;text-decoration:none;transition:border-color .2s,background .2s,color .2s}.source-link:hover{border-color:rgba(255,255,255,.2);color:#fff;background:rgba(28,25,23,.7)}.source-link svg{width:16px;height:16px;fill:currentColor}.login-story{position:relative;display:none;min-height:100dvh;flex-direction:column;justify-content:space-between;padding:48px;overflow:hidden;color:#111827;background:linear-gradient(135deg,#e4e4e4,#d7d7d7 52%,#c9c9c9)}.login-brand,.mobile-brand{position:relative;z-index:20;display:flex;align-items:center;gap:10px;font-size:18px;font-weight:600}.login-brand>span,.mobile-brand>span{display:grid;width:34px;height:34px;place-items:center;border-radius:9px;background:#fff;box-shadow:0 4px 13px rgba(0,0,0,.08)}.login-brand img,.mobile-brand img{width:28px;height:28px;border-radius:7px}.login-brand strong,.mobile-brand strong{letter-spacing:-.025em}.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}.illustration-wrap{position:relative;z-index:20;display:flex;height:500px;align-items:flex-end;justify-content:center}.story-footer{position:relative;z-index:20;display:flex;align-items:center;gap:32px;color:#4b5563;font-size:14px}.story-grid{position:absolute;inset:0;background-image:linear-gradient(rgba(255,255,255,.16) 1px,transparent 1px),linear-gradient(90deg,rgba(255,255,255,.16) 1px,transparent 1px);background-size:20px 20px}.story-glow{position:absolute;border-radius:999px;filter:blur(64px)}.glow-top{top:25%;right:25%;width:256px;height:256px;background:rgba(255,255,255,.2)}.glow-bottom{bottom:25%;left:25%;width:384px;height:384px;background:rgba(255,255,255,.14)}
.login-entry{display:flex;min-height:100dvh;align-items:center;justify-content:center;padding:32px 24px;background:#0c0a09}.entry-inner{width:100%;max-width:420px;animation:auth-panel-enter .42s cubic-bezier(.2,.8,.2,1) both}.mobile-brand{justify-content:center;margin-bottom:48px;color:#f5f5f4}.entry-heading{margin-bottom:40px;text-align:center}.entry-heading h2{margin:0 0 8px;color:#fafaf9;font-size:30px;line-height:1.2;letter-spacing:-.025em}.entry-heading p{margin:0;color:#a8a29e;font-size:14px}.entry-inner form{display:grid;gap:20px}.entry-inner label{display:grid;gap:8px;color:#f5f5f4;font-size:14px;font-weight:500}.entry-inner label small{margin-left:5px;color:#78716c;font-weight:400}#login-page .login-entry input{width:100%;height:48px;padding:0 14px;border:1px solid #292524;border-radius:8px;outline:0;color:#fafaf9;background:#0c0a09;font:inherit;transition:border-color .15s,box-shadow .15s}#login-page .login-entry input::placeholder{color:#78716c}#login-page .login-entry input:focus{border-color:#4f46a5;box-shadow:0 0 0 3px rgba(79,70,165,.2)}.password-field{position:relative;display:block}#login-page .password-field input{padding-right:48px}#login-page .password-field button{position:absolute;top:50%;right:4px;display:grid;width:44px;min-width:44px;height:44px;min-height:44px;place-items:center;padding:0;transform:translateY(-50%);border:0;border-radius:7px;color:#a8a29e;background:transparent}#login-page .password-field button:hover{color:#fafaf9;background:#1c1917}#login-page .password-field button:focus-visible{outline:2px solid #6366f1;outline-offset:1px}.login-error{margin:0;padding:10px 12px;border:1px solid rgba(239,68,68,.35);border-radius:8px;color:#fca5a5;background:rgba(127,29,29,.16);font-size:12px;line-height:1.5}#login-page .login-submit{position:relative;width:100%;height:48px;margin-top:0;overflow:hidden;border:1px solid #292524;border-radius:999px;color:#fafaf9;background:#0c0a09;font-size:16px;font-weight:500;cursor:pointer;transition:transform .2s}.login-submit>span{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;gap:8px;white-space:nowrap;transition:transform .3s,opacity .3s}.login-submit>span:last-child{z-index:1;color:#fff;background:#4f46a5;transform:translateX(-12%);opacity:0}.login-submit:not(:disabled):hover>span:first-child{transform:translateX(100%);opacity:0}.login-submit:not(:disabled):hover>span:last-child{transform:translateX(0);opacity:1}.login-submit:not(:disabled):active{transform:scale(.98)}.login-submit:focus-visible{outline:3px solid rgba(99,102,241,.4);outline-offset:3px}.login-submit:disabled{cursor:wait;opacity:.55}.mode-line{margin:32px 0 0;color:#a8a29e;font-size:14px;text-align:center}#login-page .mode-switch{min-height:36px;padding:4px;border:0;border-radius:4px;color:#fafaf9;background:transparent;font-weight:500}.mode-switch:hover{text-decoration:underline;text-underline-offset:4px}.mode-switch:focus-visible{outline:2px solid #6366f1;outline-offset:2px}
@keyframes auth-panel-enter{from{opacity:0;transform:translateY(14px)}to{opacity:1;transform:translateY(0)}}
@media(min-width:1024px){.login-page{height:100dvh;grid-template-columns:1fr 1fr;overflow:hidden}.login-story{display:flex}.login-entry{min-height:0;overflow-y:auto}.mobile-brand{display:none}}
@media(max-width:640px){.source-link{top:16px;right:16px;width:40px;padding:0}.source-link span{display:none}.login-entry{padding:76px 20px 32px}.entry-heading{margin-bottom:32px}}
@media(prefers-reduced-motion:reduce){.entry-inner{animation:none}.source-link,.login-submit,.login-submit>span{transition-duration:.01ms!important}}
</style>
