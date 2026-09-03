<script setup lang="ts">
import { computed, ref } from 'vue'
import { useJobTrackerStore, type JobEvent } from './jobTrackerStore'

const store = useJobTrackerStore()
const cursor = ref(new Date(new Date().getFullYear(), new Date().getMonth(), 1))
const selectedDate = ref(key(new Date()))
const monthTitle = computed(() => `${cursor.value.getFullYear()}年${cursor.value.getMonth() + 1}月`)
const normalized = computed(() => store.events.value.map(event => ({ ...event, startsAt: startOf(event), endsAt: endOf(event) })))
const selectedEvents = computed(() => normalized.value.filter(event => datesFor(event).includes(selectedDate.value)).sort(compareEvents))
const upcoming = computed(() => normalized.value.filter(event => !Boolean(event.completed) && startOf(event) >= nowText()).sort(compareEvents).slice(0, 6))
const overdue = computed(() => normalized.value.filter(event => !Boolean(event.completed) && startOf(event) && endOf(event) < nowText()).sort(compareEvents))
const cells = computed(() => {
  const first = cursor.value
  const offset = (first.getDay() + 6) % 7
  const start = new Date(first.getFullYear(), first.getMonth(), 1 - offset)
  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(start); date.setDate(start.getDate() + index)
    const dateKey = key(date)
    return { key: dateKey, day: date.getDate(), current: date.getMonth() === first.getMonth(), events: normalized.value.filter(event => datesFor(event).includes(dateKey)) }
  })
})

function pad(value: number) { return String(value).padStart(2, '0') }
function key(date: Date) { return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` }
function nowText() { return new Date().toISOString().slice(0, 16).replace('T', ' ') }
function value(event: JobEvent, ...names: string[]) { for (const name of names) { const found = event[name]; if (found) return String(found) } return '' }
function startOf(event: JobEvent) { return value(event, 'startsAt', 'start', 'date', 'at').replace('T', ' ') }
function endOf(event: JobEvent) { return value(event, 'endsAt', 'end')?.replace('T', ' ') || startOf(event) }
function compareEvents(left: JobEvent, right: JobEvent) { return startOf(left).localeCompare(startOf(right)) }
function datesFor(event: JobEvent) {
  const start = startOf(event).slice(0, 10); const end = endOf(event).slice(0, 10)
  if (!start) return []
  const result: string[] = []; const date = new Date(`${start}T00:00:00`); const last = new Date(`${end || start}T00:00:00`)
  while (date <= last && result.length < 370) { result.push(key(date)); date.setDate(date.getDate() + 1) }
  return result
}
function move(offset: number) { cursor.value = new Date(cursor.value.getFullYear(), cursor.value.getMonth() + offset, 1) }
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
        <div class="calendar-head"><button class="secondary" @click="move(-1)">‹</button><strong>{{ monthTitle }}</strong><button class="secondary" @click="move(1)">›</button></div>
        <div class="weekdays"><b v-for="day in ['一','二','三','四','五','六','日']" :key="day">{{ day }}</b></div>
        <div class="month-grid">
          <button v-for="cell in cells" :key="cell.key" :class="['day',{muted:!cell.current,selected:cell.key===selectedDate,today:cell.key===key(new Date())}]" @click="selectedDate=cell.key">
            <span>{{ cell.day }}</span><i v-for="event in cell.events.slice(0,2)" :key="event.id">{{ eventName(event) }}</i><small v-if="cell.events.length>2">+{{ cell.events.length-2 }}</small>
          </button>
        </div>
      </section>
      <aside class="card selected-card"><h2>{{ selectedDate }}</h2><article v-for="event in selectedEvents" :key="event.id"><strong>{{ eventCompany(event) }} · {{ eventName(event) }}</strong><span>{{ startOf(event) }}<template v-if="endOf(event)!==startOf(event)"> 至 {{ endOf(event) }}</template></span><span>{{ value(event,'location') || value(event,'notes') || (event.completed ? '已完成' : '待处理') }}</span></article><p v-if="!selectedEvents.length">当天没有安排。</p></aside>
    </div>
    <section class="card"><h2>接下来</h2><div class="upcoming"><article v-for="event in upcoming" :key="event.id"><time>{{ startOf(event) }}</time><div><strong>{{ eventCompany(event) }} · {{ eventName(event) }}</strong><span>{{ value(event,'position','location') || '待处理' }}</span></div></article><p v-if="!upcoming.length">暂无待处理日程。</p></div></section>
  </section>
  <section v-else class="card"><h2>请先登录</h2><p>登录后查看完整日历和历史安排。</p></section>
</template>

<style scoped>
.overdue-alert{display:flex;justify-content:space-between;gap:12px;margin-top:22px;padding:14px 18px;border:1px solid #f4c7c7;border-radius:12px;color:#982d2d;background:#fff1f1}.calendar-layout{display:grid;grid-template-columns:minmax(0,2fr) minmax(260px,1fr);gap:18px}.calendar-head{display:flex;align-items:center;justify-content:space-between}.weekdays,.month-grid{display:grid;grid-template-columns:repeat(7,1fr)}.weekdays b{padding:12px 4px;color:#667085;font-size:12px;text-align:center}.day{min-height:88px;padding:7px;border:1px solid #e4e9f2;border-radius:0;color:#344054;background:#fff;text-align:left}.day.muted{color:#b3bac7;background:#f8fafc}.day.selected{position:relative;z-index:1;outline:2px solid #526ddd}.day.today>span{display:grid;width:23px;height:23px;place-items:center;border-radius:50%;color:#fff;background:#526ddd}.day i{display:block;overflow:hidden;margin-top:4px;padding:3px 5px;border-radius:4px;color:#3d55bd;background:#edf1ff;font-size:10px;font-style:normal;text-overflow:ellipsis;white-space:nowrap}.day small{color:#667085}.selected-card h2{margin-top:0}.selected-card article{display:grid;gap:5px;padding:13px 0;border-top:1px solid #edf0f5}.selected-card span,.upcoming span{color:#667085;font-size:12px}.upcoming article{display:grid;grid-template-columns:150px 1fr;gap:16px;padding:13px 0;border-top:1px solid #edf0f5}.upcoming div{display:grid;gap:4px}.upcoming time{color:#526ddd;font-weight:700}@media(max-width:850px){.calendar-layout{grid-template-columns:1fr}.day{min-height:62px}.day i{height:5px;padding:0;font-size:0}.upcoming article{grid-template-columns:1fr}.overdue-alert{flex-direction:column}}
</style>
