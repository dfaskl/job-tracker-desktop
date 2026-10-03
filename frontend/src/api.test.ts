import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { apiCached, clearApiCache, invalidateApiCache } from './api'

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(done => { resolve = done })
  return { promise, resolve }
}

function json(value: unknown) {
  return new Response(JSON.stringify(value), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

beforeEach(clearApiCache)
afterEach(() => { clearApiCache(); vi.unstubAllGlobals() })

test('a company link saved during an older read stays fresh', async () => {
  const oldRead = deferred<Response>()
  const newRead = deferred<Response>()
  const fetchMock = vi.fn().mockReturnValueOnce(oldRead.promise).mockReturnValueOnce(newRead.promise)
  vi.stubGlobal('fetch', fetchMock)

  const first = apiCached<{ items: string[] }>('/api/poc/company-links')
  invalidateApiCache('/api/poc/company-links')
  const second = apiCached<{ items: string[] }>('/api/poc/company-links')
  newRead.resolve(json({ items: ['new'] }))
  await expect(second).resolves.toEqual({ items: ['new'] })
  oldRead.resolve(json({ items: ['old'] }))
  await expect(first).resolves.toEqual({ items: ['old'] })

  await expect(apiCached('/api/poc/company-links')).resolves.toEqual({ items: ['new'] })
  expect(fetchMock).toHaveBeenCalledTimes(2)
})

test('a request from a prior login cannot refill the cleared cache', async () => {
  const oldRead = deferred<Response>()
  const fetchMock = vi.fn().mockReturnValueOnce(oldRead.promise).mockResolvedValueOnce(json({ items: ['current'] }))
  vi.stubGlobal('fetch', fetchMock)

  const prior = apiCached('/api/poc/company-links')
  clearApiCache()
  await expect(apiCached('/api/poc/company-links')).resolves.toEqual({ items: ['current'] })
  oldRead.resolve(json({ items: ['prior'] }))
  await prior

  await expect(apiCached('/api/poc/company-links')).resolves.toEqual({ items: ['current'] })
  expect(fetchMock).toHaveBeenCalledTimes(2)
})
