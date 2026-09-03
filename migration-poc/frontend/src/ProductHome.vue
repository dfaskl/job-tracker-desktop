<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from './api'
import { useJobTrackerStore, type JobApplication, type JobEvent } from './jobTrackerStore'

type Page = 'applications' | 'calendar' | 'mail' | 'stats'
type AiStatus = { callsEnabled: boolean; message: string }
type Quote = { date: string; quote: string; author: string; generated: boolean }
const emit = defineEmits<{ navigate: [page: Page] }>()
const store = useJobTrackerStore()
const quoteKey = 'job_tracker_daily_quote_vue_v1'
const quote = ref<Quote>(fallbackQuote())
const quoteLoading = ref(false)
const message = ref('')
const error = ref('')
const busyId = ref('')

const upcomingItems = computed(() => store.events.value.filter(item => !item.completed && !item.missed)
  .sort((a,b) => eventDeadline(a).localeCompare(eventDeadline(b))))
const upcoming = computed(() => upcomingItems.value.filter(item => parseTime(eventDeadline(item)) >= Date.now()))
const overdue = computed(() => upcomingItems.value.filter(item => parseTime(eventDeadline(item)) < Date.now() - 2 * 60 * 60 * 1000))
const recentSchedules = computed(() => upcomingItems.value.slice(0, 4))
const active = computed(() => store.applications.value.filter(item => !isEnded(item)).length)
const staleApplications = computed(() => store.applications.value.map(item => ({ item, health: progressHealth(item) }))
  .filter(row => row.health && row.health.days >= 10).sort((a,b) => (b.health?.days || 0) - (a.health?.days || 0)))
const attention = computed(() => overdue.value.length + staleApplications.value.length)
const progressTone = computed(() => attention.value ? '需要关注' : upcoming.value.length ? '稳步推进' : '保持行动')

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
function eventDate(event:JobEvent){const value=eventStart(event);const date=new Date(value.replace(' ','T'));if(Number.isNaN(date.getTime()))return {tag:'待定',date:'未设置',time:''};const same=today()===value.slice(0,10);return {tag:same?'今天':'',date:`${date.getMonth()+1}月${date.getDate()}日`,time:`${pad(date.getHours())}:${pad(date.getMinutes())}`}}
function fallbackQuote():Quote{const items=['今天多走一步，明天就多一个选择。','把注意力放在能推进的下一步上。','每一次认真准备，都在靠近更合适的机会。','慢一点没有关系，只要方向仍在向前。','机会会迟到，但你的积累不会白费。','先完成今天能完成的，再把答案交给时间。','保持行动，好的结果往往在坚持之后出现。'];return {date:today(),quote:items[new Date().getDay()],author:'',generated:false}}
function loadCachedQuote(){try{const cached=JSON.parse(localStorage.getItem(quoteKey)||'null') as Quote|null;if(cached?.date===today()&&cached.quote){quote.value=cached;return true}}catch{/* 使用本地内容 */}return false}
async function generateQuote(force:boolean){if(!store.user.value||quoteLoading.value)return;quoteLoading.value=true;error.value='';try{const status=await api<AiStatus>('/api/poc/ai-sandbox/status');if(!status.callsEnabled){quote.value=fallbackQuote();return}const value=await api<{quote:string;author:string}>('/api/poc/ai-sandbox/daily-quote',{method:'POST',body:JSON.stringify({date:today()})});quote.value={date:today(),quote:value.quote,author:value.author||'',generated:true};localStorage.setItem(quoteKey,JSON.stringify(quote.value));if(force)message.value='已经换了一句'}catch(cause){quote.value=fallbackQuote();error.value=cause instanceof Error?cause.message:'每日一句生成失败'}finally{quoteLoading.value=false}}
async function completeEvent(event:JobEvent){busyId.value=event.id;error.value='';try{await api(`/api/poc/event-sandbox/events/${encodeURIComponent(event.id)}/resolution`,{method:'POST',body:JSON.stringify({action:'complete',expectedUpdatedAt:String(event.updatedAt||'')})});await store.refresh();message.value='日程已完成'}catch(cause){error.value=cause instanceof Error?cause.message:'更新日程失败'}finally{busyId.value=''}}
async function markRejected(item:JobApplication){if(!confirm(`确认将“${item.company} · ${item.position}”标记为未通过吗？`))return;busyId.value=item.id;error.value='';try{await api(`/api/poc/application-sandbox/applications/${encodeURIComponent(item.id)}`,{method:'PUT',body:JSON.stringify({company:item.company||'',position:item.position||'',city:item.city||'',channel:item.channel||'',appliedDate:item.appliedDate||'',stage:'已结束',status:'未通过',notes:item.notes||'',expectedUpdatedAt:item.updatedAt||''})});await store.refresh();message.value='已标记为未通过'}catch(cause){error.value=cause instanceof Error?cause.message:'更新投递失败'}finally{busyId.value=''}}
</script>

