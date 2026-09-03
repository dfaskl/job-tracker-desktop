<script setup lang="ts">
import ProductApplications from './ProductApplications.vue'
import MigrationApplicationCrud from './MigrationApplicationCrud.vue'
import { useJobTrackerStore } from './jobTrackerStore'

const store = useJobTrackerStore()
</script>

<template>
  <ProductApplications />
  <MigrationApplicationCrud v-if="store.user.value" />
</template>
