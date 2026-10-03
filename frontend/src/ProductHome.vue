<script setup lang="ts">
import { computed, nextTick, onActivated, onDeactivated, onMounted, onUnmounted, ref, watch } from 'vue'
import { api, apiCached, ApiError } from './api'
import { isFormalInterview } from './eventClassification'
import { useJobTrackerStore, type JobApplication, type JobEvent } from './jobTrackerStore'

type Page = 'applications' | 'calendar' | 'mail' | 'stats'
type AiStatus = { callsEnabled: boolean; message: string }
type Quote = { date: string; quote: string; author: string; generated: boolean }
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
const quoteKey = 'job_tracker_daily_quote_vue_v2'
const legacyQuoteKey = 'job_tracker_daily_quote_vue_v1'
const quote = ref<Quote>(fallbackQuote())
const quoteLoading = ref(false)
const quoteBurst = ref(0)
const message = ref('')
const error = ref('')
const busyId = ref('')
const scheduleAdvice = ref<ScheduleAdvice | null>(null)
const adviceLoading = ref(false)
const adviceNotice = ref('')
const sharedTimelines = ref<SharedTimelineUser[]>([])
const sharedGroupName = ref('')
const sharedTimelinesLoading = ref(false)
const sharedTimelinesNotice = ref('')
let adviceTimer: ReturnType<typeof setTimeout> | null = null
let messageTimer: ReturnType<typeof setTimeout> | null = null
let quoteBurstTimer: ReturnType<typeof setTimeout> | null = null
const homeEntering = ref(false)
const cardsRevealing = ref(false)
const timelineVisible = ref(false)
const adviceVisible = ref(false)
const detailsVisible = ref(false)
const confirmationVisible = ref(false)
const timelineSection = ref<HTMLElement | null>(null)
const adviceSection = ref<HTMLElement | null>(null)
const detailsSection = ref<HTMLElement | null>(null)
const confirmationSection = ref<HTMLElement | null>(null)
const scheduleList = ref<HTMLElement | null>(null)
const confirmationList = ref<HTMLElement | null>(null)
let sectionObserver: IntersectionObserver | null = null
let cardObserver: IntersectionObserver | null = null
let homeViewActive = false
let homeRevealRun = 0
let homeRevealStartedAt = 0
let homeSequenceScale = 1
let nextScheduleReveal = 0
let nextConfirmationReveal = 0

