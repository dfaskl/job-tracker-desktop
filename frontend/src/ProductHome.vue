<script setup lang="ts">
import { computed, nextTick, onActivated, onDeactivated, onMounted, onUnmounted, ref, watch } from 'vue'
import { api, ApiError } from './api'
import { applicationProgressHealth, isScheduleOverdue, schedulePriorityTimestamp } from './applicationProgress'
import { dailyQuotes } from './dailyQuotes'
import { useJobTrackerStore, type JobApplication, type JobEvent } from './jobTrackerStore'
import { confirmDialog } from './modalDialogs'
import HomeScheduleCat from './HomeScheduleCat.vue'

type Page = 'applications' | 'calendar' | 'mail' | 'stats'
type TimelineEntry = { id:string; label:string; date:string; start:string; end:string; status:'normal'|'tight'|'conflict'; flexible:boolean; showWindow:boolean; windowStart?:string; windowEnd?:string }
type ScheduleAdvice = { summary: string; plans: string[]; timeline?: TimelineEntry[]; warnings?: string[]; conflicts: string[] }
type TimelineLabel = { text:string; status:'normal'|'tight'|'conflict' }
type TimelineGroup = { id:string; date:string; start:string; timeLabel:string; status:'normal'|'tight'|'conflict'; labels:TimelineLabel[] }
type SharedTimelineEvent = Record<string,unknown> & { id:string; type:string; title:string; startsAt:string; endsAt:string; company:string }
type SharedTimelineUser = { email:string; displayName:string; events:SharedTimelineEvent[] }
type TeamTimelineEntry = { id:string;email:string;name:string;color:string;date:string;start:string;label:string;timestamp:number;conflict:boolean }
type TeamConflict = { id:string; text:string }
const emit = defineEmits<{ navigate: [page: Page, applicationId?: string, behavior?: 'detail' | 'focus'] }>()
const store = useJobTrackerStore()
const message = ref('')
const error = ref('')
const busyId = ref('')
const completingEventId = ref('')
const scheduleNow = ref(Date.now())
const quoteDay = computed(() => {
  const date = new Date(scheduleNow.value)
  return Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / 86_400_000
})
const quoteCursorKey = 'careerflow_quote_cursor_v1'
const quoteCursor = ref(readQuoteCursor())
const quoteArrowSpinning = ref(false)
const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')
const quoteIndex = computed(() => {
  const cursor = quoteCursor.value
  if (cursor) return (cursor.index + Number(cursor.day !== quoteDay.value)) % dailyQuotes.length
  return quoteDay.value % dailyQuotes.length
})
const quote = computed(() => dailyQuotes[quoteIndex.value])
function readQuoteCursor(): { day: number; index: number } | null {
  try {
    const cursor = JSON.parse(localStorage.getItem(quoteCursorKey) || 'null')
    if (Number.isInteger(cursor?.day) && Number.isInteger(cursor?.index)
      && cursor.index >= 0 && cursor.index < dailyQuotes.length) return cursor
  } catch { /* 浏览器禁用存储时，继续在当前页面轮换。 */ }
  return null
}
function saveQuoteCursor(index: number) {
  quoteCursor.value = { day: quoteDay.value, index }
  try {
    localStorage.setItem(quoteCursorKey, JSON.stringify(quoteCursor.value))
  } catch { /* 当前页面仍保留轮换位置。 */ }
}
function nextQuote() {
  if (quoteArrowSpinning.value) return
  if (!reducedMotion.matches) {
    quoteArrowSpinning.value = true
    return
  }
  advanceQuote()
}
function finishQuoteSpin() {
  if (!quoteArrowSpinning.value) return
  quoteArrowSpinning.value = false
  advanceQuote()
}
function advanceQuote() {
  updateScheduleClock()
  quoteCursor.value = readQuoteCursor() ?? quoteCursor.value
  saveQuoteCursor((quoteIndex.value + 1) % dailyQuotes.length)
}
function syncQuoteCursor(event: StorageEvent) {
  if (event.key === quoteCursorKey) quoteCursor.value = readQuoteCursor()
}
watch([quoteDay, () => store.user.value], () => {
  if (store.user.value && quoteCursor.value?.day !== quoteDay.value) saveQuoteCursor(quoteIndex.value)
}, { immediate: true })
let scheduleClock: ReturnType<typeof setInterval> | null = null
function updateScheduleClock() { scheduleNow.value = Date.now() }
function startScheduleClock() {
  updateScheduleClock()
  if (scheduleClock) clearInterval(scheduleClock)
  scheduleClock = setInterval(updateScheduleClock, 30_000)
}
function stopScheduleClock() {
  if (scheduleClock) clearInterval(scheduleClock)
  scheduleClock = null
}
function scheduleOverdue(event: JobEvent) { return isScheduleOverdue(event, scheduleNow.value) }
const scheduleAdvice = ref<ScheduleAdvice | null>(null)
const adviceLoading = ref(false)
const adviceNotice = ref('')
const sharedTimelines = ref<SharedTimelineUser[]>([])
const sharedGroupName = ref('')
const sharedTimelinesLoading = ref(false)
const sharedTimelinesNotice = ref('')
const teamTimelineList = ref<HTMLElement | null>(null)
const timelineEntrancePending = ref(false)
let timelineEntranceAnimations: Animation[] = []
let adviceTimer: ReturnType<typeof setTimeout> | null = null
let messageTimer: ReturnType<typeof setTimeout> | null = null
let homeViewActive = false
let sharedTimelineRequest = 0

