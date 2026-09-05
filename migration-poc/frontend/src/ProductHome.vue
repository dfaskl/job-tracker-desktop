<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { api, apiCached, ApiError } from './api'
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
const message = ref('')
const error = ref('')
const busyId = ref('')
const scheduleAdvice = ref<ScheduleAdvice | null>(null)
const adviceLoading = ref(false)
const adviceNotice = ref('')
let adviceTimer: ReturnType<typeof setTimeout> | null = null

const upcomingItems = computed(() => store.events.value.filter(item => !item.completed && !item.missed)
  .sort((a,b) => eventDeadline(a).localeCompare(eventDeadline(b))))
const recentSchedules = computed(() => upcomingItems.value)
const adviceCandidates = computed(() => upcomingItems.value)
const adviceSignature = computed(() => JSON.stringify(adviceCandidates.value.map(event => ({
  id:event.id, company:eventCompany(event), title:String(event.title || event.type || '未命名日程'),
  startsAt:eventStart(event), endsAt:String(event.endsAt || event.end || eventStart(event)), updatedAt:String(event.updatedAt || '')
}))))
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
      const group=groups.get(key)
      if(group){group.labels.push({text:entry.label,status});if(rank[status]>rank[group.status])group.status=status;if(entry.showWindow)group.timeLabel=timeLabel}
      else groups.set(key,{id:key,date:entry.date,start:entry.start,timeLabel,status,labels:[{text:entry.label,status}]})
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
function isEnded(item:JobApplication){return item.stage==='已结束'||['未通过','已放弃','已结束'].includes(String(item.status||''))}
function isInterview(item:Record<string,unknown>){return item.type==='面试'||/面试|[一二三四五六七八九]面|HR|电话/i.test(`${item.type||''} ${item.title||''}`)}
function progressHealth(item:JobApplication){
  if(isEnded(item)||item.stage==='Offer'||item.status==='已通过')return null
  const related=store.events.value.filter(event=>event.applicationId===item.id&&isInterview(event))
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
  if(Number.isNaN(date.getTime()))return {tag:'待定',date:'未设置',time:'',range:false,end:''}
  const range=Boolean(endValue.trim())&&!Number.isNaN(endDate.getTime())&&!event.completed
  const same=today()===value.slice(0,10),dateText=`${date.getMonth()+1}月${date.getDate()}日`,time=`${pad(date.getHours())}:${pad(date.getMinutes())}`
  const end=range?`${endDate.getMonth()+1}月${endDate.getDate()}日 ${pad(endDate.getHours())}:${pad(endDate.getMinutes())}`:''
  return {tag:same?'今天':range?'时间段':'',date:dateText,time,range,end}
}
function fallbackQuote():Quote{const items=['今天多走一步，明天就多一个选择。','把注意力放在能推进的下一步上。','每一次认真准备，都在靠近更合适的机会。','慢一点没有关系，只要方向仍在向前。','机会会迟到，但你的积累不会白费。','先完成今天能完成的，再把答案交给时间。','保持行动，好的结果往往在坚持之后出现。'];return {date:today(),quote:items[new Date().getDay()],author:'',generated:false}}
function quoteCacheKey(){return quoteKey+'_'+String(store.user.value?.email||'guest').toLowerCase()}
function loadCachedQuote(){try{const cached=JSON.parse(localStorage.getItem(quoteCacheKey())||localStorage.getItem(legacyQuoteKey)||'null') as Quote|null;if(cached?.date===today()&&cached.quote){quote.value=cached;localStorage.setItem(quoteCacheKey(),JSON.stringify(cached));return true}}catch{/* 使用本地内容 */}return false}
async function generateQuote(force:boolean){if(!store.user.value||quoteLoading.value)return;quoteLoading.value=true;error.value='';try{const status=await apiCached<AiStatus>('/api/poc/ai-sandbox/status');if(!status.callsEnabled){quote.value=fallbackQuote();return}const value=await api<{quote:string;author:string}>('/api/poc/ai-sandbox/daily-quote',{method:'POST',body:JSON.stringify({date:today()})});quote.value={date:today(),quote:value.quote,author:value.author||'',generated:true};localStorage.setItem(quoteCacheKey(),JSON.stringify(quote.value));if(force)message.value='已经换了一句'}catch(cause){quote.value=fallbackQuote();error.value=cause instanceof Error?cause.message:'每日一语生成失败'}finally{quoteLoading.value=false}}
async function completeEvent(event:JobEvent){busyId.value=event.id;error.value='';try{await api(`/api/poc/event-sandbox/events/${encodeURIComponent(event.id)}/resolution`,{method:'POST',body:JSON.stringify({action:'complete',expectedUpdatedAt:String(event.updatedAt||event.createdAt||'')})});await store.refresh();message.value='日程已完成'}catch(cause){error.value=cause instanceof Error?cause.message:'更新日程失败'}finally{busyId.value=''}}
async function markRejected(item:JobApplication){if(!confirm(`确认将“${item.company} · ${item.position}”标记为未通过吗？`))return;busyId.value=item.id;error.value='';try{await api(`/api/poc/application-sandbox/applications/${encodeURIComponent(item.id)}`,{method:'PUT',body:JSON.stringify({company:item.company||'',position:item.position||'',city:item.city||'',channel:item.channel||'',appliedDate:item.appliedDate||'',stage:'已结束',status:'未通过',notes:item.notes||'',expectedUpdatedAt:item.updatedAt||''})});await store.refresh();message.value='已标记为未通过'}catch(cause){error.value=cause instanceof Error?cause.message:'更新投递失败'}finally{busyId.value=''}}

