<script setup lang="ts">
import { computed, defineComponent, h, inject, onBeforeUnmount, onMounted, provide, ref, type InjectionKey, type Ref, watch } from 'vue'

type MousePoint = { x: number; y: number }
const mouseKey: InjectionKey<Ref<MousePoint>> = Symbol('auth-illustration-mouse')
const clamp = (value: number, min: number, max: number) => Math.max(min, Math.min(max, value))

const props = withDefaults(defineProps<{
  isTyping?: boolean
  showPassword?: boolean
  passwordLength?: number
  passwordActive?: boolean
}>(), { isTyping: false, showPassword: false, passwordLength: 0, passwordActive: false })

const mouse = ref<MousePoint>({ x: 0, y: 0 })
provide(mouseKey, mouse)

const purpleRef = ref<HTMLElement | null>(null)
const blackRef = ref<HTMLElement | null>(null)
const yellowRef = ref<HTMLElement | null>(null)
const orangeRef = ref<HTMLElement | null>(null)
const typingLook = ref(false)
const peek = ref(false)

function useRandomBlink() {
  const blinking = ref(false)
  let timer: number | undefined
  const queue = () => {
    timer = window.setTimeout(() => {
      blinking.value = true
      timer = window.setTimeout(() => {
        blinking.value = false
        queue()
      }, 150)
    }, 3000 + Math.random() * 4000)
  }
  onMounted(queue)
  onBeforeUnmount(() => window.clearTimeout(timer))
  return blinking
}

const purpleBlink = useRandomBlink()
const blackBlink = useRandomBlink()

let typingTimer: number | undefined
watch(() => props.isTyping, (active) => {
  window.clearTimeout(typingTimer)
  if (!active) {
    typingLook.value = false
    return
  }
  typingLook.value = true
  typingTimer = window.setTimeout(() => { typingLook.value = false }, 800)
}, { immediate: true })

let peekTimer: number | undefined
let peekCloseTimer: number | undefined
function stopPeek() {
  window.clearTimeout(peekTimer)
  window.clearTimeout(peekCloseTimer)
  peek.value = false
}
function queuePeek() {
  if (props.passwordLength <= 0 || !props.showPassword) return
  peekTimer = window.setTimeout(() => {
    peek.value = true
    peekCloseTimer = window.setTimeout(() => {
      peek.value = false
      queuePeek()
    }, 800)
  }, 2000 + Math.random() * 3000)
}
watch(() => [props.passwordLength, props.showPassword], () => {
  stopPeek()
  queuePeek()
}, { immediate: true })

function handleMouseMove(event: MouseEvent) {
  mouse.value = { x: event.clientX, y: event.clientY }
}
onMounted(() => window.addEventListener('mousemove', handleMouseMove, { passive: true }))
onBeforeUnmount(() => {
  window.removeEventListener('mousemove', handleMouseMove)
  window.clearTimeout(typingTimer)
  stopPeek()
})

function metrics(target: Ref<HTMLElement | null>) {
  const element = target.value
  if (!element) return { faceX: 0, faceY: 0, bodySkew: 0 }
  const rect = element.getBoundingClientRect()
  const centerX = rect.left + rect.width / 2
  const centerY = rect.top + rect.height / 3
  const dx = mouse.value.x - centerX
  return {
    faceX: clamp(dx / 20, -15, 15),
    faceY: clamp((mouse.value.y - centerY) / 30, -10, 10),
    bodySkew: clamp(-dx / 120, -6, 6)
  }
}

const purple = computed(() => metrics(purpleRef))
const black = computed(() => metrics(blackRef))
const yellow = computed(() => metrics(yellowRef))
const orange = computed(() => metrics(orangeRef))
const passwordInUse = computed(() => props.passwordLength > 0 || props.passwordActive)
const hidingPassword = computed(() => passwordInUse.value && !props.showPassword)

