<script setup lang="ts">
import { computed } from 'vue'
import ProductPreferences from './ProductPreferences.vue'
import ProductDataManagement from './ProductDataManagement.vue'
import BackupManagement from './BackupManagement.vue'
import MailAccountSettings from './MailAccountSettings.vue'
import AccountManagement from './AccountManagement.vue'
import { useJobTrackerStore } from './jobTrackerStore'

const store = useJobTrackerStore()
const displayName = computed(() => store.user.value?.displayName || store.user.value?.email.split('@')[0] || '未命名用户')
const avatarInitial = computed(() => displayName.value.trim().slice(0, 1).toUpperCase())
</script>
<template>
  <div class="profile-workspace">
    <header class="profile-hero">
      <div class="profile-avatar" aria-hidden="true"><img v-if="store.user.value?.avatar" :src="store.user.value.avatar" alt=""><span v-else>{{ avatarInitial }}</span></div>
      <div><span>个人主页</span><h1>{{ displayName }}</h1><p>{{ store.user.value?.email }} · 在这里管理个人资料、邮箱、偏好与数据备份</p></div>
    </header>
    <div class="settings-column">
      <AccountManagement />
      <MailAccountSettings />
      <ProductPreferences />
      <ProductDataManagement />
      <BackupManagement />
    </div>
  </div>
</template>
<style scoped>
.profile-workspace{width:60%;min-width:0;min-height:100%;margin:0 auto;padding:16px 0 18px;box-sizing:border-box}.profile-hero{display:flex;align-items:center;gap:18px;margin-bottom:16px;padding:20px 22px;border:1px solid var(--color-border);border-radius:var(--radius-panel);background:linear-gradient(135deg,color-mix(in srgb,var(--color-primary) 9%,var(--color-card)),var(--color-card));box-shadow:var(--shadow-sm)}.profile-avatar{display:grid;width:78px;height:78px;flex:0 0 78px;place-items:center;overflow:hidden;border:3px solid var(--color-card);border-radius:50%;color:var(--color-on-primary);background:var(--color-primary);box-shadow:0 9px 24px color-mix(in srgb,var(--color-primary) 24%,transparent);font-size:28px;font-weight:800}.profile-avatar img{width:100%;height:100%;object-fit:cover}.profile-hero>div:last-child{display:grid;min-width:0;gap:4px}.profile-hero>div:last-child>span{color:var(--color-primary);font-size:11px;font-weight:800;letter-spacing:.12em}.profile-hero h1{margin:0;overflow:hidden;font-size:26px;text-overflow:ellipsis;white-space:nowrap}.profile-hero p{margin:0;color:var(--color-muted-foreground);line-height:1.55;overflow-wrap:anywhere}.settings-column{display:flex;min-width:0;min-height:0;flex-direction:column;gap:16px}.settings-column :deep(.card){min-width:0;min-height:0;margin:0!important}.settings-column :deep(.data-card){align-content:start;overflow:visible}
@media(max-width:1280px){.profile-workspace{width:76%}}
@media(max-width:900px){.profile-workspace{width:100%}}
@media(max-width:620px){.profile-workspace{padding-top:10px}.profile-hero{align-items:flex-start;padding:16px}.profile-avatar{width:58px;height:58px;flex-basis:58px;font-size:22px}.profile-hero h1{font-size:22px}.profile-hero p{font-size:12px}}
</style>