onActivated(() => {
  homeViewActive = true
  timelineEntrancePending.value = !reducedMotion.matches
  startScheduleClock()
  void store.refresh(false, false, false)
  void loadSharedTimelines()
})
onDeactivated(() => {
  homeViewActive = false
  quoteArrowSpinning.value = false
  stopTimelineEntrance()
  stopScheduleMotion()
  stopScheduleClock()
})
watch(store.events, () => { if (homeViewActive) void loadSharedTimelines() })

const applicationsById = computed(() => new Map(store.applications.value.map(item => [item.id, item])))
const eventsByApplication = computed(() => {
  const groups = new Map<string, JobEvent[]>()
  for (const event of store.events.value) {
    if (!event.applicationId) continue
    const group = groups.get(event.applicationId)
    if (group) group.push(event)
    else groups.set(event.applicationId, [event])
  }
  return groups
})
const upcomingItems = computed(() => store.events.value.filter(item => !item.completed && !item.missed && !item.abandoned && !isEnded(appFor(item)))
  .sort((a,b) => schedulePriorityTimestamp(a)-schedulePriorityTimestamp(b)))
const recentSchedules = computed(() => upcomingItems.value)
const overdueSchedules = computed(() => recentSchedules.value.filter(scheduleOverdue))
const scheduleStage = ref<HTMLElement | null>(null)
const frozenSchedules = ref<JobEvent[] | null>(null)
const displayedSchedules = computed(() => frozenSchedules.value ?? recentSchedules.value)
const displayedOverdue = computed(() => displayedSchedules.value.filter(scheduleOverdue))
const scheduledGroups = computed(() => [
  { id: 'home-overdue-schedules', title: '过期待确认', events: displayedOverdue.value },
  { id: 'home-upcoming-schedules', title: '即将到来', events: displayedSchedules.value.filter(event => !scheduleOverdue(event)) }
].filter(group => group.events.length))
const catPerch = ref(0)
const catReaction = ref<'startle' | 'fall' | 'land' | 'sleep' | null>(null)
const catPose = computed(() => catReaction.value ?? (displayedSchedules.value.length ? 'routine' : 'sleep'))
const dissolvingEventId = ref('')
let scheduleMotionStopped = false
const scheduleAnimations = new Set<Animation>()

function updateCatPerch() {
  const perch = scheduleStage.value?.querySelector<HTMLElement>('.schedule-entry, .schedule-empty')
  if (perch) catPerch.value = perch.offsetTop
}
watch([scheduleStage, scheduledGroups], ([stage], _, onCleanup) => {
  updateCatPerch()
  if (!stage) return
  const observer = new ResizeObserver(updateCatPerch)
  observer.observe(stage)
  onCleanup(() => observer.disconnect())
}, { flush: 'post' })

function animateSchedule(element: Element, frames: Keyframe[], options: KeyframeAnimationOptions) {
  const animation = element.animate(frames, options)
  scheduleAnimations.add(animation)
  return animation.finished.catch(() => undefined)
}