const purpleStyle = computed(() => ({
  left: '70px', width: '180px', height: props.isTyping || hidingPassword.value ? '440px' : '400px', zIndex: 1,
  transform: props.showPassword && passwordInUse.value
    ? 'skewX(0deg)'
    : props.isTyping || hidingPassword.value
      ? `skewX(${purple.value.bodySkew - 12}deg) translateX(40px)`
      : `skewX(${purple.value.bodySkew}deg)`
}))
const blackStyle = computed(() => ({
  left: '240px', width: '120px', height: '310px', zIndex: 2,
  transform: props.showPassword && passwordInUse.value
    ? 'skewX(0deg)'
    : typingLook.value
      ? `skewX(${black.value.bodySkew * 1.5 + 10}deg) translateX(20px)`
      : props.isTyping || hidingPassword.value
        ? `skewX(${black.value.bodySkew * 1.5}deg)`
        : `skewX(${black.value.bodySkew}deg)`
}))
const orangeStyle = computed(() => ({
  left: '0', width: '240px', height: '200px', zIndex: 3,
  transform: props.showPassword && passwordInUse.value ? 'skewX(0deg)' : `skewX(${orange.value.bodySkew}deg)`
}))
const yellowStyle = computed(() => ({
  left: '310px', width: '140px', height: '230px', zIndex: 4,
  transform: props.showPassword && passwordInUse.value ? 'skewX(0deg)' : `skewX(${yellow.value.bodySkew}deg)`
}))

const EyeBall = defineComponent({
  name: 'EyeBall',
  props: {
    size: { type: Number, default: 48 }, pupilSize: { type: Number, default: 16 }, maxDistance: { type: Number, default: 10 },
    blinking: Boolean, forceLookX: Number, forceLookY: Number
  },
  setup(eyeProps) {
    const element = ref<HTMLElement | null>(null)
    const sharedMouse = inject(mouseKey, ref({ x: 0, y: 0 }))
    const offset = computed(() => {
      if (eyeProps.forceLookX !== undefined && eyeProps.forceLookY !== undefined) return { x: eyeProps.forceLookX, y: eyeProps.forceLookY }
      if (!element.value) return { x: 0, y: 0 }
      const rect = element.value.getBoundingClientRect()
      const dx = sharedMouse.value.x - (rect.left + rect.width / 2)
      const dy = sharedMouse.value.y - (rect.top + rect.height / 2)
      const distance = Math.min(Math.sqrt(dx ** 2 + dy ** 2), eyeProps.maxDistance)
      const angle = Math.atan2(dy, dx)
      return { x: Math.cos(angle) * distance, y: Math.sin(angle) * distance }
    })
    return () => h('i', { ref: element, class: 'auth-eye', style: { width: `${eyeProps.size}px`, height: eyeProps.blinking ? '2px' : `${eyeProps.size}px` } }, eyeProps.blinking ? null : h('b', { style: { width: `${eyeProps.pupilSize}px`, height: `${eyeProps.pupilSize}px`, transform: `translate(${offset.value.x}px, ${offset.value.y}px)` } }))
  }
})

const EyeDot = defineComponent({
  name: 'EyeDot',
  props: { size: { type: Number, default: 12 }, maxDistance: { type: Number, default: 5 }, forceLookX: Number, forceLookY: Number },
  setup(dotProps) {
    const element = ref<HTMLElement | null>(null)
    const sharedMouse = inject(mouseKey, ref({ x: 0, y: 0 }))
    const offset = computed(() => {
      if (dotProps.forceLookX !== undefined && dotProps.forceLookY !== undefined) return { x: dotProps.forceLookX, y: dotProps.forceLookY }
      if (!element.value) return { x: 0, y: 0 }
      const rect = element.value.getBoundingClientRect()
      const dx = sharedMouse.value.x - (rect.left + rect.width / 2)
      const dy = sharedMouse.value.y - (rect.top + rect.height / 2)
      const distance = Math.min(Math.sqrt(dx ** 2 + dy ** 2), dotProps.maxDistance)
      const angle = Math.atan2(dy, dx)
      return { x: Math.cos(angle) * distance, y: Math.sin(angle) * distance }
    })
    return () => h('i', { ref: element, class: 'auth-eye-dot', style: { width: `${dotProps.size}px`, height: `${dotProps.size}px`, transform: `translate(${offset.value.x}px, ${offset.value.y}px)` } })
  }
})
</script>

