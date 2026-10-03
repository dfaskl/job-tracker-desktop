export class ApiError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message)
  }
}

export type ApiRequestInit = RequestInit

export async function jsonFetch<T = Record<string, unknown>>(
  input: RequestInfo | URL,
  init: RequestInit = {}
): Promise<{ response: Response; body: T }> {
  const response = await fetch(input, init)
  const body = await response.json().catch(() => ({})) as T
  return { response, body }
}

export async function api<T>(url: string, init: ApiRequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  const { response, body } = await jsonFetch<Record<string, unknown>>(url, { cache: 'no-store', credentials: 'same-origin', ...init, headers })
  if (!response.ok) throw new ApiError(String(body.message || `请求失败（${response.status}）`), response.status)
  return body as T
}

type CacheEntry = { expiresAt: number; value: unknown }
const responseCache = new Map<string, CacheEntry>()
const pendingGets = new Map<string, Promise<unknown>>()
const urlGenerations = new Map<string, number>()
let cacheGeneration = 0

export function clearApiCache() {
  cacheGeneration += 1
  responseCache.clear()
  pendingGets.clear()
  urlGenerations.clear()
}

export function invalidateApiCache(url: string) {
  urlGenerations.set(url, (urlGenerations.get(url) || 0) + 1)
  responseCache.delete(url)
  pendingGets.delete(url)
}

export async function apiCached<T>(url: string, ttlMs = 30_000): Promise<T> {
  const cached = responseCache.get(url)
  if (cached && cached.expiresAt > Date.now()) return cached.value as T
  const pending = pendingGets.get(url)
  if (pending) return pending as Promise<T>
  const generation = cacheGeneration
  const urlGeneration = urlGenerations.get(url) || 0
  const request = api<T>(url).then(value => {
    if (cacheGeneration === generation && (urlGenerations.get(url) || 0) === urlGeneration) {
      responseCache.set(url, { expiresAt: Date.now() + ttlMs, value })
    }
    return value
  }).finally(() => { if (pendingGets.get(url) === request) pendingGets.delete(url) })
  pendingGets.set(url, request)
  return request
}
