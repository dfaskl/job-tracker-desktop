<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, type Component } from 'vue'
import ProductHome from './ProductHome.vue'
import ProductAnalytics from './ProductAnalytics.vue'
import ProductInterviewSummary from './ProductInterviewSummary.vue'
import ProductApplicationWorkspace from './ProductApplicationWorkspace.vue'
import ProductCalendarWorkspace from './ProductCalendarWorkspace.vue'
import MailRecognition from './MailRecognition.vue'
import AdminDashboard from './AdminDashboard.vue'
import ProductSettingsWorkspace from './ProductSettingsWorkspace.vue'
import ProductGuide from './ProductGuide.vue'
import { useJobTrackerStore } from './jobTrackerStore'

type Page = 'home' | 'applications' | 'calendar' | 'mail' | 'stats' | 'interview-summary' | 'profile' | 'admin' | 'guide'

const pages: { id: Page; label: string; icon: string }[] = [
  { id: 'home', label: '首页', icon: 'M3.5 10.5 12 3l8.5 7.5v9a1.5 1.5 0 0 1-1.5 1.5h-5v-6H9.5v6H5a1.5 1.5 0 0 1-1.5-1.5Z' },
  { id: 'applications', label: '投递记录', icon: 'M9 4h6M9 3h6v3H9ZM7 5H5v16h14V5h-2M8 12l2 2 4-4M8 18h8' },
  { id: 'calendar', label: '日程', icon: 'M5 4h14a2 2 0 0 1 2 2v14H3V6a2 2 0 0 1 2-2ZM8 2v4m8-4v4M3 9h18M7 13h3m4 0h3m-10 4h3m4 0h3' },
  { id: 'mail', label: '邮件识别', icon: 'M3 5h14v12H3ZM3 6l7 6 7-6M18 14a3 3 0 1 0 0 6 3 3 0 0 0 0-6Zm2.2 5.2L22 21' },
  { id: 'stats', label: '统计', icon: 'M4 4v16h16M7 16l4-5 3 3 5-7M16 7h3v3' },
  { id: 'interview-summary', label: '面试总结', icon: 'M5 3.5h10l4 4V20H5a2 2 0 0 1-2-2V5.5a2 2 0 0 1 2-2Zm9 0V8h5M7 12h9M7 16h6' },
  { id: 'admin', label: '管理员', icon: 'M12 3 20 6v5c0 5.2-3.2 8.4-8 10-4.8-1.6-8-4.8-8-10V6ZM9 12a2 2 0 1 0 4 0 2 2 0 0 0-4 0Zm4 0h4m-1 0v2' },
  { id: 'guide', label: '使用指引', icon: 'M4 4.5A2.5 2.5 0 0 1 6.5 2H20v17H6.5A2.5 2.5 0 0 0 4 21.5Zm0 0V21m4-15h8m-8 4h8m-8 4h5' }
]

const pageComponents: Record<Page, Component> = {
  home: ProductHome,
  applications: ProductApplicationWorkspace,
  calendar: ProductCalendarWorkspace,
  mail: MailRecognition,
  stats: ProductAnalytics,
  'interview-summary': ProductInterviewSummary,
  profile: ProductSettingsWorkspace,
  admin: AdminDashboard,
  guide: ProductGuide
}

