<script setup lang="ts">
defineProps<{ pose: 'walk' | 'startle' | 'fall' | 'land' | 'sleep' }>()
</script>

<template>
  <div class="cat-track" :data-pose="pose" aria-hidden="true">
    <div class="cat-walk">
      <svg class="cat-ink" viewBox="0 0 60 40" stroke-linecap="round" stroke-linejoin="round">
        <g class="cat-awake" fill="currentColor">
          <path class="cat-tail" d="M19 27C10 28 5 23 5 17c0-5 3-9 7-10" fill="none" stroke="currentColor" stroke-width="3.5"/>
          <path class="cat-paw cat-paw-far" d="M20 28c2 .5 3 2 3 4l-1 4h-4c-1.5 0-1.5-2 .1-2.4l.6-5.6Z"/>
          <path class="cat-paw cat-paw-far cat-paw-fore" d="M39 28h4l-.5 6c2.7.2 3.4 3 .8 3h-4.6l-.5-4.8Z"/>
          <path class="cat-paw cat-paw-near" d="M15 27c3 0 6 1 5 4l-2 4c2 .2 2.2 2.4.3 2.4h-5c-1.7-.1-1.3-2-.6-3.2l2.3-7.2Z"/>
          <path class="cat-paw cat-paw-near cat-paw-fore" d="M34 28c1.6-.8 4.2-.6 5 .7l-.4 5.5c2.2 0 2.6 3.2.2 3.2h-4.3c-1.2 0-1.5-1.4-1.3-2.9l.8-6.5Z"/>
          <g class="cat-body">
            <path d="M12 25c1.4-6.3 9-9.5 15.5-8.3 4.5.8 8 1.5 11.1-.7 4.6 3.5 4.8 11.5-1.8 15.5-4.3 2.7-10.7 1.7-14.9 1.1-5.5-.7-11.6-1.4-9.9-7.6Z"/>
            <path class="cat-detail cat-back" d="M16 24c3-4.2 7.2-5.1 11.9-4.2" fill="none" stroke="currentColor" stroke-width="1.3" opacity=".34"/>
            <g class="cat-head">
              <path d="M33 18c.1-2.1 1.1-4.5 2.4-6.4l-.6-6.4c-.1-1.1.7-1.6 1.6-.9l5.2 4.1c2.1-.3 4.4-.1 6.4.6l5.1-3.4c.9-.6 1.7 0 1.4 1l-1.6 6.8c1.2 1.5 1.7 3.5 2.5 4.7 .5.7 2 .9 2 2.3 0 3.2-4.3 6.5-9.7 6.8-7.4.4-15-2.4-14.7-9.2Z"/>
              <path class="cat-detail" d="m37.5 10.3-.3-3.2 2.5 2.1m10 1.6 2.2-2.1-.7 2.9" fill="none" stroke="currentColor" stroke-width="1.1" opacity=".58"/>
              <path class="cat-detail cat-eye" d="M47.3 17.6c1-1.3 2.1-1.5 3.2-.5" fill="none" stroke="currentColor" stroke-width="1.5"/>
              <circle class="cat-detail cat-eye-open" cx="49" cy="17.1" r="1.45" fill="currentColor"/>
              <path class="cat-detail" d="m54.2 20.3 1.3.2m-2.2 2.4c.6.2 1.2.2 1.8-.1" fill="none" stroke="currentColor" stroke-width="1.1"/>
              <path d="m53.9 22.5 5.1-.5m-4.8 2.4 3.4.5" fill="none" stroke="currentColor" stroke-width=".85"/>
            </g>
          </g>
        </g>
        <g class="cat-asleep" fill="currentColor">
          <path d="M48 24C40 15 22 17 14 24c-5 4.4-5 9.4.2 12 6.7 3.5 25.7 2 32.8.1 5.1-1.4 7-7.3 1-12.1Z"/>
          <path d="M38 26l.8-5.6c.1-.8.7-.9 1.2-.4l3.6 3.3 4 .3 4-2.8c.7-.5 1.2-.1 1.1.7l-.7 6c1.8 4.5-2.5 8.6-7.8 8.6-6.5 0-8.9-5.2-6.2-10.1Z"/>
          <path class="cat-detail" d="M16 33c4 4.5 16 2.5 17-1.9.5-2.7-3-3.6-5.5-1.6m17.8 1.2c1.1 1 2.4 1.1 3.4.2" fill="none" stroke="currentColor" stroke-width="1.35"/>
        </g>
        <g class="cat-seated" fill="currentColor">
          <path d="M24 22c-4 1.1-7.4 5.5-8 9.6-.7 4.3 3.7 6 8 6h16c2.9 0 2.6-3.1.6-3.5l-1-8.3Z"/>
          <path d="M23 18c0-2.8 1-4.8 2.6-6.5l-.8-6.7c-.1-.8.6-1.1 1.2-.7l5.4 4.5c2.3-.4 4.8-.2 7 .6l5.5-3.5c.8-.5 1.5 0 1.2.9l-1.6 6.8c2.1 3.3 3.8 5.2 3.4 7.1-.6 4.6-6.4 6.9-12.2 6.3C27.4 26 23 23 23 18Z"/>
          <path d="M21 34c-7 2.8-15 .8-15-4.2 0-2.5 2.6-4.4 5.1-3.7" fill="none" stroke="currentColor" stroke-width="3.4"/>
          <path class="cat-detail" d="M38 17.4c.9-1.2 2-1.3 3-.4m-9 14.3v4.5m4-4.5.4 4.5" fill="none" stroke="currentColor" stroke-width="1.25"/>
        </g>
      </svg>
    </div>
  </div>
