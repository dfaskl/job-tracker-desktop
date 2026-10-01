export type ScheduleTimeMode = 'point' | 'range'

export function validateScheduleTime(mode: ScheduleTimeMode, startsAt: string, endsAt: string) {
  if (!startsAt.trim()) return '请选择日程时间'
  const start = new Date(startsAt).getTime()
  if (!Number.isFinite(start)) return '日程时间格式无效'
  if (mode === 'point') return ''
  if (!endsAt.trim()) return '请选择日程结束时间'
  const end = new Date(endsAt).getTime()
  if (!Number.isFinite(end) || end <= start) return '结束时间必须晚于开始时间'
  return ''
}
