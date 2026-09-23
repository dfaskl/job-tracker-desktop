<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch, type Component } from 'vue'
import ProductHome from './ProductHome.vue'
import ProductAnalytics from './ProductAnalytics.vue'
import ProductApplicationWorkspace from './ProductApplicationWorkspace.vue'
import ProductCalendarWorkspace from './ProductCalendarWorkspace.vue'
import MigrationAiMail from './MigrationAiMail.vue'
import MigrationAdmin from './MigrationAdmin.vue'
import ProductSettingsWorkspace from './ProductSettingsWorkspace.vue'
import AccountAccess from './AccountAccess.vue'
import { useJobTrackerStore } from './jobTrackerStore'

type Page = 'home' | 'applications' | 'calendar' | 'mail' | 'stats' | 'settings' | 'admin'

const pages: { id: Page; label: string; icon: string }[] = [
  { id: 'home', label: '首页', icon: 'M3.5 10.5 12 3l8.5 7.5v9a1.5 1.5 0 0 1-1.5 1.5h-5v-6H9.5v6H5a1.5 1.5 0 0 1-1.5-1.5Z' },
  { id: 'applications', label: '投递记录', icon: 'M9 4h6M9 3h6v3H9ZM7 5H5v16h14V5h-2M8 12l2 2 4-4M8 18h8' },
  { id: 'calendar', label: '日程', icon: 'M5 4h14a2 2 0 0 1 2 2v14H3V6a2 2 0 0 1 2-2ZM8 2v4m8-4v4M3 9h18M7 13h3m4 0h3m-10 4h3m4 0h3' },
  { id: 'mail', label: '邮件识别', icon: 'M3 5h14v12H3ZM3 6l7 6 7-6M18 14a3 3 0 1 0 0 6 3 3 0 0 0 0-6Zm2.2 5.2L22 21' },
  { id: 'stats', label: '统计', icon: 'M4 4v16h16M7 16l4-5 3 3 5-7M16 7h3v3' },
  { id: 'settings', label: '设置', icon: 'M4 6h6m4 0h6M10 3v6M4 12h10m4 0h2M14 9v6M4 18h3m4 0h9M7 15v6' },
  { id: 'admin', label: '管理员', icon: 'M12 3 20 6v5c0 5.2-3.2 8.4-8 10-4.8-1.6-8-4.8-8-10V6ZM9 12a2 2 0 1 0 4 0 2 2 0 0 0-4 0Zm4 0h4m-1 0v2' }
]

const pageComponents: Record<Page, Component> = {
  home: ProductHome,
  applications: ProductApplicationWorkspace,
  calendar: ProductCalendarWorkspace,
  mail: MigrationAiMail,
  stats: ProductAnalytics,
  settings: ProductSettingsWorkspace,
  admin: MigrationAdmin
}

