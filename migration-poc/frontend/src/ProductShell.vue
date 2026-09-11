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
const pageHeading = ref<HTMLElement | null>(null)
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
  syncHash()
  window.addEventListener('hashchange', syncHash)
  void store.initialize()
})
watch(activePage, async () => { await nextTick(); pageHeading.value?.focus({ preventScroll: true }) })
onBeforeUnmount(() => window.removeEventListener('hashchange', syncHash))
</script>

<template>
  <div class="product-shell">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <aside class="sidebar">
      <button class="brand" type="button" aria-label="返回首页" @click="navigate('home')">
        <img src="/favicon.svg" alt="" aria-hidden="true"><div><strong>求职进度本</strong><small>Vue + Java</small></div>
      </button>
      <nav aria-label="主要导航">
        <button v-for="item in pages" :key="item.id" type="button" :class="{ active: activePage === item.id }" :aria-current="activePage === item.id ? 'page' : undefined" @click="navigate(item.id)">
          <span aria-hidden="true">{{ item.icon }}</span>{{ item.label }}
        </button>
      </nav>
      <div class="sidebar-account"><AccountAccess v-if="store.user.value" compact /></div>
    </aside>

    <main id="main-content" class="product-main" :class="{ 'application-page': activePage === 'applications', 'calendar-page': activePage === 'calendar', 'mail-page-shell': activePage === 'mail', 'settings-page-shell': activePage === 'settings', 'admin-page-shell': activePage === 'admin' }">
      <header class="topbar">
        <div><h1 ref="pageHeading" tabindex="-1">{{ current.label }}</h1><p>{{ current.subtitle }}</p></div>
        <div v-show="activePage === 'home'" id="home-quote-slot" class="home-quote-slot"></div>
        <div id="application-toolbar-slot" class="application-toolbar-slot" :class="{ active: activePage === 'applications' }"></div>
        <button v-if="activePage === 'home' || activePage === 'applications'" type="button" @click="createApplication">＋ 新建投递</button>
      </header>

      <div class="page-content" :class="{ 'application-content': activePage === 'applications', 'calendar-content': activePage === 'calendar', 'mail-content': activePage === 'mail', 'settings-content': activePage === 'settings', 'admin-content': activePage === 'admin' }">
        <AccountAccess v-if="!store.user.value" />
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
  padding: 24px 16px 18px;
  color: #e0f2fe;
  background: var(--sidebar);
  border-right: 1px solid rgba(255,255,255,.1);
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
nav { display: grid; gap: 5px; margin-top: 28px; }
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
nav button span { width: 20px; color: #7dd3fc; font-size: 16px; text-align: center; }
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
  min-height: 104px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  border-bottom: 1px solid var(--color-border);
  background: color-mix(in srgb, var(--color-background) 94%, transparent);
  backdrop-filter: blur(16px);
}
.topbar h1 { margin: 0 0 4px; font-size: 27px; line-height: 1.15; }
.topbar p { margin: 0; color: var(--color-muted-foreground); line-height: 1.4; }
.topbar > button {
  min-width: 116px;
  color: var(--color-on-primary);
  background: var(--color-primary);
}
.home-quote-slot { display: flex; min-width: 0; flex: 1; justify-content: center; }
.application-toolbar-slot { display: none; min-width: 0; flex: 1; margin: 7px 18px; }
.application-toolbar-slot.active { display: flex; }
.page-content { width: min(1180px, 100%); margin: 0 auto; }
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
.page-content.admin-content { height: calc(100vh - 104px); }
.page-content :deep(.card) { margin-top: 18px; }

@media (max-width: 1200px) {
  .product-main.settings-page-shell { height: auto; overflow: visible; padding-bottom: 44px; }
  .page-content.settings-content { height: auto; }
}
@media (max-width: 900px) {
  .product-main.mail-page-shell, .product-main.admin-page-shell { height: auto; overflow: visible; padding-bottom: 44px; }
  .page-content.mail-content, .page-content.settings-content, .page-content.admin-content { height: auto; }
}
@media (max-width: 820px) {
  .sidebar {
    position: sticky;
    top: 0;
    width: 100%;
    height: auto;
    padding: 8px 12px 10px;
  }
  .brand { width: auto; }
  .brand small { display: none; }
  .sidebar-account { position: absolute; top: 8px; right: 12px; width: auto; margin: 0; }
  .sidebar-account :deep(.signed.compact) { display: flex; width: auto; padding: 5px 7px; }
  .sidebar-account :deep(.signed.compact span) { display: none; }
  .sidebar-account :deep(.signed.compact button) { width: auto; }
  nav { display: flex; margin: 6px -2px 0; overflow-x: auto; scrollbar-width: none; }
  nav::-webkit-scrollbar { display: none; }
  nav button { width: auto; min-width: max-content; flex: 0 0 auto; padding: 8px 12px; }
  nav button span, nav button.active::before { display: none; }
  .product-main { margin-left: 0; padding: 0 16px 44px; }
  .product-main.calendar-page,
  .product-main.mail-page-shell,
  .product-main.settings-page-shell,
  .product-main.admin-page-shell { height: auto; overflow: visible; padding-bottom: 44px; }
  .page-content.mail-content, .page-content.settings-content, .page-content.admin-content { height: auto; }
  .topbar { min-height: 92px; flex-wrap: wrap; padding: 12px 0; }
  .topbar h1 { font-size: 22px; }
  .topbar p { font-size: 13px; }
  .home-quote-slot, .application-toolbar-slot { order: 3; width: 100%; flex-basis: 100%; margin: 4px 0; }
}
@media (max-width: 520px) {
  .topbar > button { display: none; }
  .product-main { padding-inline: 12px; }
}
</style>
