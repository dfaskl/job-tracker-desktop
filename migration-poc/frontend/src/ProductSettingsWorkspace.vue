<script setup lang="ts">
import ProductPreferences from './ProductPreferences.vue'
import ProductDataManagement from './ProductDataManagement.vue'
import MigrationBackups from './MigrationBackups.vue'
</script>
<template>
  <div class="settings-grid">
    <ProductPreferences />
    <ProductDataManagement />
    <MigrationBackups />
  </div>
</template>
<style scoped>
.settings-grid{display:grid;height:100%;min-height:0;grid-template-columns:minmax(250px,.82fr) minmax(440px,1.28fr) minmax(380px,1fr);grid-template-rows:auto minmax(0,1fr);grid-template-areas:"theme official backup" "about api backup";align-items:stretch;gap:16px;padding:16px 0 18px;box-sizing:border-box;overflow:hidden}
.settings-grid :deep(.preferences-layout){display:contents}
.settings-grid :deep(.card){min-width:0;min-height:0;margin:0!important}
.settings-grid :deep(.data-card){grid-area:official}
.settings-grid :deep(.backup-card){grid-area:backup}
@media(max-width:1200px){.settings-grid{height:auto;grid-template-columns:1fr;grid-template-rows:auto;grid-template-areas:"about" "theme" "official" "api" "backup";overflow:visible}.settings-grid :deep(.backup-card){max-height:720px}}
</style>