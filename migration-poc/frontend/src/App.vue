<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import ProductShell from './ProductShell.vue'
import LoginPage from './LoginPage.vue'
import GlobalToastBridge from './GlobalToastBridge.vue'
import { useJobTrackerStore } from './jobTrackerStore'

const store = useJobTrackerStore()
const ready = ref(false)
const allowedPages = new Set(['home', 'applications', 'calendar', 'mail', 'stats', 'settings', 'admin'])
const requestedPage = ref(readRequestedPage())

function readPageFromHash() {
  const value = window.location.hash.replace(/^#\/?/, '')
  return allowedPages.has(value) ? value : 'home'
}

function readRequestedPage() {
  const fromHash = readPageFromHash()
  const fromSession = window.sessionStorage.getItem('job-tracker-requested-page') || ''
  return window.location.hash.includes('login') && allowedPages.has(fromSession) ? fromSession : fromHash
}

function replaceHash(page: string) {
  window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}#/${page}`)
}

function enforceAuthRoute() {
  if (!ready.value) return
  const value = window.location.hash.replace(/^#\/?/, '')
  if (!store.user.value) {
    if (allowedPages.has(value)) {
      requestedPage.value = value
      window.sessionStorage.setItem('job-tracker-requested-page', value)
    }
    if (value !== 'login') replaceHash('login')
    return
  }
  if (value === 'login' || !allowedPages.has(value)) {
    replaceHash(requestedPage.value || 'home')
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
onBeforeUnmount(() => window.removeEventListener('hashchange', enforceAuthRoute))
</script>

<template>
  <div v-if="!ready" class="session-loading" role="status" aria-live="polite">
    <img src="/favicon.svg" alt=""><span>正在确认登录状态…</span>
  </div>
  <ProductShell v-else-if="store.user.value" />
  <LoginPage v-else />
  <GlobalToastBridge />
</template>

<style scoped>
.session-loading{display:grid;min-height:100vh;place-content:center;justify-items:center;gap:14px;color:var(--color-muted-foreground);background:var(--color-background)}
.session-loading img{width:52px;height:52px;border-radius:13px;animation:session-pulse 1.2s ease-in-out infinite}
@keyframes session-pulse{50%{transform:translateY(-3px);opacity:.72}}
@media(prefers-reduced-motion:reduce){.session-loading img{animation:none}}
</style>
