<script setup lang="ts">
import { computed, onBeforeUnmount, onDeactivated, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  label: string
  actionLabel?: string
  undoLabel?: string
  windowMs: number
  pending?: boolean
  disabled?: boolean
  submitting?: boolean
}>(), { undoLabel: '撤销' })
const emit = defineEmits<{ press: []; undo: []; expire: [] }>()
const hovered = ref(false)
const keyboardFocused = ref(false)
const paused = computed(() => hovered.value || keyboardFocused.value)
let timer = 0
let startedAt = 0
let remaining = 0

function stop() {
  if (!timer) return
  window.clearTimeout(timer)
  remaining = Math.max(0, remaining - (performance.now() - startedAt))
  timer = 0
}

function resume() {
  if (!props.pending || props.submitting || paused.value || timer) return
  startedAt = performance.now()
  timer = window.setTimeout(() => {
    timer = 0
    emit('expire')
  }, remaining)
}

watch(() => [props.pending, props.submitting] as const, ([pending, submitting]) => {
  stop()
  if (!pending || submitting) return
  remaining = props.windowMs
  resume()
}, { immediate: true })
watch(paused, value => value ? stop() : resume())
onDeactivated(() => { hovered.value = false; keyboardFocused.value = false })
onBeforeUnmount(stop)
</script>

<template>
  <button
    type="button"
    class="mail-fuse"
    :class="{ 'is-pending': pending }"
    :disabled="submitting || (!pending && disabled)"
    :aria-label="submitting ? '正在处理邮件' : pending ? undoLabel : actionLabel || label"
    :title="submitting ? '正在处理邮件' : pending ? undoLabel : actionLabel || label"
    @pointerenter="hovered = $event.pointerType === 'mouse'"
    @pointerleave="hovered = false"
    @focus="keyboardFocused = ($event.target as HTMLElement).matches(':focus-visible')"
    @blur="keyboardFocused = false"
    @click="pending ? emit('undo') : emit('press')"
  >
    <span v-if="submitting">正在处理…</span>
    <template v-else-if="pending">
      <span class="undo-label">{{ undoLabel }}</span>
      <svg class="fuse-outline" viewBox="0 0 100 40" preserveAspectRatio="none" aria-hidden="true">
        <rect x="1" y="1" width="98" height="38" rx="9" pathLength="100"
          :style="{ animationDuration: windowMs + 'ms', animationPlayState: paused ? 'paused' : 'running' }" />
      </svg>
    </template>
    <slot v-else>{{ label }}</slot>
  </button>
</template>

<style scoped>
#app button.mail-fuse, button.mail-fuse {
  position: relative;
  min-height: 44px;
  border-radius: 10px;
}
#app button.mail-fuse.is-pending, button.mail-fuse.is-pending {
  border-color: var(--color-border-strong);
  color: var(--color-foreground);
  background: var(--color-card);
}
.fuse-outline {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: visible;
  pointer-events: none;
}
.fuse-outline rect {
  fill: none;
  stroke: var(--mail-fuse-color, #b87816);
  stroke-width: 1.5px;
  stroke-dasharray: 100;
  vector-effect: non-scaling-stroke;
  animation: burn-fuse linear both;
}
:global(:root[data-theme="dark"]) .fuse-outline rect { --mail-fuse-color: #f5a524; }
.undo-label { animation: show-undo 180ms ease-out; }
@keyframes burn-fuse { to { stroke-dashoffset: 100; } }
@keyframes show-undo { from { opacity: .35; } }
@media (prefers-reduced-motion: reduce) {
  .fuse-outline rect, .undo-label { animation: none; }
}
</style>
