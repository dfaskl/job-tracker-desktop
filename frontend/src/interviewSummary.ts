import { api, ApiError } from './api'

let inFlight: Promise<unknown> | null = null
const INTERVIEW_SUMMARY_UPDATED_KEY = 'careerflow:interview-summary:updated-at'

export function notifyInterviewSummaryUpdated(): void {
  let updatedAt = Date.now()
  try {
    updatedAt = Math.max(updatedAt, Number(localStorage.getItem(INTERVIEW_SUMMARY_UPDATED_KEY) || 0) + 1)
    localStorage.setItem(INTERVIEW_SUMMARY_UPDATED_KEY, String(updatedAt))
  } catch { /* Keep the in-app notice even when storage is unavailable. */ }
  window.dispatchEvent(new CustomEvent('careerflow:interview-summary-updated', { detail: updatedAt }))
}

export function summarizeInterviewReviews<T>(): Promise<T> {
  if (inFlight) return inFlight as Promise<T>
  const request = (async () => {
    await summarizeInterviewReviewsStream(() => { /* Background refresh uses the same resumable batched pipeline. */ })
    const result = await api<T>('/api/poc/interview-workbench')
    notifyInterviewSummaryUpdated()
    return result
  })()
  inFlight = request.finally(() => { inFlight = null })
  return inFlight as Promise<T>
}

export type InterviewSummaryStreamEvent =
  | { type: 'progress'; message: string }
  | { type: 'classified'; summary: { sourceKey: string; stale?: boolean; topics: unknown[] } }
  | { type: 'question'; question: string; answer: string; frequency?: number }

export async function summarizeInterviewReviewsStream(
  onEvent: (event: InterviewSummaryStreamEvent) => void,
  force = false
): Promise<void> {
  for (let attempt = 0; ; attempt++) {
    const response = await fetch(`/api/poc/interview-workbench/summarize/stream${force ? '?force=true' : ''}`, {
      method: 'POST',
      cache: 'no-store',
      credentials: 'same-origin',
      headers: { Accept: 'text/event-stream' }
    })
    if (response.status === 429 && attempt === 0) {
      await new Promise(resolve => window.setTimeout(resolve, 3200))
      continue
    }
    if (!response.ok) {
      const body = await response.json().catch(() => ({})) as { message?: string }
      throw new ApiError(body.message || `请求失败（${response.status}）`, response.status)
    }
    if (!response.body) throw new Error('浏览器不支持读取流式响应')

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let completed = false
    const dispatch = (frame: string) => {
      let name = 'message'
      const data: string[] = []
      for (const line of frame.split(/\r?\n/)) {
        if (line.startsWith('event:')) name = line.slice(6).trim()
        else if (line.startsWith('data:')) data.push(line.slice(5).trimStart())
      }
      if (!data.length) return
      const payload = JSON.parse(data.join('\n')) as Record<string, unknown>
      if (name === 'progress') onEvent({ type: 'progress', message: String(payload.message || '正在生成面试复习内容…') })
      else if (name === 'classified') onEvent({ type: 'classified', summary: payload.summary as { sourceKey: string; stale?: boolean; topics: unknown[] } })
      else if (name === 'question') onEvent({ type: 'question', question: String(payload.question || ''), answer: String(payload.answer || ''), frequency: Number(payload.frequency) || 1 })
      else if (name === 'error') throw new Error(String(payload.message || '面试总结失败'))
      else if (name === 'complete') completed = true
    }

    try {
      while (true) {
        const { value, done } = await reader.read()
        buffer += decoder.decode(value, { stream: !done })
        const frames = buffer.split(/\r?\n\r?\n/)
        buffer = frames.pop() || ''
        for (const frame of frames) dispatch(frame)
        if (done) break
      }
      if (buffer.trim()) dispatch(buffer)
      if (!completed) throw new Error('流式响应意外中断，请重试')
    } catch (error) {
      await reader.cancel().catch(() => undefined)
      throw error
    }
    return
  }
}
