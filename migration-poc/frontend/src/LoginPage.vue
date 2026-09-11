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
  registrationCode.value = ''
  message.value = ''
  await nextTick()
  emailInput.value?.focus()
}
</script>

<template>
  <main class="login-page">
    <section class="login-story" aria-labelledby="welcome-title">
      <div class="login-brand"><img src="/favicon.svg" alt=""><strong>求职进度本</strong></div>
      <div class="story-copy">
        <p>从投递到结果</p>
        <h1 id="welcome-title">把每一次机会<br>放在清晰的进度里</h1>
        <span>记录岗位、日程和反馈，在关键节点到来之前做好准备。</span>
      </div>
      <ol class="stage-route" aria-label="求职流程示意">
        <li class="done"><i aria-hidden="true"></i><span>已投递</span></li>
        <li class="done"><i aria-hidden="true"></i><span>测评与笔试</span></li>
        <li><i aria-hidden="true"></i><span>面试</span></li>
        <li><i aria-hidden="true"></i><span>结果</span></li>
      </ol>
    </section>

    <section class="login-entry" aria-labelledby="login-title">
      <div class="entry-inner">
        <p class="entry-kicker">{{ mode === 'login' ? '欢迎回来' : '创建账号' }}</p>
        <h2 id="login-title">{{ mode === 'login' ? '登录后继续管理进展' : '开始记录你的求职进展' }}</h2>
        <p class="entry-help">{{ mode === 'login' ? '使用你的账号进入工作台。' : '如果管理员启用了注册码，请一并填写。' }}</p>

        <form @submit.prevent="submit">
          <label>邮箱
            <input ref="emailInput" v-model.trim="email" type="email" autocomplete="username" inputmode="email" placeholder="name@example.com" required autofocus>
          </label>
          <label>密码
            <input v-model="password" type="password" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" placeholder="请输入密码" minlength="10" required>
          </label>
          <label v-if="mode === 'register'">注册码（如需要）
            <input v-model.trim="registrationCode" autocomplete="one-time-code" placeholder="输入管理员提供的注册码">
          </label>
          <p v-if="message" class="login-error" role="alert">{{ message }}</p>
          <button class="login-submit" type="submit" :disabled="submitting">
            {{ submitting ? '正在处理…' : mode === 'login' ? '登录' : '注册并登录' }}
          </button>
        </form>

        <button class="mode-switch" type="button" :disabled="submitting" @click="switchMode">
          {{ mode === 'login' ? '还没有账号？创建账号' : '已有账号？返回登录' }}
        </button>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page{display:grid;min-height:100vh;grid-template-columns:minmax(420px,1.05fr) minmax(440px,.95fr);color:#e0f2fe;background:#073b5c}
.login-story{position:relative;display:flex;min-height:100vh;flex-direction:column;padding:clamp(28px,5vw,72px);overflow:hidden;background:linear-gradient(145deg,#073b5c 0 68%,#075985 68%)}
.login-story::after{content:"";position:absolute;right:-170px;bottom:-190px;width:430px;height:430px;border:1px solid rgba(186,230,253,.2);border-radius:50%;box-shadow:0 0 0 52px rgba(186,230,253,.035),0 0 0 104px rgba(186,230,253,.025)}
.login-brand{display:flex;align-items:center;gap:12px;font-size:18px}.login-brand img{width:44px;height:44px;border-radius:11px}.login-brand strong{font-family:"Fira Code","Noto Sans SC",sans-serif}
.story-copy{position:relative;z-index:1;margin:auto 0;max-width:620px}.story-copy>p{margin:0 0 18px;color:#7dd3fc;font-weight:750}.story-copy h1{margin:0;color:#fff;font-family:"Fira Code","Noto Sans SC",sans-serif;font-size:clamp(38px,5vw,68px);line-height:1.15;letter-spacing:-.055em}.story-copy>span{display:block;max-width:34em;margin-top:24px;color:#bae6fd;font-size:17px;line-height:1.8}
.stage-route{position:relative;z-index:1;display:grid;grid-template-columns:repeat(4,1fr);margin:0;padding:0;list-style:none}.stage-route::before{content:"";position:absolute;top:8px;right:8%;left:2%;height:2px;background:rgba(186,230,253,.28)}.stage-route li{position:relative;display:grid;gap:10px;color:#9fc7df;font-size:12px}.stage-route i{z-index:1;width:17px;height:17px;border:4px solid #073b5c;border-radius:50%;background:#7aa5bb;box-shadow:0 0 0 2px #7aa5bb}.stage-route .done i{background:#34b383;box-shadow:0 0 0 2px #34b383}.stage-route .done span{color:#e0f2fe}
.login-entry{display:grid;min-height:100vh;place-items:center;padding:32px;background:var(--color-background)}.entry-inner{width:min(430px,100%);padding:clamp(26px,4vw,44px);border-top:4px solid var(--color-primary);border-radius:4px 18px 18px 18px;background:#fff;box-shadow:0 24px 70px rgba(4,31,49,.16)}
.entry-kicker{margin:0;color:var(--color-primary);font-size:13px;font-weight:750}.entry-inner h2{margin:8px 0 8px;font-size:28px;line-height:1.3}.entry-help{margin:0 0 26px}.entry-inner form{display:grid;gap:17px}.entry-inner label{display:grid;gap:7px;color:var(--color-card-foreground);font-size:13px;font-weight:700}.entry-inner input{width:100%;padding-inline:14px}.login-error{margin:0;padding:10px 12px;border-left:3px solid var(--color-destructive);border-radius:5px;color:#a52d2d;background:#fceaea}.login-submit{width:100%;color:#fff;background:var(--color-primary)}.mode-switch{width:100%;margin-top:12px;border:0;color:var(--color-primary);background:transparent}.mode-switch:hover{background:var(--color-muted)}
@media(max-width:860px){.login-page{grid-template-columns:1fr;background:var(--color-background)}.login-story{min-height:auto;padding:22px 20px 26px;background:#073b5c}.login-story::after,.stage-route{display:none}.story-copy{margin:42px 0 0}.story-copy h1{font-size:clamp(30px,8vw,44px)}.story-copy>span{margin-top:14px;font-size:14px}.login-entry{min-height:auto;padding:24px 16px 44px;place-items:start center}.entry-inner{margin-top:-1px;padding:26px 22px;border-radius:4px 4px 16px 16px;box-shadow:0 16px 44px rgba(4,31,49,.12)}}
@media(max-width:420px){.login-brand img{width:38px;height:38px}.story-copy{margin-top:30px}.entry-inner h2{font-size:24px}}
</style>
