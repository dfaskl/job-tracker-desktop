<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useJobTrackerStore, type JobEvent } from './jobTrackerStore'

const store = useJobTrackerStore()
const cursor = ref(new Date(new Date().getFullYear(), new Date().getMonth(), 1))
const selectedDate = ref(key(new Date()))
const monthTitle = computed(() => `${cursor.value.getFullYear()}年${cursor.value.getMonth() + 1}月`)
const normalized = computed(() => store.events.value.map(event => ({ ...event, startsAt: startOf(event), endsAt: endOf(event) })))
type CalendarEntry = { event: JobEvent; position: 'point' | 'start' | 'middle' | 'end'; lane: number; color: number }
const eventColors = [
  ['#356fc0','#e3efff'], ['#7b58bd','#eee7fb'], ['#c47819','#fff0d5'], ['#198176','#ddf4f0'],
  ['#c34f5a','#fde6e9'], ['#347f9d','#e0f1f7'], ['#66752e','#edf2d8'], ['#a04f91','#f7e4f3']
] as const
const eventLanes = computed(() => allocateEventLanes(normalized.value))
const eventColorAssignments = computed(() => allocateEventColors(normalized.value))
const selectedEvents = computed(() => normalized.value.filter(event => datesFor(event).includes(selectedDate.value)).sort(compareEvents))
const overdue = computed(() => normalized.value.filter(event => !Boolean(event.completed) && startOf(event) && endOf(event) < nowText()).sort(compareEvents))
const applicationBounds = computed(() => {
  const months=[...store.applications.value.map(item => String(item.appliedDate || item.createdAt || '').slice(0,7)),...normalized.value.flatMap(event=>datesFor(event).map(date=>date.slice(0,7)))].filter(value => /^\d{4}-\d{2}$/.test(value)).sort()
  const fallback=firstOfMonth(new Date())
  if(!months.length)return {first:fallback,last:fallback}
  const parse=(value:string)=>new Date(Number(value.slice(0,4)),Number(value.slice(5,7))-1,1)
  return {first:parse(months[0]),last:parse(months[months.length-1])}
})
const canGoPrevious=computed(()=>monthValue(cursor.value)>monthValue(applicationBounds.value.first))
const canGoNext=computed(()=>monthValue(cursor.value)<monthValue(applicationBounds.value.last))
watch(applicationBounds,bounds=>{cursor.value=clampMonth(cursor.value,bounds)},{immediate:true})
const cells = computed(() => {
  const first = cursor.value
  const offset = (first.getDay() + 6) % 7
  const start = new Date(first.getFullYear(), first.getMonth(), 1 - offset)
  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(start); date.setDate(start.getDate() + index)
    const dateKey = key(date)
    return { key: dateKey, day: date.getDate(), current: date.getMonth() === first.getMonth(), events: calendarEventsOn(dateKey) }
  })
})