function stopScheduleMotion() {
  scheduleMotionStopped = true
  for (const animation of scheduleAnimations) animation.cancel()
  scheduleAnimations.clear()
  dissolvingEventId.value = ''
  catReaction.value = null
}
function focusOverdueSchedules() {
  const heading = document.getElementById('home-overdue-schedules')
  heading?.scrollIntoView({ block: 'nearest' })
  heading?.focus({ preventScroll: true })
}
const nextScheduleId = computed(() => recentSchedules.value.find(event => !scheduleOverdue(event) && Number.isFinite(schedulePriorityTimestamp(event)))?.id)
const adviceCandidates = computed(() => upcomingItems.value)
const adviceSignature = computed(() => JSON.stringify(adviceCandidates.value.map(event => ({
  id:event.id, company:eventCompany(event), title:String(event.title || event.type || '未命名日程'),
  startsAt:eventStart(event), endsAt:String(event.endsAt || event.end || eventStart(event)), updatedAt:String(event.updatedAt || '')
}))))
function timelineEventLabel(text: string, eventId?: string) {
  const event = adviceCandidates.value.find(item => String(item.id) === String(eventId || ''))
  if (!event) return text

  const start = eventStart(event)
  const end = String(event.endsAt || event.end || '').trim()
  const startTime = new Date(start.replace(' ', 'T')).getTime()
  const endTime = new Date(end.replace(' ', 'T')).getTime()
  if (!end || !Number.isFinite(startTime) || !Number.isFinite(endTime) || endTime <= startTime) return text

  const match = end.match(/^\d{4}-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})/)
  if (!match) return text
  return `${text}（${Number(match[1])}.${Number(match[2])} ${match[3]}:${match[4]}前）`
}
const adviceTimeline = computed<TimelineGroup[]>(() => {
  const groups = new Map<string,TimelineGroup>(), rank={normal:0,tight:1,conflict:2}
  const entries=scheduleAdvice.value?.timeline||[]
  if(entries.length){
    for(const entry of entries){
      const key=entry.date+' '+entry.start, status=entry.status||'normal'
      const windowStart=entry.windowStart||'',windowEnd=entry.windowEnd||''
      const sameWindowDay=windowStart.slice(0,10)===windowEnd.slice(0,10)
      const dayStart=windowStart.slice(0,10)===entry.date?windowStart.slice(11,16):entry.start
      const dayEnd=windowEnd.slice(0,10)===entry.date?windowEnd.slice(11,16):'23:59'
      const rangeLabel=sameWindowDay?windowStart.slice(11,16)+'–'+windowEnd.slice(11,16):dayStart+'–'+dayEnd
      const timeLabel=entry.showWindow?rangeLabel:entry.start
      const group=groups.get(key),entryLabel=timelineEventLabel(entry.label,entry.id)
      if(group){group.labels.push({text:entryLabel,status});if(rank[status]>rank[group.status])group.status=status;if(entry.showWindow)group.timeLabel=timeLabel}
      else groups.set(key,{id:key,date:entry.date,start:entry.start,timeLabel,status,labels:[{text:entryLabel,status}]})
    }
    return [...groups.values()].sort((a,b)=>(a.date+' '+a.start).localeCompare(b.date+' '+b.start))
  }
  for(const text of scheduleAdvice.value?.plans||[]){
    const match=text.match(/^(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})-(\d{2}:\d{2})\s+(.+)$/)
    const date=match?.[1]||'',start=match?.[2]||'',label=match?.[4]||text,key=date+' '+start,group=groups.get(key)
    if(group)group.labels.push({text:label,status:'normal'});else groups.set(key,{id:key||text,date,start,timeLabel:start,status:'normal',labels:[{text:label,status:'normal'}]})
  }
  return [...groups.values()]
})
function timelineFromEvents(events:Record<string,unknown>[]) {
  const groups = new Map<string,TimelineGroup>()
  for (const event of events) {
    const value = eventStart(event)
    if (!value) continue
    const date = value.slice(0, 10), start = value.slice(11, 16), key = `${date} ${start}`
    const company = String(event.company || '未填写公司')
    const title = String(event.title || event.type || '未命名日程')
    const end = String(event.endsAt || event.end || '').trim()
    const timeLabel = end && end.slice(0, 10) === date ? `${start}–${end.slice(11, 16)}` : start
    const label = { text: `${company} · ${title}`, status: 'normal' as const }
    const group = groups.get(key)
    if (group) group.labels.push(label)
    else groups.set(key, { id:key, date, start, timeLabel, status:'normal', labels:[label] })
  }
  return [...groups.values()].sort((a,b) => `${a.date} ${a.start}`.localeCompare(`${b.date} ${b.start}`))
}
const memberColors=['#7f6f59','#3e77c5','#d07832','#8662b8','#c84f64','#4f8b45','#b360a8','#697a2c']
const timelineMembers=computed(()=>sharedTimelines.value.map((user,index)=>({
  ...user,name:displayName(user.displayName,user.email),color:memberColors[index%memberColors.length]
})).filter(user=>user.events?.length))
const teamSchedule=computed<{items:TeamTimelineEntry[];conflicts:TeamConflict[]}>(()=>{
  const entries=timelineMembers.value.flatMap(user=>(user.events||[]).map(event=>{
    const value=eventStart(event),timestamp=parseTime(value)
    return {id:`${user.email}:${event.id}`,email:user.email,name:user.name,color:user.color,date:value.slice(0,10),start:value.slice(11,16),label:`${String(event.company||'未填写公司')} · ${String(event.title||event.type||'未命名日程')}`,timestamp,conflict:false}
  })).filter(item=>item.date&&item.start&&Number.isFinite(item.timestamp)).sort((a,b)=>a.timestamp-b.timestamp)
  const conflictIds=new Set<string>(),conflicts:TeamConflict[]=[]
  for(let i=0;i<entries.length;i++){
    for(let j=i+1;j<entries.length;j++){
      const minutes=Math.round((entries[j].timestamp-entries[i].timestamp)/60000)
      if(minutes>=60)break
      conflictIds.add(entries[i].id);conflictIds.add(entries[j].id)
      conflicts.push({id:`${entries[i].id}:${entries[j].id}`,text:`${teamConflictTime(entries[i])}，${entries[i].name}「${entries[i].label}」与 ${entries[j].name}「${entries[j].label}」仅间隔 ${minutes} 分钟`})
    }
  }
  return {items:entries.map(item=>({...item,conflict:conflictIds.has(item.id)})),conflicts}
})
const teamTimeline=computed(()=>teamSchedule.value.items)
watch([teamTimelineList, timelineEntrancePending], ([list, pending]) => {
  if (!list || !pending) return
  timelineEntrancePending.value = false

  const nodes = Array.from(list.querySelectorAll('.team-timeline-node'))
  const stagger = Math.min(95, 760 / Math.max(1, nodes.length - 1))
  timelineEntranceAnimations = nodes.map((node, index) => node.animate(
    [{ transform: 'translateX(88px)', opacity: 0 }, { transform: 'translateX(0)', opacity: 1 }],
    { delay: index * stagger, duration: 460, easing: 'cubic-bezier(.2,.75,.28,1)', fill: 'backwards' },
  ))
}, { flush: 'post' })

function stopTimelineEntrance() {
  timelineEntrancePending.value = false
  for (const animation of timelineEntranceAnimations) animation.cancel()
  timelineEntranceAnimations = []
}
const teamConflicts=computed(()=>teamSchedule.value.conflicts)
const teamConflictSummary=computed(()=>teamConflicts.value.length?'请优先协调时间轴上标出的日程。':'小组内时间点日程之间均至少间隔 1 小时。')
const staleApplications = computed(() => store.applications.value.map(item => ({ item, health: progressHealth(item) }))
  .filter(row => row.health && row.health.days >= 22).sort((a,b) => (b.health?.days || 0) - (a.health?.days || 0)))
