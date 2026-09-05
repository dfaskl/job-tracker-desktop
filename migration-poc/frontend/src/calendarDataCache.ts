export type CalendarData = {
  events: unknown[]
  applications: unknown[]
  total: number
}

let cached: CalendarData | null = null
let loadedAt = 0
let pending: Promise<CalendarData> | null = null
const CACHE_TTL = 30_000

export async function loadCalendarData(force = false): Promise<CalendarData> {
  if (!force && cached && Date.now() - loadedAt < CACHE_TTL) return cached
  if (!force && pending) return pending
  pending = fetch('/api/poc/event-sandbox/events', { cache: 'no-store' }).then(async (response) => {
    const body = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(response.status === 401 ? '请先登录后再读取日程' : (body.message || '读取日程失败'))
    cached = {
      events: Array.isArray(body.events) ? body.events : [],
      applications: Array.isArray(body.applications) ? body.applications : [],
      total: Number(body.total || 0)
    }
    loadedAt = Date.now()
    return cached
  }).finally(() => { pending = null })
  return pending
}

export function preloadCalendarData() {
  void loadCalendarData().catch(() => undefined)
}