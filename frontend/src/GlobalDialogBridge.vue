<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'
import { activeDialog, resolveDialog } from './modalDialogs'

const dialog = ref<HTMLDialogElement | null>(null)
const input = ref<HTMLInputElement | null>(null)

watch(activeDialog, async request => {
  if (!request) {
    if (dialog.value?.open) dialog.value.close()
    return
  }
  await nextTick()
  if (!dialog.value?.open) dialog.value?.showModal()
  if (request.mode === 'prompt') {
    await nextTick()
    input.value?.focus()
    input.value?.select()
  }
}, { flush: 'post' })

function cancel() {
  if (!activeDialog.value) return
  resolveDialog(null)
}

function confirm() {
  const request = activeDialog.value
  if (!request) return
  if (request.mode === 'prompt') {
    const value = input.value?.value ?? ''
    if (!value.trim()) {
      input.value?.focus()
      return
    }
    resolveDialog(value)
  } else resolveDialog('confirmed')
}
</script>

<template>
  <dialog ref="dialog" class="system-dialog" :class="`tone-${activeDialog?.tone || 'neutral'}`" :aria-labelledby="activeDialog ? 'system-dialog-title' : undefined" @close="cancel">
    <form v-if="activeDialog" class="system-dialog-content" @submit.prevent="confirm">
      <div class="system-dialog-icon" aria-hidden="true"><AppIcon :name="activeDialog.tone === 'danger' ? 'trash' : activeDialog.tone === 'warning' ? 'info' : 'check'" :size="21" /></div>
      <div class="system-dialog-copy">
        <h2 id="system-dialog-title">{{ activeDialog.title }}</h2>
        <p>{{ activeDialog.message }}</p>
      </div>
      <label v-if="activeDialog.mode === 'prompt'" class="system-dialog-input">
        <span>{{ activeDialog.label }}</span>
        <input ref="input" :value="activeDialog.value" type="text" autocomplete="off" @input="activeDialog.value = ($event.target as HTMLInputElement).value">
      </label>
      <div class="system-dialog-actions">
        <button type="button" class="secondary" @click="cancel">{{ activeDialog.cancelText }}</button>
        <button type="submit" class="system-dialog-primary">{{ activeDialog.confirmText }}</button>
      </div>
    </form>
  </dialog>
</template>
