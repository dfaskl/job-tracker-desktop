<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { api, apiCached, ApiError } from './api'
import { isFormalInterview } from './eventClassification'
import { useJobTrackerStore, type JobApplication, type JobEvent } from './jobTrackerStore'

type Page = 'applications' | 'calendar' | 'mail' | 'stats'
type AiStatus = { callsEnabled: boolean; message: string }
type Quote = { date: string; quote: string; author: string; generated: boolean }
type TimelineEntry = { id:string; label:string; date:string; start:string; end:string; status:'normal'|'tight'|'conflict'; flexible:boolean; showWindow:boolean; windowStart?:string; windowEnd?:string }
type ScheduleAdvice = { summary: string; plans: string[]; timeline?: TimelineEntry[]; warnings?: string[]; conflicts: string[] }
const emit = defineEmits<{ navigate: [page: Page] }>()
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
let adviceTimer: ReturnType<typeof setTimeout> | null = null
let messageTimer: ReturnType<typeof setTimeout> | null = null
let quoteBurstTimer: ReturnType<typeof setTimeout> | null = null

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
const adviceTimeline = computed(() => {
  type Label = {text:string;status:'normal'|'tight'|'conflict'}
  type Group = {id:string;date:string;start:string;timeLabel:string;status:'normal'|'tight'|'conflict';labels:Label[]}
  const groups = new Map<string,Group>(), rank={normal:0,tight:1,conflict:2}
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
const staleApplications = computed(() => store.applications.value.map(item => ({ item, health: progressHealth(item) }))
  .filter(row => row.health && row.health.days >= 10).sort((a,b) => (b.health?.days || 0) - (a.health?.days || 0)))

onMounted(async () => {
  await store.initialize()
  const cached = loadCachedQuote()
  if (store.user.value && !cached) await generateQuote(false)
})
function pad(value:number){return String(value).padStart(2,'0')}
function localText(date=new Date()){return `${date.getFullYear()}-${pad(date.getMonth()+1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`}
function today(){return localText().slice(0,10)}
function eventStart(item:Record<string,unknown>){return String(item.startsAt||item.start||item.date||'')}
function eventDeadline(item:Record<string,unknown>){return String(item.endsAt||item.end||eventStart(item))}
function parseTime(value:string){const time=new Date(value.replace(' ','T')).getTime();return Number.isFinite(time)?time:Infinity}
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
function eventCompany(event:JobEvent){return String(event.company||appFor(event)?.company||'未填写公司')}
function eventPosition(event:JobEvent){return String(event.position||appFor(event)?.position||'未填写岗位')}
function eventDate(event:JobEvent){
  const value=eventStart(event),endValue=String(event.endsAt||event.end||'')
  const date=new Date(value.replace(' ','T')),endDate=new Date(endValue.replace(' ','T'))
  if(Number.isNaN(date.getTime()))return {tag:'待定',date:'未设置',time:'',range:false,endDate:'',endTime:''}
  const range=Boolean(endValue.trim())&&!Number.isNaN(endDate.getTime())&&!event.completed
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
async function completeEvent(event:JobEvent){busyId.value=event.id;error.value='';try{await api(`/api/poc/event-sandbox/events/${encodeURIComponent(event.id)}/resolution`,{method:'POST',body:JSON.stringify({action:'complete',expectedUpdatedAt:String(event.updatedAt||event.createdAt||'')})});await store.refresh();message.value='日程已完成'}catch(cause){error.value=cause instanceof Error?cause.message:'更新日程失败'}finally{busyId.value=''}}
async function markRejected(item:JobApplication){if(!confirm(`确认将“${item.company} · ${item.position}”标记为未通过吗？`))return;busyId.value=item.id;error.value='';try{await api(`/api/poc/application-sandbox/applications/${encodeURIComponent(item.id)}`,{method:'PUT',body:JSON.stringify({company:item.company||'',position:item.position||'',city:item.city||'',channel:item.channel||'',appliedDate:item.appliedDate||'',stage:'已结束',status:'未通过',notes:item.notes||'',expectedUpdatedAt:item.updatedAt||''})});await store.refresh();message.value='已标记为未通过'}catch(cause){error.value=cause instanceof Error?cause.message:'更新投递失败'}finally{busyId.value=''}}

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
onUnmounted(()=>{if(adviceTimer)clearTimeout(adviceTimer);if(messageTimer)clearTimeout(messageTimer);if(quoteBurstTimer)clearTimeout(quoteBurstTimer)})</script>

<template>
  <div v-if="store.user.value" class="home-dashboard">
    <Teleport defer to="#home-quote-slot"><section class="quote-strip" :class="{'is-refreshing':quoteLoading,'is-refreshed':quoteBurst}"><span v-if="quoteBurst" :key="quoteBurst" class="quote-sparks" aria-hidden="true"><i v-for="index in 7" :key="index"></i></span><button class="quote-trigger" :disabled="quoteLoading" title="换一句" aria-label="刷新每日一语" @click="generateQuote(true)"><span class="quote-glyph" aria-hidden="true">✦</span></button><span class="quote-copy"><small>每日一语</small><strong>{{quote.quote}}<em v-if="quote.author"> — {{quote.author}}</em></strong></span></section></Teleport>
    <section class="dashboard-panel">
      <div class="panel-head"><h2>近期日程 <span title="显示最近的待办、笔试和面试安排">ⓘ</span></h2><button class="text-link" @click="emit('navigate','calendar')">查看全部</button></div>
      <section v-if="adviceLoading || scheduleAdvice || (adviceCandidates.length>1 && adviceNotice)" class="schedule-advice"><div class="advice-head"><div class="advice-title"><button class="advice-trigger" :class="{'is-loading':adviceLoading}" :disabled="adviceLoading" title="重新生成安排建议" aria-label="重新生成安排建议" @click="generateScheduleAdvice(adviceSignature,0,true)"><span class="advice-glyph" aria-hidden="true"><svg viewBox="0 0 24 24"><circle cx="5" cy="6" r="2"></circle><circle cx="19" cy="18" r="2"></circle><path d="M7 6h4.5a3.5 3.5 0 0 1 0 7H10a3 3 0 0 0 0 6h7"></path></svg></span></button><div><strong>安排建议</strong><small>{{adviceLoading?'正在计算安排建议…':scheduleAdvice?.summary}}</small></div></div></div><p v-if="adviceNotice && !adviceLoading" class="advice-notice">{{adviceNotice}}</p><template v-if="scheduleAdvice"><aside v-if="scheduleAdvice.plans?.length" class="advice-timeline" aria-label="日程时间轴"><strong>时间轴</strong><div class="timeline-scroll"><div class="timeline-list"><article v-for="item in adviceTimeline" :key="item.id" :class="item.status"><time>{{item.date.slice(5).replace('-','月')+'日'}}</time><i></i><span><b v-if="item.start">{{item.timeLabel}}</b><em v-for="label in item.labels" :key="label.text" :class="label.status">{{label.text}}</em></span></article></div></div></aside><div v-if="scheduleAdvice.warnings?.length" class="advice-warnings"><strong>时间紧张</strong><span v-for="item in scheduleAdvice.warnings" :key="item">{{item}}</span></div><div v-if="scheduleAdvice.conflicts?.length" class="advice-conflicts"><strong>时间冲突</strong><span v-for="item in scheduleAdvice.conflicts" :key="item">{{item}}</span></div></template></section>
      <div v-if="recentSchedules.length" class="schedule-list">
        <article v-for="(event,index) in recentSchedules" :key="event.id">
          <div v-if="eventDate(event).range" class="date-range"><div class="date-block"><em v-if="eventDate(event).tag">{{eventDate(event).tag}}</em><strong>{{eventDate(event).date}}</strong><small>{{eventDate(event).time}}</small></div><i>至</i><div class="date-block"><strong>{{eventDate(event).endDate}}</strong><small>{{eventDate(event).endTime}}</small></div></div><div v-else class="date-block"><em v-if="eventDate(event).tag">{{eventDate(event).tag}}</em><strong>{{eventDate(event).date}}</strong><small>{{eventDate(event).time}}</small></div>
          <div class="schedule-copy"><strong>{{eventCompany(event)}} · {{event.title||event.type||'未命名日程'}}</strong><p>{{eventPosition(event)}} <a v-if="String(event.location||'').startsWith('http')" :href="String(event.location)" target="_blank" rel="noreferrer">· 打开链接 ↗</a> <b>{{event.type||'其他'}}</b></p><small>备注：{{event.notes||'暂无备注'}}</small></div>
          <div class="schedule-actions"><i v-if="index===0">下一场</i><button class="secondary" @click="emit('navigate','calendar')">编辑</button><button :disabled="busyId===event.id||store.readOnly.value" @click="completeEvent(event)">完成</button></div>
        </article>
      </div>
      <div v-else class="empty">暂无待完成日程。</div>
    </section>

    <section class="dashboard-panel confirmation-panel">
      <div class="panel-head"><div><h2>人工确认 <span title="面试结束较久且没有新进展的岗位">ⓘ</span></h2><p>面试结束达到 10 天仍无进展的岗位，请确认是否标记为未通过。</p></div><b v-if="staleApplications.length">{{staleApplications.length}} 个待确认</b></div>
      <div v-if="staleApplications.length" class="confirmation-list"><article v-for="row in staleApplications" :key="row.item.id"><div><strong>{{row.item.company||'未填写公司'}}</strong><span>{{row.item.position||'未填写岗位'}}</span></div><div class="progress-line"><i>已投递</i><span>→</span><i>测评 / 笔试</i><span>→</span><i>面试</i><span>→</span><i class="current">等待结果</i></div><em>{{row.health?.days}}天无进展</em><button :disabled="busyId===row.item.id||store.readOnly.value" @click="markRejected(row.item)">标记未通过</button></article></div>
      <div v-else class="empty">目前没有需要人工确认的岗位。</div>
    </section>
  </div>

  <section v-else-if="store.initialized.value" class="card sign-in-card"><h2>登录后查看你的求职进展</h2><p>使用现有账号即可进入，新旧系统账号及业务数据保持兼容。</p><button @click="emit('navigate','applications')">前往登录</button></section>
  <p v-if="message" class="feedback success">{{message}}</p><p v-if="error||store.error.value" class="feedback danger" role="alert">{{error||store.error.value}}</p>
</template>

<style scoped>
.home-dashboard {
  --home-ink: #16233a;
  --home-muted: #68788d;
  --home-paper: #fcfdfb;
  --home-line: #d8e1e8;
  --home-progress: #19725a;
  --home-deadline: #d07832;
  display: grid;
  gap: 22px;
  padding: 18px 0 30px;
  color: var(--home-ink);
  font-family: "MiSans", "HarmonyOS Sans SC", "Microsoft YaHei UI", "Microsoft YaHei", sans-serif;
  font-variant-numeric: tabular-nums;
}

.quote-strip {
  display: inline-flex;
  width: fit-content;
  max-width: min(880px, 100%);
  min-width: 0;
  align-items: center;
  gap: 11px;
  margin: 0;
  padding: 9px 12px;
  border: 1px solid color-mix(in srgb, var(--accent, var(--color-primary)) 24%, #d7e0e9);
  border-radius: 7px;
  color: var(--home-ink);
  background: rgba(252, 253, 251, .94);
}
.quote-trigger {
  display: grid;
  width: 30px;
  height: 30px;
  min-height: 30px;
  flex: 0 0 30px;
  padding: 0;
  place-items: center;
  border: 0;
  border-radius: 50%;
  color: #fff;
  background: var(--accent, var(--color-primary));
  cursor: pointer;
}
.quote-trigger:hover { box-shadow: 0 0 0 4px color-mix(in srgb,var(--accent,var(--color-primary)) 12%,transparent); }
.quote-trigger:disabled { cursor: wait; }
.quote-trigger > span { display:grid;place-items:center;line-height:1; }
.quote-copy { display: grid; min-width: 0; max-width: 72ch; flex: 0 1 auto; }
.quote-strip small { color: var(--accent, var(--color-primary)); font-size: 10px; font-weight: 700; }
.quote-strip strong {
  overflow-wrap: anywhere;
  font-size: 12px;
  font-weight: 550;
  line-height: 1.45;
  white-space: normal;
}
.quote-strip em { color: var(--home-muted); font-style: normal; }
.quote-trigger:focus-visible,
.text-link:focus-visible,
.advice-trigger:focus-visible,
.schedule-actions button:focus-visible,
.confirmation-list button:focus-visible {
  outline: 3px solid color-mix(in srgb, var(--accent, var(--color-primary)) 24%, transparent);
  outline-offset: 2px;
}

.quote-strip { position: relative; isolation: isolate; transition: border-color .2s ease, background-color .2s ease, box-shadow .2s ease; }
.quote-strip.is-refreshing {
  border-color: color-mix(in srgb, var(--accent, var(--color-primary)) 48%, #d7e0e9);
  animation: quote-breathe 1.25s ease-in-out infinite;
}
.quote-strip.is-refreshing > .quote-trigger > .quote-glyph { animation: quote-refresh-spin .85s linear infinite; }
.quote-strip.is-refreshed {
  border-color: color-mix(in srgb, var(--accent, var(--color-primary)) 62%, #fff);
  background: color-mix(in srgb, var(--accent, var(--color-primary)) 5%, #fff);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--accent, var(--color-primary)) 10%, transparent);
}
.quote-sparks { position:absolute; inset:0; z-index:3; overflow:visible; pointer-events:none; }
.quote-sparks > i {
  position:absolute;
  top:50%;
  left:50%;
  width:9px;
  height:9px;
  border-radius:1px;
  background:var(--accent, var(--color-primary));
  clip-path:polygon(50% 0,62% 36%,100% 50%,62% 64%,50% 100%,38% 64%,0 50%,38% 36%);
  opacity:0;
  filter:drop-shadow(0 0 3px color-mix(in srgb,var(--accent,var(--color-primary)) 34%,transparent));
  animation:quote-spark .82s cubic-bezier(.16,.78,.24,1) forwards;
}
.quote-sparks > i:nth-child(even){width:7px;height:7px;background:#e7ad38}
.quote-sparks > i:nth-child(1){--spark-x:-30px;--spark-y:-24px;left:5%;top:16%;animation-delay:.02s}
.quote-sparks > i:nth-child(2){--spark-x:-18px;--spark-y:-30px;left:27%;top:5%;animation-delay:.1s}
.quote-sparks > i:nth-child(3){--spark-x:12px;--spark-y:-32px;left:52%;top:4%;animation-delay:.04s}
.quote-sparks > i:nth-child(4){--spark-x:29px;--spark-y:-23px;left:90%;top:14%;animation-delay:.12s}
.quote-sparks > i:nth-child(5){--spark-x:31px;--spark-y:24px;left:94%;top:80%;animation-delay:.05s}
.quote-sparks > i:nth-child(6){--spark-x:10px;--spark-y:30px;left:63%;top:94%;animation-delay:.14s}
.quote-sparks > i:nth-child(7){--spark-x:-29px;--spark-y:23px;left:10%;top:84%;animation-delay:.08s}
@keyframes quote-breathe {
  0%,100% { box-shadow:0 0 0 0 color-mix(in srgb,var(--accent,var(--color-primary)) 6%,transparent); background:rgba(252,253,251,.94); }
  50% { box-shadow:0 0 0 5px color-mix(in srgb,var(--accent,var(--color-primary)) 12%,transparent); background:color-mix(in srgb,var(--accent,var(--color-primary)) 4%,#fff); }
}
@keyframes quote-refresh-spin { to { transform:rotate(360deg); } }
@keyframes quote-spark {
  0% { opacity:0; transform:translate(-50%,-50%) scale(.2) rotate(0); }
  26% { opacity:1; transform:translate(-50%,-50%) scale(1.15) rotate(35deg); }
  62% { opacity:.9; transform:translate(calc(-50% + var(--spark-x)),calc(-50% + var(--spark-y))) scale(.92) rotate(82deg); }
  100% { opacity:0; transform:translate(calc(-50% + var(--spark-x)),calc(-50% + var(--spark-y))) scale(.28) rotate(135deg); }
}
@media (prefers-reduced-motion: reduce) {
  .quote-strip.is-refreshing, .quote-strip.is-refreshing > .quote-trigger > .quote-glyph, .advice-trigger.is-loading > .advice-glyph { animation:none; }
  .quote-sparks > i { animation-name:quote-spark-soft;animation-duration:.55s; }
}
@keyframes quote-spark-soft {
  0%,100% { opacity:0;transform:translate(-50%,-50%) scale(.7); }
  45% { opacity:1;transform:translate(-50%,-50%) scale(1); }
}

.dashboard-panel {
  position: relative;
  overflow: hidden;
  padding: 25px 28px 28px;
  border: 1px solid var(--home-line);
  border-radius: 20px;
  background: var(--home-paper);
  box-shadow: 0 16px 48px rgba(35, 53, 75, .07);
}
.dashboard-panel:first-of-type::before {
  content: "";
  position: absolute;
  inset: 0 auto 0 0;
  width: 5px;
  background: linear-gradient(180deg, var(--accent, var(--color-primary)), var(--home-progress) 58%, var(--home-deadline));
}

.panel-head,
.schedule-actions,
.confirmation-list article {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}
.panel-head { padding-left: 4px; }
.panel-head h2 {
  margin: 0;
  color: var(--home-ink);
  font-size: clamp(20px, 1.45vw, 25px);
  font-weight: 760;
  letter-spacing: -.035em;
}
.panel-head h2 span { color: var(--accent, var(--color-primary)); font-size: 14px; letter-spacing: 0; }
.panel-head p { max-width: 64ch; margin: 6px 0 0; color: var(--home-muted); }
.text-link {
  padding: 9px 14px;
  border: 1px solid color-mix(in srgb, var(--accent, var(--color-primary)) 28%, #d8e0e8);
  color: var(--accent, var(--color-primary));
  background: transparent;
}

.schedule-advice {
  display: grid;
  gap: 14px;
  margin-top: 18px;
  padding: 18px;
  border: 1px solid color-mix(in srgb, var(--accent, var(--color-primary)) 28%, #cfdae4);
  border-radius: 14px;
  background:
    linear-gradient(115deg, color-mix(in srgb, var(--accent, var(--color-primary)) 8%, #fff), rgba(255,255,255,.92) 42%),
    repeating-linear-gradient(90deg, transparent 0 79px, rgba(31,67,91,.035) 79px 80px);
  animation: track-reveal .34s cubic-bezier(.2,.7,.2,1) both;
}
.advice-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.advice-title { display: flex; min-width: 0; align-items: center; gap: 12px; }
.advice-trigger {
  display: grid;
  width: 36px;
  height: 36px;
  min-height: 36px;
  flex: 0 0 36px;
  padding: 0;
  place-items: center;
  border: 0;
  border-radius: 9px 9px 9px 2px;
  color: #fff;
  background: var(--accent, var(--color-primary));
  cursor: pointer;
}
.advice-trigger:hover { box-shadow: 0 0 0 4px color-mix(in srgb,var(--accent,var(--color-primary)) 12%,transparent); }
.advice-trigger:disabled { cursor: wait; }
.advice-glyph{display:grid;width:20px;height:20px;place-items:center;transform-origin:center}.advice-glyph svg{width:20px;height:20px;overflow:visible;fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round}.advice-trigger.is-loading > .advice-glyph svg{display:none}.advice-trigger.is-loading > .advice-glyph::before{content:"✦";line-height:1}.advice-trigger.is-loading > .advice-glyph { animation: quote-refresh-spin .85s linear infinite; }
.advice-title > div { display: grid; min-width: 0; gap: 3px; }
.advice-title strong { font-size: 16px; }
.advice-title small { color: var(--home-muted); line-height: 1.5; }
.advice-notice { margin: 0; color: #8a5608; font-size: 12px; }

.advice-timeline {
  min-width: 0;
  padding: 13px 15px 8px;
  border: 1px solid rgba(25, 114, 90, .2);
  border-radius: 10px;
  background: rgba(252, 253, 251, .82);
}
.advice-timeline > strong {
  display: block;
  margin-bottom: 6px;
  color: var(--home-progress);
  font-size: 11px;
}
.timeline-scroll { overflow-x: auto; padding: 2px 2px 10px; scrollbar-width: thin; }
.timeline-list {
  position: relative;
  display: flex;
  width: max-content;
  min-width: 100%;
  align-items: flex-start;
  padding: 24px 14px 0;
}
.timeline-list::before {
  content: "";
  position: absolute;
  top: 42px;
  right: 14px;
  left: 14px;
  height: 2px;
  background: linear-gradient(90deg, var(--home-progress), #b7d2c8 70%, var(--home-deadline));
}
.timeline-list article {
  position: relative;
  display: grid;
  width: max-content;
  min-width: 112px;
  flex: 0 0 auto;
  grid-template-rows: 18px 18px auto;
  justify-items: center;
  padding: 0 9px;
}
.timeline-list time { color: var(--home-muted); font-size: 10px; }
.timeline-list i {
  z-index: 1;
  width: 12px;
  height: 12px;
  border: 3px solid var(--home-paper);
  border-radius: 50%;
  background: var(--home-progress);
  box-shadow: 0 0 0 2px #84b9a7;
}
.timeline-list article:nth-child(4n+2) i { background: #3e77c5; box-shadow: 0 0 0 2px #a9c6e9; }
.timeline-list article:nth-child(4n+3) i { background: var(--home-deadline); box-shadow: 0 0 0 2px #edc99e; }
.timeline-list article:nth-child(4n) i { background: #8662b8; box-shadow: 0 0 0 2px #c7b6df; }
.timeline-list article.tight i { background: #d89522; box-shadow: 0 0 0 2px #f0ce91; }
.timeline-list article.conflict i { background: #c94a44; box-shadow: 0 0 0 2px #efaaa5; }
.timeline-list span { display: grid; width: 100%; gap: 4px; padding-top: 6px; text-align: center; }
.timeline-list b { color: #284f43; font-size: 11px; }
.timeline-list em {
  min-width: 100%;
  padding: 4px 8px;
  border: 1px solid rgba(25,114,90,.1);
  border-radius: 4px;
  color: #2f4053;
  background: #eef5f2;
  font-size: 11px;
  font-style: normal;
  white-space: nowrap;
}
.timeline-list em.tight { color: #875716; background: #fff0c9; }
.timeline-list em.conflict { color: #a43731; background: #ffe4e1; }

.advice-warnings,
.advice-conflicts {
  display: grid;
  gap: 5px;
  padding: 10px 12px;
  border-left: 3px solid currentColor;
  border-radius: 4px;
  font-size: 12px;
}
.advice-warnings { color: #8b5a18; background: #fff5e4; }
.advice-conflicts { color: #9d342e; background: #fff0ee; }
.advice-warnings span::before,
.advice-conflicts span::before { content: "• "; }

.schedule-list {
  display: grid;
  margin-top: 22px;
  border-top: 1px solid var(--home-line);
}
.schedule-list article {
  position: relative;
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr) max-content;
  align-items: center;
  gap: 20px;
  min-height: 106px;
  padding: 18px 4px;
  border-bottom: 1px solid var(--home-line);
}
.schedule-list article:last-child { border-bottom: 0; }
.date-block {
  display: grid;
  width: max-content;
  min-width: 82px;
  gap: 2px;
  padding: 10px 12px;
  border-left: 4px solid var(--accent, var(--color-primary));
  border-radius: 2px 9px 9px 2px;
  background: color-mix(in srgb, var(--accent, var(--color-primary)) 7%, #fff);
}
.date-block em {
  width: max-content;
  margin: -17px 0 2px;
  padding: 2px 7px;
  border-radius: 999px;
  color: #b9502e;
  background: #ffe9df;
  font-size: 10px;
  font-style: normal;
}
.date-block strong { color: var(--accent, var(--color-primary)); font-size: 16px; white-space: nowrap; }
.date-block small { color: var(--home-muted); }
.date-range { display: flex; width: max-content; align-items: center; gap: 7px; }
.date-range > .date-block { min-width: 76px; border-left-color: var(--home-deadline); background: #fff8ee; }
.date-range > i { color: var(--home-deadline); font-size: 12px; font-style: normal; font-weight: 750; }
.date-range .date-block strong,
.date-range .date-block small {
  color: #9a5e28;
  font-family: inherit;
  font-size: 13px;
  font-weight: 650;
  line-height: 1.4;
  white-space: nowrap;
}

.schedule-copy { display: grid; min-width: 0; gap: 6px; }
.schedule-copy > strong {
  overflow: hidden;
  color: var(--home-ink);
  font-size: 15px;
  font-weight: 720;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.schedule-copy p { margin: 0; color: var(--home-muted); }
.schedule-copy a { color: var(--accent, var(--color-primary)); font-weight: 650; text-underline-offset: 3px; }
.schedule-copy p b {
  display: inline-block;
  margin-left: 5px;
  padding: 2px 7px;
  border: 1px solid rgba(25,114,90,.35);
  border-radius: 4px;
  color: var(--home-progress);
  background: #f1f8f5;
  font-size: 10px;
}
.schedule-copy > small {
  padding: 6px 9px;
  border-radius: 4px;
  color: #6b7784;
  background: #f1f4f5;
}
.schedule-actions { position: relative; justify-content: flex-end; flex-wrap: wrap; }
.schedule-actions > i {
  position: absolute;
  right: -4px;
  bottom: calc(100% + 9px);
  padding: 4px 9px;
  border-radius: 3px 7px 3px 7px;
  color: #fff;
  background: var(--accent, var(--color-primary));
  font-size: 10px;
  font-style: normal;
}
.schedule-actions button { min-width: 60px; }
.secondary { color: var(--color-card-foreground); background: #edf1f5; }

.confirmation-panel {
  padding-top: 22px;
  background:
    linear-gradient(90deg, rgba(208,120,50,.04), transparent 24%),
    var(--home-paper);
}
.confirmation-panel .panel-head > b {
  padding: 5px 9px;
  border-radius: 4px;
  color: #b13f36;
  background: #ffe9e7;
  font-size: 11px;
}
.confirmation-list { display: grid; margin-top: 16px; border-top: 1px solid var(--home-line); }
.confirmation-list article { padding: 14px 4px; border-bottom: 1px solid var(--home-line); }
.confirmation-list article > div:first-child { display: grid; min-width: 180px; }
.confirmation-list article > div:first-child span { color: var(--home-muted); font-size: 12px; }
.progress-line { display: flex; align-items: center; flex: 1; gap: 8px; color: #8796a4; font-size: 11px; }
.progress-line .current { color: #a95c20; }
.confirmation-list article > em {
  padding: 5px 8px;
  border-radius: 4px;
  color: #be3f35;
  background: #ffebe8;
  font-size: 11px;
  font-style: normal;
}
.confirmation-list article > button { border: 1px solid #edc7c3; color: #a93c35; background: transparent; }

.empty { padding: 34px 12px; color: var(--home-muted); text-align: center; }
.feedback {
  position: fixed;
  top: 18px;
  left: 50%;
  z-index: 70;
  margin: 0;
  padding: 11px 18px;
  transform: translateX(-50%);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 12px 34px rgba(20,35,54,.16);
}

@keyframes track-reveal {
  from { opacity: 0; transform: translateY(8px) scale(.995); }
  to { opacity: 1; transform: none; }
}
@media (prefers-reduced-motion: reduce) {
  .schedule-advice { animation: none; }
}
@media (max-width: 900px) {
  .dashboard-panel { padding: 21px 18px; border-radius: 15px; }
  .schedule-list article { grid-template-columns: max-content minmax(0,1fr); }
  .schedule-actions { grid-column: 2; justify-content: flex-start; }
  .schedule-actions > i { display: none; }
}
@media (max-width: 680px) {
  .home-dashboard { gap: 14px; padding-top: 10px; }
  .quote-strip { width: 100%; }
  .timeline-scroll { margin-inline: -4px; padding-inline: 4px; scroll-snap-type: x proximity; }
  .timeline-list article { scroll-snap-align: start; }
  .panel-head, .advice-head { align-items: flex-start; }
  .advice-head { flex-direction: column; }
  .schedule-list article { grid-template-columns: 1fr; gap: 12px; }
  .date-block { width: 100%; }
  .date-range { display: grid; width: 100%; grid-template-columns: minmax(0,1fr) auto minmax(0,1fr); }
  .date-range > .date-block { width: 100%; }
  .schedule-actions { grid-column: 1; }
  .confirmation-list article { align-items: stretch; flex-direction: column; }
  .progress-line { width: 100%; overflow-x: auto; }
}
</style>
