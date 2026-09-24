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
  <div v-if="pageMutationBusy" ref="mutationOverlay" class="mutation-shield" role="status" aria-live="assertive" aria-atomic="true" tabindex="-1">
    <div class="mutation-progress">
      <div class="journey-loader" aria-hidden="true">
        <svg class="journey-route" viewBox="0 0 190 112" focusable="false">
          <defs>
            <linearGradient id="journey-gradient" x1="0" y1="0" x2="1" y2="0">
              <stop offset="0" stop-color="#7f6f59" />
              <stop offset=".72" stop-color="#55bcae" />
              <stop offset="1" stop-color="#f0a24b" />
            </linearGradient>
          </defs>
          <path class="route-bed" d="M14 83 C40 45 60 102 91 68 C121 35 145 51 176 80" />
          <path class="route-drawn" pathLength="1" d="M14 83 C40 45 60 102 91 68 C121 35 145 51 176 80" />
          <circle class="route-node node-start" cx="14" cy="83" r="4" />
          <circle class="route-node node-middle" cx="91" cy="68" r="4" />
          <circle class="route-node node-finish" cx="176" cy="80" r="5" />
        </svg>
        <span class="journey-beacon"></span>
        <span class="journey-logo"><img src="/favicon.svg" alt=""></span>
        <span class="dog-runner">
          <span class="puppy-sprite"></span>
        </span>
      </div>
      <strong>正在处理</strong>
      <span>请稍候，完成后将自动更新</span>
    </div>
  </div>
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
.journey-loader{position:relative;width:190px;height:158px}
.journey-route{position:absolute;top:0;left:0;width:100%;height:112px;overflow:visible}
.route-bed,.route-drawn{fill:none;stroke-linecap:round;stroke-linejoin:round}
.route-bed{stroke:rgba(127,111,89,.14);stroke-width:5}
.route-drawn{stroke:url(#journey-gradient);stroke-width:4;stroke-dasharray:1;stroke-dashoffset:1;filter:drop-shadow(0 2px 5px rgba(127,111,89,.28));animation:route-draw 2.45s cubic-bezier(.45,0,.2,1) infinite}
.route-node{fill:#fffaf0;stroke:#7f6f59;stroke-width:2;opacity:.38;transform:scale(.82);transform-box:fill-box;transform-origin:center;animation:route-node-pulse 2.45s ease-in-out infinite both}
.node-middle{animation-delay:.55s}.node-finish{stroke:#f0a24b;animation-delay:1.2s}
.journey-beacon{position:absolute;left:0;top:0;width:12px;height:12px;border:2px solid rgba(255,255,255,.94);border-radius:50%;background:#7f6f59;box-shadow:0 0 0 5px rgba(127,111,89,.14),0 0 17px rgba(127,111,89,.62);offset-path:path("M14 83 C40 45 60 102 91 68 C121 35 145 51 176 80");offset-anchor:center;animation:beacon-travel 2.45s cubic-bezier(.45,0,.2,1) infinite}
.journey-logo{position:absolute;top:0;left:50%;z-index:3;display:grid;width:60px;height:60px;place-items:center;border:1px solid rgba(255,255,255,.9);border-radius:19px;background:rgba(255,255,255,.83);box-shadow:0 13px 30px rgba(20,93,91,.2),inset 0 1px 0 rgba(255,255,255,.95);animation:logo-float 1.8s ease-in-out infinite}
.journey-logo::after{content:"";position:absolute;right:8px;bottom:7px;width:8px;height:8px;border-radius:50%;background:#f0a24b;box-shadow:0 0 0 4px rgba(240,162,75,.15),0 0 12px rgba(240,162,75,.62);animation:logo-signal 1.8s ease-in-out infinite}
.journey-logo img{width:48px;height:48px;border-radius:14px;filter:drop-shadow(0 6px 9px rgba(20,93,91,.16))}
.dog-runner{position:absolute;top:72px;left:50%;z-index:4;display:block;width:60px;height:80px;pointer-events:none;filter:drop-shadow(0 5px 5px rgba(82,51,28,.2));animation:dog-run 4.8s ease-in-out infinite;will-change:transform}
.dog-runner::after{content:"";position:absolute;right:5px;bottom:0;width:48px;height:5px;border-radius:50%;background:rgba(82,51,28,.14);filter:blur(1px);animation:dog-shadow .34s ease-in-out infinite alternate}
.puppy-sprite{display:block;width:100%;height:100%;background:url('/puppy-run-sprite.webp') 0 0/400% 100% no-repeat;animation:puppy-frames .64s steps(1,end) infinite,dog-bob .34s ease-in-out infinite alternate;will-change:transform,background-position}
@keyframes session-pulse{50%{transform:translateY(-3px);opacity:.72}}
@keyframes route-draw{0%{stroke-dashoffset:1;opacity:.25}72%{stroke-dashoffset:0;opacity:1}88%{stroke-dashoffset:0;opacity:1}100%{stroke-dashoffset:0;opacity:.18}}
@keyframes beacon-travel{0%{offset-distance:0%;opacity:0;transform:scale(.65)}10%{opacity:1}72%{offset-distance:100%;opacity:1;transform:scale(1)}88%{offset-distance:100%;opacity:1;transform:scale(1.18)}100%{offset-distance:100%;opacity:0;transform:scale(.65)}}
@keyframes route-node-pulse{0%,34%,100%{transform:scale(.82);opacity:.38}54%,78%{transform:scale(1.18);opacity:1}}
@keyframes logo-float{0%,100%{transform:translate(-50%,0)}50%{transform:translate(-50%,-6px)}}
@keyframes logo-signal{0%,100%{transform:scale(.72);opacity:.55}50%{transform:scale(1.12);opacity:1}}
@keyframes dog-run{0%{transform:translateX(-96px) scaleX(1)}47%{transform:translateX(24px) scaleX(1)}50%{transform:translateX(24px) scaleX(-1)}97%{transform:translateX(-96px) scaleX(-1)}100%{transform:translateX(-96px) scaleX(1)}}
@keyframes dog-bob{to{transform:translateY(-4px) rotate(-2deg)}}
@keyframes dog-shadow{to{transform:scaleX(.62);opacity:.45}}
@keyframes puppy-frames{0%,24.99%{background-position:0 0}25%,49.99%{background-position:33.333% 0}50%,74.99%{background-position:66.666% 0}75%,100%{background-position:100% 0}}
@keyframes route-draw-soft{0%{stroke-dashoffset:1;opacity:.42}78%,92%{stroke-dashoffset:0;opacity:1}100%{stroke-dashoffset:0;opacity:.42}}
@keyframes beacon-travel-soft{0%{offset-distance:0%;opacity:0}12%,84%{opacity:.92}88%{offset-distance:100%;opacity:.92}100%{offset-distance:100%;opacity:0}}
@keyframes route-node-soft{0%,100%{opacity:.42}50%{opacity:1}}
@keyframes logo-float-soft{0%,100%{transform:translate(-50%,0)}50%{transform:translate(-50%,-2px)}}
@keyframes logo-signal-soft{0%,100%{opacity:.48}50%{opacity:1}}
@keyframes dog-run-soft{0%{transform:translateX(-82px) scaleX(1)}47%{transform:translateX(10px) scaleX(1)}50%{transform:translateX(10px) scaleX(-1)}97%{transform:translateX(-82px) scaleX(-1)}100%{transform:translateX(-82px) scaleX(1)}}
@media(prefers-reduced-motion:reduce){
  .session-loading img{animation:none}
  .route-drawn{animation:route-draw-soft 3.6s ease-in-out infinite!important}
  .route-node{transform:scale(1);animation:route-node-soft 3.6s ease-in-out infinite both!important}
  .journey-beacon{animation:beacon-travel-soft 3.6s linear infinite!important}
  .journey-logo{animation:logo-float-soft 2.8s ease-in-out infinite!important}
  .journey-logo::after{animation:logo-signal-soft 2.8s ease-in-out infinite!important}
  .dog-runner{animation:dog-run-soft 7.2s ease-in-out infinite!important}
  .puppy-sprite{animation:puppy-frames 1s steps(1,end) infinite,dog-bob .72s ease-in-out infinite alternate!important}
  .dog-runner::after{animation-duration:.72s!important}
}
</style>
