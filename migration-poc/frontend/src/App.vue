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
              <stop offset="0" stop-color="#168e89" />
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
          <svg class="running-dog" viewBox="0 0 84 56" focusable="false" aria-hidden="true">
            <g class="dog-tail">
              <path class="tail-outline" d="M21 29C9 31 3 23 8 15c4-7 14-6 17 1 2 5-2 9-8 8" />
              <path class="tail-fur" d="M21 29C9 31 3 23 8 15c4-7 14-6 17 1 2 5-2 9-8 8" />
            </g>
            <g class="dog-leg gait-a leg-rear-far"><path class="leg-far" d="M27 34c0 6-3 10-6 14-1 2 1 4 4 4h5c2 0 3-2 2-4l2-13Z" /></g>
            <g class="dog-leg gait-b leg-front-far"><path class="leg-far" d="M52 34c1 6-1 11-3 15-1 2 1 3 3 3h5c3 0 4-2 2-4l-1-14Z" /></g>
            <g class="dog-leg gait-b leg-rear-near"><path class="leg-near" d="M34 35c1 6 0 11-2 15-1 2 1 4 4 4h5c3 0 4-2 2-4l-2-15Z" /></g>
            <g class="dog-leg gait-a leg-front-near"><path class="leg-near" d="M58 34c2 6 2 11 1 15 0 3 2 4 5 4h4c3 0 4-3 1-5l-4-15Z" /></g>
            <path class="dog-body" d="M18 25c1-7 8-11 15-10 5-5 12-2 16-1 7-1 14 3 16 9 5 3 6 9 2 14-3 5-10 5-15 4-5 4-12 3-16 1-6 2-13-1-16-5-4-3-5-8-2-12Z" />
            <path class="dog-cream dog-belly" d="M24 35c9 4 22 6 34 1-3 7-10 8-16 8-8 1-15-2-18-9Z" />
            <path class="dog-body-tuft" d="m27 19 4 4 4-6 5 5 5-6 5 5" />
            <path class="dog-chest" d="M55 25c-2 4-3 9-1 15l-5-2-2 5-4-4-4 3c2-9 5-15 10-18Z" />
            <path class="dog-head" d="M49 12c2-7 9-10 15-7 7-3 15 1 16 8 5 3 5 10 1 14 1 7-7 12-14 11-8 2-17-3-18-11-4-4-4-11 0-15Z" />
            <path class="dog-ear ear-back" d="M52 13c-7-7-1-14 6-10 3 2 4 6 3 11Z" />
            <path class="dog-ear ear-front" d="M70 8c5-5 12-1 10 5-1 5-5 9-9 11l-4-9Z" />
            <path class="dog-blaze" d="M61 6c5-2 9 0 11 3l-4 5 3 6-4 7-5-3-4-9Z" />
            <path class="dog-face-tuft" d="m55 9 4 2 3-5 4 5 4-3 2 5" />
            <ellipse class="dog-eye eye-back" cx="62" cy="18" rx="3.2" ry="4" />
            <ellipse class="dog-eye eye-front" cx="72" cy="18" rx="3.7" ry="4.5" />
            <circle class="dog-eye-glint" cx="63" cy="16.7" r="1" />
            <circle class="dog-eye-glint" cx="73" cy="16.5" r="1.1" />
            <path class="dog-cream dog-muzzle" d="M60 24c2-4 7-5 10-2 4-2 10 0 10 5 0 5-6 8-11 6-5 2-11-1-9-9Z" />
            <path class="dog-nose" d="M76 22c4-1 6 2 3 5l-3 2-3-3c-1-2 1-4 3-4Z" />
            <path class="dog-mouth" d="M76 28c-1 4-7 5-10 1" />
            <path class="dog-tongue" d="M70 31c2 0 4-1 5-2 0 4-1 6-3 6s-3-2-2-4Z" />
            <path class="dog-collar" d="M51 29c5 4 12 6 19 5" />
            <path class="dog-tag" d="M61 34c4 0 5 3 3 6-2 2-5 1-6-2-1-2 1-4 3-4Z" />
          </svg>
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
.journey-loader{position:relative;width:190px;height:142px}
.journey-route{position:absolute;top:0;left:0;width:100%;height:112px;overflow:visible}
.route-bed,.route-drawn{fill:none;stroke-linecap:round;stroke-linejoin:round}
.route-bed{stroke:rgba(22,142,137,.12);stroke-width:5}
.route-drawn{stroke:url(#journey-gradient);stroke-width:4;stroke-dasharray:1;stroke-dashoffset:1;filter:drop-shadow(0 2px 5px rgba(22,142,137,.26));animation:route-draw 2.45s cubic-bezier(.45,0,.2,1) infinite}
.route-node{fill:#effaf7;stroke:#168e89;stroke-width:2;opacity:.38;transform:scale(.82);transform-box:fill-box;transform-origin:center;animation:route-node-pulse 2.45s ease-in-out infinite both}
.node-middle{animation-delay:.55s}.node-finish{stroke:#f0a24b;animation-delay:1.2s}
.journey-beacon{position:absolute;left:0;top:0;width:12px;height:12px;border:2px solid rgba(255,255,255,.94);border-radius:50%;background:#168e89;box-shadow:0 0 0 5px rgba(22,142,137,.13),0 0 17px rgba(22,142,137,.72);offset-path:path("M14 83 C40 45 60 102 91 68 C121 35 145 51 176 80");offset-anchor:center;animation:beacon-travel 2.45s cubic-bezier(.45,0,.2,1) infinite}
.journey-logo{position:absolute;top:0;left:50%;z-index:3;display:grid;width:60px;height:60px;place-items:center;border:1px solid rgba(255,255,255,.9);border-radius:19px;background:rgba(255,255,255,.83);box-shadow:0 13px 30px rgba(20,93,91,.2),inset 0 1px 0 rgba(255,255,255,.95);animation:logo-float 1.8s ease-in-out infinite}
.journey-logo::after{content:"";position:absolute;right:8px;bottom:7px;width:8px;height:8px;border-radius:50%;background:#f0a24b;box-shadow:0 0 0 4px rgba(240,162,75,.15),0 0 12px rgba(240,162,75,.62);animation:logo-signal 1.8s ease-in-out infinite}
.journey-logo img{width:48px;height:48px;border-radius:14px;filter:drop-shadow(0 6px 9px rgba(20,93,91,.16))}
.dog-runner{position:absolute;top:84px;left:50%;z-index:4;display:block;width:84px;height:56px;pointer-events:none;filter:drop-shadow(0 5px 5px rgba(82,51,28,.22));animation:dog-run 4.8s ease-in-out infinite;will-change:transform}
.dog-runner::after{content:"";position:absolute;right:5px;bottom:0;width:54px;height:5px;border-radius:50%;background:rgba(82,51,28,.15);filter:blur(1px);animation:dog-shadow .34s ease-in-out infinite alternate}
.running-dog{display:block;width:100%;height:100%;overflow:visible;animation:dog-bob .34s ease-in-out infinite alternate;will-change:transform}
.dog-tail{transform-box:view-box;transform-origin:21px 29px;animation:dog-tail-wag .28s ease-in-out infinite alternate;will-change:transform}.tail-outline,.tail-fur{fill:none;stroke-linecap:round;stroke-linejoin:round}.tail-outline{stroke:#8e5b36;stroke-width:10}.tail-fur{stroke:#e9bd78;stroke-width:7}
.dog-leg{transform-box:view-box;will-change:transform}.gait-a{animation:dog-gait-a .48s linear infinite}.gait-b{animation:dog-gait-b .48s linear infinite}.leg-rear-far{transform-origin:29px 34px}.leg-front-far{transform-origin:55px 34px}.leg-rear-near{transform-origin:36px 35px}.leg-front-near{transform-origin:61px 34px}.leg-far,.leg-near{stroke:#8b5735;stroke-width:1.25;stroke-linejoin:round}.leg-far{fill:#c98b53}.leg-near{fill:#edbd7a}
.dog-body,.dog-head{fill:#edbd7a;stroke:#8b5735;stroke-width:1.5;stroke-linejoin:round}.dog-cream{fill:#fff0ce}.dog-body-tuft,.dog-face-tuft{fill:none;stroke:#fff1d2;stroke-width:2.3;stroke-linecap:round;stroke-linejoin:round}.dog-chest{fill:#fff2d4;stroke:#dba76b;stroke-width:.8;stroke-linejoin:round}
.dog-ear{stroke:#8b5735;stroke-width:1.25;stroke-linejoin:round}.ear-back{fill:#c88750}.ear-front{fill:#d89a5e}.dog-blaze{fill:#fff4d9;opacity:.98}
.dog-eye{fill:#2f201b;stroke:#130e0d;stroke-width:.7}.dog-eye-glint{fill:#fff}.dog-muzzle{stroke:#d8a46e;stroke-width:.65}.dog-nose{fill:#2a1d1d}.dog-mouth{fill:none;stroke:#6d3d31;stroke-width:1.25;stroke-linecap:round}.dog-tongue{fill:#ec7b85;stroke:#8d4b4e;stroke-width:.65}
.dog-collar{fill:none;stroke:#b84537;stroke-width:3.5;stroke-linecap:round}.dog-tag{fill:#e5a238;stroke:#fff2c9;stroke-width:.8}
@keyframes session-pulse{50%{transform:translateY(-3px);opacity:.72}}
@keyframes route-draw{0%{stroke-dashoffset:1;opacity:.25}72%{stroke-dashoffset:0;opacity:1}88%{stroke-dashoffset:0;opacity:1}100%{stroke-dashoffset:0;opacity:.18}}
@keyframes beacon-travel{0%{offset-distance:0%;opacity:0;transform:scale(.65)}10%{opacity:1}72%{offset-distance:100%;opacity:1;transform:scale(1)}88%{offset-distance:100%;opacity:1;transform:scale(1.18)}100%{offset-distance:100%;opacity:0;transform:scale(.65)}}
@keyframes route-node-pulse{0%,34%,100%{transform:scale(.82);opacity:.38}54%,78%{transform:scale(1.18);opacity:1}}
@keyframes logo-float{0%,100%{transform:translate(-50%,0)}50%{transform:translate(-50%,-6px)}}
@keyframes logo-signal{0%,100%{transform:scale(.72);opacity:.55}50%{transform:scale(1.12);opacity:1}}
@keyframes dog-run{0%{transform:translateX(-96px) scaleX(1)}47%{transform:translateX(24px) scaleX(1)}50%{transform:translateX(24px) scaleX(-1)}97%{transform:translateX(-96px) scaleX(-1)}100%{transform:translateX(-96px) scaleX(1)}}
@keyframes dog-bob{to{transform:translateY(-4px) rotate(-2deg)}}
@keyframes dog-shadow{to{transform:scaleX(.62);opacity:.45}}
@keyframes dog-tail-wag{from{transform:rotate(34deg)}to{transform:rotate(-38deg)}}
@keyframes dog-gait-a{0%,100%{transform:rotate(38deg) translateY(0)}25%{transform:rotate(0) translateY(-2px)}50%{transform:rotate(-42deg) translateY(0)}75%{transform:rotate(0) translateY(2px)}}
@keyframes dog-gait-b{0%,100%{transform:rotate(-42deg) translateY(0)}25%{transform:rotate(0) translateY(2px)}50%{transform:rotate(38deg) translateY(0)}75%{transform:rotate(0) translateY(-2px)}}
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
  .running-dog,.dog-runner::after,.dog-tail,.dog-leg{animation-duration:.72s!important}
}
</style>
