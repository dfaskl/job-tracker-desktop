<script setup lang="ts">
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref } from 'vue'
import AccountManagement from './AccountManagement.vue'
import ResumeSettings from './ResumeSettings.vue'
import AppIcon from './AppIcon.vue'
import { profileSectionScrollTarget } from './profileNavigation'
import ProductPreferences from './ProductPreferences.vue'
import ProductDataManagement from './ProductDataManagement.vue'
import MailAccountSettings from './MailAccountSettings.vue'
import BackupManagement from './BackupManagement.vue'
import McpAccessSettings from './McpAccessSettings.vue'

type SectionId = 'center' | 'resume' | 'ai' | 'mail' | 'mcp' | 'data'
const sections: { id: SectionId; label: string; icon: string }[] = [
  { id: 'center', label: '个人中心', icon: 'user' },
  { id: 'resume', label: '我的简历', icon: 'file-spreadsheet' },
  { id: 'ai', label: '大模型 API', icon: 'info' },
  { id: 'mail', label: '邮件配置', icon: 'mail' },
  { id: 'mcp', label: 'MCP 访问', icon: 'key' },
  { id: 'data', label: '数据管理', icon: 'database' }
]
function sectionFromHash(): SectionId {
  const query = new URLSearchParams(window.location.hash.split('?')[1] || '')
  const section = query.get('section')
  return sections.some(item => item.id === section) ? section as SectionId : 'center'
}
const activeSection = ref<SectionId>(sectionFromHash())
const activeIndex = computed(() => sections.findIndex(section => section.id === activeSection.value))
const navigation = ref<HTMLElement | null>(null)
const burst = ref<{ section: SectionId; id: number } | null>(null)
let burstId = 0
const particles = Array.from({ length: 15 }, (_, index) => {
  const angle = index * 2.39996
  const distance = 24 + (index * 11 % 29)
  return {
    '--particle-x': `${Math.cos(angle) * distance}px`,
    '--particle-y': `${Math.sin(angle) * distance * .65}px`,
    '--particle-size': `${8 + index % 4 * 2}px`,
    '--particle-time': `${450 + index * 53 % 290}ms`,
  }
})
const sectionRefs = ref<Partial<Record<SectionId, HTMLElement>>>({})
let scrollFrame = 0
let pendingSectionHash: string | null = null
function setSectionRef(section: SectionId, element: Element | null) {
  if (element instanceof HTMLElement) sectionRefs.value[section] = element
  else delete sectionRefs.value[section]
}
function syncActiveSectionFromScroll() {
  scrollFrame = 0
  if (!navigation.value) return
  const anchorLine = navigation.value.getBoundingClientRect().bottom + 20
  const root = document.documentElement
  if (root.scrollHeight > root.clientHeight && root.scrollHeight - root.clientHeight - window.scrollY <= 1) {
    activeSection.value = 'data'
    return
  }
  const visible = sections
    .map(section => ({ section, top: sectionRefs.value[section.id]?.getBoundingClientRect().top ?? Number.POSITIVE_INFINITY }))
    .filter(item => item.top <= anchorLine)
  activeSection.value = visible.at(-1)?.section.id || sections[0].id
}
function onPageScroll() {
  if (!scrollFrame) scrollFrame = window.requestAnimationFrame(syncActiveSectionFromScroll)
}
async function scrollToSection(section: SectionId) {
  await nextTick()
  const target = sectionRefs.value[section]
  const nav = navigation.value
  if (!target || !nav) return
  const navBottom = nav.offsetHeight + (parseFloat(getComputedStyle(nav).top) || 0)
  window.scrollTo({ top: window.scrollY + target.getBoundingClientRect().top - navBottom - 16, behavior: 'instant' })
}
function syncSectionFromHash(shouldScroll = true) {
  activeSection.value = sectionFromHash()
  if (!shouldScroll) return
  const target = profileSectionScrollTarget(window.location.hash)
  void nextTick(() => {
    if (target) void scrollToSection(target as SectionId)
    else window.scrollTo({ top: 0, behavior: 'auto' })
  })
}
function selectSection(section: SectionId) {
  if (section !== activeSection.value && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    burst.value = { section, id: ++burstId }
  }
  activeSection.value = section
  const nextHash = `#/profile?section=${section}`
  if (window.location.hash !== nextHash) {
    pendingSectionHash = nextHash
    window.location.hash = `/profile?section=${section}`
  }
  void scrollToSection(section)
}
onMounted(() => {
  window.addEventListener('hashchange', onHashChange)
  window.addEventListener('scroll', onPageScroll, { passive: true })
  syncSectionFromHash()
})
const onHashChange = () => {
  const page = window.location.hash.replace(/^#\/?/, '').split('?')[0]
  if (page === 'profile' || page === 'settings') {
    if (pendingSectionHash === window.location.hash) {
      pendingSectionHash = null
      syncSectionFromHash(false)
    } else syncSectionFromHash()
  } else pendingSectionHash = null
}
onActivated(() => syncSectionFromHash(false))
onDeactivated(() => { if (scrollFrame) window.cancelAnimationFrame(scrollFrame); scrollFrame = 0; burst.value = null })
onBeforeUnmount(() => {
  window.removeEventListener('hashchange', onHashChange)
  window.removeEventListener('scroll', onPageScroll)
  if (scrollFrame) window.cancelAnimationFrame(scrollFrame)
})
</script>

<template>
  <div class="profile-workspace">
    <nav ref="navigation" class="settings-nav workspace-light-surface" aria-label="个人设置目录">
      <svg class="nav-filter" aria-hidden="true" width="0" height="0">
        <filter id="profile-nav-goo" x="-30%" y="-50%" width="160%" height="200%">
          <feGaussianBlur in="SourceGraphic" stdDeviation="3" />
          <feColorMatrix type="matrix" values="1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  0 0 0 15 -6" />
        </filter>
      </svg>
      <span class="nav-indicator-track" aria-hidden="true">
        <span class="nav-indicator" :style="{ '--index': activeIndex, '--column': activeIndex % 3, '--row': Math.floor(activeIndex / 3) }"></span>
      </span>
      <a v-for="section in sections" :id="`profile-nav-${section.id}`" :key="section.id" :href="`#/profile?section=${section.id}`" :aria-current="activeSection === section.id ? 'location' : undefined" @click.left.exact.prevent="selectSection(section.id)">
        <span v-if="burst?.section === section.id" :key="burst.id" class="nav-burst" aria-hidden="true" @animationend.self="burst = null">
          <i v-for="(particle, index) in particles" :key="index" :style="particle"></i>
        </span>
        <span class="nav-label"><AppIcon :name="section.icon" :size="18" /><span>{{ section.label }}</span></span>
      </a>
    </nav>
    <div class="settings-content">
      <section v-for="section in sections" :id="`profile-section-${section.id}`" :key="section.id" :ref="element => setSectionRef(section.id, element as Element | null)" :aria-labelledby="`profile-nav-${section.id}`" class="workspace-light-surface profile-section" :class="{ 'data-sections': section.id === 'data' }">
        <AccountManagement v-if="section.id === 'center'" />
        <ResumeSettings v-else-if="section.id === 'resume'" />
        <ProductPreferences v-else-if="section.id === 'ai'" />
        <MailAccountSettings v-else-if="section.id === 'mail'" />
        <McpAccessSettings v-else-if="section.id === 'mcp'" />
        <template v-else>
          <ProductDataManagement />
          <BackupManagement />
        </template>
      </section>
    </div>
  </div>
</template>

<style scoped>
.profile-workspace {
  min-height: 100%;
  margin: 0 auto;
  padding-bottom: 36px;
}
.settings-nav {
  --nav-columns: 6;
  --nav-gap: 8px;
  --nav-padding: 8px;
  --nav-height: 46px;
  position: sticky;
  top: 12px;
  z-index: 5;
  isolation: isolate;
  display: grid;
  grid-template-columns: repeat(var(--nav-columns), minmax(0, 1fr));
  gap: var(--nav-gap);
  padding: var(--nav-padding);
  overflow: hidden;
  background-color: var(--color-card);
  box-shadow: var(--shadow-sm);
  backdrop-filter: blur(14px);
}
.nav-filter { position: absolute; pointer-events: none; }
.nav-indicator-track {
  position: absolute;
  inset: var(--nav-padding);
  pointer-events: none;
}
.nav-indicator {
  --cell-width: calc((100% - (var(--nav-columns) - 1) * var(--nav-gap)) / var(--nav-columns));
  position: absolute;
  inset: 0 auto auto 0;
  width: var(--cell-width);
  height: var(--nav-height);
  translate: calc(var(--index) * (100% + var(--nav-gap))) 0;
  border: 1px solid color-mix(in srgb, var(--color-primary) 36%, var(--color-border));
  border-radius: 10px;
  background: color-mix(in srgb, var(--color-primary) 15%, var(--color-card));
}
.settings-nav a {
  position: relative;
  display: grid;
  height: var(--nav-height);
  min-width: 0;
  place-items: center;
  border-radius: 10px;
  color: var(--color-muted-foreground);
  font-size: 14px;
  font-weight: 650;
  text-decoration: none;
}
.settings-nav a[aria-current] { color: var(--color-primary); }
.settings-nav a:hover { color: var(--color-foreground); background: color-mix(in srgb, var(--color-primary) 7%, transparent); }
.settings-nav a:focus-visible { outline: 2px solid var(--color-primary); outline-offset: -2px; }
.nav-label { position: relative; display: flex; align-items: center; gap: 8px; white-space: nowrap; }
.nav-burst { display: none; }

@media (prefers-reduced-motion: no-preference) {
  .nav-indicator { transition: translate 420ms cubic-bezier(.25, .75, .25, 1.12); }
  .settings-nav a { transition: color 180ms ease, background-color 180ms ease; }
  .nav-burst {
    position: absolute;
    inset: 0;
    display: block;
    color: var(--color-primary);
    filter: url(#profile-nav-goo);
    pointer-events: none;
    animation: profile-nav-fade 760ms both;
  }
  .nav-burst i {
    position: absolute;
    top: 50%;
    left: 50%;
    width: var(--particle-size);
    aspect-ratio: 1;
    border-radius: 50%;
    background: currentColor;
    animation: profile-nav-particle var(--particle-time) cubic-bezier(.18, .68, .35, 1) both;
  }
  .nav-burst i:nth-child(3n) { color: var(--workspace-tint); }
}
@keyframes profile-nav-particle {
  from { transform: translate(-50%, -50%) scale(1.25); }
  72% { transform: translate(calc(-50% + var(--particle-x)), calc(-50% + var(--particle-y))) scale(.85); }
  to { transform: translate(calc(-50% + var(--particle-x)), calc(-50% + var(--particle-y))) scale(0); }
}
@keyframes profile-nav-fade {
  from { opacity: .8; }
  65% { opacity: .65; }
  to { opacity: 0; }
}

.settings-content { display: grid; min-width: 0; gap: 24px; margin-top: 20px; }
.profile-section { display: grid; grid-template-columns: minmax(0, 1fr); min-width: 0; gap: 12px; padding-block: var(--workspace-inset); }
.profile-section > * { width: 100%; min-width: 0; }
.profile-section :deep(.card) { min-width: 0; margin: 0; border: 1px solid var(--color-border); box-shadow: var(--shadow-sm); }
.profile-section :deep(input:not([type=checkbox]):not([type=radio]):not([type=file]):not([type=range])),
.profile-section :deep(textarea),
.profile-section :deep(select) { max-width: min(720px, 100%); }
.profile-section :deep(.api-form .wide input),
.profile-section :deep(.password-field input),
.profile-section :deep(.api-key-field input) { max-width: none; }
.profile-section.data-sections { gap: 14px; }
.profile-section :deep(:is(.data-card, .mail-account-card, .backup-card)) { padding: 20px; }
.data-sections :deep(.data-card > p),
.data-sections :deep(.backup-copy p) { font-size: 12px; color: var(--color-muted-foreground); }

@media (max-width: 820px) {
  .settings-nav { --nav-padding: 6px; top: 74px; }
}
@media (max-width: 700px) {
  .settings-nav { --nav-columns: 3; --nav-gap: 4px; }
  .nav-indicator { translate: calc(var(--column) * (100% + var(--nav-gap))) calc(var(--row) * (100% + var(--nav-gap))); }
  .settings-nav a { font-size: 12px; }
  .nav-label { gap: 4px; }
  .nav-label :deep(svg) { width: 15px; height: 15px; flex: none; }
  .settings-content { gap: 18px; margin-top: 16px; }
  .profile-section :deep(:is(.data-card, .mail-account-card, .backup-card)) { padding: 16px; }
}
</style>
