import { afterEach, expect, test, vi } from 'vitest'
import { useJobTrackerStore } from './jobTrackerStore'

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>(done => { resolve = done })
  return { promise, resolve }
}

function inbox(pendingCount: number) {
  return new Response(JSON.stringify({ accounts: [], messages: [], pendingCount }), {
    status: 200, headers: { 'Content-Type': 'application/json' }
  })
}

afterEach(() => { vi.unstubAllGlobals() })

test('manual mail sync sends POST during a background read and keeps the newer result', async () => {
  const read = deferred<Response>()
  const sync = deferred<Response>()
  const calls: string[] = []
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    calls.push(`${init?.method || 'GET'} ${String(input)}`)
    return init?.method === 'POST' ? sync.promise : read.promise
  }))
  const store = useJobTrackerStore()
  store.mailInbox.value = { accounts: [], messages: [], pendingCount: 0 }

  const background = store.refreshMailInbox()
  const manual = store.refreshMailInbox(true, true)
  expect(calls).toEqual(['GET /api/poc/mail-inbox', 'POST /api/poc/mail-inbox/sync'])
  sync.resolve(inbox(2))
  await manual
  read.resolve(inbox(1))
  await background
  expect(store.mailInbox.value.pendingCount).toBe(2)
})

test('manual mail sync reports service failures to the caller', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: '邮箱服务不可用' }), { status: 503 })))
  await expect(useJobTrackerStore().refreshMailInbox(true, true)).rejects.toThrow('邮箱服务不可用')
})

test('mail from a signed-out account cannot return after logout', async () => {
  const read = deferred<Response>()
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) =>
    String(input).endsWith('/logout') ? Promise.resolve(new Response(JSON.stringify({ ok: true }))) : read.promise
  ))
  const store = useJobTrackerStore()
  store.user.value = { id: '1', email: 'old@example.com' }
  const background = store.refreshMailInbox()
  await store.logout()
  read.resolve(inbox(3))
  await background
  expect(store.user.value).toBeNull()
  expect(store.mailInbox.value.pendingCount).toBe(0)
})

test('business data from a signed-out account cannot restore the old session', async () => {
  const read = deferred<Response>()
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) =>
    String(input).endsWith('/logout') ? Promise.resolve(new Response(JSON.stringify({ ok: true }))) : read.promise
  ))
  const store = useJobTrackerStore()
  store.user.value = { id: '1', email: 'old@example.com' }
  const background = store.refresh()
  await store.logout()
  read.resolve(new Response(JSON.stringify({ user: { id: '1', email: 'old@example.com' }, data: { applications: [], events: [] }, readOnly: false })))
  await background
  expect(store.user.value).toBeNull()
})
