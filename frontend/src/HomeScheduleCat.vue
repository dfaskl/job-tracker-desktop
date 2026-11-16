<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import tabbyAtlas from './assets/home-schedule-tabby.png'

const props = defineProps<{ pose: 'routine' | 'startle' | 'fall' | 'land' | 'sleep' }>()

// Recolored PixelPaws sprites. The MIT notice ships with the asset.
const frame = {
  idle: 0,
  walk: [1, 2, 3, 4],
  sleep: [5, 6],
  fall: 7,
  crouch: [8, 9],
  wake: [10, 11],
}
const wakeStart = 16000
const walkStart = wakeStart + 800
const walkLegDuration = 1600
const turnStart = walkStart + walkLegDuration
const returnStart = turnStart + 140
const settleStart = returnStart + walkLegDuration
const routineDuration = settleStart + 500

const track = ref<HTMLElement>()
const walkingTrack = ref<HTMLElement>()
const position = ref(0)
const facingLeft = ref(false)
const frameIndex = ref(frame.sleep[0])
const backgroundPosition = computed(() => {
  const index = frameIndex.value
  return `${index % 8 * -50}px ${Math.floor(index / 8) * -50}px`
})
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')
let observer: IntersectionObserver | undefined
let resizeObserver: ResizeObserver | undefined
let walkingWidth = 0
let active = false
let visible = false
let animationFrame = 0
let lastFrameTime: number | undefined
let elapsed = 0
let restingPosition = 0
let restingFacingLeft = false

function drawRoutine() {
  if (elapsed < wakeStart) {
    frameIndex.value = frame.sleep[Math.floor(elapsed / 720) % frame.sleep.length]
    return
  }

  const walksLeft = restingPosition > .5
  if (elapsed < walkStart) {
    frameIndex.value = frame.wake[elapsed - wakeStart < 400 ? 0 : 1]
    facingLeft.value = walksLeft
    return
  }

  if (elapsed < settleStart) {
    const returning = elapsed >= returnStart
    const distance = walksLeft ? restingPosition : 1 - restingPosition
    const progress = Math.min(1, (elapsed - (returning ? returnStart : walkStart)) / walkLegDuration)
    const run = distance * (returning ? 1 - progress : progress)
    position.value = restingPosition + (walksLeft ? -run : run)
    facingLeft.value = returning ? !walksLeft : walksLeft
    frameIndex.value = elapsed >= turnStart && !returning
      ? frame.idle
      : frame.walk[Math.floor(distance * walkingWidth * progress / 2.9) % frame.walk.length]
    return
  }

  position.value = restingPosition
  frameIndex.value = frame.crouch[elapsed - settleStart < 250 ? 0 : 1]
}

function tick(now: number) {
  if (lastFrameTime !== undefined) {
    elapsed += Math.min(now - lastFrameTime, 50)
    if (elapsed >= routineDuration) elapsed %= routineDuration
  }
  lastFrameTime = now
  drawRoutine()
  animationFrame = requestAnimationFrame(tick)
}

function stop() {
  cancelAnimationFrame(animationFrame)
  animationFrame = 0
  lastFrameTime = undefined
}

function updatePlayback() {
  if (active && visible && !document.hidden && !reducedMotion.matches && props.pose === 'routine') {
    if (!animationFrame) animationFrame = requestAnimationFrame(tick)
  } else {
    stop()
  }
}

function resetRoutine() {
  elapsed = 0
  restingPosition = position.value
  restingFacingLeft = facingLeft.value
  frameIndex.value = frame.sleep[0]
}

watch(() => props.pose, pose => {
  if (pose === 'startle') frameIndex.value = frame.crouch[0]
  else if (pose === 'fall') frameIndex.value = frame.fall
  else if (pose === 'land') frameIndex.value = frame.crouch[1]
  else resetRoutine()
  updatePlayback()
}, { immediate: true })

function motionChanged() {
  if (reducedMotion.matches) {
    position.value = restingPosition
    facingLeft.value = restingFacingLeft
    resetRoutine()
  }
  updatePlayback()
}

onMounted(() => {
  observer = new IntersectionObserver(([entry]) => {
    visible = entry.isIntersecting
    updatePlayback()
  })
  if (track.value) observer.observe(track.value)
  resizeObserver = new ResizeObserver(([entry]) => {
    walkingWidth = entry.contentRect.width
  })
  if (walkingTrack.value) resizeObserver.observe(walkingTrack.value)
  document.addEventListener('visibilitychange', updatePlayback)
  reducedMotion.addEventListener('change', motionChanged)
})

onActivated(() => {
  active = true
  updatePlayback()
})
onDeactivated(() => {
  active = false
  stop()
})
onBeforeUnmount(() => {
  observer?.disconnect()
  resizeObserver?.disconnect()
  document.removeEventListener('visibilitychange', updatePlayback)
  reducedMotion.removeEventListener('change', motionChanged)
  stop()
})
</script>

<template>
  <div ref="track" class="cat-track" :data-pose="pose" aria-hidden="true">
    <div ref="walkingTrack" class="cat-walk" :style="{ translate: (position * 100) + '% 0' }">
      <div
        class="cat-frame"
        :style="{ backgroundImage: `url(${tabbyAtlas})`, backgroundPosition, scale: facingLeft ? '-1 1' : '1 1' }"
      ></div>
    </div>
  </div>
</template>

<style scoped>
.cat-track {
  --cat-size: 50px;
  position: absolute;
  z-index: 3;
  top: var(--cat-perch, 0px);
  right: clamp(12px, 11%, 180px);
  width: clamp(80px, 17%, 144px);
  height: var(--cat-size);
  margin-top: calc(2px - var(--cat-size));
  pointer-events: none;
}
.cat-walk {
  width: min(74px, calc(100% - var(--cat-size)));
  height: 100%;
}
.cat-frame {
  width: var(--cat-size);
  height: var(--cat-size);
  background-repeat: no-repeat;
  background-size: 400px 100px;
  transform-origin: center bottom;
}
@media (prefers-reduced-motion: no-preference) {
  [data-pose="startle"] .cat-frame {
    animation: cat-startle 340ms steps(1, jump-end) both;
  }
}
@keyframes cat-startle {
  0% { background-position: 0 -50px; }
  50%, 100% { background-position: -50px -50px; }
}
</style>
