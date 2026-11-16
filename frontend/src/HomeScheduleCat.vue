<script setup lang="ts">
import { useId } from 'vue'

defineProps<{ pose: 'walk' | 'startle' | 'fall' | 'land' | 'sleep' }>()
const faceId = useId()
</script>

<template>
  <div class="cat-track" :data-pose="pose" aria-hidden="true">
    <div class="cat-walk">
      <svg class="cat-ink" viewBox="0 0 54 64" stroke-linecap="round" stroke-linejoin="round">
        <defs>
          <g :id="faceId">
            <path class="cat-outline" d="M26 27c-1.8-3.1-2.8-9.5-2.2-14.4.2-1.5 1.1-1.6 2.4-.9l7.6 4.7c1.4-.9 3.1-1.4 4.8-1.6l4.1-6.9c.6-1.1 1.5-.9 2 .3 1.3 2.8 1.8 5.5 1.9 8 2.9 2.6 5 6 4.8 9.2-.2 4.4-4 7.6-8.4 8.7-5.9 1.5-12.7-.7-16-4.1-.6-.6-.9-1.5-1-3Z"/>
            <path class="cat-black" d="M24.7 12.4c1.5.2 6.2 3.8 9 4.6 1.4-.8 3.6-1.7 5.1-1.7 1.1 1.4.2 3.6-1.7 4.7-3.9 2.3-4.7 6.2-10.6 8.7-2-2.3-2.6-11.7-1.8-16.3Z"/>
            <path class="cat-orange" d="M41.3 15.4c3.2-.6 5.5 1.1 7.1 3.3 1.3 1.5 2 3.3 2.3 5-1.5 1-2.8-.6-3.7-2.3-.9-1.4-4.7-3-5.7-6Zm-12.6 13c2.8-.6 4.6-3 6.9-3.6 1 .7.3 2.2-.8 3.5-1.3 1.7-3.2 2.6-5.1 2.6-.9-.7-1.1-1.7-1-2.5Z"/>
            <path class="cat-pink" d="m25.6 14.5 5.5 3.8-4.3 3.4c-.8-1.7-1.2-4.9-1.2-7.2Zm15.5.9 2.7-5.6 1.2 6.2c-1.4-.5-2.5-.6-3.9-.6Z"/>
            <path class="cat-eye cat-detail" d="M35.3 23.5c1.5.9 3.5.7 4.5-1m5.2-.9c1 1.2 2.4 1.4 3.2.4"/>
            <g class="cat-eye-open cat-black">
              <ellipse cx="38.2" cy="22.5" rx="1.1" ry="1.45"/>
              <ellipse cx="46.8" cy="21.9" rx="1" ry="1.3"/>
            </g>
            <path class="cat-detail" d="m43.2 24.3 1.3.1m-.7 0c0 1.3-.8 1.8-1.7 1.9m1.7-1.9c.4 1.1 1 1.4 1.7 1.3M50 24.5l3-1.1m-2.8 3.1 2.9.7"/>
            <path class="cat-grain" d="m27.9 22.5 3.8-4.7m-3 6.6 5-6m-2.9 5.3 4.8-5.5m-4.5 10.5 2.4-2.1"/>
          </g>
        </defs>
        <g class="cat-awake">
          <g class="cat-tail">
            <path class="cat-outline" d="M15.6 34C9 32.7 3.9 25.8 3.3 18.9 2.7 11.8 6.4 5.3 11.1 3.2c1.9-.9 4.3-.8 5.6.4 1.1 1 .2 2.6-1 3.8-3.6 3.4-6 8-5.7 12.5.3 4.5 3.1 7.9 7.2 9.9Z"/>
            <path class="cat-black" d="M4 17.9c-.3-6.4 3.6-12.7 7.7-14.2 1.5-.6 3.2-.4 4.3.4-3.7 2.6-5.9 7.1-6.5 11.4L7 18.8Z"/>
            <path class="cat-orange" d="M4.1 17.5c1.3.8 3.9-.4 5.3-2 .1 5.3 1 9.7 5.9 13.7l-2.1 2C7.6 27.8 4.8 23 4.1 17.5Z"/>
            <path class="cat-grain" d="m5.7 14.6 2.7-4.4m-1.2 5.3 2-3.6m-3 8.6 1.8-2m-.7 4.6 1.1-1.6"/>
          </g>
          <path class="cat-paw cat-paw-far cat-outline" d="M13.5 41.1c3.1-.7 6.9 2.7 5.5 6.2-1.3 3.4-4.5 5.4-7.8 7.4l-2.5 3.5c1.9 1.8.5 3.3-1.6 3.1H4.8c-2.4-.2-2.5-2.6-.9-5.7l3.8-6.5c2-2.5 3.6-6 5.8-8Z"/>
          <path class="cat-paw cat-paw-far cat-paw-fore cat-outline" d="M32.1 42.7c3.4-.2 5.4 2.3 4.2 5.8l-3.6 9.2c2.2 1.1 1.7 3.9-.6 3.8l-3.8-.2c-2.4-.2-2.5-2.1-1.8-4l2.1-9.3c.7-3.1 1.7-5.1 3.5-5.3Z"/>
          <g class="cat-body">
            <path class="cat-outline" d="M13 33.2c3.2-5.2 11-6.3 17.1-4.4 3.1 1 4.7 1.9 6.8-.4 3.5-.5 7.4 3 7.7 7.3l-1.9 4.6.2 2.2-1.6-.7-.7 4c-1.7 4.2-4.6 5.9-9.4 5.8-5.7-.1-13.9-2.3-18.8-6.3-2.4-2.1-2.1-7.5.6-12.1Z"/>
            <path class="cat-orange" d="M14.4 32.8c3.6-4.2 8.2-3.7 12-3.4 1.7 4.9-1.4 9.8-5.9 12-2.1 1-5.9.9-7.2-1.3-1.4-2.5-.6-4.8 1.1-7.3Zm13.1 6.7c3.7-4.1 7.7-6.5 10.3-6.4 1.2 1.9.4 4.8-1.2 6.8-3.1 3.6-7.6 3.8-9.1-.4Z"/>
            <path class="cat-black" d="M21.8 29c4.7-.9 8.8.6 12.6 2.5 1.5.8 2.7 1.2 4 1.4-1.7 1.6-3.9 1.1-5.6 2.3-1.6 1.4-1.6 4.7-5.3 4.8-3.5.1-3.7-2.2-5.2-3.1-3.1-1.8-3.3-5.6-.5-7.9Z"/>
            <path class="cat-grain" d="m14.9 37.5 3.4-4m-2.4 6.7 3.9-4.4m2.2-2.2 2.7-3.2m-1.7 4.8 4.1-4.4m-2.8 5.7 4.8-4.8m-2.3 5.8 3.7-4m1.7 7.1 2.9-3.1"/>
            <g class="cat-head"><use :href="'#' + faceId"/></g>
          </g>
          <g class="cat-paw cat-paw-near">
            <path class="cat-limb" d="M14.7 41c-.4 4.6 2.1 8.5 5 11.5l3.1 6.8c1.1 2.3 3.8 3 6.2 2.1 1.4-.7 1.1-2.8-.6-3.1l-2.9-8.2.4-6.1"/>
            <path class="cat-detail" d="m26.7 59.4 1.2 1.9"/>
          </g>
          <g class="cat-paw cat-paw-near cat-paw-fore">
            <path class="cat-limb" d="M41.4 40.6c-.9 4.2.3 8.8 3.6 12.5l3.6 4.9c2.1-.4 3.9 1.3 2.6 2.7-1.3 1.4-4.6 1.3-5.8.3l-8.2-8.8c-2.1-2.3-3.5-4.3-3.8-6.3"/>
            <path class="cat-detail" d="m48 59.5 1.1 1.7"/>
          </g>
        </g>
        <g class="cat-asleep">
          <path class="cat-outline" d="M45.1 46.4C38.5 37 20.5 37.1 11 45.6c-7.1 6.3-5.2 14 2.3 16.1 7.6 2 26.9 1.3 34.1-1.1 5.7-1.9 5.2-9.9-2.3-14.2Z"/>
          <path class="cat-orange" d="M8.8 50.3c3.5-6.4 10.2-9.5 17.5-10.1 2.9 5.2-1 9.7-6.7 10.1-4 .3-6.8 2.1-9.2 4.2-1.2-.3-1.9-2.4-1.6-4.2Z"/>
          <path class="cat-black" d="M22.4 40.8c5.7-1.6 13.7-.4 16.3 1.3.7 3.1-2.5 5.2-5.4 5-4.6-.4-6.4-2.5-10.9-6.3Z"/>
          <path class="cat-detail" d="M13 56c4.2 5.5 17.2 3.9 18.5-.7.7-2.3-2.2-3.3-5-2.3"/>
          <use :href="'#' + faceId" transform="translate(12 35) scale(.72)"/>
        </g>
        <g class="cat-seated">
          <path class="cat-outline" d="M18 53.5C11.3 56 6 53.8 4.6 50.3c-.6-1.7.3-3.3 2.2-2.9 2.5 4.2 5.9 5.1 10.4 2.9Z"/>
          <path class="cat-outline" d="M27.4 29.6C20 32 13.5 41.2 13.9 52.4c.2 5.3 4.3 8.5 11.1 8.5h13.4c2.4 0 3.1-2.5 1.2-3.8l-1.6-19.5Z"/>
          <path class="cat-orange" d="M19 37.8c1.4 2 4 5 2.1 9.5-1.3 3.2-3.4 4.9-6.5 4.7-.4-5.8 1.3-10.1 4.4-14.2Z"/>
          <path class="cat-black" d="M25 31.7c2.7-1.3 6.3 0 8.9 2.6.9 2.6-.1 5.9-3 5.9-3.6 0-6.3-3.8-5.9-8.5Z"/>
          <path class="cat-detail" d="m29.9 47.7.4 10.9m4.1-9.9.7 8.9"/>
          <use :href="'#' + faceId" transform="translate(-6 7)"/>
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
  --cat-height: 46px;
  --cat-coat: #fff8e9;
  --cat-ink-color: #302921;
  --cat-marking: #3a332c;
  --cat-ginger: #f4a74d;
  --cat-ear: #e8a08c;
  position: absolute;
  z-index: 3;
  top: var(--cat-perch, 0px);
  right: clamp(12px, 11%, 180px);
  width: clamp(80px, 17%, 144px);
  height: var(--cat-height);
  margin-top: -44px;
  pointer-events: none;
}
.cat-walk { width: calc(100% - var(--cat-size)); height: var(--cat-height); }
.cat-ink { display: block; width: var(--cat-size); height: var(--cat-height); overflow: visible; transform-origin: center bottom; }
:global(:root[data-theme="dark"] .cat-ink) { filter: drop-shadow(0 0 .8px #fff8e97a); }
.cat-outline, .cat-limb { fill: var(--cat-coat); stroke: var(--cat-ink-color); stroke-width: 2; }
.cat-detail { fill: none; stroke: var(--cat-ink-color); stroke-width: 1.35; }
.cat-eye { stroke-width: 1.8; }
.cat-black { fill: var(--cat-marking); }
.cat-orange { fill: var(--cat-ginger); }
.cat-pink { fill: var(--cat-ear); }
.cat-grain { fill: none; stroke: var(--cat-coat); stroke-width: .85; opacity: .66; }
.cat-seated, .cat-asleep, .cat-eye-open { display: none; }
[data-pose="sleep"] .cat-awake { display: none; }
[data-pose="sleep"] .cat-asleep { display: inline; }
:is([data-pose="startle"], [data-pose="fall"]) .cat-eye { display: none; }
:is([data-pose="startle"], [data-pose="fall"]) .cat-eye-open { display: inline; }
.cat-tail { transform-origin: 15px 32px; }
.cat-head { transform-origin: 37px 31px; }
.cat-body { transform-origin: 26px 47px; }
.cat-paw { transform-origin: 16px 43px; }
.cat-paw-fore { transform-origin: 37px 44px; }
.cat-paw-far { fill: #e7ddca; }

@media (prefers-reduced-motion: no-preference) {
  .cat-walk { animation: cat-pace 9.6s linear infinite paused; }
  .cat-ink { animation: cat-turn 9.6s linear infinite paused; }
  [data-pose="walk"] :is(.cat-walk, .cat-ink) { animation-play-state: running; }
  .cat-awake { transform-origin: 28px 61px; }
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
