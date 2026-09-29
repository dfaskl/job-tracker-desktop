export const SHANGHAI_TIME_ZONE = 'Asia/Shanghai'

export function formatShanghaiDateTime(
  value: string | number | Date,
  options: Intl.DateTimeFormatOptions = {},
  fallback = ''
) {
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return fallback || String(value || '')
  return date.toLocaleString('zh-CN', {
    hour12: false,
    timeZone: SHANGHAI_TIME_ZONE,
    ...options
  })
}

export function shanghaiDateKey(value: string | number | Date = new Date()) {
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: SHANGHAI_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).formatToParts(date)
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find(item => item.type === type)?.value || ''
  return `${part('year')}-${part('month')}-${part('day')}`
}

// Schedules are stored as timezone-free Asia/Shanghai wall time. Preserve those
// components instead of treating them as UTC and applying an extra conversion.
export function formatShanghaiScheduleDateTime(value: string, fallback = '') {
  const match = value.trim().match(/^(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{2}):(\d{2}))?/)
  if (!match) return fallback || value
  const date = `${Number(match[2])}月${Number(match[3])}日`
  return match[4] && match[5] ? `${date} ${match[4]}:${match[5]}` : date
}
