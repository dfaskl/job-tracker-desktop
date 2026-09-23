<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
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
const pointerX = ref(0)
const pointerY = ref(0)

const illustrationStyle = computed(() => ({
  '--look-x': `${pointerX.value * 7}px`,
  '--look-y': `${pointerY.value * 5}px`,
  '--tilt': `${pointerX.value * 3.5}deg`,
  '--tilt-reverse': `${pointerX.value * -2.5}deg`,
  '--tilt-soft': `${pointerX.value * 1.6}deg`,
  '--tilt-soft-reverse': `${pointerX.value * -1.2}deg`
}))
const illustrationState = computed(() => ({
  'is-email-active': emailFocused.value,
  'is-password-private': !showPassword.value && (passwordFocused.value || password.value.length > 0),
  'is-password-visible': showPassword.value && (passwordFocused.value || password.value.length > 0)
}))

function trackPointer(event: PointerEvent) {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  pointerX.value = Math.max(-1, Math.min(1, event.clientX / window.innerWidth * 2 - 1))
  pointerY.value = Math.max(-1, Math.min(1, event.clientY / window.innerHeight * 2 - 1))
}

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
  <main id="login-page" class="login-page" @pointermove="trackPointer">
    <section class="login-story" aria-labelledby="welcome-title">
      <div class="login-brand"><span><img src="/favicon.svg" alt=""></span><strong>求职进度本</strong></div>
      <h1 id="welcome-title" class="sr-only">求职进度本登录</h1>

      <div class="character-stage" :class="illustrationState" :style="illustrationStyle" aria-hidden="true">
        <div class="creature creature-purple"><div class="face two-eyes"><i><b></b></i><i><b></b></i></div></div>
        <div class="creature creature-charcoal"><div class="face two-eyes"><i><b></b></i><i><b></b></i></div></div>
        <div class="creature creature-orange"><div class="face dot-eyes"><i></i><i></i></div></div>
        <div class="creature creature-yellow"><div class="face dot-eyes"><i></i><i></i><b class="mouth"></b></div></div>
      </div>

      <p class="story-note">把投递、笔试与面试放进一条清晰的求职路径。</p>
    </section>

    <section class="login-entry" aria-labelledby="login-title">
      <div class="tech-badge" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="m8 9-4 3 4 3M16 9l4 3-4 3M14 5l-4 14" /></svg><span>Vue + Java</span></div>
      <div class="entry-inner">
        <div class="mobile-brand"><span><img src="/favicon.svg" alt=""></span><strong>求职进度本</strong></div>
        <header class="entry-heading">
          <h2 id="login-title">{{ mode === 'login' ? '欢迎回来' : '创建你的账号' }}</h2>
          <p>{{ mode === 'login' ? '继续查看你的求职计划' : '保存求职进度，规划下一步' }}</p>
        </header>

        <form @submit.prevent="submit">
          <label for="login-email">邮箱地址
            <input id="login-email" ref="emailInput" v-model.trim="email" type="email" autocomplete="username" inputmode="email" placeholder="name@example.com" required autofocus @focus="emailFocused=true" @blur="emailFocused=false">
          </label>
          <label for="login-password">{{ mode === 'login' ? '登录密码' : '设置密码' }}
            <span class="password-field"><input id="login-password" v-model="password" :type="showPassword?'text':'password'" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" placeholder="••••••••••" minlength="10" required @focus="passwordFocused=true" @blur="passwordFocused=false"><button type="button" :aria-label="showPassword?'隐藏密码':'显示密码'" :title="showPassword?'隐藏密码':'显示密码'" @click="showPassword=!showPassword"><AppIcon :name="showPassword?'eye-off':'eye'" /></button></span>
          </label>
          <label v-if="mode === 'register'" for="registration-code">注册码 <small>如管理员已启用</small>
            <input id="registration-code" v-model.trim="registrationCode" autocomplete="one-time-code" placeholder="输入管理员提供的注册码" @focus="emailFocused=true" @blur="emailFocused=false">
          </label>
          <p v-if="message" class="login-error" role="alert">{{ message }}</p>
          <button class="login-submit" type="submit" :disabled="submitting">
            <span>{{ submitting ? '正在处理…' : mode === 'login' ? '进入账户' : '注册并进入' }}</span><span aria-hidden="true">{{ submitting ? '请稍候' : mode === 'login' ? '进入账户' : '开始使用' }}<AppIcon name="chevron-right" :size="17" /></span>
          </button>
        </form>

        <p class="mode-line">{{ mode === 'login' ? '还没有账号？' : '已经有账号？' }} <button class="mode-switch" type="button" :disabled="submitting" @click="switchMode">{{ mode === 'login' ? '立即创建' : '返回登录' }}</button></p>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page{--ink:#080807;--stage:#d8d8d8;--purple:#6c3ff5;--orange:#ff9b6b;--yellow:#e8d754;--charcoal:#2d2d2d;display:grid;min-height:100dvh;grid-template-columns:1fr 1fr;background:var(--ink);font-family:"Fira Sans","Noto Sans SC",sans-serif}
.login-story{position:relative;display:flex;height:100dvh;min-height:620px;flex-direction:column;justify-content:space-between;padding:clamp(28px,3.4vw,52px);overflow:hidden;color:#111827;background-color:var(--stage);background-image:linear-gradient(rgba(255,255,255,.12) 1px,transparent 1px),linear-gradient(90deg,rgba(255,255,255,.12) 1px,transparent 1px);background-size:20px 20px}
.login-story::after{content:"";position:absolute;inset:0;background:radial-gradient(circle at 65% 34%,rgba(255,255,255,.3),transparent 36%),linear-gradient(120deg,rgba(255,255,255,.12),rgba(119,119,119,.05));pointer-events:none}
.login-brand,.mobile-brand{position:relative;z-index:3;display:flex;align-items:center;gap:11px;font-size:19px}.login-brand>span,.mobile-brand>span{display:grid;width:34px;height:34px;place-items:center;border-radius:9px;background:#fff;box-shadow:0 4px 13px rgba(0,0,0,.08)}.login-brand img,.mobile-brand img{width:28px;height:28px;border-radius:7px}.login-brand strong,.mobile-brand strong{font-family:"Fira Code","Noto Sans SC",sans-serif;letter-spacing:-.04em}.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
.character-stage{position:absolute;left:50%;bottom:clamp(96px,14vh,150px);z-index:2;width:min(550px,78%);height:min(480px,54vh);transform:translateX(-50%)}.creature{position:absolute;bottom:0;transform-origin:bottom center;transition:height .7s cubic-bezier(.2,.8,.2,1),transform .7s cubic-bezier(.2,.8,.2,1)}.creature-purple{left:13%;z-index:1;width:33%;height:88%;border-radius:11px 11px 0 0;background:var(--purple);transform:skewX(var(--tilt))}.creature-charcoal{left:44%;z-index:2;width:22%;height:68%;border-radius:9px 9px 0 0;background:var(--charcoal);transform:skewX(var(--tilt-reverse))}.creature-orange{left:2%;z-index:3;width:44%;height:43%;border-radius:999px 999px 0 0;background:var(--orange);transform:skewX(var(--tilt-soft))}.creature-yellow{left:57%;z-index:4;width:27%;height:50%;border-radius:999px 999px 0 0;background:var(--yellow);transform:skewX(var(--tilt-soft-reverse))}
.face{position:absolute;display:flex;align-items:center;gap:30px;transform:translate(var(--look-x),var(--look-y));transition:transform .12s ease-out,left .55s cubic-bezier(.2,.8,.2,1),top .55s cubic-bezier(.2,.8,.2,1)}.two-eyes{top:9%;left:19%}.two-eyes i{display:grid;width:18px;height:18px;place-items:center;overflow:hidden;border-radius:50%;background:#fff;animation:login-blink 6.2s infinite}.creature-charcoal .two-eyes{top:8%;left:18%;gap:24px}.creature-charcoal .two-eyes i{width:16px;height:16px;animation-delay:1.7s}.two-eyes b{width:7px;height:7px;border-radius:50%;background:var(--charcoal);transform:translate(calc(var(--look-x) * .45),calc(var(--look-y) * .45));transition:transform .12s ease-out}.dot-eyes{gap:30px}.dot-eyes i{width:12px;height:12px;border-radius:50%;background:var(--charcoal)}.creature-orange .dot-eyes{top:39%;left:32%}.creature-yellow .dot-eyes{top:14%;left:27%;gap:26px}.mouth{position:absolute;top:47px;left:-8px;width:80px;height:4px;border-radius:999px;background:var(--charcoal)}
.character-stage.is-email-active .creature-purple{height:96%;transform:skewX(-8deg) translateX(28px)}.character-stage.is-email-active .creature-charcoal{transform:skewX(8deg) translateX(14px)}.character-stage.is-email-active .face{--look-x:4px;--look-y:5px}.character-stage.is-password-private .creature-purple{height:96%;transform:skewX(-10deg) translateX(35px)}.character-stage.is-password-private .creature-charcoal{transform:skewX(7deg) translateX(18px)}.character-stage.is-password-private .face{--look-x:-5px;--look-y:-4px}.character-stage.is-password-visible .face{--look-x:-5px;--look-y:5px}.character-stage.is-password-visible .creature-purple,.character-stage.is-password-visible .creature-charcoal,.character-stage.is-password-visible .creature-orange,.character-stage.is-password-visible .creature-yellow{transform:skewX(0)}
.story-note{position:relative;z-index:3;max-width:390px;margin:0;color:#4b5563;font-size:13px;line-height:1.6}
.login-entry{position:relative;display:grid;height:100dvh;min-height:620px;place-items:center;padding:70px 32px 40px;overflow:auto;color:#f5f5f4;background:var(--ink)}.login-entry::before{content:"";position:absolute;inset:0;background:radial-gradient(circle at 64% 42%,rgba(22,140,158,.055),transparent 33%);pointer-events:none}.tech-badge{position:absolute;top:24px;right:28px;display:flex;align-items:center;gap:8px;padding:9px 13px;border:1px solid #2f2f2d;border-radius:999px;color:#d6d3d1;background:rgba(20,20,19,.6);font-size:12px;font-weight:600}.tech-badge svg{width:16px;height:16px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}.entry-inner{position:relative;z-index:1;width:min(420px,100%)}.mobile-brand{display:none}.entry-heading{margin-bottom:42px;text-align:center}.entry-heading h2{margin:0 0 8px;color:#fff;font-size:31px;line-height:1.25;letter-spacing:-.035em}.entry-heading p{margin:0;color:#8f8f8a;font-size:14px}.entry-inner form{display:grid;gap:21px}.entry-inner label{display:grid;gap:8px;color:#ececea;font-size:13px;font-weight:600}.entry-inner label small{margin-left:5px;color:#777772;font-weight:400}#login-page .login-entry input{width:100%;height:49px;padding:0 16px;border:1px solid #30302e;border-radius:999px;outline:0;color:#f5f5f4;background:#0c0c0b;font:inherit;transition:border-color .18s ease,box-shadow .18s ease,background .18s ease}#login-page .login-entry input::placeholder{color:#74746f}#login-page .login-entry input:hover{border-color:#444440}#login-page .login-entry input:focus{border-color:#168c9e;box-shadow:0 0 0 3px rgba(22,140,158,.16);background:#10100f}.password-field{position:relative;display:block}#login-page .password-field input{padding-right:54px}#login-page .password-field button{position:absolute;top:50%;right:3px;display:grid;width:44px;min-width:44px;height:44px;min-height:44px;place-items:center;padding:0;transform:translateY(-50%);border:0;border-radius:50%;color:#9b9b96;background:transparent}#login-page .password-field button:hover{color:#fff;background:#20201e}#login-page .password-field button:focus-visible{outline:2px solid #168c9e;outline-offset:1px}
.login-error{margin:0;padding:10px 13px;border:1px solid rgba(239,100,91,.34);border-radius:10px;color:#ffb4ae;background:rgba(174,47,39,.14);font-size:12px;line-height:1.5}#login-page .login-submit{position:relative;width:100%;height:50px;margin-top:4px;overflow:hidden;border:1px solid #363633;border-radius:999px;color:#f7f7f5;background:#0c0c0b}.login-submit>span{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;gap:7px;transition:transform .3s cubic-bezier(.2,.8,.2,1),opacity .25s ease}.login-submit>span:last-child{color:#fff;background:#168c9e;transform:translateX(-12%);opacity:0}.login-submit:not(:disabled):hover>span:first-child{transform:translateX(100%);opacity:0}.login-submit:not(:disabled):hover>span:last-child{transform:translateX(0);opacity:1}.login-submit:not(:disabled):active{transform:scale(.985)}.login-submit:focus-visible{outline:3px solid rgba(73,190,196,.45);outline-offset:3px}.login-submit:disabled{cursor:wait;opacity:.58}.mode-line{margin:30px 0 0;color:#858580;font-size:13px;text-align:center}#login-page .mode-switch{min-height:auto;padding:4px;border:0;border-radius:4px;color:#f4f4f2;background:transparent;font-weight:650}.mode-switch:hover{text-decoration:underline;text-underline-offset:4px}.mode-switch:focus-visible{outline:2px solid #168c9e;outline-offset:2px}
@keyframes login-blink{0%,47%,51%,100%{transform:scaleY(1)}49%{transform:scaleY(.12)}}
@media(max-width:980px){.login-page{grid-template-columns:1fr}.login-story{display:none}.login-entry{min-height:100dvh;height:auto;padding:92px 24px 44px}.mobile-brand{display:flex;justify-content:center;margin-bottom:46px}.mobile-brand>span{background:#f5f5f4}.tech-badge{top:18px;right:18px}.entry-heading{margin-bottom:34px}}
@media(max-width:480px){.login-entry{padding-inline:18px}.entry-heading h2{font-size:28px}.mobile-brand{margin-bottom:38px}.mobile-brand strong{font-size:17px}.tech-badge span{display:none}.tech-badge{padding:9px}.entry-inner form{gap:18px}}
@media(prefers-reduced-motion:reduce){.creature,.face,.two-eyes b,.login-submit>span{transition:none}.two-eyes i{animation:none}}
</style>
