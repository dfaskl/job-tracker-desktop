<script setup lang="ts">
defineProps<{ pose: 'walk' | 'startle' | 'fall' | 'land' | 'sleep' }>()
</script>

<template>
  <div class="cat-track" :data-pose="pose" aria-hidden="true">
    <div class="cat-walk">
      <svg class="cat-ink" viewBox="0 0 48 34" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round">
        <g class="cat-awake">
          <path class="cat-tail" d="M14 21C8 22 5 18 6 12c.7-3.4 4-4.9 5.9-3.2 1 .9.8 2.5-.3 3.2"/>
          <path class="cat-tail-raised" d="M14 21C7 20 8 14 8 8c0-3-1.1-4.7-3-4.2"/>
          <path d="M26 19c-4-3.2-11-3.5-14.4.3-1.7 2-1.7 5.8.6 7.2 2.7 1.7 12.1 1.2 16.1-.1"/>
          <path class="cat-leg cat-leg-back" d="m15 26-2 5h3m4-4 2 4h2"/>
          <path class="cat-leg cat-leg-front" d="m30 26-1 5h3m3-5 2 5h3"/>
          <path d="m27 16 2-9 5 5 6-1 4-5 1 10c2 3.5.6 8.5-3.2 10.4-3.7 1.9-10 1.5-13.2-1.1-2.8-2.2-3.2-6-1.6-9.3Z"/>
          <circle class="cat-eye" cx="38.4" cy="18.1" r=".95" fill="currentColor" stroke="none"/>
          <circle class="cat-eye-wide" cx="38.4" cy="18.1" r="1.6"/>
          <path d="m44 21-1 .5m.7 1.9 3 .6m-3-2.2 3.5-.6"/>
        </g>
        <g class="cat-asleep">
          <path d="M36 24c-1.4-8.4-13.7-11.7-22-7.7C7.9 19 5.9 24 8 27.6c3.2 5.1 19.7 4.2 25.4 1.9"/>
          <path d="m29 24 1-5 4 3 4 .2 4-3.3.2 6c2.2 3.2-1.2 6-6.2 6-4.4 0-8-2.8-7-6.9Z"/>
          <path d="M14 27c2.7 4 10.4 3 11.8-.5.8-2.1-1.1-4.2-3.7-3.7m13.2 3.1c1 .9 2 1 3 .3"/>
        </g>
        <g class="cat-seated">
          <path d="M25 17c-7 1-11 7-10 11 .6 2.8 5.4 3 10 3h8m-9-7 2 7m4-8 1 8"/>
          <path d="M16 29c-5 2-12 .9-12-3 0-2 2-3 4-2m17-9 1-7 5 4 6-.4 4-4 1 8c2.5 4.8-2.4 8.7-8.4 8.3-5.7-.3-10.6-4.2-8.6-8.9Z"/>
          <circle cx="35.9" cy="17.6" r=".95" fill="currentColor" stroke="none"/>
          <path d="m41 20 3-1.1m-3 .9 3 .7"/>
        </g>
        <path class="cat-startle-mark" d="m34 3-1-2m5 2 1-2"/>
      </svg>
    </div>
  </div>
</template>

<style scoped>
.cat-track {
  position: absolute;
  z-index: 3;
  top: var(--cat-perch, 0px);
  right: clamp(12px, 11%, 180px);
  width: clamp(72px, 17%, 138px);
  height: 32px;
  margin-top: -30px;
  color: #303833;
  pointer-events: none;
}
:global(:root[data-theme="dark"] .cat-track) { color: #eee9dc; }
.cat-walk { position: relative; width: 44px; height: 32px; }
.cat-ink { display: block; width: 100%; height: 100%; transform-origin: center bottom; }
.cat-tail, .cat-leg { transform-box: fill-box; transform-origin: center top; }
.cat-tail { transform-origin: right bottom; }
.cat-seated, .cat-asleep, .cat-tail-raised, .cat-eye-wide, .cat-startle-mark { display: none; }
[data-pose="sleep"] .cat-awake { display: none; }
[data-pose="sleep"] .cat-asleep { display: inline; }
[data-pose="startle"] :is(.cat-tail-raised, .cat-eye-wide, .cat-startle-mark),
[data-pose="fall"] :is(.cat-tail-raised, .cat-eye-wide) { display: inline; }
[data-pose="startle"] :is(.cat-tail, .cat-eye),
[data-pose="fall"] :is(.cat-tail, .cat-eye) { display: none; }
[data-pose="fall"] .cat-leg-front { transform: rotate(-27deg); }
[data-pose="fall"] .cat-leg-back { transform: rotate(31deg); }
[data-pose="startle"] .cat-ink { transform: scale(.91, 1.12); }

@media (prefers-reduced-motion: no-preference) {
  .cat-walk { animation: cat-pace 7s linear infinite paused; }
  .cat-track[data-pose="walk"] .cat-walk { animation-play-state: running; }
  .cat-track[data-pose="walk"] .cat-leg-front { animation: cat-step .36s ease-in-out infinite alternate; }
  .cat-track[data-pose="walk"] .cat-leg-back { animation: cat-step .36s ease-in-out infinite alternate-reverse; }
  .cat-track[data-pose="walk"] .cat-tail { animation: cat-tail .72s ease-in-out infinite alternate; }
  .cat-track[data-pose="land"] .cat-ink { animation: cat-landing .28s ease-out both; }
}
@media (prefers-reduced-motion: reduce) {
  [data-pose="walk"] .cat-awake { display: none; }
  [data-pose="walk"] .cat-seated { display: inline; }
}
@keyframes cat-pace {
  0%, 5% { left: 0; scale: 1 1; }
  46%, 49% { left: calc(100% - 44px); scale: 1 1; }
  50%, 54% { left: calc(100% - 44px); scale: -1 1; }
  95%, 99% { left: 0; scale: -1 1; }
  100% { left: 0; scale: 1 1; }
}
@keyframes cat-step { from { transform: rotate(-13deg); } to { transform: rotate(13deg); } }
@keyframes cat-tail { from { transform: rotate(-6deg); } to { transform: rotate(7deg); } }
@keyframes cat-landing {
  0% { transform: scale(1.12, .72); }
  62% { transform: scale(.97, 1.05); }
  100% { transform: none; }
}
</style>