const activePage = ref<Page>('home')
const focusApplicationId = ref('')
const mobileMenuOpen = ref(false)
const mainContent = ref<HTMLElement | null>(null)
const store = useJobTrackerStore()
const theme = ref<'light' | 'dark'>(document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light')
const sidebarDisplayName = computed(() => store.user.value?.displayName || store.user.value?.email.split('@')[0] || '个人主页')
const mobileViewport = ref(window.matchMedia('(max-width: 820px)').matches)
function syncViewport() { mobileViewport.value = window.matchMedia('(max-width: 820px)').matches }
let workspaceRefreshTimer: number | undefined
let resumeFocusTimer: number | undefined
let lastBusinessRefresh = 0

function routeFromHash() {
  const [pageValue, query = ''] = window.location.hash.replace(/^#\/?/, '').split('?')
  const normalizedPage = pageValue === 'settings' ? 'profile' : pageValue
  const page = Object.prototype.hasOwnProperty.call(pageComponents, normalizedPage) ? (normalizedPage as Page) : 'home'
  const applicationId = page === 'applications' ? new URLSearchParams(query).get('application') || '' : ''
  return { page, applicationId }
}

function syncHash() {
  const route = routeFromHash()
  activePage.value = route.page
  focusApplicationId.value = route.page === 'applications' ? new URLSearchParams(window.location.hash.split('?')[1] || '').get('focus') || '' : ''
  if (route.applicationId) store.requestApplicationDetail(route.applicationId)
}

async function createApplication() {
  if (activePage.value !== 'applications') {
    navigate('applications')
    await nextTick()
  }
  store.requestNewApplication()
}
function navigate(page: Page, applicationId?: string, behavior: 'detail' | 'focus' = 'detail') {
  mobileMenuOpen.value = false
  const targetHash = page === 'applications' && applicationId ? `applications?${behavior === 'focus' ? 'focus' : 'application'}=${encodeURIComponent(applicationId)}` : page
  if (window.location.hash.replace(/^#\/?/, '') === targetHash) {
    if (applicationId && behavior === 'detail') store.requestApplicationDetail(applicationId)
    return
  }
  window.location.hash = targetHash
  activePage.value = page
  focusApplicationId.value = page === 'applications' && behavior === 'focus' ? applicationId || '' : ''
  window.scrollTo({ top: 0, behavior: 'auto' })
}

async function focusResumeSettings() {
  if (activePage.value !== 'profile') navigate('profile')
  await nextTick()
  await nextTick()
  const resumeSection = document.getElementById('resume-settings')
  if (!resumeSection) return
  resumeSection.scrollIntoView({ behavior: 'smooth', block: 'center' })
  resumeSection.classList.remove('resume-highlight')
  void resumeSection.offsetWidth
  resumeSection.classList.add('resume-highlight')
  if (resumeFocusTimer !== undefined) window.clearTimeout(resumeFocusTimer)
  resumeFocusTimer = window.setTimeout(() => {
    resumeSection.classList.remove('resume-highlight')
    resumeFocusTimer = undefined
  }, 1500)
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

function refreshWorkspaceData(forceBusiness = false) {
  if (!store.user.value || document.visibilityState === 'hidden') return
  const now = Date.now()
  if (forceBusiness || now - lastBusinessRefresh >= 60_000) {
    lastBusinessRefresh = now
    void store.refresh(false, false, false)
  }
  void store.refreshMailInbox()
}
function handleWindowFocus() { refreshWorkspaceData(true) }
function handleVisibilityChange() { if (document.visibilityState === 'visible') refreshWorkspaceData(true) }

onMounted(async () => {
  syncHash()
  window.addEventListener('hashchange', syncHash)
  window.addEventListener('keydown', handleGlobalKeydown)
  window.addEventListener('focus', handleWindowFocus)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  window.addEventListener('resize', syncViewport)
  await store.initialize()
  if (store.user.value) await Promise.all([store.refresh(), store.refreshMailInbox()])
  lastBusinessRefresh = Date.now()
  syncHash()
  workspaceRefreshTimer = window.setInterval(refreshWorkspaceData, 15_000)
})
watch(activePage, async () => { await nextTick(); mainContent.value?.focus({ preventScroll: true }) })
onBeforeUnmount(() => {
  window.removeEventListener('hashchange', syncHash)
  window.removeEventListener('keydown', handleGlobalKeydown)
  window.removeEventListener('focus', handleWindowFocus)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  window.removeEventListener('resize', syncViewport)
  if (workspaceRefreshTimer !== undefined) window.clearInterval(workspaceRefreshTimer)
  if (resumeFocusTimer !== undefined) window.clearTimeout(resumeFocusTimer)
})
</script>

<template>
  <div class="product-shell">
    <a class="skip-link" href="#main-content" @click.prevent="mainContent?.focus()">跳到主内容</a>
    <aside class="sidebar" :class="{ 'menu-open': mobileMenuOpen }">
      <button class="brand" type="button" aria-label="返回首页" @click="navigate('home')">
        <img src="/favicon.svg" alt="" aria-hidden="true"><div><strong>CareerFlow</strong><small>求职进度本</small></div>
      </button>
      <button class="theme-toggle" type="button" :aria-label="theme === 'dark' ? '切换为浅色模式' : '切换为暗色模式'" :aria-pressed="theme === 'dark'" :title="theme === 'dark' ? '切换为浅色模式' : '切换为暗色模式'" @click="toggleTheme">
        <AppIcon :name="theme === 'dark' ? 'sun' : 'moon'" :size="20" />
      </button>
      <button class="menu-toggle" type="button" :aria-label="mobileMenuOpen ? '收起页面导航' : '展开页面导航'" aria-controls="primary-navigation" :aria-expanded="mobileMenuOpen" @click="mobileMenuOpen = !mobileMenuOpen">
        <span aria-hidden="true"></span><span aria-hidden="true"></span><span aria-hidden="true"></span>
      </button>
      <nav id="primary-navigation" aria-label="主要导航" :inert="mobileViewport && !mobileMenuOpen">
        <button v-for="item in pages" :key="item.id" type="button" :class="{ active: activePage === item.id, 'has-badge': item.id === 'mail' && store.pendingMailCount.value > 0 }" :aria-label="item.id === 'mail' && store.pendingMailCount.value > 0 ? `${item.label}，${store.pendingMailCount.value} 封待处理邮件` : item.label" :aria-current="activePage === item.id ? 'page' : undefined" @click="navigate(item.id)">
          <span class="nav-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path :d="item.icon" /></svg></span><span class="nav-copy"><strong>{{ item.label }}</strong></span><span class="nav-arrow" aria-hidden="true">›</span><b v-if="item.id === 'mail' && store.pendingMailCount.value > 0" class="nav-badge" aria-hidden="true">{{ store.pendingMailCount.value > 99 ? '99+' : store.pendingMailCount.value }}</b>
        </button>
      </nav>
      <span class="sr-status" role="status" aria-live="polite" aria-atomic="true">{{ store.pendingMailCount.value > 0 ? `有 ${store.pendingMailCount.value} 封待处理邮件` : '没有待处理邮件' }}</span>
      <div class="sidebar-account" :inert="mobileViewport && !mobileMenuOpen"><button v-if="store.user.value" type="button" class="profile-entry" :class="{ active: activePage === 'profile' }" :aria-label="`进入个人主页，当前用户 ${sidebarDisplayName}`" :title="sidebarDisplayName" @click="navigate('profile')"><span class="profile-entry-avatar"><img v-if="store.user.value.avatar" :src="store.user.value.avatar" alt=""><AppIcon v-else name="user" :size="20" /></span><span class="profile-entry-copy"><strong>{{ sidebarDisplayName }}</strong><small>账户与设置</small></span><AppIcon name="chevron-right" :size="16" /></button></div>
    </aside>

    <main id="main-content" ref="mainContent" tabindex="-1" class="product-main" :class="{ 'application-page': activePage === 'applications', 'calendar-page': activePage === 'calendar', 'mail-page-shell': activePage === 'mail', 'profile-page-shell': activePage === 'profile', 'admin-page-shell': activePage === 'admin', 'stats-page-shell': activePage === 'stats', 'interview-summary-page': activePage === 'interview-summary', 'guide-page-shell': activePage === 'guide' }">
      <header v-show="activePage === 'home' || activePage === 'applications'" class="topbar">
        <div v-show="activePage === 'home'" id="home-quote-slot" class="home-quote-slot"></div>
        <div id="application-toolbar-slot" class="application-toolbar-slot" :class="{ active: activePage === 'applications' }"></div>
        <button v-if="activePage === 'applications'" type="button" @click="createApplication">＋ 新建投递</button>
      </header>

      <div class="page-content" :class="{ 'home-content': activePage === 'home', 'application-content': activePage === 'applications', 'calendar-content': activePage === 'calendar', 'mail-content': activePage === 'mail', 'profile-content': activePage === 'profile', 'admin-content': activePage === 'admin', 'stats-content': activePage === 'stats', 'interview-summary-content': activePage === 'interview-summary', 'guide-content': activePage === 'guide' }">
        <KeepAlive :max="8">
          <component :is="pageComponents[activePage]" :key="activePage" v-bind="activePage === 'applications' ? { focusApplicationId } : {}" @navigate="navigate" @focus-resume="focusResumeSettings" />
        </KeepAlive>
      </div>
    </main>
  </div>
</template>

<style scoped>
.product-shell { min-height: 100dvh; background: var(--color-background); }
.skip-link { position: fixed; z-index: 100; top: 8px; left: 232px; padding: 12px 18px; transform: translateY(-150%); border-radius: 8px; color: var(--color-on-primary); background: var(--color-primary); }
.skip-link:focus { transform: none; }
.sidebar { position: fixed; inset: 0 auto 0 0; z-index: 20; display: flex; width: var(--sidebar-width); flex-direction: column; padding: 24px 12px 16px; border-right: 1px solid var(--color-border); color: var(--color-foreground); background: var(--color-card); }
.brand { display: flex; align-items: center; gap: 10px; width: 100%; padding: 4px 10px 22px; border: 0; background: transparent; text-align: left; }
.brand > img { width: 34px; height: 34px; flex: none; border-radius: 10px; }
.brand div { display: grid; gap: 3px; }
.brand strong { font-size: 18px; font-weight: 650; letter-spacing: -.5px; }
.brand small { color: var(--color-muted-foreground); font-size: 12px; font-weight: 400; }
nav { display: grid; min-height: 0; flex: 1; align-content: start; gap: 5px; padding-top: 12px; border-top: 1px solid var(--color-border); overflow-y: auto; scrollbar-width: thin; }
nav button { position: relative; display: flex; width: 100%; min-height: 46px; align-items: center; gap: 12px; padding: 10px 12px; border: 1px solid transparent; background: transparent; text-align: left; }
nav button:hover { background: var(--color-muted); }
nav button.active { border-color: color-mix(in srgb,var(--color-primary) 14%,transparent); background: color-mix(in srgb,var(--color-primary) 9%,var(--color-card)); }
nav button.active::before { content: ''; position: absolute; left: -1px; top: 13px; bottom: 13px; width: 3px; border-radius: 3px; background: var(--color-primary); }
.nav-icon { display: grid; width: 20px; height: 20px; flex: none; place-items: center; }
.nav-icon svg { width: 20px; height: 20px; fill: none; stroke: currentColor; stroke-width: 1.7; stroke-linecap: round; stroke-linejoin: round; }
.nav-copy { min-width: 0; flex: 1; }
.nav-copy strong { font-size: 14px; font-weight: 550; }
.nav-arrow { display: none; }
.nav-badge { display: grid; min-width: 22px; height: 22px; padding: 0 5px; place-items: center; border-radius: 6px; color: var(--color-on-primary); background: var(--color-primary); font-size: 11px; font-weight: 600; }
.sr-status { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
.theme-toggle { position: absolute; right: 14px; bottom: 90px; display: grid; width: 36px; height: 36px; min-height: 36px; place-items: center; padding: 0; border: 1px solid var(--color-border); background: var(--color-card); }
.theme-toggle:hover { background: var(--color-muted); }
.sidebar-account { flex: none; padding-top: 12px; margin-top: 56px; border-top: 1px solid var(--color-border); }
.profile-entry { display: flex; width: 100%; align-items: center; gap: 10px; padding: 8px; border: 1px solid transparent; background: transparent; text-align: left; }
.profile-entry:hover, .profile-entry.active { background: var(--color-muted); }
.profile-entry-avatar { display: grid; width: 36px; height: 36px; flex: none; place-items: center; overflow: hidden; border: 1px solid var(--color-border); border-radius: 50%; color: var(--color-primary); background: var(--color-muted); }
.profile-entry-avatar img { width: 100%; height: 100%; object-fit: cover; }
.profile-entry-copy { display: grid; flex: 1; min-width: 0; gap: 3px; }
.profile-entry-copy strong { overflow: hidden; color: var(--color-foreground); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.profile-entry-copy small { color: var(--color-muted-foreground); font-size: 12px; font-weight: 400; }
.menu-toggle { display: none; }
.product-main { width: auto; min-width: 0; margin: 0 0 0 var(--sidebar-width); padding: 0 28px 32px; }
.topbar {
  position: sticky;
  top: 0;
  z-index: 12;
  display: flex;
  min-height: 76px;
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
.page-content.guide-content { width: 100%; max-width: none; }
.page-content.stats-content { display: flow-root; }
.product-main.interview-summary-page{height:100dvh;min-height:0;padding-bottom:0;overflow:hidden}
.page-content.interview-summary-content{width:100%;max-width:none;height:100%;min-height:0}
.page-content.home-content,
.page-content.application-content,
.page-content.calendar-content,
.page-content.mail-content,
.page-content.profile-content,
.page-content.admin-content,
.page-content.stats-content { width: 100%; max-width: none; }
.product-main.application-page,
.product-main.calendar-page,
.product-main.mail-page-shell,
.product-main.profile-page-shell,
.product-main.admin-page-shell { height: 100vh; overflow: hidden; padding-bottom: 0; }
.page-content.calendar-content,
.page-content.mail-content,
.page-content.profile-content,
.page-content.admin-content { height: 100%; min-height: 0; }
.page-content :deep(.card) { margin-top: 18px; }
.page-content.admin-content :deep(.card) { margin-top: 0; }
.page-content.stats-content :deep(.analytics-layout > .interview-panel.card) { margin-top: 0; }
.product-main.profile-page-shell { height: auto; min-height: 100vh; overflow: visible; padding-bottom: 44px; }
.page-content.profile-content { height: auto; }

/* The desktop analytics dashboard fits inside one viewport. Keep the outer
   document fixed there and let the interview list own its scrollbar. */
@media (min-width: 1181px) and (min-height: 900px) {
  .product-main.stats-page-shell {
    height: 100dvh;
    min-height: 0;
    padding-bottom: 0;
    overflow: hidden;
  }
  .page-content.stats-content {
    height: 100%;
    min-height: 0;
  }
  .page-content.stats-content :deep(.analytics-layout) {
    height: 100%;
    min-height: 0;
  }
}

@media (min-width: 821px) and (min-height: 620px) {
  .product-main.application-page {
    display: grid;
    height: 100dvh;
    min-height: 0;
    grid-template-rows: auto minmax(0, 1fr);
  }
  .page-content.application-content {
    height: 100%;
    min-height: 0;
    padding-top: 12px;
  }
  .page-content.application-content :deep(.workspace) {
    height: 100%;
    margin-top: 0 !important;
  }
}

@media (max-width: 1200px) {
  .product-main.profile-page-shell { height: auto; overflow: visible; padding-bottom: 44px; }
  .page-content.profile-content { height: auto; }
}
@media (max-width: 900px) {
  .product-main.mail-page-shell, .product-main.admin-page-shell { height: auto; overflow: visible; padding-bottom: 44px; }
  .page-content.mail-content, .page-content.profile-content, .page-content.admin-content { height: auto; }
}

@media (max-width: 820px) {
  .product-shell { width: 100%; max-width: 100%; overflow-x: clip; }
  .skip-link { left: 12px; }
  .sidebar { inset: 0 0 auto; z-index: 30; display: grid; width: 100%; grid-template-columns: minmax(0,1fr) auto auto; gap: 0 8px; padding: 10px 16px; border-right: 0; border-bottom: 1px solid var(--color-border); box-shadow: var(--shadow-sm); }
  .brand { width: fit-content; min-width: 0; padding: 0; }
  .brand small { display: none; }
  .brand strong { font-size: 17px; }
  .theme-toggle { position: static; width: 44px; height: 44px; min-height: 44px; }
  .menu-toggle { display: grid; width: 44px; height: 44px; place-content: center; gap: 5px; border: 1px solid var(--color-border); background: var(--color-card); }
  .menu-toggle span { display: block; width: 18px; height: 2px; background: currentColor; transition: transform .18s, opacity .18s; }
  .menu-open .menu-toggle span:nth-child(1) { transform: translateY(7px) rotate(45deg); }
  .menu-open .menu-toggle span:nth-child(2) { opacity: 0; }
  .menu-open .menu-toggle span:nth-child(3) { transform: translateY(-7px) rotate(-45deg); }
  nav { grid-column: 1/-1; grid-template-columns: repeat(3,minmax(0,1fr)); max-height: 0; padding-top: 0; border-top: 0; overflow: hidden; opacity: 0; transition: opacity .16s; }
  .menu-open nav { max-height: 240px; margin-top: 12px; opacity: 1; }
  nav button { gap: 8px; padding: 10px 8px; }
  .nav-copy strong { font-size: 13px; }
  .nav-icon { width: 18px; }
  .nav-badge { position: absolute; top: 0; right: 0; min-width: 17px; height: 17px; font-size: 10px; }
  .sidebar-account { grid-column: 1/-1; display: none; margin-top: 12px; }
  .menu-open .sidebar-account { display: block; }
  .profile-entry { max-width: 100%; }
  .product-main { width: 100%; max-width: 100%; margin-left: 0; padding: 76px 16px 32px; }
  .product-main.interview-summary-page{height:100dvh;padding-bottom:0;overflow:hidden}
  .product-main.application-page, .product-main.calendar-page, .product-main.mail-page-shell, .product-main.profile-page-shell, .product-main.admin-page-shell { height: auto; overflow: visible; padding-bottom: 32px; }
  .page-content { min-width: 0; max-width: 100%; }
  .page-content.calendar-content, .page-content.mail-content, .page-content.profile-content, .page-content.admin-content { height: auto; }
  .topbar { position: relative; top: auto; min-height: 0; flex-wrap: wrap; padding: 10px 0; }
  .home-quote-slot, .application-toolbar-slot { width: 100%; max-width: 100%; flex: 0 0 100%; margin: 0; }
}
@media (max-width: 480px) {
  .sidebar { padding-inline: 12px; }
  .product-main { padding-inline: 12px; }
  .topbar > button { width: 100%; }
}
</style>
