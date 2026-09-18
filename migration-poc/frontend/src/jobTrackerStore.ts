import { computed, ref } from 'vue'
import { api, ApiError, clearApiCache } from './api'

export type User = { id: string; email: string }
export type JobApplication = Record<string, unknown> & {
  id: string; company?: string; position?: string; city?: string; channel?: string
  appliedDate?: string; stage?: string; status?: string; updatedAt?: string
}
export type JobEvent = Record<string, unknown> & {
  id: string; applicationId?: string; title?: string; type?: string; date?: string
  start?: string; end?: string; status?: string; completed?: boolean; missed?: boolean; abandoned?: boolean
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
export type MailAccount = { id: number; email: string; provider: string; lastSyncedAt: string; lastError: string }
export type CollectedMail = { id: number; sender: string; subject: string; body: string; receivedAt: string; accountEmail: string }
export type MailInbox = { accounts: MailAccount[]; messages: CollectedMail[]; pendingCount: number }
const mailInbox = ref<MailInbox>({ accounts: [], messages: [], pendingCount: 0 })
const pendingMailCount = computed(() => mailInbox.value.pendingCount)
const newApplicationRequest = ref(0)
const applicationDetailRequest = ref({ applicationId: '', sequence: 0 })
let refreshPromise: Promise<void> | null = null
let mailInboxPromise: Promise<void> | null = null

const applications = computed(() => data.value.applications || [])
const events = computed(() => data.value.events || [])

function refresh(throwOnError = false) {
  if (refreshPromise) return refreshPromise
  loading.value = true
  error.value = ''
  refreshPromise = (async () => {
    try {
      const result = await api<{ user: User; exists: boolean; data: BusinessData | null; readOnly: boolean }>('/api/poc/data')
      user.value = result.user
      data.value = result.data || { applications: [], events: [] }
      readOnly.value = result.readOnly
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) {
        user.value = null
        data.value = { applications: [], events: [] }
        if (throwOnError) throw cause
        return
      }
      error.value = cause instanceof Error ? cause.message : '读取业务数据失败'
      if (throwOnError) throw cause
    } finally {
      loading.value = false
      initialized.value = true
      refreshPromise = null
    }
  })()
  return refreshPromise
}

async function initialize() {
  if (initialized.value || loading.value) return
  await refresh()
}

function refreshMailInbox(sync = false, blockPage = sync) {
  if (mailInboxPromise) return mailInboxPromise
  mailInboxPromise = (async () => {
    try {
      const result = await api<MailInbox>(sync ? '/api/poc/mail-inbox/sync' : '/api/poc/mail-inbox', sync ? { method: 'POST', blockPage } : {})
      mailInbox.value = { accounts: result.accounts || [], messages: result.messages || [], pendingCount: Math.max(0, Number(result.pendingCount) || 0) }
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) mailInbox.value = { accounts: [], messages: [], pendingCount: 0 }
    } finally {
      mailInboxPromise = null
    }
  })()
  return mailInboxPromise
}

async function login(email: string, password: string) {
  clearApiCache()
  error.value = ''
  await api<{ user: User; readOnly: boolean }>('/api/poc/auth/login', {
    method: 'POST', body: JSON.stringify({ email, password })
  })
  await refresh(true)
  await refreshMailInbox()
}

async function register(email: string, password: string, registrationCode: string) {
  clearApiCache()
  error.value = ''
  await api<{ user: User; readOnly: boolean }>('/api/poc/auth/register', {
    method: 'POST', body: JSON.stringify({ email, password, registrationCode })
  })
  await refresh(true)
  await refreshMailInbox()
}
function requestNewApplication() { newApplicationRequest.value += 1 }
function requestApplicationDetail(applicationId: string) {
  const normalizedId = String(applicationId || '').trim()
  if (!normalizedId) return
  applicationDetailRequest.value = { applicationId: normalizedId, sequence: applicationDetailRequest.value.sequence + 1 }
}

async function logout() {
  await api('/api/poc/auth/logout', { method: 'POST' })
  clearApiCache()
  user.value = null
  data.value = { applications: [], events: [] }
  mailInbox.value = { accounts: [], messages: [], pendingCount: 0 }
  applicationDetailRequest.value = { applicationId: '', sequence: applicationDetailRequest.value.sequence + 1 }
  error.value = ''
}

export function useJobTrackerStore() {
  return { user, data, applications, events, initialized, loading, error, readOnly, mailInbox, pendingMailCount, refreshMailInbox, newApplicationRequest, requestNewApplication, applicationDetailRequest, requestApplicationDetail, initialize, refresh, login, register, logout }
}
