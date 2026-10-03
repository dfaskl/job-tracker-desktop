import { api, ApiError } from './api'

let inFlight: Promise<unknown> | null = null

export function classifyInterviewPositions<T>(): Promise<T> {
  if (inFlight) return inFlight as Promise<T>
  const request = (async () => {
    try { return await api<T>('/api/poc/interview-workbench/classify', { method: 'POST' }) }
    catch (cause) {
      if (!(cause instanceof ApiError) || cause.status !== 429) throw cause
      await new Promise(resolve => window.setTimeout(resolve, 3200))
      return api<T>('/api/poc/interview-workbench/classify', { method: 'POST' })
    }
  })()
  inFlight = request.finally(() => { inFlight = null })
  return inFlight as Promise<T>
}
