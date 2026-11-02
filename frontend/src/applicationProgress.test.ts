import { describe, expect, it } from 'vitest'
import { applicationProgressHealth, isScheduleOverdue, schedulePriorityTimestamp } from './applicationProgress'
import type { JobApplication, JobEvent } from './jobTrackerStore'

const now = Date.parse('2026-10-11T00:00:00Z')
const interviewApplication: JobApplication = { id: 'app-1', stage: '面试', status: '等待结果' }

describe('isScheduleOverdue', () => {
  it('flags a pending point only after its scheduled time', () => {
    const point = { id: 'point', startsAt: '2026-11-02T10:00:00+08:00' }
    const timestamp = Date.parse(point.startsAt)
    expect(isScheduleOverdue(point, timestamp)).toBe(false)
    expect(isScheduleOverdue(point, timestamp + 1)).toBe(true)
  })

  it('waits until a time range has ended', () => {
    const range = { id: 'range', startsAt: '2026-11-01T10:00:00+08:00', endsAt: '2026-11-03T10:00:00+08:00' }
    expect(isScheduleOverdue(range, Date.parse('2026-11-02T10:00:00+08:00'))).toBe(false)
    expect(isScheduleOverdue(range, Date.parse(range.endsAt) + 1)).toBe(true)
  })

  it.each(['completed', 'missed', 'abandoned'])('does not flag a resolved %s schedule', flag => {
    expect(isScheduleOverdue({ id: 'resolved', startsAt: '2026-09-18 15:00', [flag]: true }, now)).toBe(false)
  })

  it('does not flag missing or invalid dates', () => {
    expect(isScheduleOverdue({ id: 'missing' }, now)).toBe(false)
    expect(isScheduleOverdue({ id: 'invalid', startsAt: 'unknown' }, now)).toBe(false)
  })
})

function event(overrides: Partial<JobEvent>): JobEvent {
  return { id: 'event-1', applicationId: 'app-1', type: '面试', startsAt: '2026-09-28T10:00:00Z', ...overrides }
}

describe('applicationProgressHealth', () => {
  it.each([
    [7, '进展正常'],
    [8, '等待较久'],
    [21, '等待较久'],
    [22, '建议确认']
  ])('uses the correct threshold at %i days', (days, label) => {
    const startsAt = new Date(now - Number(days) * 86_400_000).toISOString()
    const result = applicationProgressHealth(interviewApplication, [event({ startsAt })], now)
    expect(result).toEqual({ days, label })
  })

  it('counts from the latest associated schedule regardless of event type', () => {
    const result = applicationProgressHealth(interviewApplication, [
      event({ id: 'interview', type: '面试', completed: true, completedAt: '2026-09-28T10:00:00Z' }),
      event({ id: 'authorization', type: '授权书签署', startsAt: '2026-10-10T10:00:00Z', completed: true, completedAt: '2026-10-10T12:00:00Z' })
    ], now)

    expect(result).toEqual({ days: 0, label: '进展正常' })
  })

  it('does not show a no-progress timer while an active future schedule is pending', () => {
    expect(applicationProgressHealth(interviewApplication, [
      event({ startsAt: '2026-10-12T10:00:00Z' })
    ], now)).toBeNull()
  })

  it('only counts applications already in the interview stage', () => {
    expect(applicationProgressHealth({ ...interviewApplication, stage: '笔试' }, [
      event({ startsAt: '2026-09-01T10:00:00Z' })
    ], now)).toBeNull()
  })

  it('counts the newest associated schedule even when it is missed or abandoned', () => {
    expect(applicationProgressHealth(interviewApplication, [
      event({ id: 'old-interview', startsAt: '2026-09-28T10:00:00Z', completed: true }),
      event({ id: 'missed-authorization', type: '其他', startsAt: '2026-10-10T10:00:00Z', missed: true }),
      event({ id: 'abandoned-followup', type: '其他', startsAt: '2026-10-10T12:00:00Z', abandoned: true })
    ], Date.parse('2026-10-11T18:00:00Z'))).toEqual({ days: 1, label: '进展正常' })
  })

  it('uses the scheduled time instead of the later completion time', () => {
    expect(applicationProgressHealth(interviewApplication, [
      event({
        id: 'authorization',
        type: '其他',
        title: '授权书签署',
        startsAt: '2026-10-10 17:20',
        completed: true,
        completedAt: '2026-10-11 00:51'
      })
    ], new Date('2026-10-11T18:00:00').getTime())).toEqual({ days: 1, label: '进展正常' })
  })
})

describe('schedulePriorityTimestamp', () => {
  it('uses the start for a point schedule and the end for a time range', () => {
    const point = event({ startsAt: '2026-10-21T14:00:00+08:00' })
    const range = event({ startsAt: '2026-10-20T14:33:00+08:00', endsAt: '2026-10-22T14:33:00+08:00' })

    expect(schedulePriorityTimestamp(point)).toBe(Date.parse('2026-10-21T06:00:00Z'))
    expect(schedulePriorityTimestamp(range)).toBe(Date.parse('2026-10-22T06:33:00Z'))
    expect(schedulePriorityTimestamp(range)).toBeGreaterThan(schedulePriorityTimestamp(point))
  })

  it('places schedules without a valid time after dated schedules', () => {
    expect(schedulePriorityTimestamp(event({ startsAt: '' }))).toBe(Number.POSITIVE_INFINITY)
  })
})
