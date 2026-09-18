import { readonly, ref } from 'vue'

const blocking = ref(false)
let activeBlockingRequests = 0
let mutationSessionOpen = false
let visibleSince = 0
let releaseTimer: number | undefined

const quietPeriodMs = 140
const minimumVisibleMs = 420

export const pageMutationBusy = readonly(blocking)

function clearReleaseTimer() {
  if (releaseTimer === undefined) return
  window.clearTimeout(releaseTimer)
  releaseTimer = undefined
}

function openMutationSession() {
  clearReleaseTimer()
  mutationSessionOpen = true
  if (!blocking.value) {
    visibleSince = Date.now()
    blocking.value = true
  }
}

function scheduleMutationSessionClose() {
  if (activeBlockingRequests > 0) return
  clearReleaseTimer()
  const minimumRemaining = Math.max(0, minimumVisibleMs - (Date.now() - visibleSince))
  releaseTimer = window.setTimeout(() => {
    releaseTimer = undefined
    if (activeBlockingRequests > 0) return
    mutationSessionOpen = false
    blocking.value = false
  }, Math.max(quietPeriodMs, minimumRemaining))
}

function requestMethod(input: RequestInfo | URL, init?: RequestInit) {
  if (init?.method) return init.method.toUpperCase()
  return input instanceof Request ? input.method.toUpperCase() : 'GET'
}

export async function trackedFetch(
  input: RequestInfo | URL,
  init: RequestInit = {},
  blockPage?: boolean
) {
  const method = requestMethod(input, init)
  const writesData = !['GET', 'HEAD', 'OPTIONS'].includes(method)
  const startsMutationSession = blockPage ?? writesData
  const participatesInSession = blockPage === false ? false : startsMutationSession || mutationSessionOpen

  if (startsMutationSession) openMutationSession()
  if (participatesInSession) {
    clearReleaseTimer()
    activeBlockingRequests += 1
  }

  try {
    return await fetch(input, init)
  } finally {
    if (participatesInSession) {
      activeBlockingRequests = Math.max(0, activeBlockingRequests - 1)
      scheduleMutationSessionClose()
    }
  }
}
