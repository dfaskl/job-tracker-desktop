import { computed, ref } from 'vue'
import { api, ApiError, clearApiCache } from './api'

export type User = { id: string; email: string }
export type JobApplication = Record<string, unknown> & {
  id: string; company?: string; position?: string; city?: string; channel?: string
  appliedDate?: string; stage?: string; status?: string; updatedAt?: string
}
export type JobEvent = Record<string, unknown> & {
  id: string; applicationId?: string; title?: string; type?: string; date?: string
  start?: string; end?: string; status?: string; completed?: boolean
}
export type BusinessData = Record<string, unknown> & {
  applications: JobApplication[]; events: JobEvent[]; settings?: Record<string, unknown>
}

const user = ref<User | null>(null)
const data = ref<BusinessData>({ applications: [], events: [] })
const initialized = ref(false)
const loading = ref(false)
const error = ref('')
const readOnly = ref(true)

const applications = computed(() => data.value.applications || [])
const events = computed(() => data.value.events || [])

async function refresh() {
  loading.value = true
  error.value = ''
  try {
    const result = await api<{ user: User; exists: boolean; data: BusinessData | null; readOnly: boolean }>('/api/poc/data')
    user.value = result.user
    data.value = result.data || { applications: [], events: [] }
    readOnly.value = result.readOnly
  } catch (cause) {
    if (cause instanceof ApiError && cause.status === 401) {
      user.value = null
      data.value = { applications: [], events: [] }
      return
    }
    error.value = cause instanceof Error ? cause.message : '读取业务数据失败'
  } finally {
    loading.value = false
    initialized.value = true
  }
}

async function initialize() {
  if (initialized.value || loading.value) return
  await refresh()
}

async function login(email: string, password: string) {
  clearApiCache()
  error.value = ''
  const result = await api<{ user: User; readOnly: boolean }>('/api/poc/auth/login', {
    method: 'POST', body: JSON.stringify({ email, password })
  })
  user.value = result.user
  readOnly.value = result.readOnly
  await refresh()
}

async function register(email: string, password: string, registrationCode: string) {
  clearApiCache()
  error.value = ''
  const result = await api<{ user: User; readOnly: boolean }>('/api/poc/auth/register', {
    method: 'POST', body: JSON.stringify({ email, password, registrationCode })
  })
  user.value = result.user
  readOnly.value = result.readOnly
  await refresh()
}
async function logout() {
  await api('/api/poc/auth/logout', { method: 'POST' })
  clearApiCache()
  user.value = null
  data.value = { applications: [], events: [] }
  error.value = ''
}

export function useJobTrackerStore() {
  return { user, data, applications, events, initialized, loading, error, readOnly, initialize, refresh, login, register, logout }
}
