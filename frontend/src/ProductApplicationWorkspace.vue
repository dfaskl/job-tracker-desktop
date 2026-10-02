<script setup lang="ts">
import ProductApplications from './ProductApplications.vue'
defineProps<{ focusApplicationId?: string }>()
</script>

<template>
  <ProductApplications :focus-application-id="focusApplicationId" />
</template>
