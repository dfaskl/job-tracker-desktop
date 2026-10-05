import { describe, expect, it } from 'vitest'
import { isSupportedRoute, pageFromRoute, readHashRoute, resolveRequestedRoute } from './routes'

describe('application routes', () => {
  it('reads hash routes with or without the slash form', () => {
    expect(readHashRoute('#/guide')).toBe('guide')
    expect(readHashRoute('#guide')).toBe('guide')
    expect(readHashRoute('')).toBe('home')
  })

  it('keeps the guide route when checking supported pages', () => {
    expect(isSupportedRoute('guide')).toBe(true)
    expect(isSupportedRoute('guide?page=2')).toBe(true)
    expect(pageFromRoute('guide?page=2')).toBe('guide')
  })

  it('preserves a guide request through the authentication route', () => {
    expect(resolveRequestedRoute('#/login', 'guide')).toBe('guide')
  })

  it('falls back only for unknown pages', () => {
    expect(resolveRequestedRoute('#/guide', '')).toBe('guide')
    expect(resolveRequestedRoute('#/unknown', '')).toBe('home')
  })
})
