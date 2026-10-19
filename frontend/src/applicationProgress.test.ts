import { describe, expect, it } from 'vitest'
import { applicationProgressHealth } from './applicationProgress'
import type { JobApplication, JobEvent } from './jobTrackerStore'

const now = Date.parse('2026-10-11T00:00:00Z')
const interviewApplication: JobApplication = { id: 'app-1', stage: '面试', status: '等待结果' }

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