<template>
  <div class="auth-illustration" aria-hidden="true">
    <div ref="purpleRef" class="auth-creature purple" :style="purpleStyle">
      <div class="auth-face purple-face" :style="{ left: passwordInUse && showPassword ? '20px' : typingLook ? '55px' : `${45 + purple.faceX}px`, top: passwordInUse && showPassword ? '35px' : typingLook ? '65px' : `${40 + purple.faceY}px` }">
        <EyeBall :size="18" :pupil-size="7" :max-distance="5" :blinking="purpleBlink" :force-look-x="passwordInUse && showPassword ? (peek ? 4 : -4) : typingLook ? 3 : undefined" :force-look-y="passwordInUse && showPassword ? (peek ? 5 : -4) : typingLook ? 4 : undefined" />
        <EyeBall :size="18" :pupil-size="7" :max-distance="5" :blinking="purpleBlink" :force-look-x="passwordInUse && showPassword ? (peek ? 4 : -4) : typingLook ? 3 : undefined" :force-look-y="passwordInUse && showPassword ? (peek ? 5 : -4) : typingLook ? 4 : undefined" />
      </div>
    </div>

    <div ref="blackRef" class="auth-creature black" :style="blackStyle">
      <div class="auth-face black-face" :style="{ left: passwordInUse && showPassword ? '10px' : typingLook ? '32px' : `${26 + black.faceX}px`, top: passwordInUse && showPassword ? '28px' : typingLook ? '12px' : `${32 + black.faceY}px` }">
        <EyeBall :size="16" :pupil-size="6" :max-distance="4" :blinking="blackBlink" :force-look-x="passwordInUse && showPassword ? -4 : typingLook ? 0 : undefined" :force-look-y="passwordInUse && showPassword || typingLook ? -4 : undefined" />
        <EyeBall :size="16" :pupil-size="6" :max-distance="4" :blinking="blackBlink" :force-look-x="passwordInUse && showPassword ? -4 : typingLook ? 0 : undefined" :force-look-y="passwordInUse && showPassword || typingLook ? -4 : undefined" />
      </div>
    </div>

    <div ref="orangeRef" class="auth-creature orange" :style="orangeStyle">
      <div class="auth-face orange-face" :style="{ left: passwordInUse && showPassword ? '50px' : `${82 + orange.faceX}px`, top: passwordInUse && showPassword ? '85px' : `${90 + orange.faceY}px` }">
        <EyeDot :force-look-x="passwordInUse && showPassword ? -5 : undefined" :force-look-y="passwordInUse && showPassword ? -4 : undefined" />
        <EyeDot :force-look-x="passwordInUse && showPassword ? -5 : undefined" :force-look-y="passwordInUse && showPassword ? -4 : undefined" />
      </div>
    </div>

    <div ref="yellowRef" class="auth-creature yellow" :style="yellowStyle">
      <div class="auth-face yellow-face" :style="{ left: passwordInUse && showPassword ? '20px' : `${52 + yellow.faceX}px`, top: passwordInUse && showPassword ? '35px' : `${40 + yellow.faceY}px` }">
        <EyeDot :force-look-x="passwordInUse && showPassword ? -5 : undefined" :force-look-y="passwordInUse && showPassword ? -4 : undefined" />
        <EyeDot :force-look-x="passwordInUse && showPassword ? -5 : undefined" :force-look-y="passwordInUse && showPassword ? -4 : undefined" />
      </div>
      <div class="yellow-mouth" :style="{ left: passwordInUse && showPassword ? '10px' : `${40 + yellow.faceX}px`, top: passwordInUse && showPassword ? '88px' : `${88 + yellow.faceY}px` }" />
    </div>
  </div>
</template>

<style scoped>
.auth-illustration{position:relative;width:550px;height:400px;flex:0 0 auto}
.auth-creature{position:absolute;bottom:0;transform-origin:bottom center;transition:all .7s ease-in-out;will-change:height,transform}
.purple{border-radius:10px 10px 0 0;background:#6c3ff5}.black{border-radius:8px 8px 0 0;background:#2d2d2d}.orange{border-radius:120px 120px 0 0;background:#ff9b6b}.yellow{border-radius:70px 70px 0 0;background:#e8d754}
.auth-face{position:absolute;display:flex;align-items:center;transition:all .7s ease-in-out}.purple-face{gap:32px}.black-face{gap:24px}.orange-face{gap:32px;transition-duration:.2s}.yellow-face{gap:24px;transition-duration:.2s}
:deep(.auth-eye){display:flex;align-items:center;justify-content:center;overflow:hidden;border-radius:999px;background:#fff;transition:all .15s ease}
:deep(.auth-eye b){display:block;border-radius:999px;background:#2d2d2d;transition:transform .1s ease-out}
:deep(.auth-eye-dot){display:block;border-radius:999px;background:#2d2d2d;transition:transform .1s ease-out}
.yellow-mouth{position:absolute;width:80px;height:4px;border-radius:999px;background:#2d2d2d;transition:all .2s ease-out}
@media(max-width:1180px){.auth-illustration{transform:scale(.86);transform-origin:bottom center}}
@media(prefers-reduced-motion:reduce){.auth-creature,.auth-face{transition-duration:.7s!important}.orange-face,.yellow-face,.yellow-mouth{transition-duration:.2s!important}:deep(.auth-eye),:deep(.auth-eye b),:deep(.auth-eye-dot){transition-duration:.1s!important}}
</style>