<template>
  <div v-if="store.user.value" class="home-dashboard">
    <section class="quote-strip"><i>✦</i><span><small>{{quote.generated?'AI 每日一句':'每日一句'}}</small><strong>{{quote.quote}}<em v-if="quote.author"> — {{quote.author}}</em></strong></span><button :disabled="quoteLoading" title="换一句" @click="generateQuote(true)">↻</button></section>
    <section class="briefing">
      <div class="briefing-head"><div class="briefing-title"><i>◆</i><span><strong>今日求职简报</strong><small>数据更新至今天</small></span></div><b :class="{attention}">● {{progressTone}}</b></div>
      <div class="briefing-metrics"><div><strong>{{store.applications.value.length}}</strong><span>总岗位</span></div><div><strong>{{active}}</strong><span>推进中</span></div><div><strong>{{upcoming.length}}</strong><span>待参加</span></div><div><strong>{{attention}}</strong><span>需关注</span></div></div>
      <button class="text-link" @click="emit('navigate','stats')">查看统计 →</button>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head"><h2>近期日程 <span title="显示最近的待办、笔试和面试安排">ⓘ</span></h2><button class="text-link" @click="emit('navigate','calendar')">查看全部</button></div>
      <div v-if="recentSchedules.length" class="schedule-list">
        <article v-for="(event,index) in recentSchedules" :key="event.id">
          <div class="date-block"><em v-if="eventDate(event).tag">{{eventDate(event).tag}}</em><strong>{{eventDate(event).date}}</strong><small>{{eventDate(event).time}}</small></div>
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
.home-dashboard{display:grid;gap:16px;padding-top:16px}.quote-strip{display:flex;align-items:center;gap:10px;width:min(500px,100%);margin:0 auto;padding:10px 14px;border:1px solid #cdd9e9;border-radius:10px;background:#f9fbff}.quote-strip>i{display:grid;width:28px;height:28px;place-items:center;border-radius:8px;color:#285dac;background:#edf3fb;font-style:normal}.quote-strip>span{display:grid;flex:1}.quote-strip small{color:#285dac;font-size:10px}.quote-strip strong{font-size:12px;font-weight:500}.quote-strip em{color:#74808c;font-style:normal}.quote-strip button{padding:5px 8px;color:#285dac;background:transparent}.briefing,.dashboard-panel{padding:20px 24px;border:1px solid #dbe3ec;border-radius:14px;background:#fff}.briefing{border-left:3px solid #3264b8}.briefing-head,.briefing-title,.panel-head,.schedule-list article,.schedule-actions,.confirmation-list article{display:flex;align-items:center;justify-content:space-between;gap:14px}.briefing-title{justify-content:flex-start}.briefing-title>i{display:grid;width:36px;height:36px;place-items:center;border-radius:10px;color:#fff;background:#3264b8;font-style:normal}.briefing-title span{display:grid}.briefing-title strong{color:#285dac}.briefing-title small{color:#7a8794}.briefing-head>b{padding:6px 10px;border-radius:999px;color:#2d679e;background:#eaf4ed;font-size:12px}.briefing-head>b.attention{color:#a04b18;background:#fff0e7}.briefing-metrics{display:grid;grid-template-columns:repeat(4,1fr);margin:14px 0;border:1px solid #dbe3ec;border-radius:10px;background:#f8fafb}.briefing-metrics div{display:grid;gap:2px;padding:11px 14px;border-right:1px solid #dbe3ec}.briefing-metrics div:last-child{border:0}.briefing-metrics strong{font-size:21px}.briefing-metrics span{color:#667785;font-size:11px}.text-link{padding:5px;color:#2260b5;background:transparent}.panel-head h2{margin:0;font-size:19px}.panel-head h2 span{color:#3667ad;font-size:15px}.panel-head p{margin:5px 0 0}.schedule-list{display:grid;gap:10px;margin-top:18px}.schedule-list article{padding:16px 20px;border:1px solid #cbd9eb;border-radius:12px}.date-block{display:grid;width:80px;flex:none;gap:3px;padding:8px 10px;border-radius:10px;background:#fff8f5}.date-block em{width:max-content;padding:2px 7px;border-radius:999px;color:#d5562e;background:#ffebe4;font-size:10px;font-style:normal}.date-block strong{color:#285dac}.date-block small{color:#667785}.schedule-copy{display:grid;flex:1;gap:5px}.schedule-copy p{margin:0}.schedule-copy p b{padding:3px 8px;border:1px solid #6d989c;border-radius:6px;color:#376d73;background:#edf7f7;font-size:11px}.schedule-copy>small{padding:6px 8px;border-radius:6px;color:#68757d;background:#f3f5f5}.schedule-actions{position:relative;justify-content:flex-end;flex-wrap:wrap}.schedule-actions>i{position:absolute;right:-20px;top:-41px;padding:6px 12px;border-radius:0 10px 0 8px;color:#fff;background:#3264b8;font-size:11px;font-style:normal}.secondary{color:#344054;background:#eef2f8}.confirmation-panel .panel-head>b{padding:5px 9px;border-radius:6px;color:#bd3d34;background:#ffe9e7;font-size:11px}.confirmation-list{display:grid;gap:9px;margin-top:14px}.confirmation-list article{padding:14px;border:1px solid #dbe3ec;border-radius:10px}.confirmation-list article>div:first-child{display:grid;min-width:180px}.confirmation-list article>div:first-child span{color:#667785;font-size:12px}.progress-line{display:flex;align-items:center;flex:1;gap:8px;color:#8796a4;font-size:11px}.progress-line .current{color:#a95c20}.confirmation-list article>em{padding:5px 8px;border-radius:6px;color:#be3f35;background:#ffebe8;font-size:11px;font-style:normal}.confirmation-list article>button{color:#b63e35;background:#fff;border:1px solid #edc7c3}.empty{padding:28px;color:#758390;text-align:center}.feedback{position:sticky;bottom:14px;margin:14px 0 0;padding:12px 16px;border-radius:10px;background:#fff;box-shadow:0 8px 25px rgba(0,0,0,.1)}
@media(max-width:780px){.briefing-metrics{grid-template-columns:repeat(2,1fr)}.briefing-metrics div:nth-child(2){border-right:0}.schedule-list article,.confirmation-list article{align-items:stretch;flex-direction:column}.date-block{width:100%}.schedule-actions>i{display:none}.progress-line{overflow-x:auto}.panel-head{align-items:flex-start}}
</style>