watch(adviceSignature,signature=>{if(adviceTimer)clearTimeout(adviceTimer);if(store.user.value)adviceTimer=setTimeout(()=>void generateScheduleAdvice(signature),3200)},{immediate:true})</script>

<template>
  <div v-if="store.user.value" class="home-dashboard">
    <Teleport to="#home-quote-slot"><section class="quote-strip"><i>✦</i><span><small>每日一语</small><strong>{{quote.quote}}<em v-if="quote.author"> — {{quote.author}}</em></strong></span><button :disabled="quoteLoading" title="换一句" @click="generateQuote(true)">↻</button></section></Teleport>
    <section class="dashboard-panel">
      <div class="panel-head"><h2>近期日程 <span title="显示最近的待办、笔试和面试安排">ⓘ</span></h2><button class="text-link" @click="emit('navigate','calendar')">查看全部</button></div>
      <section v-if="adviceLoading || scheduleAdvice || (adviceCandidates.length>1 && adviceNotice)" class="schedule-advice"><div class="advice-head"><div class="advice-title"><i>✦</i><div><strong>安排建议</strong><small>{{adviceLoading?'正在计算安排建议…':scheduleAdvice?.summary}}</small></div></div><button class="advice-refresh" :disabled="adviceLoading" @click="generateScheduleAdvice(adviceSignature,0,true)">{{adviceLoading?'生成中…':'重新生成'}}</button></div><p v-if="adviceNotice && !adviceLoading" class="advice-notice">{{adviceNotice}}</p><template v-if="scheduleAdvice"><aside v-if="scheduleAdvice.plans?.length" class="advice-timeline" aria-label="日程时间轴"><strong>时间轴</strong><div class="timeline-scroll"><div class="timeline-list"><article v-for="item in adviceTimeline" :key="item.id" :class="item.status"><time>{{item.date.slice(5).replace('-','月')+'日'}}</time><i></i><span><b v-if="item.start">{{item.timeLabel}}</b><em v-for="label in item.labels" :key="label.text" :class="label.status">{{label.text}}</em></span></article></div></div></aside><div v-if="scheduleAdvice.warnings?.length" class="advice-warnings"><strong>时间紧张</strong><span v-for="item in scheduleAdvice.warnings" :key="item">{{item}}</span></div><div v-if="scheduleAdvice.conflicts?.length" class="advice-conflicts"><strong>时间冲突</strong><span v-for="item in scheduleAdvice.conflicts" :key="item">{{item}}</span></div></template></section>
      <div v-if="recentSchedules.length" class="schedule-list">
        <article v-for="(event,index) in recentSchedules" :key="event.id">
          <div class="date-block" :class="{range:eventDate(event).range}"><em v-if="eventDate(event).tag">{{eventDate(event).tag}}</em><strong>{{eventDate(event).date}}</strong><small>{{eventDate(event).time}}</small><span v-if="eventDate(event).range">至 {{eventDate(event).end}}</span></div>
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
  <p v-if="message" class="feedback success">{{message}}</p><p v-if="error||store.error.value" class="feedback danger">{{error||store.error.value}}</p>
