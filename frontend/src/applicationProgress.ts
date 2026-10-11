import type { JobApplication, JobEvent } from './jobTrackerStore'

const TERMINAL_STATUSES = new Set(['未通过', '已放弃', '已结束', '已通过'])

function eventTime(value: unknown) {
  if (typeof value !== 'string' || !value.trim()) return Number.NaN
  const time = new Date(value.replace(' ', 'T')).getTime()
  return Number.isFinite(time) ? time : Number.NaN
}

function eventEnd(event: JobEvent) {
  return event.endsAt || event.end || event.startsAt || event.start || event.date || ''
}

export function applicationProgressHealth(
  application: JobApplication,
  events: JobEvent[],
  now = Date.now()
) {
  if (application.stage !== '面试' || TERMINAL_STATUSES.has(String(application.status || ''))) return null

  const related = events.filter(event => event.applicationId === application.id)
  if (!related.length) return null

  // A future, still-active schedule means progress is already planned; start the
  // no-progress reminder only after that schedule's end time has passed.
  if (related.some(event => {
    if (event.completed || event.missed || event.abandoned) return false
    return eventTime(eventEnd(event)) >= now
  })) return null

  // Any associated schedule can mark progress, regardless of its event type.
  // Completed schedules use the actual completion time when available.
  const times = related
    .filter(event => !event.missed && !event.abandoned)
    .map(event => eventTime(event.completed ? event.completedAt || eventEnd(event) : eventEnd(event)))
    .filter(Number.isFinite)
  if (!times.length) return null

  const days = Math.max(0, Math.floor((now - Math.max(...times)) / 86_400_000))
  return { days, label: days <= 3 ? '进展正常' : days < 10 ? '等待较久' : '等待确认' }
}
