<script setup lang="ts">
import ProductPreferences from './ProductPreferences.vue'
import ProductDataManagement from './ProductDataManagement.vue'
import MigrationBackups from './MigrationBackups.vue'
import MailAccountSettings from './MailAccountSettings.vue'
</script>
<template>
  <div class="settings-grid">
    <MailAccountSettings />
    <ProductPreferences />
    <ProductDataManagement />
    <MigrationBackups />
  </div>
</template>
<style scoped>
.settings-grid{display:grid;height:100%;min-height:0;grid-template-columns:minmax(380px,.95fr) minmax(440px,1.12fr) minmax(380px,1fr);grid-template-rows:auto minmax(0,1fr);grid-template-areas:"mail official backup" "api official backup";align-items:stretch;gap:16px;padding:16px 0 18px;box-sizing:border-box;overflow:hidden}
.settings-grid :deep(.preferences-layout){display:contents}
.settings-grid :deep(.card){min-width:0;min-height:0;margin:0!important}
.settings-grid :deep(.data-card){grid-area:official;align-content:start;overflow-y:auto}
.settings-grid :deep(.data-grid){grid-template-columns:1fr}
.settings-grid :deep(.backup-card){grid-area:backup}
@media(max-width:1600px){.settings-grid{height:auto;grid-template-columns:minmax(380px,.9fr) minmax(480px,1.1fr);grid-template-rows:auto;grid-template-areas:"mail official" "api official" "backup backup";overflow:visible}.settings-grid :deep(.data-card){overflow:visible}.settings-grid :deep(.backup-card){max-height:720px}}
@media(max-width:1100px){.settings-grid{grid-template-columns:1fr;grid-template-areas:"mail" "api" "official" "backup"}}
</style>
