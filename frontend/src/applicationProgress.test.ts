import { describe, expect, it } from 'vitest'
import { applicationProgressHealth } from './applicationProgress'
import type { JobApplication, JobEvent } from './jobTrackerStore'

const now = Date.parse('2026-10-11T00:00:00Z')
const interviewApplication: JobApplication = { id: 'app-1', stage: '面试', status: '等待结果' }

function event(overrides: Partial<JobEvent>): JobEvent {
  return { id: 'event-1', applicationId: 'app-1', type: '面试', startsAt: '2026-09-28T10:00:00Z', ...overrides }
}

describe('applicationProgressHealth', () => {
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

  it('does not count missed or abandoned schedules as progress', () => {
    expect(applicationProgressHealth(interviewApplication, [
      event({ startsAt: '2026-10-10T10:00:00Z', missed: true }),
      event({ id: 'abandoned', startsAt: '2026-10-10T10:00:00Z', abandoned: true })
    ], now)).toBeNull()
  })
})
