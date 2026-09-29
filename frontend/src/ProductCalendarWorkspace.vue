<script setup lang="ts">
import ProductCalendar from './ProductCalendar.vue'
import EventCalendar from './EventCalendar.vue'
import { useJobTrackerStore } from './jobTrackerStore'
const store = useJobTrackerStore()
</script>
<template><EventCalendar v-if="store.user.value && !store.readOnly.value" /><ProductCalendar v-else /></template>