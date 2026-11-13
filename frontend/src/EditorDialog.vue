<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

defineProps<{ labelledBy: string; backdropClass: string }>()
const emit = defineEmits<{ dismiss: [] }>()
const dialog = ref<HTMLDialogElement | null>(null)
const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null

onMounted(() => dialog.value?.showModal())
onBeforeUnmount(() => {
  dialog.value?.close()
  if (opener?.isConnected) opener.focus({ preventScroll: true })
})
</script>

<template>
  <dialog ref="dialog" class="editor-dialog" :aria-labelledby="labelledBy" @cancel.prevent="emit('dismiss')">
    <div :class="backdropClass" @click.self="emit('dismiss')"><slot /></div>
  </dialog>
</template>

<style scoped>
.editor-dialog { position: fixed; inset: 0; width: 100%; height: 100%; max-width: none; max-height: none; margin: 0; padding: 0; border: 0; background: transparent; color: inherit; }
.editor-dialog::backdrop { background: transparent; }
</style>
