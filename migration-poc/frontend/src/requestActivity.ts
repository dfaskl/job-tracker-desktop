import { readonly, ref } from 'vue'

const blocking = ref(false)
let activeBlockingRequests = 0
let mutationSessionOpen = false
let releaseTimer: number | undefined

export const pageMutationBusy = readonly(blocking)

function clearReleaseTimer() {
  if (releaseTimer === undefined) return
  window.clearTimeout(releaseTimer)
  releaseTimer = undefined
}

function openMutationSession() {
  clearReleaseTimer()
  mutationSessionOpen = true
  blocking.value = true
}

function scheduleMutationSessionClose() {
  if (activeBlockingRequests > 0) return
  clearReleaseTimer()
  releaseTimer = window.setTimeout(() => {
    releaseTimer = undefined
    if (activeBlockingRequests > 0) return
    mutationSessionOpen = false
    blocking.value = false
  }, 0)
}

function requestMethod(input: RequestInfo | URL, init?: RequestInit) {
  if (init?.method) return init.method.toUpperCase()
  return input instanceof Request ? input.method.toUpperCase() : 'GET'
}

export async function trackedJsonFetch<T = Record<string, unknown>>(
  input: RequestInfo | URL,
  init: RequestInit = {},
  blockPage?: boolean
): Promise<{ response: Response; body: T }> {
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
    const response = await fetch(input, init)
    const body = await response.json().catch(() => ({})) as T
    return { response, body }
  } finally {
    if (participatesInSession) {
      activeBlockingRequests = Math.max(0, activeBlockingRequests - 1)
      scheduleMutationSessionClose()
    }
  }
}
