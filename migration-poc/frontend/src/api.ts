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