onMounted(() => {
  startScheduleClock()
  window.addEventListener('focus', updateScheduleClock)
  window.addEventListener('storage', syncQuoteCursor)
  reducedMotion.addEventListener('change', stopTimelineEntrance)
  reducedMotion.addEventListener('change', stopScheduleMotion)
  void store.initialize()
})
function pad(value:number){return String(value).padStart(2,'0')}
function localText(date=new Date()){return `${date.getFullYear()}-${pad(date.getMonth()+1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`}
function today(){return localText().slice(0,10)}
function eventStart(item:Record<string,unknown>){return String(item.startsAt||item.start||item.date||'')}
function eventDeadline(item:Record<string,unknown>){return String(item.endsAt||item.end||eventStart(item))}
function parseTime(value:string){const time=new Date(value.replace(' ','T')).getTime();return Number.isFinite(time)?time:Infinity}
function teamConflictTime(item:TeamTimelineEntry){const match=item.date.match(/^\d{4}-(\d{2})-(\d{2})$/);return match?`${Number(match[1])}月${Number(match[2])}日 ${item.start}`:`${item.date} ${item.start}`}
function displayName(name:string|undefined,email:string){return String(name||'').trim()||String(email||'').split('@')[0]||'未命名用户'}
async function loadSharedTimelines(){
  if(!store.user.value)return
  const request=++sharedTimelineRequest
  sharedTimelinesLoading.value=true;sharedTimelinesNotice.value=''
  try{const result=await api<{groupId:string;groupName:string;users:SharedTimelineUser[]}>('/api/poc/event-sandbox/timelines');if(request!==sharedTimelineRequest)return;sharedTimelines.value=Array.isArray(result.users)?result.users:[];sharedGroupName.value=String(result.groupName||'')}
  catch{if(request===sharedTimelineRequest){sharedTimelines.value=[];sharedGroupName.value='';sharedTimelinesNotice.value='小组时间轴暂时无法加载'}}
  finally{if(request===sharedTimelineRequest)sharedTimelinesLoading.value=false}
}
function adviceCacheKey(){return 'job_tracker_schedule_advice_v12_'+String(store.user.value?.email||'guest').toLowerCase()}
function scheduleAdviceExpiry(){const now=Date.now();const times=adviceCandidates.value.map(event=>parseTime(eventDeadline(event))).filter(time=>Number.isFinite(time)&&time>now);return times.length?Math.min(...times):now}
function loadScheduleAdvice(signature:string){try{const cached=JSON.parse(localStorage.getItem(adviceCacheKey())||'null');if(Number(cached?.expiresAt)>Date.now()&&cached?.signature===signature&&cached.advice){scheduleAdvice.value=cached.advice;return true}}catch{/* 重新生成 */}return false}
function localScheduleAdvice():ScheduleAdvice{
  const duration=90*60*1000
  const now=Date.now()
  const items=[...adviceCandidates.value]
  const label=(event:JobEvent)=>eventCompany(event)+' · '+String(event.title||event.type||'未命名日程')
  const format=(time:number)=>{const date=new Date(time),pad=(value:number)=>String(value).padStart(2,'0');return date.getFullYear()+'-'+pad(date.getMonth()+1)+'-'+pad(date.getDate())+' '+pad(date.getHours())+':'+pad(date.getMinutes())}
  const fixed=items.filter(event=>{const end=String(event.endsAt||event.end||'');return !end||end===eventStart(event)}).map(event=>({event,start:parseTime(eventStart(event)),end:parseTime(eventStart(event))+duration}))
  const flexible=items.filter(event=>{const end=String(event.endsAt||event.end||'');return Boolean(end&&end!==eventStart(event))}).sort((a,b)=>eventDeadline(a).localeCompare(eventDeadline(b)))
  const futureFixed=fixed.filter(slot=>Number.isFinite(slot.start)&&slot.start>=now)
  const occupied=[...futureFixed],scheduled=[...futureFixed],conflicts:string[]=[]
  for(let i=0;i<futureFixed.length;i++)for(let j=i+1;j<futureFixed.length;j++)if(futureFixed[i].start<futureFixed[j].end&&futureFixed[j].start<futureFixed[i].end)conflicts.push(label(futureFixed[i].event)+' 与 '+label(futureFixed[j].event)+' 的固定时间冲突')
  for(const event of flexible){
    const windowStart=parseTime(eventStart(event)),windowEnd=parseTime(eventDeadline(event))
    let candidate=Math.max(windowStart,now)
    while(candidate+duration<=windowEnd){
      const collision=occupied.filter(slot=>candidate<slot.end&&slot.start<candidate+duration).sort((a,b)=>a.end-b.end)[0]
      if(!collision)break
      candidate=collision.end
    }
    if(!Number.isFinite(candidate)||candidate+duration>windowEnd){conflicts.push(label(event)+'：可用时间段内无法安排连续 90 分钟');continue}
    const slot={event,start:candidate,end:candidate+duration};occupied.push(slot);scheduled.push(slot)
  }
  const plans=scheduled.sort((a,b)=>a.start-b.start).map(slot=>format(slot.start)+'-'+format(slot.end).slice(11)+' '+label(slot.event))
  return{summary:conflicts.length?'已保留固定时间并发现 '+conflicts.length+' 处冲突':'已保留固定时间，并将弹性日程安排到可用空档',plans,conflicts}
}async function generateScheduleAdvice(signature:string,attempt=0,force=false){
  if(!signature||adviceCandidates.value.length<2){scheduleAdvice.value=null;adviceNotice.value='';return}
  if((!force&&loadScheduleAdvice(signature))||adviceLoading.value)return
  adviceLoading.value=true
  try{
    const schedules=adviceCandidates.value.map(event=>({id:event.id,company:eventCompany(event),title:String(event.title||event.type||'未命名日程'),startsAt:eventStart(event),endsAt:String(event.endsAt||event.end||eventStart(event))}))
    const advice=await api<ScheduleAdvice>('/api/poc/ai-sandbox/schedule-advice',{method:'POST',body:JSON.stringify({schedules})})
    if(adviceSignature.value!==signature)return
    scheduleAdvice.value=advice
    localStorage.setItem(adviceCacheKey(),JSON.stringify({expiresAt:scheduleAdviceExpiry(),signature,advice}))
    if(force)message.value='安排建议已重新生成'
  }catch(cause){
    if(cause instanceof ApiError&&cause.status===429&&attempt<2&&adviceSignature.value===signature){
      adviceNotice.value='AI 请求正在排队，将自动重试…'
      adviceTimer=setTimeout(()=>void generateScheduleAdvice(signature,attempt+1,force),3500)
    }else{scheduleAdvice.value=localScheduleAdvice();adviceNotice.value=''}
  }finally{adviceLoading.value=false}
}
function isEnded(item:JobApplication|undefined){return Boolean(item)&&(item?.stage==='已结束'||['未通过','已放弃','已结束'].includes(String(item?.status||'')))}
function progressHealth(item:JobApplication){return applicationProgressHealth(item,eventsByApplication.value.get(item.id) || [])}
function appFor(event:JobEvent){return event.applicationId ? applicationsById.value.get(event.applicationId) : undefined}
function openApplicationDetail(event:JobEvent){const application=appFor(event);if(!application){error.value='未找到该日程关联的投递记录';return}emit('navigate','applications',String(application.id))}
function focusApplication(event:JobEvent){const application=appFor(event);if(!application){error.value='未找到该日程关联的投递记录';return}emit('navigate','applications',String(application.id),'focus')}
function eventCompany(event:JobEvent){return String(event.company||appFor(event)?.company||'未填写公司')}
function eventPosition(event:JobEvent){return String(event.position||appFor(event)?.position||'未填写岗位')}
function eventDate(event:JobEvent){
  const value=eventStart(event),endValue=String(event.endsAt||event.end||'')
  const date=new Date(value.replace(' ','T')),endDate=new Date(endValue.replace(' ','T'))
  if(Number.isNaN(date.getTime()))return {tag:'待定',date:'未设置',time:'',range:false,endDate:'',endTime:''}
  const range=Boolean(endValue.trim())&&!Number.isNaN(endDate.getTime())
  const same=today()===value.slice(0,10),dateText=`${date.getMonth()+1}.${date.getDate()}`,time=`${pad(date.getHours())}:${pad(date.getMinutes())}`
  const endDateText=range?`${endDate.getMonth()+1}.${endDate.getDate()}`:''
  const endTime=range?`${pad(endDate.getHours())}:${pad(endDate.getMinutes())}`:''
  return {tag:same?'今天':'',date:dateText,time,range,endDate:endDateText,endTime}
}
async function playScheduleCompletion(event: JobEvent, applyCompletion: () => void) {
  const stage = scheduleStage.value
  const entries = Array.from(stage?.querySelectorAll<HTMLElement>('.schedule-entry') ?? [])
  const index = entries.findIndex(entry => entry.dataset.scheduleId === event.id)
  const card = entries[index]?.querySelector('article')
  if (!homeViewActive || reducedMotion.matches || !stage || !card) {
    applyCompletion()
    return
  }

  const catTrack = stage.querySelector('.cat-track')
  const cat = index === 0 ? catTrack : null
  const firstTop = entries[0].offsetTop
  const landingTop = entries[1]?.offsetTop ?? firstTop + 18
  scheduleMotionStopped = false
  frozenSchedules.value = recentSchedules.value
  dissolvingEventId.value = event.id
  if (cat) catReaction.value = 'startle'

  const dissolve = animateSchedule(card, [
    { opacity: 1, filter: 'blur(0)', translate: '0 0' },
    { opacity: .68, filter: 'blur(2px)', translate: '0 -4px', offset: .46 },
    { opacity: 0, filter: 'blur(12px)', translate: '0 -16px' },
  ], { duration: 430, easing: 'ease-in', fill: 'forwards' })

  if (cat && !scheduleMotionStopped) {
    await animateSchedule(cat, [
      { translate: '0 0' }, { translate: '0 -5px', offset: .58 }, { translate: '0 -2px' },
    ], { duration: 340, easing: 'ease-out', fill: 'forwards' })
  }
  if (cat && !scheduleMotionStopped) {
    catReaction.value = 'fall'
    const drop = landingTop - firstTop
    const fall = animateSchedule(cat, [{ translate: '0 -2px' }, { translate: `0 ${drop}px` }], {
      duration: Math.min(470, Math.max(205, Math.sqrt(Math.max(0, drop)) * 29)),
      easing: 'cubic-bezier(.44,.04,.78,.49)', fill: 'forwards',
    })
    await Promise.all([dissolve, fall])
    if (!scheduleMotionStopped) catReaction.value = 'land'
  } else {
    await dissolve
  }

  const previousStageTop = stage.getBoundingClientRect().top
  const before = new Map(entries.map(entry => [entry.dataset.scheduleId, entry.offsetTop]))
  applyCompletion()
  frozenSchedules.value = null
  if (scheduleMotionStopped) return
  await nextTick()
  if (scheduleMotionStopped) return

  for (const animation of scheduleAnimations) animation.cancel()
  scheduleAnimations.clear()
  updateCatPerch()
  const stageShift = previousStageTop - stage.getBoundingClientRect().top
  const lift = (element: Element, distance: number) => animateSchedule(element, [
    { translate: `0 ${distance}px` }, { translate: '0 0' },
  ], { delay: cat ? 145 : 0, duration: 390, easing: 'cubic-bezier(.32,.03,.2,1)', fill: 'backwards' })
  const moving = Array.from(stage.querySelectorAll<HTMLElement>('.schedule-entry'), entry => {
    const distance = (before.get(entry.dataset.scheduleId) ?? entry.offsetTop) - entry.offsetTop + stageShift
    return distance ? lift(entry, distance) : undefined
  })
  if (cat) {
    if (!displayedSchedules.value.length) catReaction.value = 'sleep'
    moving.push(lift(cat, landingTop - catPerch.value + stageShift))
  } else if (catTrack && stageShift) {
    moving.push(lift(catTrack, stageShift))
  }
  await Promise.all(moving)
}

