export class ApiError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message)
  }
}

export async function api<T>(url: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  const response = await fetch(url, { cache: 'no-store', credentials: 'same-origin', ...init, headers })
  const body = await response.json().catch(() => ({}))
  if (!response.ok) throw new ApiError(body.message || `请求失败（${response.status}）`, response.status)
  return body as T
}

type CacheEntry = { expiresAt: number; value: unknown }
const responseCache = new Map<string, CacheEntry>()
const pendingGets = new Map<string, Promise<unknown>>()

export function clearApiCache() {
  responseCache.clear()
  pendingGets.clear()
}

export async function apiCached<T>(url: string, ttlMs = 30_000): Promise<T> {
  const cached = responseCache.get(url)
  if (cached && cached.expiresAt > Date.now()) return cached.value as T
  const pending = pendingGets.get(url)
  if (pending) return pending as Promise<T>
  const request = api<T>(url).then(value => {
    responseCache.set(url, { expiresAt: Date.now() + ttlMs, value })
    return value
  }).finally(() => pendingGets.delete(url))
  pendingGets.set(url, request)
  return request
}