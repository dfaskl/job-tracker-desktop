<script setup lang="ts">
import { nextTick, ref } from 'vue'
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
      <div class="login-brand"><span><img src="/favicon.svg" alt=""></span><strong>CareerFlow</strong></div>
      <div class="story-content">
        <p class="story-label">求职进度本</p>
        <h1 id="welcome-title">让每一次机会，<br>都有清晰的下一步。</h1>
        <p class="story-description">把投递、招聘邮件与面试安排放在一起，<br>专注准备，也看得见自己的进展。</p>
        <ol class="story-flow" aria-label="求职管理流程">
          <li><span class="story-node"><AppIcon name="check" :size="18" /></span><div><strong>记录投递</strong><small>公司、岗位与进度，一处整理</small></div><span class="story-tag">起点</span></li>
          <li><span class="story-node"><AppIcon name="mail" :size="18" /></span><div><strong>接收下一步</strong><small>识别招聘通知，关联日程</small></div></li>
          <li><span class="story-node"><AppIcon name="calendar" :size="18" /></span><div><strong>从容赴约</strong><small>掌握面试安排，记录每份进展</small></div></li>
        </ol>
      </div>
      <div class="story-footer"><span>你的求职旅程，有序向前。</span><span>CareerFlow</span></div>
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
            <input id="login-email" ref="emailInput" v-model.trim="email" type="email" autocomplete="username" inputmode="email" placeholder="name@example.com" required autofocus>
          </label>
          <label for="login-password">{{ mode === 'login' ? '登录密码' : '设置密码' }}
            <span class="password-field">
              <input id="login-password" v-model="password" :type="showPassword?'text':'password'" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" placeholder="••••••••" minlength="10" required>
              <button type="button" :aria-label="showPassword?'隐藏密码':'显示密码'" :title="showPassword?'隐藏密码':'显示密码'" @click="showPassword=!showPassword"><AppIcon :name="showPassword?'eye-off':'eye'" :size="19" /></button>
            </span>
          </label>
          <label v-if="mode === 'register'" for="registration-code">注册码 <small>如管理员已启用</small>
            <input id="registration-code" v-model.trim="registrationCode" autocomplete="one-time-code" placeholder="输入管理员提供的注册码">
          </label>
          <p v-if="message" class="login-error" role="alert">{{ message }}</p>
          <button class="login-submit" type="submit" :disabled="submitting" :aria-busy="submitting">
            <span>{{ submitting ? '正在处理…' : mode === 'login' ? '进入账户' : '开始使用' }}</span>
            <AppIcon v-if="!submitting" name="chevron-right" :size="17" />
          </button>
        </form>

        <p class="mode-line">{{ mode === 'login' ? '还没有账户？' : '已经有账户？' }} <button class="mode-switch" type="button" :disabled="submitting" @click="switchMode">{{ mode === 'login' ? '立即创建' : '去登录' }}</button></p>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page { display: grid; min-height: 100dvh; color: var(--color-foreground); background: var(--color-card); font-family: var(--font-ui); }
