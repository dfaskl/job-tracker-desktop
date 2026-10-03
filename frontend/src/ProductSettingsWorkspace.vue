<script setup lang="ts">
import ProductPreferences from './ProductPreferences.vue'
import ProductDataManagement from './ProductDataManagement.vue'
import BackupManagement from './BackupManagement.vue'
import MailAccountSettings from './MailAccountSettings.vue'
import AccountManagement from './AccountManagement.vue'
import ResumeSettings from './ResumeSettings.vue'
</script>
<template>
  <div class="profile-workspace">
    <div class="settings-column">
      <AccountManagement />
      <ResumeSettings />
      <MailAccountSettings />
      <ProductPreferences />
      <ProductDataManagement />
      <BackupManagement />
    </div>
  </div>
</template>
<style scoped>
 .profile-workspace{width:60%;min-width:0;min-height:100%;margin:0 auto;padding:16px 0 18px;box-sizing:border-box}.settings-column{display:flex;min-width:0;min-height:0;flex-direction:column;gap:16px}.settings-column :deep(.card){min-width:0;min-height:0;margin:0!important}.settings-column :deep(.data-card){align-content:start;overflow:visible}
@media(max-width:1280px){.profile-workspace{width:76%}}
@media(max-width:900px){.profile-workspace{width:100%}}
@media(max-width:620px){.profile-workspace{padding-top:10px}}
</style>
