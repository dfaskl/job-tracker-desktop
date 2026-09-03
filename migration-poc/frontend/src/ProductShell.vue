<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import ProductHome from './ProductHome.vue'
import ProductStats from './ProductStats.vue'
import MigrationApplications from './MigrationApplications.vue'
import MigrationApplicationCrud from './MigrationApplicationCrud.vue'
import MigrationEventCalendar from './MigrationEventCalendar.vue'
import MigrationAiMail from './MigrationAiMail.vue'
import MigrationSessionStatus from './MigrationSessionStatus.vue'
import MigrationBackups from './MigrationBackups.vue'
import MigrationAdmin from './MigrationAdmin.vue'
import MigrationDiagnostics from './MigrationDiagnostics.vue'
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
  { id: 'admin', label: '管理员', icon: '◇', subtitle: '管理测试环境中的账号与权限' }
]

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
        <span>✓</span><div><strong>求职进度本</strong><small>Vue + Java</small></div>
      </button>
      <nav aria-label="主要导航">
        <button v-for="item in pages" :key="item.id" type="button" :class="{ active: activePage === item.id }" @click="navigate(item.id)">
          <span aria-hidden="true">{{ item.icon }}</span>{{ item.label }}
        </button>
      </nav>
      <div class="sidebar-note">新旧服务并行运行<br>当前生产数据保持只读</div>
    </aside>

    <main class="product-main">
      <header class="topbar">
        <div><h1>{{ current.label }}</h1><p>{{ current.subtitle }}</p></div>
        <button v-if="activePage === 'home' || activePage === 'applications'" type="button" @click="navigate('applications')">＋ 新建投递</button>
      </header>

      <div class="page-content">
        <AccountAccess />
        <ProductHome v-if="activePage === 'home'" @navigate="navigate" />
        <template v-else-if="activePage === 'applications'">
          <MigrationApplications />
          <MigrationApplicationCrud />
        </template>
        <MigrationEventCalendar v-else-if="activePage === 'calendar'" />
        <MigrationAiMail v-else-if="activePage === 'mail'" />
        <ProductStats v-else-if="activePage === 'stats'" />
        <template v-else-if="activePage === 'settings'">
          <MigrationSessionStatus />
          <MigrationBackups />
          <MigrationDiagnostics />
        </template>
        <MigrationAdmin v-else-if="activePage === 'admin'" />
      </div>
    </main>
  </div>
</template>

<style scoped>
.product-shell { min-height: 100vh; }
.sidebar { position: fixed; inset: 0 auto 0 0; z-index: 10; display: flex; width: 228px; flex-direction: column; padding: 24px 16px; color: #dce5ff; background: #17213a; }
.brand { display: flex; align-items: center; gap: 11px; width: 100%; padding: 8px; color: inherit; background: transparent; text-align: left; }
.brand > span { display: grid; width: 34px; height: 34px; place-items: center; border-radius: 10px; color: #fff; background: #526ddd; }
.brand div { display: grid; gap: 2px; }
.brand strong { font-size: 16px; }
.brand small { color: #91a0c5; font-size: 11px; }
nav { display: grid; gap: 6px; margin-top: 32px; }
nav button { display: flex; align-items: center; gap: 12px; width: 100%; padding: 12px 13px; color: #aebbd9; background: transparent; text-align: left; }
nav button span { width: 20px; color: #8392b7; font-size: 17px; text-align: center; }
nav button:hover, nav button.active { color: #fff; background: #273552; }
.sidebar-note { margin-top: auto; padding: 13px; border: 1px solid #344362; border-radius: 11px; color: #91a0c5; font-size: 11px; line-height: 1.7; }
.product-main { width: auto; min-width: 0; margin: 0 0 0 228px; padding: 0 42px 72px; }
.topbar { position: sticky; top: 0; z-index: 5; display: flex; align-items: center; justify-content: space-between; min-height: 112px; gap: 20px; border-bottom: 1px solid #e4e9f2; background: rgba(244, 247, 251, .94); backdrop-filter: blur(14px); }
.topbar h1 { margin: 0 0 6px; font-size: 28px; }
.topbar p { margin: 0; line-height: 1.4; }
.page-content { width: min(1120px, 100%); margin: 0 auto; }
.page-content :deep(.card) { margin-top: 22px; }

@media (max-width: 820px) {
  .sidebar { position: sticky; top: 0; width: 100%; height: auto; padding: 10px 14px; }
  .brand { width: auto; }
  .brand small, .sidebar-note { display: none; }
  nav { display: flex; margin: 8px -4px 0; overflow-x: auto; }
  nav button { flex: 0 0 auto; width: auto; padding: 9px 11px; }
  nav button span { display: none; }
  .product-main { margin-left: 0; padding: 0 16px 48px; }
  .topbar { min-height: 92px; }
  .topbar h1 { font-size: 23px; }
  .topbar p { font-size: 13px; }
}

@media (max-width: 520px) {
  .topbar > button { display: none; }
}
</style>