</template>

<style scoped>
@property --cat-stride {
  syntax: "<number>";
  inherits: true;
  initial-value: 0;
}
.cat-track {
  --cat-size: 51px;
  --cat-ink-detail: #e1d6bf;
  position: absolute;
  z-index: 3;
  top: var(--cat-perch, 0px);
  right: clamp(12px, 11%, 180px);
  width: clamp(80px, 17%, 144px);
  height: 34px;
  margin-top: -32px;
  color: #454941;
  pointer-events: none;
}
:global(:root[data-theme="dark"] .cat-track) {
  --cat-ink-detail: #5e5146;
  color: #e7dac3;
}
.cat-walk { width: calc(100% - var(--cat-size)); height: 34px; }
.cat-ink { display: block; width: var(--cat-size); height: 34px; overflow: visible; transform-origin: center bottom; }
.cat-detail { color: var(--cat-ink-detail); }
.cat-seated, .cat-asleep, .cat-eye-open { display: none; }
[data-pose="sleep"] .cat-awake { display: none; }
[data-pose="sleep"] .cat-asleep { display: inline; }
:is([data-pose="startle"], [data-pose="fall"]) .cat-eye { display: none; }
:is([data-pose="startle"], [data-pose="fall"]) .cat-eye-open { display: inline; }
.cat-tail { transform-origin: 19px 27px; }
.cat-head { transform-origin: 36px 23px; }
.cat-body { transform-origin: 25px 30px; }
.cat-paw { transform-origin: 17px 28px; }
.cat-paw-fore { transform-origin: 37px 28px; }
.cat-paw-far { opacity: .62; }