.source-link { position: absolute; top: 24px; right: 32px; z-index: 2; display: inline-flex; align-items: center; gap: 8px; min-height: 40px; padding: 8px 12px; color: var(--color-muted-foreground); border: 1px solid var(--color-border); border-radius: 8px; font-size: 12px; text-decoration: none; }
.source-link:hover { color: var(--color-primary); background: var(--color-muted); }
.source-link svg { width: 16px; height: 16px; fill: currentColor; }
.login-story { display: none; flex-direction: column; justify-content: space-between; padding: clamp(32px,4vw,64px); border-right: 1px solid var(--color-border); background: var(--color-background); }
.login-brand, .mobile-brand { display: flex; align-items: center; gap: 12px; font-size: 20px; }
.login-brand img, .mobile-brand img { display: block; width: 36px; height: 36px; border-radius: 10px; }
.login-brand strong { font-weight: 650; letter-spacing: -.5px; }
.story-content { max-width: 520px; margin: 48px 0; }
.story-label { color: var(--color-primary); font-size: 14px; font-weight: 600; }
#app .story-content h1 { margin: 20px 0; font-size: clamp(30px,3.1vw,46px); line-height: 1.5; font-weight: 600; letter-spacing: -.045em; }
.story-description { font-size: 15px; line-height: 1.9; }
.story-flow { display: grid; gap: 0; margin: 36px 0 0; padding: 8px 24px; list-style: none; border: 1px solid var(--color-border); border-radius: 12px; background: var(--color-card); }
.story-flow li { display: flex; position: relative; align-items: center; gap: 16px; min-height: 88px; }
.story-flow li + li { border-top: 1px solid var(--color-border); }
.story-node { display: grid; width: 36px; height: 36px; flex: none; place-items: center; border: 1px solid var(--color-border); border-radius: 9px; color: var(--color-primary); background: var(--surface-selected); }
.story-flow li > div { display: grid; gap: 6px; }
.story-flow strong { font-size: 14px; font-weight: 600; }
.story-flow small { color: var(--color-muted-foreground); font-size: 12px; }
.story-tag { margin-left: auto; padding: 4px 8px; color: var(--color-primary); background: var(--surface-selected); border-radius: 5px; font-size: 12px; }
.story-footer { display: flex; justify-content: space-between; gap: 12px; color: var(--color-muted-foreground); font-size: 12px; }
.login-entry { display: flex; min-height: 100dvh; align-items: center; justify-content: center; padding: 88px 32px 48px; }
.entry-inner { width: 100%; max-width: 380px; animation: auth-panel-enter .25s ease both; }
.mobile-brand { margin-bottom: 44px; }
.entry-heading { margin-bottom: 32px; }
#app .entry-heading h2 { margin: 0 0 12px; font-size: 28px; font-weight: 650; }
.entry-heading p { margin: 0; font-size: 14px; }
.entry-inner form { display: grid; gap: 20px; }
.entry-inner label { display: grid; gap: 9px; font-size: 14px; font-weight: 500; }
.entry-inner label small { color: var(--color-muted-foreground); font-size: 12px; }
#login-page .login-entry input { width: 100%; height: 48px; padding: 0 14px; border: 1px solid var(--color-border-strong); border-radius: 8px; color: var(--color-foreground); background: var(--color-card); font: inherit; }
#login-page .login-entry input:focus { border-color: var(--color-ring); box-shadow: 0 0 0 3px color-mix(in srgb,var(--color-ring) 12%,transparent); }
.password-field { position: relative; display: block; }
#login-page .password-field input { padding-right: 52px; }
#login-page .password-field button { position: absolute; top: 2px; right: 4px; display: grid; width: 44px; height: 44px; place-items: center; padding: 0; border: 0; color: var(--color-muted-foreground); background: transparent; }
#login-page .password-field button:hover { background: var(--color-muted); }
.login-error { margin: 0; padding: 12px; border: 1px solid var(--color-destructive); border-radius: 8px; color: var(--color-destructive); background: color-mix(in srgb,var(--color-destructive) 7%,var(--color-card)); font-size: 13px; }
#login-page .login-submit { display: flex; width: 100%; height: 48px; align-items: center; justify-content: center; gap: 10px; margin-top: 4px; border: 1px solid transparent; border-radius: 8px; color: var(--color-on-primary); background: var(--color-primary); font-size: 14px; font-weight: 600; }
#login-page .login-submit:hover { filter: brightness(.96); }
.mode-line { margin: 24px 0 0; color: var(--color-muted-foreground); font-size: 13px; text-align: center; }
#login-page .mode-switch { padding: 4px 8px; border: 0; color: var(--color-primary); background: transparent; font-size: 13px; }
.mode-switch:hover { text-decoration: underline; text-underline-offset: 4px; }
@keyframes auth-panel-enter { from { opacity: .5; transform: translateY(5px); } to { opacity: 1; transform: none; } }
@media (min-width: 1024px) { .login-page { grid-template-columns: 1.08fr 1fr; } .login-story { display: flex; } .mobile-brand { display: none; } }
@media (max-width: 600px) { .source-link { top: 16px; right: 20px; } .login-entry { padding-inline: 24px; } }
</style>
