<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onMounted, ref } from 'vue'
import AccountManagement from './AccountManagement.vue'
import ResumeSettings from './ResumeSettings.vue'
import ProductPreferences from './ProductPreferences.vue'
import ProductDataManagement from './ProductDataManagement.vue'
import MailAccountSettings from './MailAccountSettings.vue'
import BackupManagement from './BackupManagement.vue'
import AppIcon from './AppIcon.vue'

type SectionId = 'center' | 'resume' | 'ai' | 'data'
const sections: { id: SectionId; label: string; summary: string; icon: string }[] = [
  { id: 'center', label: '个人中心', summary: '管理头像、昵称、账户安全和登录状态。', icon: 'user' },
  { id: 'resume', label: '我的简历', summary: '维护教育、实习和项目经历，供面试复习按需参考。', icon: 'file-spreadsheet' },
  { id: 'ai', label: '大模型 API', summary: '配置兼容接口、模型和 API Key。', icon: 'info' },
  { id: 'data', label: '数据管理', summary: '导入导出业务数据，管理邮件来源与历史备份。', icon: 'database' }
]
function sectionFromHash(): SectionId {
  const query = new URLSearchParams(window.location.hash.split('?')[1] || '')
  const section = query.get('section')
  return sections.some(item => item.id === section) ? section as SectionId : 'center'
}
const activeSection = ref<SectionId>(sectionFromHash())
const currentSection = computed(() => sections.find(item => item.id === activeSection.value)!)
function syncSectionFromHash() { activeSection.value = sectionFromHash() }
function selectSection(section: SectionId) {
  if (section === activeSection.value) return
  activeSection.value = section
  window.location.hash = `/profile?section=${section}`
}
onMounted(() => window.addEventListener('hashchange', syncSectionFromHash))
onActivated(syncSectionFromHash)
onBeforeUnmount(() => window.removeEventListener('hashchange', syncSectionFromHash))
</script>

<template>
  <div class="profile-workspace">
    <header class="settings-heading">
      <div><span>账户与偏好</span><h1>个人主页</h1><p>集中管理个人资料、面试简历、模型连接和数据安全。</p></div>
    </header>
    <div class="settings-layout">
      <nav class="settings-nav" aria-label="个人主页分区">
        <a v-for="section in sections" :key="section.id" :href="`#/profile?section=${section.id}`" :class="{ active: activeSection === section.id }" :aria-current="activeSection === section.id ? 'page' : undefined" @click.prevent="selectSection(section.id)">
          <AppIcon :name="section.icon" :size="18" /><span>{{ section.label }}</span><AppIcon class="section-arrow" name="chevron-right" :size="15" />
        </a>
      </nav>
      <main class="settings-view" :key="activeSection">
        <header class="view-heading"><div><h2>{{ currentSection.label }}</h2><p>{{ currentSection.summary }}</p></div></header>
        <div class="view-content">
          <AccountManagement v-if="activeSection === 'center'" />
          <ResumeSettings v-else-if="activeSection === 'resume'" />
          <ProductPreferences v-else-if="activeSection === 'ai'" />
          <div v-else class="data-sections">
            <ProductDataManagement />
            <MailAccountSettings />
            <BackupManagement />
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.profile-workspace{width:min(1180px,100%);min-height:100%;margin:0 auto;padding:30px 0 36px}.settings-heading{display:flex;align-items:flex-end;justify-content:space-between;margin:0 0 24px}.settings-heading>div>span{color:var(--color-primary);font-size:12px;font-weight:700}.settings-heading h1{margin:5px 0;font-size:clamp(27px,3vw,36px)}.settings-heading p,.view-heading p{margin:0;color:var(--color-muted-foreground);font-size:13px;line-height:1.55}.settings-layout{display:grid;grid-template-columns:224px minmax(0,1fr);align-items:start;gap:24px}.settings-nav{position:sticky;top:18px;display:grid;gap:5px;padding:8px;border:1px solid var(--color-border);border-radius:14px;background:var(--color-card);box-shadow:var(--shadow-sm)}.settings-nav a{display:flex;min-width:0;min-height:46px;align-items:center;gap:11px;padding:10px 12px;border:1px solid transparent;border-radius:9px;color:var(--color-muted-foreground);font-size:14px;font-weight:600;text-decoration:none;transition:color .18s ease,background-color .18s ease,border-color .18s ease}.settings-nav a:hover{color:var(--color-foreground);background:var(--color-muted)}.settings-nav a.active{border-color:color-mix(in srgb,var(--color-primary) 30%,var(--color-border));color:var(--color-primary);background:color-mix(in srgb,var(--color-primary) 9%,var(--color-card))}.section-arrow{margin-left:auto;opacity:0;transition:opacity .18s ease,transform .18s ease}.settings-nav a.active .section-arrow{transform:translateX(2px);opacity:1}.settings-view{min-width:0;animation:section-arrive .22s ease-out}.view-heading{display:flex;align-items:center;justify-content:space-between;margin:1px 0 15px}.view-heading h2{margin:0 0 4px;font-size:21px}.view-content{min-width:0}.view-content :deep(.card){min-width:0;margin:0!important;border:1px solid var(--color-border);box-shadow:var(--shadow-sm)}.view-content :deep(input:not([type=checkbox]):not([type=radio]):not([type=file]):not([type=range])),.view-content :deep(textarea),.view-content :deep(select){max-width:720px}.view-content :deep(.api-form .wide input),.view-content :deep(.password-field input),.view-content :deep(.api-key-field input){max-width:none}.data-sections{display:grid;gap:16px}.data-sections :deep(.data-card),.data-sections :deep(.mail-account-card),.data-sections :deep(.backup-card){padding:20px}.data-sections :deep(.intro),.data-sections :deep(.data-card>p),.data-sections :deep(.mail-account-card>p),.data-sections :deep(.backup-copy p){font-size:12px;color:var(--color-muted-foreground)}
@keyframes section-arrive{from{opacity:.78;transform:translateY(4px)}to{opacity:1;transform:translateY(0)}}
@media(prefers-reduced-motion:reduce){.settings-view{animation:none}.settings-nav a,.section-arrow{transition:none}}
@media(max-width:900px){.profile-workspace{padding-top:20px}.settings-layout{grid-template-columns:1fr;gap:16px}.settings-nav{position:static;grid-template-columns:repeat(2,minmax(0,1fr));padding:6px}.settings-nav a{min-height:46px}.section-arrow{display:none}}
@media(max-width:560px){.profile-workspace{padding:14px 0 22px}.settings-heading{margin-bottom:16px}.settings-nav{gap:4px}.settings-nav a{gap:8px;padding:9px;font-size:13px}.view-heading h2{font-size:19px}.data-sections :deep(.data-card),.data-sections :deep(.mail-account-card),.data-sections :deep(.backup-card){padding:16px}}
</style>