@media (prefers-reduced-motion: no-preference) {
  .cat-walk { animation: cat-pace 9.6s linear infinite paused; }
  .cat-ink { animation: cat-turn 9.6s linear infinite paused; }
  [data-pose="walk"] :is(.cat-walk, .cat-ink) { animation-play-state: running; }
  .cat-awake { transform-origin: 29px 37px; }
  .cat-paw, .cat-head, .cat-tail, .cat-body { transition: transform 180ms ease-out; }
  [data-pose="walk"] .cat-paw { animation: cat-step .64s linear infinite; }
  [data-pose="walk"] .cat-paw-far { animation-delay: -.32s; }
  [data-pose="walk"] .cat-paw-fore { animation-delay: -.24s; }
  [data-pose="walk"] .cat-paw-far.cat-paw-fore { animation-delay: -.56s; }
  [data-pose="walk"] .cat-body { animation: cat-bob .32s ease-in-out infinite alternate; }
  [data-pose="walk"] .cat-tail { animation: cat-sway 1.28s ease-in-out infinite alternate; }

  [data-pose="startle"] .cat-awake { animation: cat-alert 340ms ease-out both; }
  [data-pose="startle"] .cat-head { transform: rotate(-13deg) translateY(-1px); }
  [data-pose="startle"] .cat-tail { transform: rotate(21deg); }
  [data-pose="fall"] .cat-awake { animation: cat-drop 300ms ease-out both; }
  [data-pose="fall"] .cat-tail { transform: rotate(27deg); }
  [data-pose="fall"] .cat-paw { transform: rotate(20deg); }
  [data-pose="fall"] .cat-paw-fore { transform: rotate(-25deg); }
  [data-pose="land"] .cat-awake { animation: cat-settle 275ms ease-out both; }
}
@media (prefers-reduced-motion: reduce) {
  [data-pose="walk"] .cat-awake { display: none; }
  [data-pose="walk"] .cat-seated { display: inline; }
}

/* The shared cycle gives the paws time to stop while the body turns. */
@keyframes cat-pace {
  0%, 3% { transform: translateX(0); --cat-stride: 0; }
  9% { transform: translateX(11%); --cat-stride: 1; }
  38% { transform: translateX(89%); --cat-stride: 1; }
  44%, 54% { transform: translateX(100%); --cat-stride: 0; }
  60% { transform: translateX(89%); --cat-stride: 1; }
  88% { transform: translateX(11%); --cat-stride: 1; }
  94%, 100% { transform: translateX(0); --cat-stride: 0; }
}
@keyframes cat-turn {
  0%, 43% { transform: rotateY(0); }
  46% { transform: rotateY(-32deg) translateY(.6px); animation-timing-function: ease-in-out; }
  51%, 93% { transform: rotateY(-180deg); }
  96% { transform: rotateY(-148deg) translateY(.6px); animation-timing-function: ease-in-out; }
  100% { transform: rotateY(0); }
}
@keyframes cat-step {
  0%, 100% { transform: rotate(calc(-19deg * var(--cat-stride))); }
  57% { transform: rotate(calc(19deg * var(--cat-stride))); }
  76% { transform: translateY(calc(-2.3px * var(--cat-stride))) rotate(calc(6deg * var(--cat-stride))); }
  90% { transform: translateY(calc(-1px * var(--cat-stride))) rotate(calc(-13deg * var(--cat-stride))); }
}
@keyframes cat-bob {
  from { transform: translateY(calc(.45px * var(--cat-stride))); }
  to { transform: translateY(calc(-.65px * var(--cat-stride))) rotate(calc(-.9deg * var(--cat-stride))); }
}
@keyframes cat-sway { from { transform: rotate(-9deg); } to { transform: rotate(11deg); } }
@keyframes cat-alert {
  0% { transform: none; }
  22% { transform: scale(1.045,.87); }
  49% { transform: rotate(-6deg) scale(.95,1.07); }
  100% { transform: rotate(-3deg) scale(.98,1.02); }
}
@keyframes cat-drop {
  from { transform: rotate(-3deg) scale(.98,1.02); }
  65% { transform: rotate(8deg) scale(.94,1.08); }
  to { transform: rotate(3deg) scale(.99,1.02); }
}
@keyframes cat-settle {
  0% { transform: rotate(3deg) scale(.99,1.02); }
  22% { transform: scale(1.13,.72); }
  62% { transform: scale(.98,1.04); }
  100% { transform: none; }
}
</style>
