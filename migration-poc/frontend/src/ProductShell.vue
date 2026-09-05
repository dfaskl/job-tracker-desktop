<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, type Component } from 'vue'
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
const store = useJobTrackerStore()
const current = computed(() => pages.find((item) => item.id === activePage.value) || pages[0])

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
  if (activePage.value === page) return
  window.location.hash = page
  activePage.value = page
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  store.initialize()
  syncHash()
  window.addEventListener('hashchange', syncHash)
})
onBeforeUnmount(() => window.removeEventListener('hashchange', syncHash))
</script>

<template>
  <div class="product-shell">
    <aside class="sidebar">
      <button class="brand" type="button" @click="navigate('home')">
        <img src="/favicon.svg" alt="" aria-hidden="true"><div><strong>求职进度本</strong><small>Vue + Java</small></div>
      </button>
      <nav aria-label="主要导航">
        <button v-for="item in pages" :key="item.id" type="button" :class="{ active: activePage === item.id }" @click="navigate(item.id)">
          <span aria-hidden="true">{{ item.icon }}</span>{{ item.label }}
        </button>
      </nav>
      <div class="sidebar-account"><AccountAccess v-if="store.user.value" compact /></div>
    </aside>

    <main class="product-main" :class="{ 'application-page': activePage === 'applications', 'calendar-page': activePage === 'calendar', 'mail-page-shell': activePage === 'mail', 'settings-page-shell': activePage === 'settings' }">
      <header class="topbar">
        <div><h1>{{ current.label }}</h1><p>{{ current.subtitle }}</p></div>
        <div v-if="activePage === 'home'" id="home-quote-slot" class="home-quote-slot"></div>
        <div id="application-toolbar-slot" class="application-toolbar-slot" :class="{ active: activePage === 'applications' }"></div>
        <button v-if="activePage === 'home' || activePage === 'applications'" type="button" @click="createApplication">＋ 新建投递</button>
      </header>

      <div class="page-content" :class="{ 'application-content': activePage === 'applications', 'calendar-content': activePage === 'calendar', 'mail-content': activePage === 'mail', 'settings-content': activePage === 'settings' }">
        <AccountAccess v-if="!store.user.value" />
        <KeepAlive :max="7">
          <component :is="pageComponents[activePage]" :key="`${activePage}-${cacheEpoch}`" @navigate="navigate" />
        </KeepAlive>
      </div>
    </main>
  </div>
</template>

<style scoped>
.product-shell { min-height: 100vh; }
.sidebar { position: fixed; inset: 0 auto 0 0; z-index: 10; display: flex; width: 228px; flex-direction: column; padding: 24px 16px; color: #dce5ff; background: #17213a; }
.brand { display: flex; align-items: center; gap: 11px; width: 100%; padding: 8px; color: inherit; background: transparent; text-align: left; }
.brand > img { width: 38px; height: 38px; flex: 0 0 38px; border-radius: 11px; box-shadow: 0 5px 14px rgba(18, 31, 73, .32); }
.brand div { display: grid; gap: 2px; }
.brand strong { font-size: 16px; }
.brand small { color: #91a0c5; font-size: 11px; }
nav { display: grid; gap: 6px; margin-top: 32px; }
nav button { display: flex; align-items: center; gap: 12px; width: 100%; padding: 12px 13px; color: #aebbd9; background: transparent; text-align: left; }
nav button span { width: 20px; color: #8392b7; font-size: 17px; text-align: center; }
nav button:hover, nav button.active { color: #fff; background: #273552; }
.sidebar-account { display: flex; width: 100%; margin-top: auto; align-items: center; justify-content: center; }
.product-main { width: auto; min-width: 0; margin: 0 0 0 228px; padding: 0 42px 72px; }
.topbar { position: sticky; top: 0; z-index: 5; display: flex; align-items: center; justify-content: space-between; min-height: 112px; gap: 20px; border-bottom: 1px solid #e4e9f2; background: rgba(244, 247, 251, .94); backdrop-filter: blur(14px); }
.topbar h1 { margin: 0 0 6px; font-size: 28px; }
.home-quote-slot { display: flex; flex: 1; justify-content: center; min-width: 0; }
.application-toolbar-slot { display: none; flex: 1; min-width: 0; margin: 7px 20px; }
.application-toolbar-slot.active { display: flex; }
.product-main.application-page, .product-main.calendar-page, .product-main.mail-page-shell, .product-main.settings-page-shell { height: 100vh; overflow: hidden; padding-bottom: 0; }
.topbar p { margin: 0; line-height: 1.4; }
.page-content { width: min(1120px, 100%); margin: 0 auto; }
.page-content.application-content, .page-content.calendar-content, .page-content.mail-content, .page-content.settings-content { width: 100%; max-width: none; }
.page-content.mail-content, .page-content.settings-content { height: calc(100vh - 112px); }
.page-content :deep(.card) { margin-top: 22px; }

@media (max-width: 1200px) { .product-main.settings-page-shell { height: auto; overflow: visible; padding-bottom: 48px; } .page-content.settings-content { height: auto; } }

@media (max-width: 900px) { .product-main.mail-page-shell { height: auto; overflow: visible; padding-bottom: 48px; } .page-content.mail-content, .page-content.settings-content { height: auto; } }

@media (max-width: 820px) {
  .sidebar { position: sticky; top: 0; width: 100%; height: auto; padding: 10px 14px; }
  .brand { width: auto; }
  .brand small { display: none; }
  .sidebar-account { position: absolute; top: 10px; right: 14px; width: auto; margin: 0; }
  .sidebar-account :deep(.signed.compact) { display: flex; width: auto; padding: 6px 8px; }
  .sidebar-account :deep(.signed.compact span) { display: none; }
  .sidebar-account :deep(.signed.compact button) { width: auto; }
  nav { display: flex; margin: 8px -4px 0; overflow-x: auto; }
  nav button { flex: 0 0 auto; width: auto; padding: 9px 11px; }
  nav button span { display: none; }
  .product-main { margin-left: 0; padding: 0 16px 48px; }
  .product-main.calendar-page, .product-main.mail-page-shell, .product-main.settings-page-shell { height: auto; overflow: visible; padding-bottom: 48px; }
  .page-content.mail-content, .page-content.settings-content { height: auto; }
  .topbar { min-height: 92px; }
  .topbar h1 { font-size: 23px; }
  .home-quote-slot { order: 3; width: 100%; flex-basis: 100%; }
  .application-toolbar-slot { order: 3; width: 100%; flex-basis: 100%; margin: 6px 0; }
  .topbar { flex-wrap: wrap; padding: 12px 0; }
  .topbar p { font-size: 13px; }
}

@media (max-width: 520px) {
  .topbar > button { display: none; }
}
</style>
