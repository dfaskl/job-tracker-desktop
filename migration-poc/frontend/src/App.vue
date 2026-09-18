<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import ProductShell from './ProductShell.vue'
import LoginPage from './LoginPage.vue'
import GlobalToastBridge from './GlobalToastBridge.vue'
import { useJobTrackerStore } from './jobTrackerStore'
import { pageMutationBusy } from './requestActivity'

const store = useJobTrackerStore()
const ready = ref(false)
const mutationOverlay = ref<HTMLElement | null>(null)
const allowedPages = new Set(['home', 'applications', 'calendar', 'mail', 'stats', 'settings', 'admin'])
const requestedRoute = ref(readRequestedRoute())
let focusBeforeMutation: HTMLElement | null = null
let documentOverflowBeforeMutation = ''

function readRouteFromHash() {
  return window.location.hash.replace(/^#\/?/, '') || 'home'
}

function pageFromRoute(route: string) {
  return route.split('?')[0]
}

function readRequestedRoute() {
  const route = readRouteFromHash()
  const page = pageFromRoute(route)
  const fromSession = window.sessionStorage.getItem('job-tracker-requested-page') || ''
  const sessionPage = pageFromRoute(fromSession)
  if (page === 'login' && allowedPages.has(sessionPage)) return fromSession
  return allowedPages.has(page) ? route : 'home'
}

function replaceHash(route: string) {
  window.history.replaceState(null, '', window.location.pathname + window.location.search + '#/' + route)
}

function enforceAuthRoute() {
  if (!ready.value) return
  const route = readRouteFromHash()
  const page = pageFromRoute(route)
  if (!store.user.value) {
    if (allowedPages.has(page)) {
      requestedRoute.value = route
      window.sessionStorage.setItem('job-tracker-requested-page', route)
    }
    if (page !== 'login') replaceHash('login')
    return
  }
  if (page === 'login' || !allowedPages.has(page)) {
    replaceHash(requestedRoute.value || 'home')
    window.sessionStorage.removeItem('job-tracker-requested-page')
  }
}

onMounted(async () => {
  await store.initialize()
  ready.value = true
  enforceAuthRoute()
  window.addEventListener('hashchange', enforceAuthRoute)
})
watch(store.user, enforceAuthRoute)
watch(pageMutationBusy, async busy => {
  if (busy) {
    focusBeforeMutation = document.activeElement instanceof HTMLElement ? document.activeElement : null
    focusBeforeMutation?.blur()
    documentOverflowBeforeMutation = document.documentElement.style.overflow
    document.documentElement.style.overflow = 'hidden'
    await nextTick()
    mutationOverlay.value?.focus({ preventScroll: true })
    return
  }
  document.documentElement.style.overflow = documentOverflowBeforeMutation
  await nextTick()
  if (focusBeforeMutation?.isConnected) focusBeforeMutation.focus({ preventScroll: true })
  focusBeforeMutation = null
})
onBeforeUnmount(() => {
  window.removeEventListener('hashchange', enforceAuthRoute)
  document.documentElement.style.overflow = documentOverflowBeforeMutation
})
</script>

<template>
  <div class="app-stage" :inert="pageMutationBusy || undefined" :aria-hidden="pageMutationBusy ? 'true' : undefined" :aria-busy="pageMutationBusy">
    <div v-if="!ready" class="session-loading" role="status" aria-live="polite">
      <img src="/favicon.svg" alt=""><span>正在确认登录状态…</span>
    </div>
    <ProductShell v-else-if="store.user.value" />
    <LoginPage v-else />
    <GlobalToastBridge />
  </div>
  <Transition name="submission-shield">
    <div v-if="pageMutationBusy" ref="mutationOverlay" class="mutation-shield" role="status" aria-live="assertive" aria-atomic="true" tabindex="-1">
      <div class="mutation-progress">
        <div class="submission-loader" aria-hidden="true">
          <span class="loader-orbit orbit-primary"></span>
          <span class="loader-orbit orbit-accent"></span>
          <img src="/favicon.svg" alt="">
        </div>
        <strong>正在提交修改</strong>
        <span>正在同步最新数据，请稍候</span>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.app-stage{min-height:100vh}
.session-loading{display:grid;min-height:100vh;place-content:center;justify-items:center;gap:14px;color:var(--color-muted-foreground);background:var(--color-background)}
.session-loading img{width:52px;height:52px;border-radius:13px;animation:session-pulse 1.2s ease-in-out infinite}
.mutation-shield{position:fixed;inset:0;z-index:1000;display:grid;place-items:center;padding:24px;overflow:hidden;overscroll-behavior:contain;touch-action:none;cursor:wait;outline:none;background:rgba(224,240,238,.64);backdrop-filter:blur(16px) saturate(.72);-webkit-backdrop-filter:blur(16px) saturate(.72)}
.mutation-shield::before{content:"";position:absolute;width:min(520px,86vw);height:min(520px,86vw);border-radius:50%;background:radial-gradient(circle,rgba(36,158,143,.12),rgba(255,255,255,0) 68%);pointer-events:none}
.mutation-progress{position:relative;display:grid;min-width:min(310px,calc(100vw - 48px));justify-items:center;gap:9px;padding:31px 36px 28px;border:1px solid rgba(255,255,255,.8);border-radius:28px;color:#143f43;background:rgba(247,253,251,.82);box-shadow:0 28px 80px rgba(18,75,78,.2),inset 0 1px 0 rgba(255,255,255,.9);text-align:center}
.mutation-progress strong{margin-top:7px;font-family:"Noto Serif SC","Microsoft YaHei UI",sans-serif;font-size:25px;font-weight:700;letter-spacing:.05em}
.mutation-progress>span{color:#527174;font-size:13px;font-weight:600;letter-spacing:.02em}
.submission-loader{position:relative;display:grid;width:116px;height:116px;place-items:center}
.submission-loader::before{content:"";position:absolute;inset:13px;border-radius:50%;background:rgba(255,255,255,.8);box-shadow:0 10px 30px rgba(25,110,107,.16),inset 0 0 0 1px rgba(44,159,145,.12)}
.submission-loader img{position:relative;width:54px;height:54px;border-radius:15px;filter:drop-shadow(0 7px 10px rgba(20,93,91,.2));animation:loader-breathe 1.5s ease-in-out infinite}
.loader-orbit{position:absolute;inset:2px;display:block;border:4px solid transparent;border-radius:50%;transform-origin:50% 50%;will-change:transform;animation:loader-spin 1.05s linear infinite}
.loader-orbit::after{content:"";position:absolute;width:10px;height:10px;border:2px solid rgba(255,255,255,.88);border-radius:50%;box-shadow:0 0 0 4px rgba(22,142,137,.12),0 0 14px currentColor}
.orbit-primary{color:#168e89;border-top-color:#168e89;border-right-color:rgba(22,142,137,.32)}
.orbit-primary::after{top:-6px;left:50%;background:#168e89;transform:translateX(-50%)}
.orbit-accent{inset:10px;color:#f0a24b;border-width:3px;border-bottom-color:#f0a24b;border-left-color:rgba(240,162,75,.3);animation-direction:reverse;animation-duration:1.55s}
.orbit-accent::after{right:4px;bottom:-4px;width:8px;height:8px;background:#f0a24b}
.submission-shield-enter-active,.submission-shield-leave-active{transition:opacity .2s ease}
.submission-shield-enter-active .mutation-progress,.submission-shield-leave-active .mutation-progress{transition:transform .24s cubic-bezier(.2,.8,.2,1),opacity .18s ease}
.submission-shield-enter-from,.submission-shield-leave-to{opacity:0}
.submission-shield-enter-from .mutation-progress,.submission-shield-leave-to .mutation-progress{transform:scale(.96);opacity:0}
@keyframes session-pulse{50%{transform:translateY(-3px);opacity:.72}}
@keyframes loader-spin{from{transform:rotate(0deg)}to{transform:rotate(360deg)}}
@keyframes loader-breathe{50%{transform:scale(1.06)}}
@keyframes loader-soft-pulse{0%,100%{opacity:.42}50%{opacity:1}}
@keyframes loader-icon-glow{0%,100%{filter:drop-shadow(0 5px 8px rgba(20,93,91,.12))}50%{filter:drop-shadow(0 8px 15px rgba(20,93,91,.34))}}
@media(prefers-reduced-motion:reduce){.session-loading img{animation:none}.loader-orbit{transform:none!important;animation:loader-soft-pulse 1.35s ease-in-out infinite!important}.orbit-accent{animation-delay:.42s!important}.submission-loader img{transform:none!important;animation:loader-icon-glow 1.7s ease-in-out infinite!important}.submission-shield-enter-active,.submission-shield-leave-active,.submission-shield-enter-active .mutation-progress,.submission-shield-leave-active .mutation-progress{transition-duration:.01ms}}
</style>
