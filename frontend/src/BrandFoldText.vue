<script setup lang="ts">
import { ref, type CSSProperties } from 'vue'

const title = '求职记录本'
const glyphs = Array.from(title)
const pointerIndex = ref<number | null>(null)
const finePointer = window.matchMedia('(hover: hover) and (pointer: fine) and (prefers-reduced-motion: no-preference)')

function followPointer(event: PointerEvent) {
  if (event.pointerType !== 'mouse' || !finePointer.matches) return
  const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect()
  pointerIndex.value = (event.clientX - bounds.left) / bounds.width * glyphs.length - 0.5
}

function glyphStyle(index: number): CSSProperties {
  const proximity = pointerIndex.value === null ? 0 : Math.max(0, 1 - Math.abs(index - pointerIndex.value) / 2.25)
  return {
    '--fold-delay': `${index * 45}ms`,
    '--refold-angle': `${Math.round(proximity * 45)}deg`,
    '--crease-strength': proximity * 0.55
  }
}
</script>

<template>
  <strong class="brand-fold" @pointermove="followPointer" @pointerleave="pointerIndex = null" @pointercancel="pointerIndex = null">
    <span v-for="(glyph, index) in glyphs" :key="index" class="brand-fold-slot" :style="glyphStyle(index)">
      <span class="brand-fold-leaf"><span class="brand-fold-glyph">{{ glyph }}</span></span>
    </span>
  </strong>
</template>

<style scoped>
.brand-fold {
  display: inline-flex;
  width: max-content;
  white-space: nowrap;
}
.brand-fold-slot {
  display: block;
  perspective: 700px;
  perspective-origin: 50% 0;
}
.brand-fold-leaf,
.brand-fold-glyph {
  display: block;
  transform-origin: 50% 0;
  backface-visibility: hidden;
}
@media (prefers-reduced-motion: no-preference) {
  .brand-fold-leaf {
    transform-style: preserve-3d;
    animation: brand-unfold 650ms cubic-bezier(.215, .61, .355, 1) var(--fold-delay) backwards;
  }
  @keyframes brand-unfold {
    from { transform: rotateX(-92deg); opacity: 0; }
    35% { opacity: .8; }
    to { transform: rotateX(0); opacity: 1; }
  }
}
@media (hover: hover) and (pointer: fine) and (prefers-reduced-motion: no-preference) {
  .brand-fold-glyph {
    transform: rotateX(calc(-1 * var(--refold-angle)));
    opacity: calc(1 - var(--crease-strength) * .5);
    transition: transform 180ms ease-out, opacity 180ms ease-out;
  }
}
</style>
