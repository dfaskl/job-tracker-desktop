import { onBeforeUnmount } from 'vue'

export function useWorkspacePointerEffects() {
  let pointerFrame = 0
  let litSurface: HTMLElement | null = null
  let magneticButton: HTMLButtonElement | null = null
  const pointerMotion = window.matchMedia('(hover: hover) and (pointer: fine) and (prefers-reduced-motion: no-preference)')
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')

  function resetPointerEffects() {
    cancelAnimationFrame(pointerFrame)
    pointerFrame = 0
    if (litSurface) delete litSurface.dataset.pointerActive
    if (magneticButton) delete magneticButton.dataset.magnetic
    litSurface = null
    magneticButton = null
  }

  function followPointer(event: PointerEvent) {
    if (event.pointerType !== 'mouse' || !pointerMotion.matches || !(event.target instanceof Element)) return
    if (event.buttons || !(event.currentTarget instanceof HTMLElement) || !event.currentTarget.contains(event.target)) {
      resetPointerEffects()
      return
    }
    const surface = event.target.closest<HTMLElement>('.home-optical-surface, .workspace-light-surface')
    const button = event.target.closest<HTMLButtonElement>(':is(.home-action, .workspace-light-action):not(:disabled)')
    if (!surface && !button && !litSurface && !magneticButton && !pointerFrame) return
    const { clientX, clientY } = event
    cancelAnimationFrame(pointerFrame)
    pointerFrame = requestAnimationFrame(() => {
      pointerFrame = 0
      if (litSurface && litSurface !== surface) delete litSurface.dataset.pointerActive
      if (magneticButton && magneticButton !== button) delete magneticButton.dataset.magnetic
      litSurface = surface
      magneticButton = button
      const surfaceBounds = surface?.getBoundingClientRect()
      const buttonBounds = button?.getBoundingClientRect()
      if (surface && surfaceBounds?.width && surfaceBounds.height) {
        const x = (clientX - surfaceBounds.left) / surfaceBounds.width
        const y = (clientY - surfaceBounds.top) / surfaceBounds.height
        surface.style.setProperty('--light-x', `${x * 100}%`)
        surface.style.setProperty('--light-y', `${y * 100}%`)
        surface.style.setProperty('--tilt-x', `${(.5 - y) * 3}deg`)
        surface.style.setProperty('--tilt-y', `${(x - .5) * 3}deg`)
        surface.dataset.pointerActive = ''
      }
      if (button && buttonBounds) {
        button.style.setProperty('--magnet-x', `${(clientX - buttonBounds.left - buttonBounds.width / 2) * .2}px`)
        button.style.setProperty('--magnet-y', `${(clientY - buttonBounds.top - buttonBounds.height / 2) * .2}px`)
        button.dataset.magnetic = ''
      }
    })
  }

  function rippleAction(event: MouseEvent) {
    if (reducedMotion.matches || !event.detail || !(event.target instanceof Element)) return
    const button = event.target.closest<HTMLButtonElement>(':is(.home-action, .workspace-light-action):not(:disabled)')
    const surface = event.target.closest<HTMLElement>('.analytics-tile')
    const target = button || surface
    if (!target || event.target.closest(':disabled, [aria-disabled="true"]') ||
      !(event.currentTarget instanceof HTMLElement) || !event.currentTarget.contains(target)) return
    if (!button && window.getSelection()?.type === 'Range') return
    const bounds = target.getBoundingClientRect()
    const ripple = document.createElement('span')
    ripple.className = 'home-action-ripple'
    ripple.setAttribute('aria-hidden', 'true')
    ripple.style.left = `${event.clientX - bounds.left}px`
    ripple.style.top = `${event.clientY - bounds.top}px`
    ripple.style.setProperty('--ripple-size', `${Math.ceil(2 * Math.hypot(bounds.width, bounds.height))}px`)
    target.append(ripple)
    const animation = ripple.animate([{ transform: 'translate(-50%, -50%) scale(0)', opacity: .32 },
      { transform: 'translate(-50%, -50%) scale(1)', opacity: 0 }], { duration: 420, easing: 'ease-out' })
    animation.finished.then(() => ripple.remove(), () => ripple.remove())
  }
  pointerMotion.addEventListener('change', resetPointerEffects)
  onBeforeUnmount(() => {
    resetPointerEffects()
    pointerMotion.removeEventListener('change', resetPointerEffects)
  })
  return { followPointer, resetPointerEffects, rippleAction }
}
