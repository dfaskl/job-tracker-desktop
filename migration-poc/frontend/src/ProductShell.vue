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

const pages: { id: Page; label: string; icon: string; subtitle: string }[] = [
  { id: 'home', label: '首页', icon: '⌂', subtitle: '掌握每一次机会的进展' },
  { id: 'applications', label: '投递记录', icon: '▣', subtitle: '管理岗位、状态与完整流程' },
  { id: 'calendar', label: '日程', icon: '□', subtitle: '跟进笔试、面试与 Offer 安排' },
  { id: 'mail', label: '邮件识别', icon: '✦', subtitle: '从通知邮件中提取关键信息' },
  { id: 'stats', label: '统计', icon: '◫', subtitle: '查看投递阶段与渠道分布' },
  { id: 'settings', label: '设置', icon: '⚙', subtitle: '管理会话、备份和运行配置' },
  { id: 'admin', label: '管理员', icon: '◇', subtitle: '管理账号、权限与注册策略' }
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

function pageFromHash(): Page {
  const value = window.location.hash.replace(/^#\/?/, '') as Page
  return pages.some((item) => item.id === value) ? value : 'home'
}

function syncHash() {
  activePage.value = pageFromHash()
}

async function createApplication() {
  if (activePage.value !== 'applications') {
    navigate('applications')
    await nextTick()
  }
  store.requestNewApplication()
}
function navigate(page: Page) {
  mobileMenuOpen.value = false
  if (activePage.value === page) return
  window.location.hash = page
  activePage.value = page
  window.scrollTo({ top: 0, behavior: 'auto' })
}

function handleGlobalKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') mobileMenuOpen.value = false
}

onMounted(() => {
  syncHash()
  window.addEventListener('hashchange', syncHash)
  window.addEventListener('keydown', handleGlobalKeydown)
  void store.initialize()
})
watch(activePage, async () => { await nextTick(); mainContent.value?.focus({ preventScroll: true }) })
onBeforeUnmount(() => {
  window.removeEventListener('hashchange', syncHash)
  window.removeEventListener('keydown', handleGlobalKeydown)
})
</script>

<template>
  <div class="product-shell">
    <aside class="sidebar" :class="{ 'menu-open': mobileMenuOpen }">
      <button class="brand" type="button" aria-label="返回首页" @click="navigate('home')">
        <img src="/favicon.svg" alt="" aria-hidden="true"><div><strong>求职进度本</strong><small>Vue + Java</small></div>
      </button>
      <button class="menu-toggle" type="button" :aria-label="mobileMenuOpen ? '收起页面导航' : '展开页面导航'" aria-controls="primary-navigation" :aria-expanded="mobileMenuOpen" @click="mobileMenuOpen = !mobileMenuOpen">
        <span aria-hidden="true"></span><span aria-hidden="true"></span><span aria-hidden="true"></span>
      </button>
      <nav id="primary-navigation" aria-label="主要导航">
        <button v-for="item in pages" :key="item.id" type="button" :class="{ active: activePage === item.id }" :aria-current="activePage === item.id ? 'page' : undefined" @click="navigate(item.id)">
          <span aria-hidden="true">{{ item.icon }}</span>{{ item.label }}
        </button>
      </nav>
      <div class="sidebar-account"><AccountAccess v-if="store.user.value" compact /></div>
    </aside>

    <main id="main-content" ref="mainContent" tabindex="-1" class="product-main" :class="{ 'application-page': activePage === 'applications', 'calendar-page': activePage === 'calendar', 'mail-page-shell': activePage === 'mail', 'settings-page-shell': activePage === 'settings', 'admin-page-shell': activePage === 'admin', 'stats-page-shell': activePage === 'stats' }">
      <header v-show="activePage === 'home' || activePage === 'applications'" class="topbar">
        <div v-show="activePage === 'home'" id="home-quote-slot" class="home-quote-slot"></div>
        <div id="application-toolbar-slot" class="application-toolbar-slot" :class="{ active: activePage === 'applications' }"></div>
        <button v-if="activePage === 'applications'" type="button" @click="createApplication">＋ 新建投递</button>
      </header>

      <div class="page-content" :class="{ 'application-content': activePage === 'applications', 'calendar-content': activePage === 'calendar', 'mail-content': activePage === 'mail', 'settings-content': activePage === 'settings', 'admin-content': activePage === 'admin', 'stats-content': activePage === 'stats' }">
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
  padding: 22px 16px 18px;
  color: #e0f2fe;
  background: var(--sidebar);
  border-right: 1px solid rgba(255,255,255,.1);
  box-shadow: 8px 0 28px rgba(4,31,49,.08);
}