function pad(value: number) { return String(value).padStart(2, '0') }
function firstOfMonth(date: Date) { return new Date(date.getFullYear(),date.getMonth(),1) }
function monthValue(date: Date) { return date.getFullYear()*12+date.getMonth() }
function clampMonth(date: Date,bounds=applicationBounds.value) { const value=monthValue(date); return value<monthValue(bounds.first)?bounds.first:value>monthValue(bounds.last)?bounds.last:date }
function key(date: Date) { return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` }
function nowText() { return new Date().toISOString().slice(0, 16).replace('T', ' ') }
function value(event: JobEvent, ...names: string[]) { for (const name of names) { const found = event[name]; if (found) return String(found) } return '' }
function startOf(event: JobEvent) { return value(event, 'startsAt', 'start', 'date', 'at').replace('T', ' ') }
function endOf(event: JobEvent) { return value(event, 'endsAt', 'end')?.replace('T', ' ') || startOf(event) }
function compareEvents(left: JobEvent, right: JobEvent) { return startOf(left).localeCompare(startOf(right)) }
function displayAt(event: JobEvent) { return value(event, 'completedAt', 'recordAt') || startOf(event) }
function locationLink(text: string) { return String(text || '').match(/https?:\/\/[^\s]+/i)?.[0] || '' }
function locationText(text: string) { const link=locationLink(text); return String(text || '').replace(link,'').replace(/^[\s·,，;；:：-]+|[\s·,，;；:：-]+$/g,'') }
function datesFor(event: JobEvent) {
  if (Boolean(event.completed)) return [(value(event, 'completedAt') || startOf(event)).slice(0, 10)]
  const start = startOf(event).slice(0, 10); const end = endOf(event).slice(0, 10)
  if (!start) return []
  const result: string[] = []; const date = new Date(`${start}T00:00:00`); const last = new Date(`${end || start}T00:00:00`)
  while (date <= last && result.length < 370) { result.push(key(date)); date.setDate(date.getDate() + 1) }
  return result
}
function calendarPosition(event: JobEvent, dateKey: string): CalendarEntry['position'] {
  const dates = datesFor(event)
  if (dates.length <= 1) return 'point'
  if (dateKey === dates[0]) return 'start'
  if (dateKey === dates[dates.length - 1]) return 'end'
  return 'middle'
}
function allocateEventLanes(events: JobEvent[]) {
  const occupied: Array<Set<string>> = []
  const lanes = new Map<string, number>()
  const sorted = [...events].sort((left, right) => (datesFor(left)[0] || '').localeCompare(datesFor(right)[0] || '') || compareEvents(left, right) || String(left.id).localeCompare(String(right.id)))
  for (const event of sorted) {
    const dates = datesFor(event)
    if (!dates.length) continue
    let lane = occupied.findIndex(taken => dates.every(date => !taken.has(date)))
    if (lane < 0) { lane = occupied.length; occupied.push(new Set()) }
    dates.forEach(date => occupied[lane].add(date))
    lanes.set(String(event.id), lane)
  }
  return lanes
}
function stableColor(event: JobEvent) {
  let hash = 0
  for (const character of String(event.id || `${eventCompany(event)}-${eventName(event)}-${startOf(event)}`)) hash = ((hash << 5) - hash + character.charCodeAt(0)) | 0
  return Math.abs(hash) % eventColors.length
}
function allocateEventColors(events: JobEvent[]) {
  const result = new Map<string, number>()
  const dateColors = new Map<string, Set<number>>()
  const ordered = [...events].sort((left, right) => (datesFor(left)[0] || '').localeCompare(datesFor(right)[0] || '') || String(left.id).localeCompare(String(right.id)))
  for (const event of ordered) {
    const dates = datesFor(event)
    const used = new Set(dates.flatMap(date => [...(dateColors.get(date) || [])]))
    const preferred = stableColor(event)
    const color = [preferred, ...eventColors.map((_, index) => index)].find(index => !used.has(index)) ?? preferred
    result.set(String(event.id), color)
    dates.forEach(date => { if (!dateColors.has(date)) dateColors.set(date, new Set()); dateColors.get(date)!.add(color) })
  }
  return result
}function calendarEventsOn(dateKey: string): CalendarEntry[] {
  return normalized.value.filter(event => datesFor(event).includes(dateKey)).map(event => ({ event, position: calendarPosition(event, dateKey), lane: eventLanes.value.get(String(event.id)) || 0, color: eventColorAssignments.value.get(String(event.id)) ?? stableColor(event) }))
}
function eventStyle(entry: CalendarEntry) { const color = eventColors[entry.color]; return { '--event-color':color[0], '--event-bg':color[1], gridRow:String(entry.lane+1) } }
function visibleCalendarEvents(events: CalendarEntry[]) { return events.filter(entry => entry.lane < 3) }
function hiddenCalendarEventCount(events: CalendarEntry[]) { return events.filter(entry => entry.lane >= 3).length }
function move(offset: number) { cursor.value = clampMonth(new Date(cursor.value.getFullYear(), cursor.value.getMonth() + offset, 1)) }
function eventName(event: JobEvent) { return value(event, 'title', 'type') || '未命名安排' }
function eventCompany(event: JobEvent) {
  return value(event, 'company') || String(store.applications.value.find(item => item.id === event.applicationId)?.company || '未关联公司')
}
</script>

<template>
  <section v-if="store.user.value" class="calendar-overview">
    <div v-if="overdue.length" class="overdue-alert"><strong>{{ overdue.length }} 项日程已过期</strong><span>请在启用隔离写入后标记为完成或错过。</span></div>
    <div class="calendar-layout">
      <section class="card month-card">
        <div class="calendar-head"><button class="secondary" :disabled="!canGoPrevious" @click="move(-1)">‹</button><strong>{{ monthTitle }}</strong><button class="secondary" :disabled="!canGoNext" @click="move(1)">›</button></div>
        <div class="weekdays"><b v-for="day in ['一','二','三','四','五','六','日']" :key="day">{{ day }}</b></div>
        <div class="month-grid">
          <button v-for="cell in cells" :key="cell.key" :class="['day',{muted:!cell.current,selected:cell.key===selectedDate,today:cell.key===key(new Date())}]" @click="selectedDate=cell.key">
            <span class="day-number">{{ cell.day }}</span><span class="day-events"><i v-for="entry in visibleCalendarEvents(cell.events)" :key="entry.event.id + '-' + entry.position" :class="[entry.position,{ completed:Boolean(entry.event.completed) }]" :style="eventStyle(entry)"><template v-if="entry.position==='start'">开始 {{ eventCompany(entry.event) }} · {{ eventName(entry.event) }}</template><template v-else-if="entry.position==='end'">截止 {{ eventCompany(entry.event) }} · {{ eventName(entry.event) }}</template><template v-else-if="entry.position==='point'">{{ eventCompany(entry.event) }} · {{ eventName(entry.event) }}</template></i></span><small v-if="hiddenCalendarEventCount(cell.events)">+{{ hiddenCalendarEventCount(cell.events) }}</small>
          </button>
        </div>
      </section>
      <aside class="card selected-card"><h2>{{ selectedDate }}</h2><article v-for="event in selectedEvents" :key="event.id" :class="{ completed:Boolean(event.completed) }"><strong>{{ eventCompany(event) }} · {{ eventName(event) }}</strong><span><template v-if="event.completed">{{ displayAt(event) }}</template><template v-else>{{ startOf(event) }}<template v-if="endOf(event)!==startOf(event)"> 至 {{ endOf(event) }}</template></template></span><span v-if="value(event,'location')" class="event-location"><span v-if="locationText(value(event,'location'))">{{ locationText(value(event,'location')) }}</span><a v-if="locationLink(value(event,'location'))" :href="locationLink(value(event,'location'))" target="_blank" rel="noopener noreferrer">打开链接 ↗</a></span><span v-else>{{ value(event,'notes') || (event.completed ? '已完成' : '待处理') }}</span></article><p v-if="!selectedEvents.length">当天没有安排。</p></aside>
    </div>
  </section>
  <section v-else class="card"><h2>请先登录</h2><p>登录后查看完整日历和历史安排。</p></section>
</template>

<style scoped>
.overdue-alert{display:flex;justify-content:space-between;gap:12px;margin-top:22px;padding:14px 18px;border:1px solid #f4c7c7;border-radius:12px;color:#982d2d;background:#fff1f1}.calendar-layout{display:grid;grid-template-columns:minmax(0,2fr) minmax(260px,1fr);gap:18px}.calendar-head{display:flex;align-items:center;justify-content:space-between}.weekdays,.month-grid{display:grid;grid-template-columns:repeat(7,1fr)}.weekdays b{padding:12px 4px;color:#667085;font-size:12px;text-align:center}.day{min-height:88px;padding:7px;border:1px solid #e4e9f2;border-radius:0;color:#344054;background:#fff;text-align:left}.day.muted{color:#b3bac7;background:#f8fafc}.day.selected{position:relative;z-index:1;outline:2px solid #526ddd}.day.today>.day-number{display:grid;width:23px;height:23px;place-items:center;border-radius:50%;color:#fff;background:#526ddd}.day-events{display:grid;grid-template-columns:minmax(0,1fr);grid-template-rows:repeat(3,23px);align-content:start}.day-events>i{display:block;align-self:center;overflow:hidden;padding:4px 6px;border-radius:5px;color:var(--event-color);background:var(--event-bg);font-size:11px;line-height:14px;font-style:normal;text-overflow:ellipsis;white-space:nowrap}.day-events>i.start{margin-right:-7px;border-radius:4px 0 0 4px}.day-events>i.middle{height:4px;margin:0 -7px;padding:0;border-radius:0;color:transparent;background:var(--event-color);opacity:.65}.day-events>i.end{margin-left:-7px;border-radius:0 4px 4px 0}.day-events>i.completed{color:#747b86;background:#e5e7eb;text-decoration:line-through}.day small{color:#667085}.selected-card h2{margin-top:0}.selected-card article{display:grid;gap:5px;padding:13px 0;border-top:1px solid #edf0f5}.selected-card span{color:#667085;font-size:12px}.selected-card article.completed{color:#747b86;background:#f3f4f6}.event-location{display:flex;align-items:center;gap:7px;flex-wrap:wrap}.event-location a{padding:3px 8px;border:1px solid #cbd7eb;border-radius:999px;color:#315ca8;background:#f3f7ff;font-weight:700;text-decoration:none}.upcoming article{display:grid;grid-template-columns:150px 1fr;gap:16px;padding:13px 0;border-top:1px solid #edf0f5}.upcoming div{display:grid;gap:4px}.upcoming time{color:#526ddd;font-weight:700}@media(min-width:851px) and (min-height:620px){.calendar-overview{height:calc(100vh - 112px);min-height:0;overflow:hidden}.calendar-overview>.calendar-layout{height:100%;min-height:0}.calendar-layout>.card{height:calc(100% - 22px);min-height:0;overflow:hidden}.month-card{display:flex;flex-direction:column}.month-card>.calendar-head,.month-card>.weekdays{flex:0 0 auto}.month-card>.month-grid{min-height:0;flex:1;grid-template-rows:repeat(6,minmax(0,1fr))}.month-card .day{position:relative;min-height:0;padding:34px 7px 7px}.month-card .day-number{position:absolute;top:7px;left:7px;display:grid;width:23px;height:23px;place-items:center}.selected-card{overflow-y:auto;overscroll-behavior:contain;scrollbar-width:thin;scrollbar-color:#b9c5d5 transparent}.selected-card>h2{position:sticky;top:0;z-index:2;padding-bottom:12px;background:#fff}}@media(max-width:850px){.calendar-layout{grid-template-columns:1fr}.day{min-height:62px}.day i{height:5px;padding:0;font-size:0}.upcoming article{grid-template-columns:1fr}.overdue-alert{flex-direction:column}}
</style>
