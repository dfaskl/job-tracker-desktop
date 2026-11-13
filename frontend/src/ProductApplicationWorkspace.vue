<script setup lang="ts">
import { onActivated } from 'vue'
import ProductApplications from './ProductApplications.vue'
defineProps<{ focusApplicationId?: string }>()
const emit = defineEmits<{ 'workspace-ready': []; 'exit-location': [] }>()
onActivated(() => emit('workspace-ready'))
</script>

<template>
  <ProductApplications :focus-application-id="focusApplicationId" @exit-location="emit('exit-location')" />
</template>
