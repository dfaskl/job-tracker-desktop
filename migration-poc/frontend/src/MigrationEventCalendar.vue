<script setup lang="ts">
import { computed, onActivated, onMounted, reactive, ref } from 'vue'

type SandboxStatus = { enabled: boolean; configured: boolean; isolated: boolean; message: string }
type ApplicationOption = { id: string; company: string; position: string }
type EventItem = {
  id: string
  applicationId: string
  type: string
  title: string
  startsAt: string
  endsAt: string
  location: string
  notes: string
  company: string
  position: string
  completed: boolean
  missed: boolean
  completedAt: string
  createdAt: string
  updatedAt: string
  recordAt: string
}
type EventForm = {
  applicationId: string
  type: string
  title: string
  timeMode: 'point' | 'range'
  startsAt: string
  endsAt: string
  location: string
  notes: string
}
type CalendarEvent = { event: EventItem; position: 'point' | 'start' | 'middle' | 'end' }
type CalendarCell = { key: string; day: number; inMonth: boolean; events: CalendarEvent[] }

const eventTypes = ['测评', '笔试', '面试', 'Offer', '其他']
const sandbox = ref<SandboxStatus | null>(null)
const applications = ref<ApplicationOption[]>([])
const events = ref<EventItem[]>([])
const total = ref(0)
const month = ref(firstOfMonth(new Date()))
const selectedDate = ref(dateKey(new Date()))
const editing = ref<EventItem | null>(null)
const loading = ref(false)
const error = ref('')
const message = ref('')
const lastLoadedAt = ref(0)
const form = reactive<EventForm>(emptyForm())

const monthTitle = computed(() => `${month.value.getFullYear()}年${month.value.getMonth() + 1}月`)
const selectedEvents = computed(() => calendarEventsOn(selectedDate.value))
const cells = computed<CalendarCell[]>(() => {
  const year = month.value.getFullYear()
  const monthIndex = month.value.getMonth()
  const offset = (new Date(year, monthIndex, 1).getDay() + 6) % 7
  const firstCell = new Date(year, monthIndex, 1 - offset)
  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(firstCell)
    date.setDate(firstCell.getDate() + index)
    const key = dateKey(date)
    return { key, day: date.getDate(), inMonth: date.getMonth() === monthIndex, events: calendarEventsOn(key) }
  })
})

onMounted(async () => { sandbox.value = { enabled:true, configured:true, isolated:false, message:'可写数据源' }; await loadEvents() })
onActivated(async () => { if (lastLoadedAt.value && Date.now() - lastLoadedAt.value > 30_000) await loadEvents() })