</template>

<style scoped>
.home-dashboard{display:grid;gap:16px;padding-top:16px}.quote-strip{display:flex;align-items:center;gap:10px;width:fit-content;max-width:none;margin:0;padding:10px 14px;border:1px solid #cdd9e9;border-radius:10px;background:#f9fbff}.quote-strip>i{display:grid;width:28px;height:28px;place-items:center;border-radius:8px;color:#285dac;background:#edf3fb;font-style:normal}.quote-strip>span{display:grid;flex:1}.quote-strip small{color:#285dac;font-size:10px}.quote-strip strong{font-size:12px;font-weight:500;white-space:nowrap}.quote-strip em{color:#74808c;font-style:normal}.quote-strip button{padding:5px 8px;color:#285dac;background:transparent}.dashboard-panel{padding:20px 24px;border:1px solid #dbe3ec;border-radius:14px;background:#fff}.panel-head,.schedule-list article,.schedule-actions,.confirmation-list article{display:flex;align-items:center;justify-content:space-between;gap:14px}.panel-head h2{margin:0;font-size:19px}.panel-head h2 span{color:#3667ad;font-size:15px}.panel-head p{margin:5px 0 0}.schedule-advice{display:grid;gap:11px;margin-top:14px;padding:14px 16px;border:1px solid color-mix(in srgb,var(--accent,#4461d8) 24%,#dbe3ec);border-radius:11px;background:color-mix(in srgb,var(--accent,#4461d8) 5%,#fff)}.advice-head{display:flex;align-items:center;justify-content:space-between;gap:12px}.advice-title{display:flex;align-items:center;gap:10px}.advice-refresh{padding:7px 12px;border:1px solid color-mix(in srgb,var(--accent,#4461d8) 30%,#dbe3ec);border-radius:8px;color:var(--accent,#4461d8);background:#fff;font-size:12px}.advice-title>i{display:grid;width:30px;height:30px;place-items:center;border-radius:9px;color:#fff;background:var(--accent,#4461d8);font-style:normal}.advice-title>div{display:grid;gap:2px}.advice-title small{color:#667785}.advice-notice{margin:0;color:#8a5608;font-size:12px}.advice-timeline{min-width:0;padding:12px 16px;border:1px solid #d8e7e1;border-radius:10px;background:rgba(255,255,255,.72)}.advice-timeline>strong{display:block;margin-bottom:10px;color:#356b59;font-size:12px}.timeline-scroll{overflow-x:auto;padding:2px 4px 8px}.timeline-list{position:relative;display:flex;align-items:flex-start;min-width:max-content;padding:24px 20px 0}.timeline-list::before{content:"";position:absolute;left:20px;right:20px;top:42px;height:2px;background:#c8ded5}.timeline-list article{position:relative;display:grid;width:180px;flex:none;grid-template-rows:18px 18px auto;justify-items:center;padding:0 10px}.timeline-list time{color:#718078;font-size:10px}.timeline-list i{z-index:1;width:12px;height:12px;border:2px solid #fff;border-radius:50%;background:#2e8b68;box-shadow:0 0 0 2px #9bc9b8}.timeline-list article:nth-child(4n+2) i{background:#3e77c5;box-shadow:0 0 0 2px #a9c6e9}.timeline-list article:nth-child(4n+3) i{background:#d38a27;box-shadow:0 0 0 2px #edd0a5}.timeline-list article:nth-child(4n) i{background:#8a63c7;box-shadow:0 0 0 2px #cab8e7}.timeline-list article.tight i{background:#d89522;box-shadow:0 0 0 2px #f0ce91}.timeline-list article.conflict i{background:#cf4e47;box-shadow:0 0 0 2px #efaaa5}.timeline-list span{display:grid;width:100%;gap:4px;padding-top:5px;text-align:center}.timeline-list b{color:#315b4d;font-size:11px}.timeline-list em{overflow:hidden;padding:3px 6px;border-radius:5px;color:#344054;background:#f3f7f5;font-size:12px;font-style:normal;text-overflow:ellipsis;white-space:nowrap}.timeline-list em.tight{color:#875716;background:#fff0c9}.timeline-list em.conflict{color:#a43731;background:#ffe4e1}.advice-warnings{display:grid;gap:5px;padding:10px 12px;border-radius:8px;color:#98621c;background:#fff7e8;font-size:12px}.advice-warnings span::before{content:"• ";}.advice-conflicts{display:grid;gap:5px;padding:10px 12px;border-radius:8px;color:#9d342e;background:#fff0ee;font-size:12px}.advice-conflicts span::before{content:"• ";}.schedule-list{display:grid;gap:10px;margin-top:18px}.schedule-list article{padding:16px 20px;border:1px solid #cbd9eb;border-radius:12px}.date-block{display:grid;width:80px;flex:none;gap:3px;padding:8px 10px;border-radius:10px;background:#fff8f5}.date-block em{width:max-content;padding:2px 7px;border-radius:999px;color:#d5562e;background:#ffebe4;font-size:10px;font-style:normal}.date-block strong{color:#285dac}.date-block small{color:#667785}.date-block.range{width:138px;background:#f3f7ff}.date-block>span{color:#52677d;font-size:11px;white-space:nowrap}.schedule-copy{display:grid;flex:1;gap:5px}.schedule-copy p{margin:0}.schedule-copy p b{padding:3px 8px;border:1px solid #6d989c;border-radius:6px;color:#376d73;background:#edf7f7;font-size:11px}.schedule-copy>small{padding:6px 8px;border-radius:6px;color:#68757d;background:#f3f5f5}.schedule-actions{position:relative;justify-content:flex-end;flex-wrap:wrap}.schedule-actions>i{position:absolute;right:-20px;top:-41px;padding:6px 12px;border-radius:0 10px 0 8px;color:#fff;background:#3264b8;font-size:11px;font-style:normal}.secondary{color:#344054;background:#eef2f8}.confirmation-panel .panel-head>b{padding:5px 9px;border-radius:6px;color:#bd3d34;background:#ffe9e7;font-size:11px}.confirmation-list{display:grid;gap:9px;margin-top:14px}.confirmation-list article{padding:14px;border:1px solid #dbe3ec;border-radius:10px}.confirmation-list article>div:first-child{display:grid;min-width:180px}.confirmation-list article>div:first-child span{color:#667785;font-size:12px}.progress-line{display:flex;align-items:center;flex:1;gap:8px;color:#8796a4;font-size:11px}.progress-line .current{color:#a95c20}.confirmation-list article>em{padding:5px 8px;border-radius:6px;color:#be3f35;background:#ffebe8;font-size:11px;font-style:normal}.confirmation-list article>button{color:#b63e35;background:#fff;border:1px solid #edc7c3}.empty{padding:28px;color:#758390;text-align:center}.feedback{position:sticky;bottom:14px;margin:14px 0 0;padding:12px 16px;border-radius:10px;background:#fff;box-shadow:0 8px 25px rgba(0,0,0,.1)}
@media(max-width:780px){.schedule-list article,.confirmation-list article{align-items:stretch;flex-direction:column}.date-block,.date-block.range{width:100%}.schedule-actions>i{display:none}.progress-line{overflow-x:auto}.panel-head{align-items:flex-start}}
</style>