const activePage = ref<Page>('home')
const mobileMenuOpen = ref(false)
const mainContent = ref<HTMLElement | null>(null)
const store = useJobTrackerStore()
const theme = ref<'light' | 'dark'>(document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light')
let workspaceRefreshTimer: number | undefined

function routeFromHash() {
  const [pageValue, query = ''] = window.location.hash.replace(/^#\/?/, '').split('?')
  const page = pages.some((item) => item.id === pageValue) ? (pageValue as Page) : 'home'
  const applicationId = page === 'applications' ? new URLSearchParams(query).get('application') || '' : ''
  return { page, applicationId }
}

function syncHash() {
  const route = routeFromHash()
  activePage.value = route.page
  if (route.applicationId) store.requestApplicationDetail(route.applicationId)
}

async function createApplication() {
  if (activePage.value !== 'applications') {
    navigate('applications')
    await nextTick()
  }
  store.requestNewApplication()
}
function navigate(page: Page, applicationId?: string) {
  mobileMenuOpen.value = false
  const targetHash = page === 'applications' && applicationId ? 'applications?application=' + encodeURIComponent(applicationId) : page
  if (window.location.hash.replace(/^#\/?/, '') === targetHash) {
    if (applicationId) store.requestApplicationDetail(applicationId)
    return
  }
  window.location.hash = targetHash
  activePage.value = page
  window.scrollTo({ top: 0, behavior: 'auto' })
}

function handleGlobalKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') mobileMenuOpen.value = false
}

function toggleTheme() {
  const nextTheme = theme.value === 'light' ? 'dark' : 'light'
  theme.value = nextTheme
  if (nextTheme === 'dark') document.documentElement.dataset.theme = 'dark'
  else delete document.documentElement.dataset.theme
  try { localStorage.setItem('job-tracker-theme', nextTheme) } catch { /* Keep the selected theme for this session. */ }
}

function refreshWorkspaceData(syncMail = false) {
  if (!store.user.value) return
  void Promise.all([store.refresh(false, false, false), store.refreshMailInbox(syncMail, false)])
}
function handleWindowFocus() { refreshWorkspaceData(true) }

onMounted(async () => {
  syncHash()
  window.addEventListener('hashchange', syncHash)
  window.addEventListener('keydown', handleGlobalKeydown)
  window.addEventListener('focus', handleWindowFocus)
  await store.initialize()
  if (store.user.value) await Promise.all([store.refresh(), store.refreshMailInbox()])
  syncHash()
  workspaceRefreshTimer = window.setInterval(refreshWorkspaceData, 15_000)
})
watch(activePage, async () => { await nextTick(); mainContent.value?.focus({ preventScroll: true }) })
onBeforeUnmount(() => {
  window.removeEventListener('hashchange', syncHash)
  window.removeEventListener('keydown', handleGlobalKeydown)
  window.removeEventListener('focus', handleWindowFocus)
  if (workspaceRefreshTimer !== undefined) window.clearInterval(workspaceRefreshTimer)
})
</script>

<template>
  <div class="product-shell">
    <aside class="sidebar" :class="{ 'menu-open': mobileMenuOpen }">
      <button class="brand" type="button" aria-label="返回首页" @click="navigate('home')">
        <img src="/favicon.svg" alt="" aria-hidden="true"><div><strong>求职进度本</strong><small>Vue + Java</small></div>
      </button>
      <button class="theme-toggle" type="button" :aria-label="theme === 'dark' ? '切换为浅色模式' : '切换为暗色模式'" :aria-pressed="theme === 'dark'" :title="theme === 'dark' ? '切换为浅色模式' : '切换为暗色模式'" @click="toggleTheme">
        <AppIcon :name="theme === 'dark' ? 'sun' : 'moon'" :size="20" />
      </button>
      <button class="menu-toggle" type="button" :aria-label="mobileMenuOpen ? '收起页面导航' : '展开页面导航'" aria-controls="primary-navigation" :aria-expanded="mobileMenuOpen" @click="mobileMenuOpen = !mobileMenuOpen">
        <span aria-hidden="true"></span><span aria-hidden="true"></span><span aria-hidden="true"></span>
      </button>
      <nav id="primary-navigation" aria-label="主要导航">
        <button v-for="item in pages" :key="item.id" type="button" :class="{ active: activePage === item.id, 'has-badge': item.id === 'mail' && store.pendingMailCount.value > 0 }" :aria-label="item.id === 'mail' && store.pendingMailCount.value > 0 ? `${item.label}，${store.pendingMailCount.value} 封待处理邮件` : item.label" :aria-current="activePage === item.id ? 'page' : undefined" @click="navigate(item.id)">
          <span class="nav-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path :d="item.icon" /></svg></span><span class="nav-copy"><strong>{{ item.label }}</strong></span><span class="nav-arrow" aria-hidden="true">›</span><b v-if="item.id === 'mail' && store.pendingMailCount.value > 0" class="nav-badge" aria-hidden="true">{{ store.pendingMailCount.value > 99 ? '99+' : store.pendingMailCount.value }}</b>
        </button>
      </nav>
      <span class="sr-status" role="status" aria-live="polite" aria-atomic="true">{{ store.pendingMailCount.value > 0 ? `有 ${store.pendingMailCount.value} 封待处理邮件` : '没有待处理邮件' }}</span>
      <div class="sidebar-account"><AccountAccess v-if="store.user.value" compact /></div>
    </aside>

    <main id="main-content" ref="mainContent" tabindex="-1" class="product-main" :class="{ 'application-page': activePage === 'applications', 'calendar-page': activePage === 'calendar', 'mail-page-shell': activePage === 'mail', 'settings-page-shell': activePage === 'settings', 'admin-page-shell': activePage === 'admin', 'stats-page-shell': activePage === 'stats' }">
      <header v-show="activePage === 'home' || activePage === 'applications'" class="topbar">
        <div v-show="activePage === 'home'" id="home-quote-slot" class="home-quote-slot"></div>
        <div id="application-toolbar-slot" class="application-toolbar-slot" :class="{ active: activePage === 'applications' }"></div>
        <button v-if="activePage === 'applications'" type="button" @click="createApplication">＋ 新建投递</button>
      </header>

      <div class="page-content" :class="{ 'home-content': activePage === 'home', 'application-content': activePage === 'applications', 'calendar-content': activePage === 'calendar', 'mail-content': activePage === 'mail', 'settings-content': activePage === 'settings', 'admin-content': activePage === 'admin', 'stats-content': activePage === 'stats' }">
        <KeepAlive :max="7">
          <component :is="pageComponents[activePage]" :key="activePage" @navigate="navigate" />
        </KeepAlive>
      </div>
    </main>
  </div>
</template>

<style scoped>
.product-shell { min-height: 100vh; background: var(--color-background); }
.sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 20;
  display: flex;
  width: 232px;
  flex-direction: column;
  padding: 20px 14px 18px;
  color: #f2eadc;
  background:
    radial-gradient(circle at 12% 8%, rgba(255,248,235,.09), transparent 26%),
    radial-gradient(circle at 1px 1px, rgba(255,248,235,.07) 1px, transparent 1.4px) 0 0 / 22px 22px,
    repeating-linear-gradient(132deg, transparent 0 47px, rgba(255,248,235,.025) 48px, transparent 49px 96px),
    linear-gradient(180deg, var(--sidebar-top), var(--sidebar));
  border-right: 1px solid rgba(255,255,255,.1);
  box-shadow: 10px 0 34px rgba(0,0,0,.18);
  overflow: hidden;
}
.sidebar::before {
  content: "";
  position: absolute;
  inset: 76px 8px 104px 22px;
  background:
    radial-gradient(circle at 13% 9%, rgba(255,248,235,.42) 0 2px, transparent 3px),
    radial-gradient(circle at 79% 29%, rgba(255,248,235,.3) 0 2px, transparent 3px),
    radial-gradient(circle at 27% 56%, rgba(255,248,235,.25) 0 2px, transparent 3px),
    radial-gradient(circle at 86% 82%, rgba(255,248,235,.32) 0 2px, transparent 3px),
    linear-gradient(150deg, transparent 0 18%, rgba(255,248,235,.06) 18.2% 18.55%, transparent 18.75% 47%, rgba(255,248,235,.045) 47.2% 47.5%, transparent 47.7% 73%, rgba(255,248,235,.05) 73.2% 73.5%, transparent 73.7%);
  opacity: .72;
  mask-image: linear-gradient(to bottom, transparent, #000 8%, #000 90%, transparent);
  -webkit-mask-image: linear-gradient(to bottom, transparent, #000 8%, #000 90%, transparent);
  pointer-events: none;
}
.sidebar::after {
  content: "";
  position: absolute;
  right: -90px;
  bottom: 12%;
  width: 180px;
  height: 180px;
  border: 1px solid rgba(255,248,235,.1);
  border-radius: 50%;
  box-shadow: 0 0 0 34px rgba(255,248,235,.025), 0 0 0 68px rgba(255,248,235,.018);
  pointer-events: none;
}

.brand {
  position: relative;
  z-index: 1;
  display: flex;
  width: 100%;
  min-height: 52px;
  align-items: center;
  gap: 11px;
  padding: 7px 9px;
  border: 1px solid transparent;
  border-radius: 13px;
  color: inherit;
  background: transparent;
  text-align: left;
  transition: background-color .2s ease, border-color .2s ease, transform .2s ease;
}
.brand:hover { transform: translateY(-1px); border-color: rgba(255,255,255,.1); background: rgba(255,255,255,.055); }
.brand > img { width: 38px; height: 38px; flex: 0 0 38px; border-radius: 11px; box-shadow: 0 7px 18px rgba(0,0,0,.16); transition: transform .24s cubic-bezier(.2,.8,.2,1); }
.brand:hover > img { transform: rotate(-4deg) scale(1.04); }
.brand div { display: grid; gap: 2px; }
.brand strong { font-family: "Fira Code", "Noto Sans SC", sans-serif; font-size: 15px; letter-spacing: -.04em; }
.brand small { color: #c7beb0; font-size: 11px; }
.menu-toggle { display: none; }
nav { position: relative; z-index: 1; display: grid; min-height: 0; flex: 1; grid-auto-rows: max-content; align-content: space-evenly; gap: clamp(6px,.75vh,10px); margin: 14px 0 12px; overflow-y: auto; scrollbar-width: none; animation: nav-group-in .38s both cubic-bezier(.2,.8,.2,1); }
nav::-webkit-scrollbar { display: none; }
nav button {
  position: relative;
  display: flex;
  width: 100%;
  min-height: clamp(52px,6.4vh,66px);
  align-items: center;
  gap: 11px;
  padding: 8px 10px;
  overflow: hidden;
  border: 1px solid transparent;
  border-radius: 13px;
  color: #ebe4d8;
  background: transparent;
  text-align: left;
  transition: color .2s ease, background-color .2s ease, border-color .2s ease, box-shadow .2s ease, transform .2s cubic-bezier(.2,.8,.2,1);
}
.nav-icon { position: relative; z-index: 1; display: grid; width: 34px; height: 34px; flex: 0 0 34px; place-items: center; border: 1px solid rgba(255,248,235,.14); border-radius: 10px; color: #d9d0c2; background: rgba(255,255,255,.045); transition: color .2s ease, background-color .2s ease, border-color .2s ease, transform .2s cubic-bezier(.2,.8,.2,1); }
.nav-icon svg { width: 20px; height: 20px; fill: none; stroke: currentColor; stroke-width: 1.75; stroke-linecap: round; stroke-linejoin: round; }
.nav-copy { position: relative; z-index: 1; display: flex; min-width: 0; flex: 1; align-items: center; }
.nav-copy strong { font-family: "Noto Serif SC", "STKaiti", "KaiTi", serif; font-size: 17px; font-weight: 700; line-height: 1.25; letter-spacing: .045em; text-shadow: 0 1px 10px rgba(255,248,235,.1); }
.nav-arrow { position: relative; z-index: 1; flex: none; color: rgba(235,228,216,.36); font-size: 21px; line-height: 1; transform: translateX(-3px); opacity: 0; transition: opacity .2s ease, transform .2s ease; }
nav button.has-badge { padding-right: 40px; }
nav button.has-badge .nav-arrow { display: none; }
.nav-badge { position: absolute; top: 5px; right: 8px; display: grid; min-width: 20px; height: 20px; place-items: center; padding: 0 5px; border: 2px solid var(--sidebar); border-radius: 999px; color: #fff; background: var(--color-destructive); font: 800 10px/1 var(--font-button); letter-spacing: 0; box-shadow: 0 2px 6px rgba(0,0,0,.22); }
.sr-status { position: absolute; width: 1px; height: 1px; padding: 0; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; border: 0; }
nav button:hover { transform: translateX(3px); color: #fff; background: rgba(255,255,255,.075); }
nav button:hover .nav-icon { transform: scale(1.05); color: #fff8eb; border-color: rgba(255,248,235,.24); background: rgba(255,255,255,.08); }
nav button:hover .nav-arrow { transform: translateX(0); opacity: 1; }
nav button.active {
  border-color: rgba(255,248,235,.18);
  color: #fff;
  background: var(--sidebar-active);
  box-shadow: 0 9px 20px rgba(1,39,42,.18), inset 0 1px rgba(255,255,255,.08);
}
nav button.active::before {
  content: "";
  position: absolute;
  top: 12px;
  bottom: 12px;
  left: 0;
  width: 3px;
  border-radius: 0 99px 99px 0;
  background: #f2eadc;
  box-shadow: 0 0 14px rgba(242,234,220,.38);
}
nav button.active .nav-icon { color: #fff; border-color: rgba(255,255,255,.22); background: rgba(255,255,255,.12); box-shadow: inset 0 1px rgba(255,255,255,.08); }
nav button.active .nav-arrow { transform: translateX(0); opacity: .82; }
.sidebar :is(button,a):focus-visible { outline: 3px solid rgba(242,234,220,.66); outline-offset: 2px; }
.theme-toggle { position:absolute; z-index:2; top:26px; right:14px; display:grid; width:40px; min-width:40px; height:40px; min-height:40px; place-items:center; padding:0; border:1px solid rgba(255,248,235,.18); border-radius:12px; color:#ebe4d8; background:rgba(255,255,255,.045); box-shadow:inset 0 1px rgba(255,255,255,.04); }
.theme-toggle:hover { color:#fff; border-color:rgba(255,248,235,.3); background:rgba(255,255,255,.1); }
.theme-toggle svg { flex:none; }
.sidebar-account { position: relative; z-index: 1; display: flex; width: 100%; flex: none; align-items: center; justify-content: center; }
@keyframes nav-group-in { from { transform: translateX(-7px); opacity: 0; } to { transform: translateX(0); opacity: 1; } }
.product-main { width: auto; min-width: 0; margin: 0 0 0 232px; padding: 0 38px 64px; }
.topbar {
  position: sticky;
  top: 0;
  z-index: 12;
  display: flex;
  min-height: 88px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border);
  background: color-mix(in srgb, var(--color-background) 92%, transparent);
  backdrop-filter: blur(16px);
}
.topbar > button {
  min-width: 116px;
  color: var(--color-on-primary);
  background: var(--color-primary);
}
.home-quote-slot { display: flex; min-width: 0; flex: 1; justify-content: center; }
.application-toolbar-slot { display: none; min-width: 0; flex: 1; margin: 7px 18px 7px 0; }
.application-toolbar-slot.active { display: flex; }
.page-content { width: min(1240px, 100%); margin: 0 auto; }
.page-content.stats-content { display: flow-root; }
.page-content.home-content,
.page-content.application-content,
.page-content.calendar-content,
.page-content.mail-content,
.page-content.settings-content,
.page-content.admin-content,
.page-content.stats-content { width: 100%; max-width: none; }
.product-main.application-page,
.product-main.calendar-page,
.product-main.mail-page-shell,
.product-main.settings-page-shell,
.product-main.admin-page-shell { height: 100vh; overflow: hidden; padding-bottom: 0; }
.page-content.mail-content,
.page-content.settings-content,
.page-content.admin-content { height: 100vh; }
.page-content :deep(.card) { margin-top: 18px; }
.page-content.admin-content :deep(.card) { margin-top: 0; }
.page-content.stats-content :deep(.analytics-layout > .interview-panel.card) { margin-top: 0; }
.product-main.settings-page-shell { height: auto; min-height: 100vh; overflow: visible; padding-bottom: 44px; }
.page-content.settings-content { height: auto; }

@media (max-width: 1200px) {
  .product-main.settings-page-shell, .product-main.mail-page-shell { height: auto; overflow: visible; padding-bottom: 44px; }
  .page-content.settings-content, .page-content.mail-content { height: auto; }
}
@media (max-width: 900px) {
  .product-main.mail-page-shell, .product-main.admin-page-shell { height: auto; overflow: visible; padding-bottom: 44px; }
  .page-content.mail-content, .page-content.settings-content, .page-content.admin-content { height: auto; }
}
@media (max-width: 820px) {
  .product-shell { width: 100%; max-width: 100%; overflow-x: clip; }
  .sidebar {
    position: fixed;
    inset: 0 0 auto 0;
    top: 0;
    z-index: 30;
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto auto;
    column-gap: 6px;
    width: 100%;
    max-width: 100vw;
    height: auto;
    padding: 8px 12px 10px;
    overflow: hidden;
    transition: box-shadow .24s ease;
  }
  .sidebar::before, .sidebar::after { display: none; }
  .brand { width: fit-content; max-width: 100%; min-width: 0; }
  .brand div { min-width: 0; }
  .brand strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .brand small { display: none; }
  .menu-toggle {
    display: grid;
    width: 44px;
    height: 44px;
    min-height: 44px;
    align-self: center;
    place-content: center;
    gap: 5px;
    padding: 0;
    border: 1px solid rgba(242,234,220,.32);
    color: #f2eadc;
    background: rgba(255,255,255,.06);
  }
  .menu-toggle span { display: block; width: 19px; height: 2px; border-radius: 99px; background: currentColor; transition: transform .18s ease, opacity .18s ease; }
  .menu-open .menu-toggle span:nth-child(1) { transform: translateY(7px) rotate(45deg); }
  .menu-open .menu-toggle span:nth-child(2) { opacity: 0; }
  .menu-open .menu-toggle span:nth-child(3) { transform: translateY(-7px) rotate(-45deg); }
  .theme-toggle { position:static; width:44px; min-width:44px; height:44px; min-height:44px; align-self:center; }
  .sidebar-account { position: static; grid-column: 1 / -1; width: 100%; min-width: 0; max-height: 0; margin: 0; overflow: hidden; opacity: 0; transform: translateY(-6px); transition: max-height .25s ease, margin .25s ease, opacity .2s ease, transform .25s ease; }
  .menu-open .sidebar-account { max-height: 72px; margin-top: 6px; opacity: 1; transform: translateY(0); }
  .sidebar-account :deep(.signed.compact) { display: flex; width: auto; min-width: 0; padding: 5px 7px; flex-direction: row; }
  .sidebar-account :deep(.signed.compact > div) { width: auto; }
  .sidebar-account :deep(.signed.compact span) { display: none; }
  .sidebar-account :deep(.signed.compact button) { width: auto; }
  nav { grid-column: 1 / -1; display: grid; width: 100%; min-width: 0; max-height: 0; flex: none; align-content: stretch; margin: 0; overflow: hidden; grid-template-columns: repeat(4, minmax(0, 1fr)); grid-auto-rows: auto; gap: 4px; opacity: 0; transform: translateY(-8px); pointer-events: none; animation: none; transition: max-height .3s ease, margin .3s ease, opacity .2s ease, transform .3s ease; }
  .menu-open nav { max-height: 240px; margin-top: 8px; opacity: 1; transform: translateY(0); pointer-events: auto; }
  nav::-webkit-scrollbar { display: none; }
  nav button { width: 100%; min-width: 0; min-height: 44px; justify-content: center; padding: 7px 5px; border-radius: 10px; text-align: center; animation: none; }
  nav button:hover { transform: translateY(-1px); }
  .nav-icon, .nav-arrow, nav button.active::before { display: none; }
  .nav-copy { display: block; flex: 0 1 auto; }
  .nav-copy strong { font-size: 16px; letter-spacing: .035em; }
  nav button.has-badge { padding-right: 26px; }
  .nav-badge { top: 2px; right: 3px; }
  .product-main { width: 100%; max-width: 100vw; margin-left: 0; padding: 70px 16px 44px; overflow-x: clip; }
  .product-main.application-page,
  .product-main.calendar-page,
  .product-main.mail-page-shell,
  .product-main.settings-page-shell,
  .product-main.admin-page-shell { height: auto; overflow: visible; padding-bottom: 44px; }
  .page-content { min-width: 0; max-width: 100%; }
  .page-content.mail-content, .page-content.settings-content, .page-content.admin-content { height: auto; }
  .topbar { position: relative; top: auto; min-height: 0; flex-wrap: wrap; padding: 12px 0; }
  .home-quote-slot, .application-toolbar-slot { order: 3; width: auto; max-width: 100%; flex: 0 0 100%; margin: 4px 0; }
}
@media (max-width: 620px) {
  nav, .menu-open nav { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  nav button { width: 100%; min-width: 0; min-height: 38px; justify-content: center; padding: 7px 4px; font-size: 13px; text-align: center; }
  .topbar > button { order: 4; display: block; width: 100%; min-width: 0; min-height: 44px; }
}
@media (max-width: 520px) {
  .product-main { padding-inline: 12px; }
}
@media (prefers-reduced-motion: reduce) {
  .sidebar *, .sidebar *::before, .sidebar *::after { scroll-behavior: auto !important; animation-duration: .01ms !important; animation-delay: 0ms !important; transition-duration: .01ms !important; }
}
</style>