function stopHomeReveal() {
  homeRevealRun += 1
  sectionObserver?.disconnect()
  cardObserver?.disconnect()
  sectionObserver = null
  cardObserver = null
  homeEntering.value = false
  cardsRevealing.value = false
  timelineVisible.value = false
  adviceVisible.value = false
  detailsVisible.value = false
  confirmationVisible.value = false
  for (const section of [timelineSection.value, adviceSection.value, detailsSection.value, confirmationSection.value]) {
    section?.style.removeProperty('--home-section-delay')
  }
  for (const list of [scheduleList.value, confirmationList.value]) {
    list?.querySelectorAll<HTMLElement>('.home-card-visible').forEach(card => {
      card.classList.remove('home-card-visible')
      card.style.removeProperty('--home-card-delay')
    })
  }
}
function observeHomeSections() {
  if (!sectionObserver) return
  for (const [element, visible] of [
    [timelineSection.value, timelineVisible.value],
    [adviceSection.value, adviceVisible.value],
    [detailsSection.value, detailsVisible.value],
    [confirmationSection.value, confirmationVisible.value]
  ] as const) if (element && !visible) sectionObserver.observe(element)
}
function observeHomeCards() {
  if (!cardObserver) return
  cardObserver.disconnect()
  for (const list of [scheduleList.value, confirmationList.value]) {
    list?.querySelectorAll<HTMLElement>('article:not(.home-card-visible)').forEach(card => cardObserver?.observe(card))
  }
}
async function startHomeReveal() {
  stopHomeReveal()
  const run = homeRevealRun
  if (!homeViewActive || !store.user.value) return
  await nextTick()
  if (!homeViewActive || run !== homeRevealRun) return
  homeEntering.value = true
  cardsRevealing.value = typeof IntersectionObserver !== 'undefined'
  homeRevealStartedAt = performance.now()
  homeSequenceScale = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? .45 : 1
  await nextTick()
  if (!homeViewActive || run !== homeRevealRun) return
  if (typeof IntersectionObserver === 'undefined') {
    timelineVisible.value = adviceVisible.value = detailsVisible.value = confirmationVisible.value = true
    return
  }
  sectionObserver = new IntersectionObserver(entries => {
    if (!homeViewActive || run !== homeRevealRun) return
    entries.filter(entry => entry.isIntersecting).forEach(entry => {
      const section = entry.target as HTMLElement
      const targetStart = section === timelineSection.value ? 0
        : section === adviceSection.value ? 500
        : section === detailsSection.value ? 650 : 1000
      section.style.setProperty('--home-section-delay', `${Math.max(0, targetStart * homeSequenceScale - (performance.now() - homeRevealStartedAt))}ms`)
      if (entry.target === timelineSection.value) timelineVisible.value = true
      if (entry.target === adviceSection.value) adviceVisible.value = true
      if (entry.target === detailsSection.value) detailsVisible.value = true
      if (entry.target === confirmationSection.value) confirmationVisible.value = true
      sectionObserver?.unobserve(entry.target)
    })
  }, { threshold:0 })
  const duration = homeSequenceScale < 1 ? 200 : 520
  const interval = duration * .3
  nextScheduleReveal = nextConfirmationReveal = performance.now()
  cardObserver = new IntersectionObserver(entries => {
    if (!homeViewActive || run !== homeRevealRun) return
    const reveal = (list: HTMLElement | null, schedule: boolean) => {
      if (!list) return
      const cards = Array.from(list.querySelectorAll('article'))
      const visible = entries.filter(entry => entry.isIntersecting && cards.includes(entry.target as HTMLElement))
        .sort((left, right) => cards.indexOf(left.target as HTMLElement) - cards.indexOf(right.target as HTMLElement))
      if (!visible.length) return
      const now = performance.now()
      let next = schedule ? nextScheduleReveal : nextConfirmationReveal
      const initialStart = (schedule ? 760 : 1110) * homeSequenceScale + homeRevealStartedAt
      next = Math.max(now + 120 * homeSequenceScale, initialStart, Math.min(next, now + duration))
      visible.forEach(entry => {
        const card = entry.target as HTMLElement
        card.style.setProperty('--home-card-delay', `${Math.max(0, next - now)}ms`)
        card.classList.add('home-card-visible')
        next += interval
        cardObserver?.unobserve(card)
      })
      if (schedule) nextScheduleReveal = next
      else nextConfirmationReveal = next
    }
    reveal(scheduleList.value, true)
    reveal(confirmationList.value, false)
  }, { threshold:.05 })
  observeHomeSections()
  observeHomeCards()
}
onActivated(() => { homeViewActive = true; void startHomeReveal() })
onDeactivated(() => { homeViewActive = false; stopHomeReveal() })
watch(() => store.user.value?.id, () => { if (homeViewActive) void startHomeReveal() })
watch([timelineSection, adviceSection, detailsSection, confirmationSection], () => {
  if (homeEntering.value) void nextTick(observeHomeSections)
})
watch(adviceSection, (current, previous) => {
  if (!current && previous) adviceVisible.value = false
})

const upcomingItems = computed(() => store.events.value.filter(item => !item.completed && !item.missed && !isEnded(appFor(item)))
  .sort((a,b) => eventDeadline(a).localeCompare(eventDeadline(b))))
const recentSchedules = computed(() => upcomingItems.value)
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
const teamConflicts=computed(()=>teamSchedule.value.conflicts)
const teamConflictSummary=computed(()=>teamConflicts.value.length?`小组内发现 ${teamConflicts.value.length} 组日程间隔不足 1 小时，请及时协调。`:'小组内时间点日程之间均至少间隔 1 小时。')
const staleApplications = computed(() => store.applications.value.map(item => ({ item, health: progressHealth(item) }))
  .filter(row => row.health && row.health.days >= 10).sort((a,b) => (b.health?.days || 0) - (a.health?.days || 0)))