.brand {
  display: flex;
  width: 100%;
  min-height: 52px;
  align-items: center;
  gap: 11px;
  padding: 7px 8px;
  color: inherit;
  background: transparent;
  text-align: left;
}
.brand > img { width: 38px; height: 38px; flex: 0 0 38px; border-radius: 9px; }
.brand div { display: grid; gap: 2px; }
.brand strong { font-family: "Fira Code", "Noto Sans SC", sans-serif; font-size: 15px; letter-spacing: -.04em; }
.brand small { color: #bae6fd; font-size: 11px; }
.menu-toggle { display: none; }
nav { position: relative; display: grid; gap: 5px; margin-top: 28px; }
nav::before { content:""; position:absolute; top:18px; bottom:18px; left:22px; width:1px; background:rgba(125,211,252,.18); }
nav button {
  position: relative;
  display: flex;
  width: 100%;
  min-height: 46px;
  align-items: center;
  gap: 12px;
  padding: 10px 13px;
  border: 1px solid transparent;
  color: #bae6fd;
  background: transparent;
  text-align: left;
}
nav button span { position:relative; z-index:1; width: 20px; color: #7dd3fc; font-size: 16px; text-align: center; }
nav button:hover { color: #fff; background: rgba(255,255,255,.07); }
nav button.active {
  border-color: rgba(255,255,255,.12);
  color: #fff;
  background: var(--sidebar-active);
}
nav button.active::before {
  content: "";
  position: absolute;
  top: 11px;
  bottom: 11px;
  left: -16px;
  width: 4px;
  background: #38bdf8;
}
.sidebar-account { display: flex; width: 100%; margin-top: auto; align-items: center; justify-content: center; }
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
.page-content.application-content,
.page-content.calendar-content,
.page-content.mail-content,
.page-content.settings-content,
.page-content.admin-content { width: 100%; max-width: none; }
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

@media (max-width: 1200px) {
  .product-main.settings-page-shell { height: auto; overflow: visible; padding-bottom: 44px; }
  .page-content.settings-content { height: auto; }
}
@media (max-width: 900px) {
  .product-main.mail-page-shell, .product-main.admin-page-shell { height: auto; overflow: visible; padding-bottom: 44px; }
  .page-content.mail-content, .page-content.settings-content, .page-content.admin-content { height: auto; }
}
@media (max-width: 820px) {
  .product-shell { width: 100%; max-width: 100%; overflow-x: clip; }
  .sidebar {
    position: sticky;
    top: 0;
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    width: 100%;
    max-width: 100vw;
    height: auto;
    padding: 8px 12px 10px;
    overflow: hidden;
  }
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
    border: 1px solid rgba(186,230,253,.3);
    color: #e0f2fe;
    background: rgba(255,255,255,.06);
  }
  .menu-toggle span { display: block; width: 19px; height: 2px; border-radius: 99px; background: currentColor; transition: transform .18s ease, opacity .18s ease; }
  .menu-open .menu-toggle span:nth-child(1) { transform: translateY(7px) rotate(45deg); }
  .menu-open .menu-toggle span:nth-child(2) { opacity: 0; }
  .menu-open .menu-toggle span:nth-child(3) { transform: translateY(-7px) rotate(-45deg); }
  .sidebar-account { position: static; display: none; grid-column: 1 / -1; width: 100%; min-width: 0; margin: 6px 0 0; }
  .menu-open .sidebar-account { display: flex; }
  .sidebar-account :deep(.signed.compact) { display: flex; width: auto; min-width: 0; padding: 5px 7px; flex-direction: row; }
  .sidebar-account :deep(.signed.compact > div) { width: auto; }
  .sidebar-account :deep(.signed.compact span) { display: none; }
  .sidebar-account :deep(.signed.compact button) { width: auto; }
  nav { grid-column: 1 / -1; display: none; width: 100%; min-width: 0; margin: 8px 0 0; overflow: hidden; }
  .menu-open nav { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 4px; }
  nav::-webkit-scrollbar { display: none; }
  nav button { width: 100%; min-width: 0; min-height: 40px; justify-content: center; padding: 8px 6px; text-align: center; }
  nav button span, nav button.active::before { display: none; }
  .product-main { width: 100%; max-width: 100vw; margin-left: 0; padding: 0 16px 44px; overflow-x: clip; }
  .product-main.application-page,
  .product-main.calendar-page,
  .product-main.mail-page-shell,
  .product-main.settings-page-shell,
  .product-main.admin-page-shell { height: auto; overflow: visible; padding-bottom: 44px; }
  .page-content { min-width: 0; max-width: 100%; }
  .page-content.mail-content, .page-content.settings-content, .page-content.admin-content { height: auto; }
  .topbar { min-height: 0; flex-wrap: wrap; padding: 12px 0; }
  .home-quote-slot, .application-toolbar-slot { order: 3; width: auto; max-width: 100%; flex: 0 0 100%; margin: 4px 0; }
}
@media (max-width: 620px) {
  .menu-open nav { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  nav::before { display: none; }
  nav button { width: 100%; min-width: 0; min-height: 38px; justify-content: center; padding: 7px 4px; font-size: 13px; text-align: center; }
  .topbar > button { display: none; }
}
@media (max-width: 520px) {
  .product-main { padding-inline: 12px; }
}
</style>
