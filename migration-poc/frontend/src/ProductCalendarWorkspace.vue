<script setup lang="ts">
import ProductCalendar from './ProductCalendar.vue'
import MigrationEventCalendar from './MigrationEventCalendar.vue'
import { useJobTrackerStore } from './jobTrackerStore'
const store = useJobTrackerStore()
</script>
<template><MigrationEventCalendar v-if="store.user.value && !store.readOnly.value" /><ProductCalendar v-else /></template>