function pad(value: number) { return String(value).padStart(2, '0') }
function dateKey(date: Date) { return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` }
function firstOfMonth(date: Date) { return new Date(date.getFullYear(), date.getMonth(), 1) }
function tomorrowAtNine() {
  const date = new Date()
  date.setDate(date.getDate() + 1)
  date.setHours(9, 0, 0, 0)
  return `${dateKey(date)}T09:00`
}
function emptyForm(): EventForm {
  return { applicationId: '', type: '面试', title: '', timeMode: 'point', startsAt: tomorrowAtNine(), endsAt: '', location: '', notes: '' }
}
function toInputTime(value: string) { return value ? value.replace(' ', 'T').slice(0, 16) : '' }
function toApiTime(value: string) { return value ? value.replace('T', ' ').slice(0, 16) : '' }
function formatTime(value: string) { return value ? value.replace('T', ' ') : '未设置' }

async function requestJson(url: string, init?: RequestInit) {
  const response = await fetch(url, { cache: 'no-store', ...init })
  const body = await response.json().catch(() => ({}))
  return { response, body }
}

async function checkSandbox() {
  loading.value = true
  error.value = ''
  try {
    const statusResult = await requestJson('/api/poc/event-sandbox/status')
    if (!statusResult.response.ok) throw new Error(statusResult.body.message || '无法检查日程沙箱')
    sandbox.value = statusResult.body as SandboxStatus
    if (sandbox.value.enabled) await loadEvents()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '检查失败'
  } finally {
    loading.value = false
  }
}

async function loadEvents() {
  const result = await requestJson('/api/poc/event-sandbox/events')
  if (!result.response.ok) throw new Error(
    result.response.status === 401 ? '请先在上方登录旧账号，再重新检查日程沙箱' : (result.body.message || '读取日程失败')
  )
  events.value = result.body.events as EventItem[]
  applications.value = result.body.applications as ApplicationOption[]
  total.value = Number(result.body.total || 0)
  if (!form.applicationId && applications.value.length) form.applicationId = applications.value[0].id
  lastLoadedAt.value = Date.now()
}

function datesBetween(start: string, end: string) {
  const result: string[] = []
  const cursor = new Date(`${start}T00:00:00`)
  const last = new Date(`${end}T00:00:00`)
  while (cursor <= last && result.length < 370) {
    result.push(dateKey(cursor))
    cursor.setDate(cursor.getDate() + 1)
  }
  return result
}

function calendarEntries(event: EventItem): Array<{ key: string; position: CalendarEvent['position'] }> {
  if (event.endsAt && !event.completed) {
    const dates = datesBetween(event.startsAt.slice(0, 10), event.endsAt.slice(0, 10))
    return dates.map((key, index) => ({
      key,
      position: dates.length === 1 ? 'point' : index === 0 ? 'start' : index === dates.length - 1 ? 'end' : 'middle'
    }))
  }
  return [{ key: (event.recordAt || event.startsAt).slice(0, 10), position: 'point' }]
}

function calendarEventsOn(key: string): CalendarEvent[] {
  return events.value.flatMap((event) =>
    calendarEntries(event).filter((entry) => entry.key === key).map((entry) => ({ event, position: entry.position }))
  ).sort((left, right) => left.event.recordAt.localeCompare(right.event.recordAt))
}

function changeMonth(offset: number) {
  month.value = new Date(month.value.getFullYear(), month.value.getMonth() + offset, 1)
}
function resetMonth() {
  month.value = firstOfMonth(new Date())
  selectedDate.value = dateKey(new Date())
}
function selectDate(key: string) { selectedDate.value = key }

function edit(item: EventItem) {
  editing.value = item
  Object.assign(form, {
    applicationId: item.applicationId,
    type: item.type,
    title: item.title,
    timeMode: item.endsAt ? 'range' : 'point',
    startsAt: toInputTime(item.startsAt),
    endsAt: toInputTime(item.endsAt),
    location: item.location,
    notes: item.notes
  })
  message.value = ''
  error.value = ''
}

function resetForm() {
  editing.value = null
  const fresh = emptyForm()
  fresh.applicationId = applications.value[0]?.id || ''
  Object.assign(form, fresh)
}

async function save() {
  loading.value = true
  error.value = ''
  message.value = ''
  try {
    const current = editing.value
    const payload = {
      applicationId: form.applicationId,
      type: form.type,
      title: form.title,
      startsAt: toApiTime(form.startsAt),
      endsAt: form.timeMode === 'range' ? toApiTime(form.endsAt) : '',
      location: form.location,
      notes: form.notes,
      expectedUpdatedAt: current?.updatedAt || ''
    }
    const result = await requestJson(
      current ? `/api/poc/event-sandbox/events/${encodeURIComponent(current.id)}` : '/api/poc/event-sandbox/events',
      { method: current ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }
    )
    if (!result.response.ok) throw new Error(result.body.message || '保存日程失败')
    message.value = current ? '日程已更新，并已生成变更前备份' : '日程已新增，并已同步岗位进度'
    resetForm()
    await loadEvents()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '保存日程失败'
  } finally {
    loading.value = false
  }
}

async function resolve(item: EventItem, action: 'complete' | 'miss' | 'restore') {
  loading.value = true
  error.value = ''
  message.value = ''
  try {
    const result = await requestJson(`/api/poc/event-sandbox/events/${encodeURIComponent(item.id)}/resolution`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action, expectedUpdatedAt: item.updatedAt })
    })
    if (!result.response.ok) throw new Error(result.body.message || '更新日程状态失败')
    message.value = action === 'complete' ? '日程已完成' : action === 'miss' ? '日程已标记错过' : '日程已恢复为待完成'
    await loadEvents()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '更新日程状态失败'
  } finally {
    loading.value = false
  }
}

async function remove(item: EventItem) {
  if (!window.confirm(`确认从测试库删除“${item.company} / ${item.title || item.type}”日程吗？`)) return
  loading.value = true
  error.value = ''
  message.value = ''
  try {
    const result = await requestJson(`/api/poc/event-sandbox/events/${encodeURIComponent(item.id)}`, {
      method: 'DELETE', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ expectedUpdatedAt: item.updatedAt })
    })
    if (!result.response.ok) throw new Error(result.body.message || '删除日程失败')
    message.value = '日程及对应岗位历史已删除'
    if (editing.value?.id === item.id) resetForm()
    await loadEvents()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '删除日程失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="card event-sandbox">
    <div class="section-head">
      <div><span class="section-kicker">CALENDAR</span><h2>日历</h2></div>
    </div>

    <div v-if="sandbox && !sandbox.enabled" class="notice">
      <strong>{{ sandbox.message }}</strong><span>日程写入与职位 CRUD 共用同一套隔离测试库开关。</span>
    </div>

    <template v-else-if="sandbox?.enabled">
<p v-if="message" class="success">{{ message }}</p>
      <div class="calendar-head">
        <div><strong>{{ monthTitle }}</strong><span>共 {{ total }} 项日程</span></div>
        <div><button class="secondary compact" @click="changeMonth(-1)">‹</button><button class="secondary compact" @click="resetMonth">本月</button><button class="secondary compact" @click="changeMonth(1)">›</button></div>
      </div>
      <div class="weekdays"><b v-for="day in ['一','二','三','四','五','六','日']" :key="day">周{{ day }}</b></div>
      <div class="calendar-grid">
        <button v-for="cell in cells" :key="cell.key" type="button" :class="['day', { outside: !cell.inMonth, selected: cell.key === selectedDate, today: cell.key === dateKey(new Date()) }]" @click="selectDate(cell.key)">
          <span>{{ cell.day }}</span>
          <small v-for="entry in cell.events.slice(0, 3)" :key="entry.event.id + entry.position" :class="['event-chip', entry.position, `type-${entry.event.type}`]">
            {{ entry.position === 'middle' ? '' : entry.position === 'start' ? `开始 ${entry.event.title}` : entry.position === 'end' ? `截止 ${entry.event.title}` : entry.event.title }}
          </small>
          <i v-if="cell.events.length > 3">+{{ cell.events.length - 3 }}</i>
        </button>
      </div>

      <div class="selected-list">
        <h3>{{ selectedDate }} 的日程</h3>
        <article v-for="entry in selectedEvents" :key="entry.event.id">
          <div class="event-main">
            <strong>{{ entry.event.company }} · {{ entry.event.title || entry.event.type }}</strong>
            <span>{{ entry.event.position }} · {{ entry.event.type }}</span>
            <span>{{ entry.event.endsAt && !entry.event.completed ? `${formatTime(entry.event.startsAt)} 至 ${formatTime(entry.event.endsAt)}` : formatTime(entry.event.recordAt) }}</span>
            <span v-if="entry.event.location">{{ entry.event.location }}</span>
          </div>
          <div class="event-actions">
            <b :class="{ missed: entry.event.missed, done: entry.event.completed && !entry.event.missed }">{{ entry.event.missed ? '已错过' : entry.event.completed ? '已完成' : '待完成' }}</b>
            
            <button class="secondary compact" @click="edit(entry.event)">编辑</button>
            <button v-if="entry.event.completed" class="secondary compact" @click="resolve(entry.event, 'restore')">恢复</button>
            <template v-else><button class="success-button compact" @click="resolve(entry.event, 'complete')">完成</button><button class="warning-button compact" @click="resolve(entry.event, 'miss')">错过</button></template>
            <button class="danger-button compact" @click="remove(entry.event)">删除</button>
          </div>
        </article>
        <div v-if="!selectedEvents.length" class="empty">当天没有日程</div>
      </div>

      <div v-if="editing" class="edit-backdrop" @click.self="resetForm">
        <form class="event-form edit-modal" @submit.prevent="save">
          <button type="button" class="modal-close" @click="resetForm">×</button>
          <h3 class="wide">编辑日程</h3>
          <label class="wide"><span>关联岗位</span><select v-model="form.applicationId" disabled><option v-for="item in applications" :key="item.id" :value="item.id">{{item.company}} · {{item.position}}</option></select></label>
          <label><span>类型</span><select v-model="form.type"><option v-for="item in eventTypes" :key="item">{{item}}</option></select></label>
          <label><span>安排名称 *</span><input v-model="form.title" maxlength="200" required /></label>
          <label><span>时间类型</span><select v-model="form.timeMode"><option value="point">时间点</option><option value="range">时间段</option></select></label>
          <label><span>开始时间 *</span><input v-model="form.startsAt" type="datetime-local" required /></label>
          <label v-if="form.timeMode==='range'"><span>结束时间 *</span><input v-model="form.endsAt" type="datetime-local" required /></label>
          <label><span>地点 / 会议方式</span><input v-model="form.location" maxlength="1000" /></label>
          <label class="wide"><span>备注</span><textarea v-model="form.notes" maxlength="4000" rows="3" /></label>
          <div class="form-actions wide"><button :disabled="loading">保存修改</button><button type="button" class="secondary" @click="resetForm">取消</button></div>
        </form>
      </div>
    </template>

    <p v-else>正在检查日程沙箱…</p>
    <p v-if="error" class="danger">{{ error }}</p>
  </section>
</template>

<style scoped>
.edit-backdrop{position:fixed;inset:0;z-index:40;display:grid;place-items:center;padding:20px;background:rgba(17,24,39,.58)}.edit-modal{position:relative;width:min(720px,100%);max-height:90vh;overflow:auto;margin:0;padding:24px;border-radius:16px;background:#fff}.edit-modal h3{margin:0}.modal-close{position:absolute;top:10px;right:10px;padding:4px 10px;color:#475467;background:#eef2f8;font-size:20px}.section-head, .calendar-head, .selected-list article, .form-actions, .event-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.section-kicker { display: block; margin-bottom: 5px; color: #4461d8; font-size: 12px; font-weight: 800; letter-spacing: .08em; }
.section-head h2 { margin-bottom: 0; }
.mode-badge { padding: 7px 10px; border-radius: 999px; font-size: 12px; font-weight: 800; white-space: nowrap; }
.mode-badge.enabled { color: #167647; background: #e9f8ef; }
.mode-badge.disabled { color: #7a4d0b; background: #fff3d6; }
.notice, .empty { display: grid; gap: 7px; padding: 18px; border: 1px solid #dbe3f1; border-radius: 12px; background: #f7f9fc; }
.notice span { color: #667085; }
.event-form { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; margin: 22px 0; }
.event-form label { display: grid; gap: 7px; color: #475467; font-size: 13px; font-weight: 700; }
.event-form .wide { grid-column: 1 / -1; }
select, textarea { width: 100%; padding: 12px 14px; border: 1px solid #d4dbea; border-radius: 10px; background: #fff; font: inherit; }
.form-actions { justify-content: flex-start; }
.calendar-head { margin: 24px 0 12px; padding-top: 18px; border-top: 1px solid #edf0f5; }
.calendar-head > div { display: flex; align-items: center; gap: 10px; }
.calendar-head span { color: #667085; font-size: 13px; }
.weekdays, .calendar-grid { display: grid; grid-template-columns: repeat(7, 1fr); }
.weekdays b { padding: 8px; color: #667085; font-size: 12px; text-align: center; }
.day { min-height: 92px; padding: 7px; border: 1px solid #e5e9f1; border-radius: 0; color: #344054; background: #fff; text-align: left; }
.day.outside { color: #b3bac7; background: #f8fafc; }
.day.selected { position: relative; z-index: 1; outline: 2px solid #4461d8; }
.day.today > span { display: inline-grid; width: 24px; height: 24px; border-radius: 50%; color: #fff; background: #4461d8; place-items: center; }
.day > small { --event-color:#4357ad; --event-bg:#edf1ff; display:block; overflow:hidden; margin-top:4px; padding:3px 5px; border-radius:5px; color:var(--event-color); background:var(--event-bg); font-size:10px; text-overflow:ellipsis; white-space:nowrap; }
.day > small.type-测评 { --event-color:#71602f; --event-bg:#f7edc7; }
.day > small.type-笔试 { --event-color:#8a5a16; --event-bg:#f8e7ca; }
.day > small.type-面试 { --event-color:#27656b; --event-bg:#dceff0; }
.day > small.type-Offer { --event-color:#1f7448; --event-bg:#dff3e8; }
.day > small.type-其他 { --event-color:#596579; --event-bg:#e8edf3; }
.day > small.middle { height:3px; margin:7px -7px 0; padding:0; border-radius:0; color:transparent; background:var(--event-color); opacity:.55; }
.day > small.start { border-radius:5px 2px 2px 5px; }
.day > small.end { border-radius:2px 5px 5px 2px; }
.day > i { color: #667085; font-size: 10px; }
.selected-list { margin-top: 20px; }
.selected-list h3 { font-size: 16px; }
.selected-list article { align-items: flex-start; padding: 14px 0; border-top: 1px solid #edf0f5; }
.event-main { display: grid; gap: 4px; }
.event-main span { color: #667085; font-size: 13px; }
.event-actions { justify-content: flex-end; flex-wrap: wrap; }
.event-actions b { color: #7a4d0b; font-size: 12px; }
.event-actions b.done { color: #167647; }
.event-actions b.missed { color: #ad2f2f; }
.secondary { color: #344054; background: #eef2f8; }
.success-button { color: #167647; background: #e9f8ef; }
.warning-button { color: #8a5608; background: #fff1cf; }
.danger-button { color: #a52d2d; background: #fceaea; }
.compact { padding: 8px 11px; }

@media (max-width: 720px) {
  .event-form { grid-template-columns: 1fr; }
  .event-form .wide { grid-column: auto; }
  .day { min-height: 68px; padding: 4px; }
  .day > small { font-size: 0; height: 5px; padding: 0; }
  .selected-list article { flex-direction: column; }
  .event-actions { justify-content: flex-start; }
}
</style>
