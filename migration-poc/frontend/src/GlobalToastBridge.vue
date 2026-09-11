<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'

const timers = new WeakMap<Element, ReturnType<typeof setTimeout>>()
let observer: MutationObserver | null = null

function isToast(element: Element): element is HTMLElement {
  return element instanceof HTMLElement
    && element.dataset.globalToast !== 'true'
    && element.matches('p.success, p.danger, .feedback')
}

function showToast(element: HTMLElement) {
  if (!element.textContent?.trim()) return
  const previous = timers.get(element)
  if (previous) clearTimeout(previous)
  element.dataset.globalToast = 'true'
  element.setAttribute('role', element.classList.contains('danger') ? 'alert' : 'status')
  element.setAttribute('aria-live', element.classList.contains('danger') ? 'assertive' : 'polite')
  element.style.removeProperty('display')
  element.classList.add('global-operation-toast')
  timers.set(element, setTimeout(() => {
    element.style.setProperty('display', 'none', 'important')
    timers.delete(element)
  }, 3000))
}

function inspect(node: Node) {
  if (node instanceof Element) {
    if (isToast(node)) showToast(node)
    node.querySelectorAll('p.success, p.danger, .feedback').forEach(item => {
      if (isToast(item)) showToast(item)
    })
  }
  const parent = node.parentElement
  if (parent?.dataset.globalToast === 'true') {
    parent.dataset.globalToast = 'false'
    showToast(parent)
  }
}

onMounted(() => {
  document.querySelectorAll('p.success, p.danger, .feedback').forEach(item => {
    if (isToast(item)) showToast(item)
  })
  observer = new MutationObserver(records => records.forEach(record => inspect(record.target)))
  observer.observe(document.body, { childList: true, subtree: true, characterData: true })
})

onBeforeUnmount(() => observer?.disconnect())
</script>

<template></template>

<style>
.global-operation-toast {
  position: fixed !important;
  top: 20px !important;
  left: 50% !important;
  right: auto !important;
  bottom: auto !important;
  z-index: 10000 !important;
  width: max-content !important;
  max-width: min(560px, calc(100vw - 32px)) !important;
  margin: 0 !important;
  padding: 11px 18px !important;
  transform: translateX(-50%) !important;
  border: 1px solid color-mix(in srgb,var(--color-success) 35%,white) !important;
  border-radius: var(--radius-md,8px) !important;
  color: var(--color-success) !important;
  background: #f0fdf4 !important;
  box-shadow: var(--shadow-lg) !important;
  text-align: center !important;
  animation: global-toast-in .18s ease-out !important;
}
.global-operation-toast.danger,
.global-operation-toast.feedback.danger {
  border-color: color-mix(in srgb,var(--color-destructive) 32%,white) !important;
  color: var(--color-destructive) !important;
  background: #fef2f2 !important;
}
.global-operation-toast button { margin-left: 10px !important; }
@keyframes global-toast-in {
  from { opacity: 0; transform: translate(-50%, -8px); }
  to { opacity: 1; transform: translate(-50%, 0); }
}
</style>