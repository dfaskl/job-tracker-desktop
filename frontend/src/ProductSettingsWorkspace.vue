<script setup lang="ts">
import { nextTick, onActivated, onBeforeUnmount, onMounted, ref } from 'vue'
import AccountManagement from './AccountManagement.vue'
import ResumeSettings from './ResumeSettings.vue'
import ProductPreferences from './ProductPreferences.vue'
import ProductDataManagement from './ProductDataManagement.vue'
import MailAccountSettings from './MailAccountSettings.vue'
import BackupManagement from './BackupManagement.vue'
import AppIcon from './AppIcon.vue'
import { profileSectionScrollTarget } from './profileNavigation'

type SectionId = 'center' | 'resume' | 'ai' | 'mail' | 'data'
const sections: { id: SectionId; label: string; icon: string }[] = [
  { id: 'center', label: '个人中心', icon: 'user' },
  { id: 'resume', label: '我的简历', icon: 'file-spreadsheet' },
  { id: 'ai', label: '大模型 API', icon: 'info' },
  { id: 'mail', label: '邮件配置', icon: 'mail' },
  { id: 'data', label: '数据管理', icon: 'database' }
]
function sectionFromHash(): SectionId {
  const query = new URLSearchParams(window.location.hash.split('?')[1] || '')
  const section = query.get('section')
  return sections.some(item => item.id === section) ? section as SectionId : 'center'
}
const activeSection = ref<SectionId>(sectionFromHash())
const sectionRefs = ref<Partial<Record<SectionId, HTMLElement>>>({})
let scrollFrame = 0
function setSectionRef(section: SectionId, element: Element | null) {
  if (element instanceof HTMLElement) sectionRefs.value[section] = element
  else delete sectionRefs.value[section]
}
function syncActiveSectionFromScroll() {
  scrollFrame = 0
  const anchorLine = 150
  const visible = sections
    .map(section => ({ section, top: sectionRefs.value[section.id]?.getBoundingClientRect().top ?? Number.POSITIVE_INFINITY }))
    .filter(item => item.top <= anchorLine)
  if (visible.length) activeSection.value = visible[visible.length - 1].section.id
  else activeSection.value = sections[0].id
  if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 8) activeSection.value = sections[sections.length - 1].id
}
function onPageScroll() {
  if (!scrollFrame) scrollFrame = window.requestAnimationFrame(syncActiveSectionFromScroll)
}
function syncSectionFromHash(shouldScroll = true) {
  activeSection.value = sectionFromHash()
  if (!shouldScroll) return
  const target = profileSectionScrollTarget(window.location.hash)
  void nextTick(() => {
    if (target) sectionRefs.value[target as SectionId]?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    else window.scrollTo({ top: 0, behavior: 'auto' })
  })
}
function flashSection(section: SectionId) {
  const target = sectionRefs.value[section]
  if (!target) return
  target.classList.remove('section-highlight')
  void target.offsetWidth
  target.classList.add('section-highlight')
  window.setTimeout(() => target.classList.remove('section-highlight'), 1300)
}
function selectSection(section: SectionId) {
  activeSection.value = section
  window.location.hash = `/profile?section=${section}`
  sectionRefs.value[section]?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  window.setTimeout(() => flashSection(section), 220)
}
onMounted(() => {
  window.addEventListener('hashchange', onHashChange)
  window.addEventListener('scroll', onPageScroll, { passive: true })
  void nextTick(() => {
    const target = profileSectionScrollTarget(window.location.hash)
    if (target) sectionRefs.value[target as SectionId]?.scrollIntoView({ behavior: 'auto', block: 'start' })
    else window.scrollTo({ top: 0, behavior: 'auto' })
    syncActiveSectionFromScroll()
  })
})
const onHashChange = () => {
  const page = window.location.hash.replace(/^#\/?/, '').split('?')[0]
  if (page === 'profile' || page === 'settings') syncSectionFromHash()
}
onActivated(() => syncSectionFromHash(false))
onBeforeUnmount(() => {
  window.removeEventListener('hashchange', onHashChange)
  window.removeEventListener('scroll', onPageScroll)
  if (scrollFrame) window.cancelAnimationFrame(scrollFrame)
})
</script>

<template>
  <div class="profile-workspace">
    <header class="settings-heading">
      <div><span>账户与偏好</span><h1>个人主页</h1><p>集中管理个人资料、面试简历、模型和邮箱连接及数据安全。</p></div>
    </header>
    <div class="settings-layout">
      <nav class="settings-nav" aria-label="个人主页分区">
        <a v-for="section in sections" :key="section.id" :href="`#/profile?section=${section.id}`" :class="{ active: activeSection === section.id }" :aria-current="activeSection === section.id ? 'page' : undefined" @click.prevent="selectSection(section.id)">
          <AppIcon :name="section.icon" :size="18" /><span>{{ section.label }}</span><AppIcon class="section-arrow" name="chevron-right" :size="15" />
        </a>
      </nav>
      <main class="settings-content">
        <section :id="`profile-section-${sections[0].id}`" :ref="element => setSectionRef('center', element as Element | null)" class="profile-section">
          <div class="section-title"><h2>个人中心</h2><p>管理个人资料、账户安全和登录状态。</p></div>
          <AccountManagement />
        </section>
        <section :id="`profile-section-${sections[1].id}`" :ref="element => setSectionRef('resume', element as Element | null)" class="profile-section">
          <div class="section-title"><h2>我的简历</h2><p>维护教育、实习和项目经历，供面试复习按需参考。</p></div>
          <ResumeSettings />
        </section>
        <section :id="`profile-section-${sections[2].id}`" :ref="element => setSectionRef('ai', element as Element | null)" class="profile-section">
          <div class="section-title"><h2>大模型 API</h2><p>配置兼容接口、模型和 API Key。</p></div>
          <ProductPreferences />
        </section>
        <section :id="`profile-section-${sections[3].id}`" :ref="element => setSectionRef('mail', element as Element | null)" class="profile-section mail-settings-section">
          <div class="section-title"><h2>邮件配置</h2><p>连接邮箱，让系统识别邮件并辅助创建求职日程。</p></div>
          <MailAccountSettings />
        </section>
        <section :id="`profile-section-${sections[4].id}`" :ref="element => setSectionRef('data', element as Element | null)" class="profile-section data-sections">
          <div class="section-title"><h2>数据管理</h2><p>导入导出业务数据，管理历史备份。</p></div>
          <ProductDataManagement />
          <BackupManagement />
        </section>
      </main>
    </div>
  </div>
</template>

<style scoped>
.profile-workspace{width:min(1540px,100%);min-height:100%;margin:0 auto;padding:30px 0 36px}.settings-heading{display:flex;align-items:flex-end;justify-content:space-between;margin:0 0 20px}.settings-heading>div>span{color:var(--color-primary);font-size:12px;font-weight:700}.settings-heading h1{margin:5px 0;font-size:clamp(27px,3vw,36px)}.settings-heading p,.view-heading p{margin:0;color:var(--color-muted-foreground);font-size:13px;line-height:1.55}.settings-layout{display:grid;grid-template-columns:minmax(0,1fr);align-items:start;gap:22px}.settings-nav{position:sticky;top:12px;z-index:5;display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px;padding:8px;border:1px solid var(--color-border);border-radius:14px;background:color-mix(in srgb,var(--color-card) 94%,transparent);box-shadow:var(--shadow-sm);backdrop-filter:blur(14px)}.settings-nav a{display:flex;min-width:0;min-height:46px;align-items:center;justify-content:center;gap:9px;padding:10px 12px;border:1px solid transparent;border-radius:9px;color:var(--color-muted-foreground);font-size:14px;font-weight:600;text-decoration:none;transition:color .18s ease,background-color .18s ease,border-color .18s ease}.settings-nav a:hover{color:var(--color-foreground);background:var(--color-muted)}.settings-nav a.active{border-color:color-mix(in srgb,var(--color-primary) 30%,var(--color-border));color:var(--color-primary);background:color-mix(in srgb,var(--color-primary) 9%,var(--color-card))}.section-arrow{display:none}.settings-content{display:grid;min-width:0;gap:28px}.profile-section{display:grid;grid-template-columns:minmax(0,1fr);grid-auto-flow:row;min-width:0;gap:12px;scroll-margin-top:82px}.profile-section>*{width:100%;min-width:0}.section-title{padding:0 2px}.section-title h2{margin:0 0 4px;font-size:20px}.section-title p{margin:0;color:var(--color-muted-foreground);font-size:12px;line-height:1.55}.profile-section :deep(.card){width:100%;min-width:0;margin:0!important;border:1px solid var(--color-border);box-shadow:var(--shadow-sm)}.profile-section :deep(input:not([type=checkbox]):not([type=radio]):not([type=file]):not([type=range])),.profile-section :deep(textarea),.profile-section :deep(select){max-width:min(900px,100%)}.profile-section :deep(.api-form .wide input),.profile-section :deep(.password-field input),.profile-section :deep(.api-key-field input){max-width:none}.profile-section.data-sections{grid-template-columns:minmax(0,1fr);gap:14px}.data-sections :deep(.data-card),.data-sections :deep(.mail-account-card),.data-sections :deep(.backup-card){padding:22px}.data-sections :deep(.data-card>p),.data-sections :deep(.mail-account-card>p),.data-sections :deep(.backup-copy p){font-size:12px;color:var(--color-muted-foreground)}
/* Keep all profile modules mounted in one page; the rail only changes the scroll target. */
.settings-content{display:grid;min-width:0;gap:28px}.profile-section{display:grid;min-width:0;gap:12px;scroll-margin-top:24px}.section-title{padding:0 2px}.section-title h2{margin:0 0 4px;font-size:20px}.section-title p{margin:0;color:var(--color-muted-foreground);font-size:12px;line-height:1.55}.profile-section :deep(.card){min-width:0;margin:0!important;border:1px solid var(--color-border);box-shadow:var(--shadow-sm)}.profile-section :deep(input:not([type=checkbox]):not([type=radio]):not([type=file]):not([type=range])),.profile-section :deep(textarea),.profile-section :deep(select){max-width:720px}.profile-section :deep(.api-form .wide input),.profile-section :deep(.password-field input),.profile-section :deep(.api-key-field input){max-width:none}.profile-section.data-sections{gap:14px}.data-sections :deep(.data-card),.data-sections :deep(.mail-account-card),.data-sections :deep(.backup-card){padding:20px}.data-sections :deep(.data-card>p),.data-sections :deep(.mail-account-card>p),.data-sections :deep(.backup-copy p){font-size:12px;color:var(--color-muted-foreground)}.profile-section.section-highlight :deep(.card){animation:profile-section-pulse 1.05s ease-in-out 2}@keyframes profile-section-pulse{0%,100%{border-color:var(--color-border);box-shadow:var(--shadow-sm)}45%,70%{border-color:var(--color-primary);box-shadow:0 0 0 4px color-mix(in srgb,var(--color-primary) 17%,transparent),var(--shadow-md)}}
@keyframes section-arrive{from{opacity:.78;transform:translateY(4px)}to{opacity:1;transform:translateY(0)}}
.profile-section>*{grid-area:auto!important;width:100%;min-width:0}
@media(max-width:900px){.profile-workspace{padding-top:20px}.settings-layout{gap:18px}.settings-nav{top:76px;padding:6px}.settings-nav a{min-height:46px}}
@media(max-width:560px){.profile-workspace{padding:14px 0 22px}.settings-heading{margin-bottom:16px}.settings-nav{top:74px;gap:4px}.settings-nav a{gap:6px;padding:8px 5px;font-size:12px}.settings-nav a :deep(svg){width:16px;height:16px}.section-title h2{font-size:18px}.settings-content{gap:22px}.data-sections :deep(.data-card),.data-sections :deep(.mail-account-card),.data-sections :deep(.backup-card){padding:16px}}
</style>
<style scoped>
.settings-nav{grid-template-columns:repeat(5,minmax(0,1fr))}
.mail-settings-section :deep(.mail-account-card){grid-area:auto;padding:20px}
@media(max-width:560px){.settings-nav{grid-template-columns:repeat(3,minmax(0,1fr))}.mail-settings-section :deep(.mail-account-card){padding:16px}}
</style>