async function completeEvent(event: JobEvent) {
  if (completingEventId.value || store.readOnly.value) return
  const owner = store.user.value?.id
  completingEventId.value = event.id
  error.value = ''
  message.value = ''
  try {
    const { event: completed } = await api<{ event: JobEvent }>(`/api/poc/event-sandbox/events/${encodeURIComponent(event.id)}/resolution`, {
      method: 'POST',
      body: JSON.stringify({ action: 'complete', expectedUpdatedAt: String(event.updatedAt || event.createdAt || '') }),
    })
    if (store.user.value?.id !== owner) return
    const applyCompletion = () => {
      if (store.user.value?.id !== owner) return
      const current = store.events.value.find(item => item.id === event.id)
      if (!current || current.completed || current.updatedAt !== event.updatedAt) return
      store.data.value.events = store.events.value.map(item => item === current ? { ...item, ...completed, completed: true } : item)
    }
    try {
      await playScheduleCompletion(event, applyCompletion)
    } finally {
      applyCompletion()
      stopScheduleMotion()
      frozenSchedules.value = null
    }
    if (store.user.value?.id === owner) {
      message.value = '日程已完成'
      await store.refresh()
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '更新日程失败'
  } finally {
    completingEventId.value = ''
  }
}
async function markRejected(item:JobApplication){if(!await confirmDialog(`确认将“${item.company} · ${item.position}”标记为未通过吗？`,{title:'结束投递',confirmText:'标记未通过',tone:'warning'}))return;busyId.value=item.id;error.value='';message.value='';try{await api(`/api/poc/application-sandbox/applications/${encodeURIComponent(item.id)}`,{method:'PUT',body:JSON.stringify({company:item.company||'',position:item.position||'',city:item.city||'',channel:item.channel||'',appliedDate:item.appliedDate||'',stage:'已结束',status:'未通过',notes:item.notes||'',expectedUpdatedAt:item.updatedAt||''})});await store.refresh();message.value='已标记为未通过'}catch(cause){error.value=cause instanceof Error?cause.message:'更新投递失败'}finally{busyId.value=''}}

watch(message,value=>{if(messageTimer)clearTimeout(messageTimer);if(value)messageTimer=setTimeout(()=>{if(message.value===value)message.value=''},2600)})
function syncScheduleAdvice(signature:string){
  if(adviceTimer)clearTimeout(adviceTimer)
  if(!store.user.value)return
  if(!signature||adviceCandidates.value.length<2){scheduleAdvice.value=null;adviceNotice.value='';return}
  if(loadScheduleAdvice(signature))return
  scheduleAdvice.value=localScheduleAdvice()
  adviceTimer=setTimeout(()=>void generateScheduleAdvice(signature),600)
}
watch([adviceSignature,()=>store.user.value?.email],([signature])=>syncScheduleAdvice(signature),{immediate:true})
onUnmounted(()=>{stopScheduleClock();stopTimelineEntrance();stopScheduleMotion();reducedMotion.removeEventListener('change',stopTimelineEntrance);reducedMotion.removeEventListener('change',stopScheduleMotion);window.removeEventListener('focus',updateScheduleClock);window.removeEventListener('storage',syncQuoteCursor);if(adviceTimer)clearTimeout(adviceTimer);if(messageTimer)clearTimeout(messageTimer)})</script>

<template>
  <div v-if="store.user.value" class="home-dashboard">
    <section class="dashboard-panel">
      <section class="timeline-column team-timeline home-optical-surface" aria-labelledby="team-timeline-title">
        <div class="column-heading"><div><strong id="team-timeline-title">{{sharedGroupName||'我的'}}日程时间轴</strong></div><span>{{timelineMembers.length}} 人 · {{teamTimeline.length}} 项</span></div>
        <div v-if="timelineMembers.length" class="member-legend" aria-label="小组成员颜色图例"><span v-for="member in timelineMembers" :key="member.email"><i :style="{'--member-color':member.color}" aria-hidden="true"></i><b>{{member.name}}</b><small>{{member.events.length}} 项</small></span></div>
        <div v-if="teamTimeline.length" class="timeline-scroll"><div ref="teamTimelineList" class="timeline-list team-timeline-list"><article v-for="item in teamTimeline" :key="item.id" :class="{conflict:item.conflict}" :style="{'--member-color':item.color}" :aria-label="`${item.name}，${item.date} ${item.start}，${item.label}${item.conflict?'，与小组其他日程冲突':''}`"><div class="team-timeline-node"><time :datetime="`${item.date}T${item.start}`">{{item.date.slice(5).replace('-','月')+'日'}}</time><span v-if="item.conflict" class="warning-triangle team-node-warning" aria-hidden="true"><svg viewBox="0 0 24 22"><path d="M10.2 1.8a2.1 2.1 0 0 1 3.6 0l9.4 16.3a2.1 2.1 0 0 1-1.8 3.1H2.6a2.1 2.1 0 0 1-1.8-3.1L10.2 1.8Z"></path><path class="warning-mark" d="M12 7v6.2M12 17.2v.1"></path></svg></span><i></i><span><b>{{item.start}}</b><em><strong>{{item.name}}</strong>{{item.label}}</em></span></div></article></div></div>
        <div v-if="teamConflicts.length" class="team-conflict-alert" role="alert"><strong>小组日程冲突</strong><span v-for="item in teamConflicts" :key="item.id">{{item.text}}</span></div>
        <div v-if="!teamTimeline.length&&sharedTimelinesLoading" class="timeline-loading" role="status"><i aria-hidden="true"></i><span>正在加载小组日程…</span></div>
        <p v-else-if="!teamTimeline.length&&sharedTimelinesNotice" class="timeline-notice">{{sharedTimelinesNotice}}</p>
        <div v-else-if="!teamTimeline.length" class="timeline-empty">当前小组暂无待完成的时间点日程</div>
      </section>

      <section v-if="adviceLoading||scheduleAdvice||teamConflicts.length" class="schedule-advice" aria-live="polite">
        <div class="advice-head"><div class="advice-title"><button class="advice-trigger" :class="{'is-loading':adviceLoading}" :disabled="adviceLoading" title="重新生成安排建议" aria-label="重新生成安排建议" @click="generateScheduleAdvice(adviceSignature,0,true)"><span class="advice-glyph" aria-hidden="true"><svg viewBox="0 0 24 24"><circle cx="5" cy="6" r="2"></circle><circle cx="19" cy="18" r="2"></circle><path d="M7 6h4.5a3.5 3.5 0 0 1 0 7H10a3 3 0 0 0 0 6h7"></path></svg></span></button><div><strong>安排建议：</strong><small>{{adviceLoading?'正在计算…':teamConflictSummary}}</small></div></div></div>
        <p v-if="adviceNotice&&!adviceLoading" class="advice-notice">{{adviceNotice}}</p>
        <div v-if="scheduleAdvice?.warnings?.length" class="advice-warnings"><strong>时间紧张</strong><span v-for="item in scheduleAdvice.warnings" :key="item">{{item}}</span></div>
      </section>
      <section class="details-column" aria-labelledby="my-schedule-title">
        <div class="column-heading"><div><strong id="my-schedule-title">日程详情</strong><button v-if="overdueSchedules.length" type="button" class="text-link overdue-summary" @click="focusOverdueSchedules">{{ overdueSchedules.length }} 项已过期</button></div><span>{{recentSchedules.length}} 项</span></div>
        <div ref="scheduleStage" class="schedule-stage">
        <HomeScheduleCat :pose="catPose" :style="{ '--cat-perch': catPerch + 'px' }" />
        <template v-if="displayedSchedules.length">
          <div v-for="group in scheduledGroups" :key="group.id" class="schedule-list">
          <h3 v-if="displayedOverdue.length" :id="group.id" tabindex="-1">{{ group.title }}</h3>
          <div v-for="event in group.events" :key="event.id" class="schedule-entry" :data-schedule-id="event.id">
          <article :inert="dissolvingEventId===event.id" class="home-optical-surface" :class="{'has-date-range':eventDate(event).range,'is-overdue':scheduleOverdue(event),'is-next':event.id===nextScheduleId}">
            <div v-if="eventDate(event).range" class="date-range"><div class="date-block"><em v-if="eventDate(event).tag">{{eventDate(event).tag}}</em><strong>{{eventDate(event).date}}</strong><small>{{eventDate(event).time}}</small></div><i>至</i><div class="date-block"><strong>{{eventDate(event).endDate}}</strong><small>{{eventDate(event).endTime}}</small></div></div><div v-else class="date-block"><em v-if="eventDate(event).tag">{{eventDate(event).tag}}</em><strong>{{eventDate(event).date}}</strong><small>{{eventDate(event).time}}</small></div>
            <div class="schedule-copy"><div class="schedule-heading"><a v-if="String(event.location||'').startsWith('http')" class="schedule-link" :href="String(event.location)" target="_blank" rel="noopener noreferrer" :aria-label="`打开日程链接：${eventCompany(event)} · ${event.title||event.type||'未命名日程'}`"><strong>{{eventCompany(event)}} · {{event.title||event.type||'未命名日程'}}</strong><span aria-hidden="true">↗</span></a><strong v-else>{{eventCompany(event)}} · {{event.title||event.type||'未命名日程'}}</strong><span v-if="scheduleOverdue(event)" class="schedule-overdue-notice">已过期 · 待确认</span></div><p>{{eventPosition(event)}}</p><small v-if="String(event.notes||'').trim()">备注：{{event.notes}}</small></div>
            <div class="schedule-actions">
              <i v-if="event.id===nextScheduleId">下一场</i>
              <button v-if="appFor(event)" class="secondary icon-button home-action" type="button" :aria-label="`在投递记录中定位：${eventCompany(event)} · ${event.title||event.type||'未命名日程'}`" title="定位投递记录" @click="focusApplication(event)">
                <AppIcon name="arrow-right" />
              </button>
              <button class="secondary icon-button schedule-edit home-action" type="button" :aria-label="`编辑日程：${event.title||event.type||'未命名日程'}`" title="编辑日程" @click="openApplicationDetail(event)"><AppIcon name="edit" /></button>
              <button class="icon-button schedule-complete home-action" type="button" :disabled="Boolean(completingEventId)||store.readOnly.value" :aria-label="`完成日程：${event.title||event.type||'未命名日程'}`" title="标记完成" @click="completeEvent(event)"><AppIcon name="check" /></button>
            </div>
          </article>
          <div v-if="dissolvingEventId===event.id" class="schedule-smoke" aria-hidden="true">
            <i v-for="puff in 6" :key="puff" :style="{ '--puff-index': puff }"></i>
          </div>
          </div>
        </div>
        </template>
        <div v-else class="empty schedule-empty">暂无待完成日程。<button type="button" class="text-link" @click="emit('navigate','calendar')">前往日程安排</button></div>
        </div>
      </section>
    </section>

    <section class="dashboard-panel confirmation-panel">
      <div class="panel-head"><div><h2>人工确认 <span title="最近日程后长时间没有新进展的岗位">ⓘ</span></h2><p>最近一项日程达到 3 周仍无进展的面试岗位，请确认是否标记为未通过。</p></div><b v-if="staleApplications.length">{{staleApplications.length}} 个待确认</b></div>
      <div v-if="staleApplications.length" class="confirmation-list"><article v-for="row in staleApplications" :key="row.item.id"><div><strong>{{row.item.company||'未填写公司'}}</strong><span>{{row.item.position||'未填写岗位'}}</span></div><div class="progress-line"><i>已投递</i><span>→</span><i>测评 / 笔试</i><span>→</span><i>面试</i><span>→</span><i class="current">等待结果</i></div><em>{{row.health?.days}}天无进展</em><button :disabled="busyId===row.item.id||store.readOnly.value" @click="markRejected(row.item)">标记未通过</button></article></div>
      <div v-else class="confirmation-empty"><span class="confirmation-empty-icon" aria-hidden="true"><svg viewBox="0 0 48 48"><path d="M16 8h16a4 4 0 0 1 4 4v27H12V12a4 4 0 0 1 4-4Z"/><path d="M19 8V6h10v2M18 19h12M18 25h12M18 31h7"/><path class="empty-check" d="m29 33 3 3 6-7"/></svg></span><strong>目前没有需要人工确认的岗位</strong><p>暂时没有最近日程达到 3 周的面试记录。</p></div>
    </section>
    <section class="quote-strip" aria-label="《毛泽东选集》摘句">
      <button type="button" class="quote-trigger home-action" title="换一句" aria-label="换一句" :aria-disabled="quoteArrowSpinning" @click="nextQuote">
        <span class="quote-arrow-position" aria-hidden="true"><svg class="quote-brush-arrow" :class="{ 'is-spinning': quoteArrowSpinning }" viewBox="0 0 32 32" fill="currentColor" focusable="false" @animationend="finishQuoteSpin" @animationcancel="finishQuoteSpin">
          <!-- The open cuts and thin trails preserve the dry-brush texture at icon size. -->
          <path fill-rule="evenodd" d="
            M15 30.4 11.4 29.9 12.5 30.8C7.5 29.9 3.8 27.1 2.5 23.5L2.4 25
            C.7 21.3.7 17.4 1.8 13.5L1.2 14.1C2.2 9.8 5.5 5.9 9.4 4.2L8 4.5
            C12.6 1.6 19.1 1.7 23.6 4.9L22.5 4.6C25.7 6.5 27.7 9.5 28.6 13.1
            L31.4 12.6 30.8 14 31.2 13.9 26.5 20.3C26.1 20.8 25.7 20.9 25.2 20.5
            L18.5 15.8 20.3 15.5 19.4 15.2 23.6 14.2C22.8 9.8 19.3 7 15.3 7.2
            C10.2 7.3 6 11.3 5.5 16.4 4.8 21.8 8 27.6 15 30.4Z
            M3.3 15.7C3.2 11.4 6.1 7.6 9.5 6.2 6.4 8.3 4.7 11.3 3.3 15.7Z
            M11.2 5.4C14.4 3.2 19.1 3.4 22.2 5.7 18.9 4.3 14.7 4.2 11.2 5.4Z
            M3.8 19.1C3.8 23.8 6.4 27 10.5 29 7.5 26.4 5.1 23.7 3.8 19.1Z
            M22.2 16.2 26.1 17.8 29 14.5 26 16.9Z" />
          <path opacity=".72" d="
            M.5 16.9C.1 20.8.9 24 3.2 26.5 1.7 23.9.8 21.2.5 16.9Z
            M3.1 26.8C5.6 29.8 8.4 31.2 11.1 31.4 8.2 30.4 5.9 29.3 3.1 26.8Z
            M8.5 27.3C11.1 29.4 13.9 30.4 16.7 30.9 13.9 29.9 11.7 28.9 8.5 27.3Z
            M10.7 3.1C13.5 1.3 16.6 1 19 1.6 15.7 1.6 13.2 2.1 10.7 3.1Z
            M21.3 19.1 25.1 22 24.4 21.8 20.8 19.2Z" />
        </svg></span>
      </button>
      <p class="quote-copy" aria-live="polite" :title="`《毛泽东选集》·《${quote.source}》`">{{ quote.text }}</p>
    </section>

  </div>

  <section v-else-if="store.initialized.value" class="card sign-in-card"><h2>登录后查看你的求职进展</h2><p>使用现有账号即可进入，新旧系统账号及业务数据保持兼容。</p><button @click="emit('navigate','applications')">前往登录</button></section>
  <p v-if="message" class="feedback success">{{message}}</p><p v-if="error||store.error.value" class="feedback danger" role="alert">{{error||store.error.value}}</p>
</template>
<style scoped src="./ProductHome.css"></style>
