<script setup lang="ts">
import ProductPreferences from './ProductPreferences.vue'
import ProductDataManagement from './ProductDataManagement.vue'
import MigrationBackups from './MigrationBackups.vue'
</script>
<template><div class="settings-grid"><div class="settings-column"><ProductPreferences /></div><div class="settings-column"><ProductDataManagement /><MigrationBackups /></div></div></template>
<style scoped>
.settings-grid{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);align-items:start;gap:20px;padding:2px 0 24px}.settings-column{display:grid;gap:20px;min-width:0}.settings-column :deep(.card){margin-top:20px}@media(max-width:900px){.settings-grid{grid-template-columns:1fr}}
</style>