watch([scheduleList, confirmationList, recentSchedules, staleApplications], () => {
  if (cardsRevealing.value) void nextTick(observeHomeCards)
})

onMounted(async () => {
  await store.initialize()
  void loadSharedTimelines()
  const cached = loadCachedQuote()
  if (store.user.value && !cached) await generateQuote(false)
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
  if(!store.user.value||sharedTimelinesLoading.value)return
  sharedTimelinesLoading.value=true;sharedTimelinesNotice.value=''
  try{const result=await api<{groupId:string;groupName:string;users:SharedTimelineUser[]}>('/api/poc/event-sandbox/timelines');sharedTimelines.value=Array.isArray(result.users)?result.users:[];sharedGroupName.value=String(result.groupName||'')}
  catch{sharedTimelines.value=[];sharedGroupName.value='';sharedTimelinesNotice.value='小组时间轴暂时无法加载'}
  finally{sharedTimelinesLoading.value=false}
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
function progressHealth(item:JobApplication){
  if(isEnded(item)||item.stage==='Offer'||item.status==='已通过')return null
  const related=store.events.value.filter(event=>event.applicationId===item.id&&isFormalInterview(event))
  if(related.some(event=>!event.completed&&!event.missed&&parseTime(eventDeadline(event))>=Date.now()))return null
  const times=related.filter(event=>event.completed&&!event.missed).map(event=>parseTime(String(event.completedAt||eventDeadline(event)))).filter(Number.isFinite)
  if(!times.length)return null
  const days=Math.max(0,Math.floor((Date.now()-Math.max(...times))/86400000))
  return {days,label:days<=3?'进展正常':days<10?'等待较久':'等待确认'}
}
function appFor(event:JobEvent){return store.applications.value.find(item=>item.id===event.applicationId)}
function openApplicationDetail(event:JobEvent){const application=appFor(event);if(!application){error.value='未找到该日程关联的投递记录';return}emit('navigate','applications',String(application.id))}
function focusApplication(event:JobEvent){const application=appFor(event);if(!application){error.value='未找到该日程关联的投递记录';return}emit('navigate','applications',String(application.id),'focus')}
function eventCompany(event:JobEvent){return String(event.company||appFor(event)?.company||'未填写公司')}
function eventPosition(event:JobEvent){return String(event.position||appFor(event)?.position||'未填写岗位')}
function eventDate(event:JobEvent){
  const value=eventStart(event),endValue=String(event.endsAt||event.end||'')
  const date=new Date(value.replace(' ','T')),endDate=new Date(endValue.replace(' ','T'))
  if(Number.isNaN(date.getTime()))return {tag:'待定',date:'未设置',time:'',range:false,endDate:'',endTime:''}
  const range=Boolean(endValue.trim())&&!Number.isNaN(endDate.getTime())
  const same=today()===value.slice(0,10),dateText=`${date.getMonth()+1}月${date.getDate()}日`,time=`${pad(date.getHours())}:${pad(date.getMinutes())}`
  const endDateText=range?`${endDate.getMonth()+1}月${endDate.getDate()}日`:''
  const endTime=range?`${pad(endDate.getHours())}:${pad(endDate.getMinutes())}`:''
  return {tag:same?'今天':'',date:dateText,time,range,endDate:endDateText,endTime}
}
function fallbackQuote():Quote{const items=['今天多走一步，明天就多一个选择。','把注意力放在能推进的下一步上。','每一次认真准备，都在靠近更合适的机会。','慢一点没有关系，只要方向仍在向前。','机会会迟到，但你的积累不会白费。','先完成今天能完成的，再把答案交给时间。','保持行动，好的结果往往在坚持之后出现。'];return {date:today(),quote:items[new Date().getDay()],author:'',generated:false}}
function quoteCacheKey(){return quoteKey+'_'+String(store.user.value?.email||'guest').toLowerCase()}
function loadCachedQuote(){try{const cached=JSON.parse(localStorage.getItem(quoteCacheKey())||localStorage.getItem(legacyQuoteKey)||'null') as Quote|null;if(cached?.date===today()&&cached.quote){quote.value=cached;localStorage.setItem(quoteCacheKey(),JSON.stringify(cached));return true}}catch{/* 使用本地内容 */}return false}
function celebrateQuote(){quoteBurst.value+=1;if(quoteBurstTimer)clearTimeout(quoteBurstTimer);quoteBurstTimer=setTimeout(()=>{quoteBurst.value=0},900)}
async function generateQuote(force:boolean){
  if(!store.user.value||quoteLoading.value)return
  quoteLoading.value=true
  error.value=''
  let refreshed=false
  try{
    const status=await apiCached<AiStatus>('/api/poc/ai-sandbox/status')
    if(!status.callsEnabled){quote.value=fallbackQuote();return}
    const value=await api<{quote:string;author:string}>('/api/poc/ai-sandbox/daily-quote',{method:'POST',body:JSON.stringify({date:today()})})
    quote.value={date:today(),quote:value.quote,author:value.author||'',generated:true}
    localStorage.setItem(quoteCacheKey(),JSON.stringify(quote.value))
    refreshed=true
  }catch(cause){
    quote.value=fallbackQuote()
    error.value=cause instanceof Error?cause.message:'每日一语生成失败'
  }finally{
    quoteLoading.value=false
    if(force&&refreshed){await nextTick();celebrateQuote()}
  }
}
async function completeEvent(event:JobEvent){busyId.value=event.id;error.value='';message.value='';try{await api(`/api/poc/event-sandbox/events/${encodeURIComponent(event.id)}/resolution`,{method:'POST',body:JSON.stringify({action:'complete',expectedUpdatedAt:String(event.updatedAt||event.createdAt||'')})});await store.refresh();await loadSharedTimelines();message.value='日程已完成'}catch(cause){error.value=cause instanceof Error?cause.message:'更新日程失败'}finally{busyId.value=''}}
async function markRejected(item:JobApplication){if(!confirm(`确认将“${item.company} · ${item.position}”标记为未通过吗？`))return;busyId.value=item.id;error.value='';message.value='';try{await api(`/api/poc/application-sandbox/applications/${encodeURIComponent(item.id)}`,{method:'PUT',body:JSON.stringify({company:item.company||'',position:item.position||'',city:item.city||'',channel:item.channel||'',appliedDate:item.appliedDate||'',stage:'已结束',status:'未通过',notes:item.notes||'',expectedUpdatedAt:item.updatedAt||''})});await store.refresh();message.value='已标记为未通过'}catch(cause){error.value=cause instanceof Error?cause.message:'更新投递失败'}finally{busyId.value=''}}

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
onUnmounted(()=>{stopHomeReveal();if(adviceTimer)clearTimeout(adviceTimer);if(messageTimer)clearTimeout(messageTimer);if(quoteBurstTimer)clearTimeout(quoteBurstTimer)})</script>

<template>
  <div v-if="store.user.value" class="home-dashboard" :class="{'home-entering':homeEntering}">
    <Teleport defer to="#home-quote-slot"><section class="quote-strip" :class="{'is-refreshing':quoteLoading,'is-refreshed':quoteBurst,'home-quote-entering':homeEntering}" :aria-busy="quoteLoading"><span v-if="quoteBurst" :key="quoteBurst" class="quote-sparks" aria-hidden="true"><i v-for="index in 7" :key="index"></i></span><button class="quote-trigger" :disabled="quoteLoading" title="换一句" aria-label="刷新每日一语" @click="generateQuote(true)"><span class="quote-glyph" aria-hidden="true">✦</span></button><span class="quote-copy"><small>每日一语</small><strong :title="quote.author?`${quote.quote} — ${quote.author}`:quote.quote">{{quote.quote}}<em v-if="quote.author"> — {{quote.author}}</em></strong></span></section></Teleport>
    <section class="dashboard-panel">
      <section ref="timelineSection" class="timeline-column team-timeline" :class="{'home-visible':timelineVisible}" aria-labelledby="team-timeline-title">
        <div class="column-heading"><div><strong id="team-timeline-title">{{sharedGroupName||'我的'}}日程时间轴</strong><small>仅展示小组成员的时间点日程，时间段日程保留在个人详情中</small></div><span>{{timelineMembers.length}} 人 · {{teamTimeline.length}} 项</span></div>
        <div v-if="timelineMembers.length" class="member-legend" aria-label="小组成员颜色图例"><span v-for="member in timelineMembers" :key="member.email"><i :style="{'--member-color':member.color}" aria-hidden="true"></i><b>{{member.name}}</b><small>{{member.events.length}} 项</small></span></div>
        <div v-if="teamTimeline.length" class="timeline-scroll"><div class="timeline-list team-timeline-list"><article v-for="(item,index) in teamTimeline" :key="item.id" :class="{conflict:item.conflict}" :style="{'--member-color':item.color,'--home-node-delay':`${Math.min(index,8)*75}ms`,'--home-node-short-delay':`${Math.min(index,8)*30}ms`}" :aria-label="`${item.name}，${item.date} ${item.start}，${item.label}${item.conflict?'，与小组其他日程冲突':''}`"><time>{{item.date.slice(5).replace('-','月')+'日'}}</time><span v-if="item.conflict" class="warning-triangle team-node-warning" aria-hidden="true"><svg viewBox="0 0 24 22"><path d="M10.2 1.8a2.1 2.1 0 0 1 3.6 0l9.4 16.3a2.1 2.1 0 0 1-1.8 3.1H2.6a2.1 2.1 0 0 1-1.8-3.1L10.2 1.8Z"></path><path class="warning-mark" d="M12 7v6.2M12 17.2v.1"></path></svg></span><i></i><span><b>{{item.start}}</b><em><strong>{{item.name}}</strong>{{item.label}}</em></span></article></div></div>
        <div v-if="teamConflicts.length" class="team-conflict-alert" role="alert"><strong>小组日程冲突</strong><span v-for="item in teamConflicts" :key="item.id">{{item.text}}</span></div>
        <div v-if="!teamTimeline.length&&sharedTimelinesLoading" class="timeline-loading" role="status"><i aria-hidden="true"></i><span>正在加载小组日程…</span></div>
        <p v-else-if="!teamTimeline.length&&sharedTimelinesNotice" class="timeline-notice">{{sharedTimelinesNotice}}</p>
        <div v-else-if="!teamTimeline.length" class="timeline-empty">当前小组暂无待完成的时间点日程</div>
      </section>

      <section v-if="adviceLoading||scheduleAdvice||teamConflicts.length" ref="adviceSection" class="schedule-advice" :class="{'home-visible':adviceVisible}" aria-live="polite">
        <div class="advice-head"><div class="advice-title"><button class="advice-trigger" :class="{'is-loading':adviceLoading}" :disabled="adviceLoading" title="重新生成安排建议" aria-label="重新生成安排建议" @click="generateScheduleAdvice(adviceSignature,0,true)"><span class="advice-glyph" aria-hidden="true"><svg viewBox="0 0 24 24"><circle cx="5" cy="6" r="2"></circle><circle cx="19" cy="18" r="2"></circle><path d="M7 6h4.5a3.5 3.5 0 0 1 0 7H10a3 3 0 0 0 0 6h7"></path></svg></span></button><div><strong>安排建议</strong><small>{{adviceLoading?'正在计算安排建议…':teamConflictSummary}}</small></div></div></div>
        <p v-if="adviceNotice&&!adviceLoading" class="advice-notice">{{adviceNotice}}</p>
        <div v-if="scheduleAdvice?.warnings?.length" class="advice-warnings"><strong>时间紧张</strong><span v-for="item in scheduleAdvice.warnings" :key="item">{{item}}</span></div>
      </section>
      <section ref="detailsSection" class="details-column" :class="{'home-visible':detailsVisible}" aria-labelledby="my-schedule-title">
        <div class="column-heading"><div><strong id="my-schedule-title">我的日程详情</strong><small>可编辑或完成自己的日程</small></div><span>{{recentSchedules.length}} 项</span></div>
        <div v-if="recentSchedules.length" ref="scheduleList" class="schedule-list" :class="{'home-card-revealing':cardsRevealing}">
          <article v-for="(event,index) in recentSchedules" :key="event.id">
            <button type="button" class="schedule-row-target" :aria-label="`在投递记录中定位：${eventCompany(event)} · ${event.title||event.type||'未命名日程'}`" @click="focusApplication(event)"></button>
            <div v-if="eventDate(event).range" class="date-range"><div class="date-block"><em v-if="eventDate(event).tag">{{eventDate(event).tag}}</em><strong>{{eventDate(event).date}}</strong><small>{{eventDate(event).time}}</small></div><i>至</i><div class="date-block"><strong>{{eventDate(event).endDate}}</strong><small>{{eventDate(event).endTime}}</small></div></div><div v-else class="date-block"><em v-if="eventDate(event).tag">{{eventDate(event).tag}}</em><strong>{{eventDate(event).date}}</strong><small>{{eventDate(event).time}}</small></div>
            <div class="schedule-copy"><a v-if="String(event.location||'').startsWith('http')" class="schedule-link" :href="String(event.location)" target="_blank" rel="noopener noreferrer" :aria-label="`打开日程链接：${eventCompany(event)} · ${event.title||event.type||'未命名日程'}`"><strong>{{eventCompany(event)}} · {{event.title||event.type||'未命名日程'}}</strong><span aria-hidden="true">↗</span></a><strong v-else>{{eventCompany(event)}} · {{event.title||event.type||'未命名日程'}}</strong><p>{{eventPosition(event)}}</p><small v-if="String(event.notes||'').trim()">备注：{{event.notes}}</small></div>
            <div class="schedule-actions"><i v-if="index===0">下一场</i><button class="secondary icon-button" type="button" :aria-label="`编辑日程：${event.title||event.type||'未命名日程'}`" title="编辑日程" @click="openApplicationDetail(event)"><AppIcon name="edit" /></button><button class="icon-button" type="button" :disabled="busyId===event.id||store.readOnly.value" :aria-label="`完成日程：${event.title||event.type||'未命名日程'}`" title="标记完成" @click="completeEvent(event)"><AppIcon name="check" /></button></div>
          </article>
        </div>
        <div v-else class="empty">暂无待完成日程。<button type="button" class="text-link" @click="emit('navigate','calendar')">前往日程安排</button></div>
      </section>
    </section>

    <section ref="confirmationSection" class="dashboard-panel confirmation-panel" :class="{'home-visible':confirmationVisible}">
      <div class="panel-head"><div><h2>人工确认 <span title="面试结束较久且没有新进展的岗位">ⓘ</span></h2><p>面试结束达到 10 天仍无进展的岗位，请确认是否标记为未通过。</p></div><b v-if="staleApplications.length">{{staleApplications.length}} 个待确认</b></div>
      <div v-if="staleApplications.length" ref="confirmationList" class="confirmation-list" :class="{'home-card-revealing':cardsRevealing}"><article v-for="row in staleApplications" :key="row.item.id"><div><strong>{{row.item.company||'未填写公司'}}</strong><span>{{row.item.position||'未填写岗位'}}</span></div><div class="progress-line"><i>已投递</i><span>→</span><i>测评 / 笔试</i><span>→</span><i>面试</i><span>→</span><i class="current">等待结果</i></div><em>{{row.health?.days}}天无进展</em><button :disabled="busyId===row.item.id||store.readOnly.value" @click="markRejected(row.item)">标记未通过</button></article></div>
      <div v-else class="empty">目前没有需要人工确认的岗位。</div>
    </section>
  </div>

  <section v-else-if="store.initialized.value" class="card sign-in-card"><h2>登录后查看你的求职进展</h2><p>使用现有账号即可进入，新旧系统账号及业务数据保持兼容。</p><button @click="emit('navigate','applications')">前往登录</button></section>
  <p v-if="message" class="feedback success">{{message}}</p><p v-if="error||store.error.value" class="feedback danger" role="alert">{{error||store.error.value}}</p>
</template>
<style scoped src="./ProductHome.css"></style>
