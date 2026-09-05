<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api, apiCached } from './api'
import { useJobTrackerStore, type JobApplication, type JobEvent } from './jobTrackerStore'

const store = useJobTrackerStore()
const query = ref('')
const stageFilter = ref('全部')
const selected = ref<JobApplication | null>(null)
const editing = ref(false)
const eventEditor = ref(false)
const editingEvent = ref<JobEvent | null>(null)
const busy = ref(false)
const message = ref('')
const error = ref('')
const aiChanges = ref<string[]>([])
const aiWarnings = ref<string[]>([])
const undo = ref<{ backupId: number; expected: string } | null>(null)
const stages = ['已投递','测评','笔试','面试','Offer','已结束']
const stageCategories = ['仅投递','测评','笔试','面试','Offer','已结束']
const companyLinks = ref<{company:string;url:string}[]>([])
const statuses = ['等待结果','已通过','未通过','已放弃','已结束']
const channels = ['官网','Boss直聘','实习僧','牛客','猎聘','智联招聘','前程无忧','校园招聘平台','内推','其他']
const eventTypes = ['测评','笔试','面试','Offer','其他']
const form = reactive(emptyApplication())
const eventForm = reactive({ type:'面试', title:'', startsAt:'', endsAt:'', location:'', notes:'' })
watch(store.newApplicationRequest, () => { if (!store.readOnly.value) openCreate() })

const filtered = computed(() => {
  const keyword=query.value.trim().toLowerCase()
  return store.applications.value.filter(item => (stageFilter.value==='全部'||stageCategory(item)===stageFilter.value)
    && (!keyword || Object.values(item).join(' ').toLowerCase().includes(keyword)))
    .sort(compareApplications)
})
const flowSlots = computed(() => Math.max(1, ...filtered.value.map(item => flow(item).length)))
const applicationMonthBounds = computed(() => {
  const values=store.applications.value.map(item=>String(item.appliedDate||item.createdAt||'').slice(0,7)).filter(value=>/^\d{4}-\d{2}$/.test(value)).sort()
  const fallback=new Date(new Date().getFullYear(),new Date().getMonth(),1)
  if(!values.length)return {first:fallback,last:fallback}
  const parse=(value:string)=>new Date(Number(value.slice(0,4)),Number(value.slice(5,7))-1,1)
  return {first:parse(values[0]),last:parse(values[values.length-1])}
})
const applicationHeatMonth=ref(new Date(new Date().getFullYear(),new Date().getMonth(),1))
const applicationHeatTitle=computed(()=>`${applicationHeatMonth.value.getFullYear()}年${applicationHeatMonth.value.getMonth()+1}月`)
const canHeatPrevious=computed(()=>applicationMonthValue(applicationHeatMonth.value)>applicationMonthValue(applicationMonthBounds.value.first))
const canHeatNext=computed(()=>applicationMonthValue(applicationHeatMonth.value)<applicationMonthValue(applicationMonthBounds.value.last))
const applicationHeatCells=computed(()=>{
  const year=applicationHeatMonth.value.getFullYear(),month=applicationHeatMonth.value.getMonth(),offset=(new Date(year,month,1).getDay()+6)%7
  const first=new Date(year,month,1-offset),counts=new Map<string,number>()
  store.applications.value.forEach(item=>{const key=String(item.appliedDate||item.createdAt||'').slice(0,10);if(/^\d{4}-\d{2}-\d{2}$/.test(key))counts.set(key,(counts.get(key)||0)+1)})
  const prefix=`${year}-${String(month+1).padStart(2,'0')}`
  const maximum=Math.max(1,...[...counts.entries()].filter(([key])=>key.startsWith(prefix)).map(([,count])=>count))
  return Array.from({length:42},(_,index)=>{const date=new Date(first);date.setDate(first.getDate()+index);const key=applicationDateKey(date),count=counts.get(key)||0;return{key,count,inMonth:date.getMonth()===month,level:count?Math.max(1,Math.ceil(count/maximum*4)):0}})
})
watch(applicationMonthBounds,bounds=>{applicationHeatMonth.value=clampApplicationMonth(applicationHeatMonth.value,bounds)},{immediate:true})
const selectedEvents = computed(() => selected.value ? store.events.value.filter(event=>event.applicationId===selected.value?.id).slice().sort((a,b)=>eventRecordTime(a).localeCompare(eventRecordTime(b))) : [])
const selectedTimeline = computed(() => Array.isArray(selected.value?.timeline) ? selected.value.timeline as Record<string,unknown>[] : [])
onMounted(async()=>{try{const result=await apiCached<{items:{company:string;url:string}[]}>('/api/poc/company-links');companyLinks.value=result.items||[]}catch{/* 登录前使用搜索兜底 */}})
function applicationMonthValue(date:Date){return date.getFullYear()*12+date.getMonth()}
function applicationDateKey(date:Date){const pad=(value:number)=>String(value).padStart(2,'0');return `${date.getFullYear()}-${pad(date.getMonth()+1)}-${pad(date.getDate())}`}
function clampApplicationMonth(date:Date,bounds=applicationMonthBounds.value){const value=applicationMonthValue(date);return value<applicationMonthValue(bounds.first)?bounds.first:value>applicationMonthValue(bounds.last)?bounds.last:date}
function moveApplicationHeatMonth(offset:number){applicationHeatMonth.value=clampApplicationMonth(new Date(applicationHeatMonth.value.getFullYear(),applicationHeatMonth.value.getMonth()+offset,1))}
function stageCategory(item:JobApplication){if(item.stage==='已结束'||['未通过','已放弃','已结束'].includes(String(item.status||'')))return '已结束';return ['测评','笔试','面试','Offer'].includes(String(item.stage||''))?String(item.stage):'仅投递'}
function eventDeadline(item:Record<string,unknown>){return String(item.endsAt||item.end||item.startsAt||item.start||item.date||'')}
function eventRecordTime(item:Record<string,unknown>){const hasRange=Boolean(String(item.endsAt||item.end||'').trim());return String(hasRange&&item.completed&&item.completedAt?item.completedAt:item.startsAt||item.start||item.date||'')}
function isInterview(item:Record<string,unknown>){return item.type==='面试'||/面试|[一二三四五六七八九]面|HR|电话/i.test(`${item.type||''} ${item.title||''}`)}
function health(item:JobApplication){if(['Offer','已结束'].includes(String(item.stage))||['已通过','未通过','已放弃','已结束'].includes(String(item.status)))return null;const related=store.events.value.filter(e=>e.applicationId===item.id&&isInterview(e));if(related.some(e=>!e.completed&&!e.missed&&new Date(eventDeadline(e).replace(' ','T')).getTime()>=Date.now()))return null;const times=related.filter(e=>e.completed&&!e.missed).map(e=>new Date(String(e.completedAt||eventDeadline(e)).replace(' ','T')).getTime()).filter(Number.isFinite);if(!times.length)return null;const days=Math.max(0,Math.floor((Date.now()-Math.max(...times))/86400000));return {days,label:days<=3?'进展正常':days<10?'等待较久':'建议确认',tone:days<=3?'good':days<10?'watch':'risk'}}
function cardTone(item:JobApplication){if(item.stage==='Offer'||item.status==='已通过')return 'offer';if(stageCategory(item)==='已结束')return 'stopped';const events=store.events.value.filter(e=>e.applicationId===item.id&&!e.completed&&!e.missed);if(events.some(e=>new Date(eventDeadline(e).replace(' ','T')).getTime()>=Date.now()))return 'pending';if(store.events.value.some(e=>e.applicationId===item.id&&isInterview(e)))return 'interview';if(['测评','笔试'].includes(String(item.stage))||store.events.value.some(e=>e.applicationId===item.id&&['测评','笔试'].includes(String(e.type))))return 'assessment';return 'applied'}
function timeOf(value:unknown){const time=new Date(String(value||'').replace(' ','T')).getTime();return Number.isFinite(time)?time:0}
function hasInterviewProgress(item:JobApplication){return ['面试','Offer'].includes(String(item.stage))||item.status==='已通过'||store.events.value.some(event=>event.applicationId===item.id&&!event.missed&&(isInterview(event)||event.type==='Offer'))}
function hasAssessmentProgress(item:JobApplication){return ['测评','笔试'].includes(String(item.stage))||store.events.value.some(event=>event.applicationId===item.id&&!event.missed&&['测评','笔试'].includes(String(event.type)))}
function progressTier(item:JobApplication){return hasInterviewProgress(item)?0:hasAssessmentProgress(item)?1:2}
function nextUpcomingTime(item:JobApplication){const now=Date.now();const times=store.events.value.filter(event=>event.applicationId===item.id&&!event.completed&&!event.missed).map(event=>{const start=timeOf(event.startsAt||event.start||event.date),end=timeOf(event.endsAt||event.end||eventDeadline(event));return start>=now?start:end>=now?now:NaN}).filter(Number.isFinite);return times.length?Math.min(...times):Infinity}
function latestProgressTime(item:JobApplication){const timeline=Array.isArray(item.timeline)?item.timeline as Record<string,unknown>[]:[];const timelineTimes=timeline.filter(entry=>!entry.eventId&&entry.title!=='创建投递记录').map(entry=>entry.at);const completedTimes=store.events.value.filter(event=>event.applicationId===item.id&&event.completed&&!event.missed).map(event=>event.completedAt||event.endsAt||event.startsAt||event.date);const times=[item.appliedDate,...timelineTimes,...completedTimes].map(timeOf).filter(Boolean);return times.length?Math.max(...times):0}
function compareApplications(a:JobApplication,b:JobApplication){const aRejected=a.status==='未通过',bRejected=b.status==='未通过';if(aRejected!==bRejected)return aRejected?1:-1;if(aRejected&&bRejected)return timeOf(b.updatedAt||b.createdAt)-timeOf(a.updatedAt||a.createdAt);const aUpcoming=nextUpcomingTime(a),bUpcoming=nextUpcomingTime(b),aHas=Number.isFinite(aUpcoming),bHas=Number.isFinite(bUpcoming);if(aHas!==bHas)return aHas?-1:1;if(aHas&&bHas&&aUpcoming!==bUpcoming)return aUpcoming-bUpcoming;const aTier=progressTier(a),bTier=progressTier(b);if(aTier!==bTier)return aTier-bTier;return latestProgressTime(b)-latestProgressTime(a)}
function flowVisual(label:unknown,type:unknown=''){const value=`${type||''} ${label||''}`;if(/未通过|错过|放弃/.test(value))return{style:'failed',icon:'×'};if(/Offer|录用|通过/.test(value))return{style:'offer',icon:'★'};if(value.includes('测评'))return{style:'assessment',icon:'◇'};if(value.includes('笔试'))return{style:'test',icon:'✎'};if(/面试|[一二三四五六七八九]面|HR/.test(value))return{style:'interview',icon:'◎'};if(value.includes('电话'))return{style:'phone',icon:'☎'};if(value.includes('等待'))return{style:'waiting',icon:'◷'};if(value.includes('投递'))return{style:'applied',icon:'↗'};return{style:'other',icon:'＋'}}
function eventFlowDate(item:Record<string,unknown>){const start=String(item.startsAt||item.start||item.date||''),end=String(item.endsAt||item.end||'');return end&&!item.completed?`${start.slice(0,10)} 至 ${end.slice(0,10)}`:String(item.completed&&item.completedAt?item.completedAt:start).slice(0,10)}
function flow(item:JobApplication){
  const events=store.events.value.filter(e=>e.applicationId===item.id).slice().sort((a,b)=>eventDeadline(a).localeCompare(eventDeadline(b)))
  const applied=flowVisual('已投递')
  const nodes=[{label:'已投递',at:String(item.appliedDate||''),kind:events.length||item.stage!=='已投递'?'done':'current',...applied}]
  events.forEach(event=>{const deadline=timeOf(eventDeadline(event)),kind=event.missed?'failed':event.completed?'done':deadline>Date.now()?'upcoming':'current';nodes.push({label:String(event.title||event.type||'日程'),at:eventFlowDate(event),kind,...flowVisual(event.title||event.type,event.type)})})
  const last=nodes[nodes.length-1],latest=events[events.length-1],latestPending=latest&&!latest.completed&&!latest.missed,terminal=['未通过','已放弃','已结束'].includes(String(item.status||'')),offer=item.stage==='Offer'||item.status==='已通过',enteredInterview=hasInterviewProgress(item)
  let statusLabel=offer?'Offer':terminal?String(item.status):String(item.status||'')
  if(statusLabel==='等待结果'&&!enteredInterview)statusLabel=''
  if(statusLabel&&statusLabel!==last.label&&!(statusLabel==='等待结果'&&latestPending)){nodes.push({label:statusLabel,at:offer?'':'当前状态',kind:offer?'success':terminal?'failed':'current',...flowVisual(statusLabel)})}
  else if(!events.length&&item.stage!=='已投递'){nodes.push({label:String(item.stage),at:'当前阶段',kind:'current',...flowVisual(item.stage,item.stage)})}
  return nodes
}
function officialUrl(item:JobApplication){const company=String(item.company||'').trim().toLowerCase();const direct=companyLinks.value.find(link=>link.company.trim().toLowerCase()===company)?.url;return direct||'https://www.bing.com/search?q='+encodeURIComponent(String(item.company||'')+' 校园招聘 官网')}
function localParts(date=new Date()){const pad=(v:number)=>String(v).padStart(2,'0');return date.getFullYear()+'-'+pad(date.getMonth()+1)+'-'+pad(date.getDate())+'T'+pad(date.getHours())+':'+pad(date.getMinutes())}
function today(){return localParts().slice(0,10)}
function emptyApplication(){return {company:'',position:'',city:'',channel:'官网',appliedDate:today(),stage:'已投递',status:'等待结果',notes:''}}
function text(value:unknown,fallback='未填写'){return String(value||fallback)}
function openCreate(){selected.value=null;Object.assign(form,emptyApplication());editing.value=true;message.value='';error.value='';aiChanges.value=[];aiWarnings.value=[]}
function openEdit(item:JobApplication){selected.value=item;aiChanges.value=[];aiWarnings.value=[];Object.assign(form,{company:item.company||'',position:item.position||'',city:item.city||'',channel:item.channel||'其他',appliedDate:item.appliedDate||today(),stage:item.stage||'已投递',status:item.status||'等待结果',notes:item.notes||''});editing.value=true}
function closeEditors(){editing.value=false;eventEditor.value=false;editingEvent.value=null}
async function normalizeApplication(){
  if(!form.company.trim()||!form.position.trim()){error.value='请先填写公司和岗位';return}
  busy.value=true;error.value='';message.value=''
  try{
    const result=await api<Record<string,unknown>&{changes:string[];warnings:string[]}>('/api/poc/ai-sandbox/normalize-application',{method:'POST',body:JSON.stringify({application:form})})
    Object.assign(form,{company:String(result.company||form.company),position:String(result.position||form.position),city:String(result.city||form.city),channel:String(result.channel||form.channel),stage:String(result.stage||form.stage),status:String(result.status||form.status),notes:String(result.notes??form.notes)})
    aiChanges.value=Array.isArray(result.changes)?result.changes:[];aiWarnings.value=Array.isArray(result.warnings)?result.warnings:[]
    message.value='AI 建议已填入表单，请核对后保存'
  }catch(cause){error.value=cause instanceof Error?cause.message:'AI 规范失败'}finally{busy.value=false}
}
async function saveApplication(){
  if(!selected.value){const duplicate=store.applications.value.find(item=>String(item.company||'').trim().toLowerCase()===form.company.trim().toLowerCase()&&String(item.position||'').trim().toLowerCase()===form.position.trim().toLowerCase());if(duplicate){editing.value=false;selected.value=duplicate;error.value='';message.value='已存在相同公司和岗位的投递，不会重复创建；你可以直接追加日程或编辑原记录';return}}
  busy.value=true;error.value=''
  try{
    const current=selected.value
    const response=current
      ? await api<{application:JobApplication}>(`/api/poc/application-sandbox/applications/${encodeURIComponent(current.id)}`,{method:'PUT',body:JSON.stringify({...form,expectedUpdatedAt:current.updatedAt||''})})
      : await api<{application:JobApplication}>('/api/poc/application-sandbox/applications',{method:'POST',body:JSON.stringify({...form,expectedUpdatedAt:''})})
    await store.refresh();selected.value=response.application;editing.value=false;message.value=current?'投递已更新':'投递已创建';undo.value=null
  }catch(cause){error.value=cause instanceof Error?cause.message:'保存失败'}finally{busy.value=false}
}
async function quickUpdate(stage:string,status:string){
  if(!selected.value)return
  Object.assign(form,{company:selected.value.company||'',position:selected.value.position||'',city:selected.value.city||'',channel:selected.value.channel||'其他',appliedDate:selected.value.appliedDate||today(),notes:selected.value.notes||'',stage,status})
  await saveApplication()
}
async function removeApplication(){
  const item=selected.value;if(!item||!confirm(`确认删除“${item.company} / ${item.position}”及其关联日程吗？`))return
  busy.value=true;error.value=''
  try{
    await api(`/api/poc/application-sandbox/applications/${encodeURIComponent(item.id)}`,{method:'DELETE',body:JSON.stringify({expectedUpdatedAt:item.updatedAt||''})})
    const backups=await api<{items:{id:number}[];currentUpdatedAt:string}>('/api/poc/backup-sandbox/backups')
    undo.value=backups.items.length?{backupId:backups.items[0].id,expected:backups.currentUpdatedAt}:null
    selected.value=null;await store.refresh();message.value='投递及关联日程已删除'
  }catch(cause){error.value=cause instanceof Error?cause.message:'删除失败'}finally{busy.value=false}
}
async function undoDelete(){
  if(!undo.value)return
  busy.value=true;error.value=''
  try{
    await api(`/api/poc/backup-sandbox/backups/${undo.value.backupId}/restore`,{method:'POST',body:JSON.stringify({expectedCurrentUpdatedAt:undo.value.expected})})
    undo.value=null;await store.refresh();message.value='刚才删除的投递和日程已恢复'
  }catch(cause){error.value=cause instanceof Error?cause.message:'撤销失败'}finally{busy.value=false}
}
function toInputTime(value:unknown){return String(value||'').replace(' ','T').slice(0,16)}
function eventDateLabel(item:JobEvent){const value=String(item.completed&&item.completedAt?item.completedAt:item.startsAt||item.start||item.date||'');const match=value.match(/^\d{4}-(\d{2})-(\d{2})/);return match?`${Number(match[1])}月${Number(match[2])}日`:'日期未填'}
function eventTimeLabel(item:JobEvent){const start=String(item.completed&&item.completedAt?item.completedAt:item.startsAt||item.start||item.date||'');const end=String(item.endsAt||item.end||'');const startTime=start.slice(11,16)||'时间未填';return end&&!item.completed?`${startTime} 至 ${end.slice(0,10)===start.slice(0,10)?end.slice(11,16):end.slice(0,16).replace('T',' ')}`:startTime}
function eventState(item:JobEvent){return item.missed?'已错过':item.completed?'已完成':'待处理'}
function eventLink(value:unknown){const text=String(value||'').trim();return /^https?:\/\//i.test(text)?text:''}
async function refreshSelected(){const id=selected.value?.id;await store.refresh();if(id)selected.value=store.applications.value.find(item=>item.id===id)||selected.value}
function openEvent(item?:JobEvent){
  editingEvent.value=item||null
  if(item){Object.assign(eventForm,{type:item.type||'面试',title:item.title||'',startsAt:toInputTime(item.startsAt||item.start||item.date),endsAt:toInputTime(item.endsAt||item.end),location:item.location||'',notes:item.notes||''})}
  else{const tomorrow=new Date();tomorrow.setDate(tomorrow.getDate()+1);tomorrow.setHours(9,0,0,0);Object.assign(eventForm,{type:'面试',title:'',startsAt:localParts(tomorrow),endsAt:'',location:'',notes:''})}
  eventEditor.value=true
}
async function saveEvent(){
  if(!selected.value)return
  busy.value=true;error.value=''
  try{
    const current=editingEvent.value
    await api(current?`/api/poc/event-sandbox/events/${encodeURIComponent(current.id)}`:'/api/poc/event-sandbox/events',{method:current?'PUT':'POST',body:JSON.stringify({applicationId:selected.value.id,...eventForm,startsAt:eventForm.startsAt.replace('T',' '),endsAt:eventForm.endsAt.replace('T',' '),expectedUpdatedAt:current?.updatedAt||''})})
    eventEditor.value=false;editingEvent.value=null;await refreshSelected();message.value=current?'日程已更新':'关联日程已创建'
  }catch(cause){error.value=cause instanceof Error?cause.message:'保存日程失败'}finally{busy.value=false}
}
async function resolveEvent(item:JobEvent,action:'complete'|'restore'){
  busy.value=true;error.value=''
  try{await api(`/api/poc/event-sandbox/events/${encodeURIComponent(item.id)}/resolution`,{method:'POST',body:JSON.stringify({action,expectedUpdatedAt:item.updatedAt||''})});await refreshSelected();message.value=action==='complete'?'日程已完成':'日程已恢复为待处理'}
  catch(cause){error.value=cause instanceof Error?cause.message:'更新日程状态失败'}finally{busy.value=false}
}
async function removeEvent(item:JobEvent){
  if(!confirm(`确认删除“${item.title||item.type||'日程'}”吗？`))return
  busy.value=true;error.value=''
  try{await api(`/api/poc/event-sandbox/events/${encodeURIComponent(item.id)}`,{method:'DELETE',body:JSON.stringify({expectedUpdatedAt:item.updatedAt||''})});await refreshSelected();message.value='日程已删除'}
  catch(cause){error.value=cause instanceof Error?cause.message:'删除日程失败'}finally{busy.value=false}
}
</script>

<template>
<Teleport to="#application-toolbar-slot">
  <div class="application-toolbar-portal">

    <div v-if="store.user.value" class="toolbar">
      <div class="application-filter-stack">
        <div class="application-filter-fields"><input v-model="query" type="search" placeholder="搜索公司、岗位、地点、渠道或备注"><select v-model="stageFilter"><option>全部</option><option v-for="item in stageCategories" :key="item">{{item}}</option></select></div>
        <div class="application-legend" aria-label="投递卡片颜色说明"><span class="legend-title">卡片颜色</span><span><i class="pending"></i>待参加日程</span><span><i class="interview"></i>有面试进展</span><span><i class="assessment"></i>测评 / 笔试</span><span><i class="applied"></i>仅投递</span><span><i class="stopped"></i>未通过 / 已结束</span><span><i class="offer"></i>Offer / 已通过</span></div>
      </div>
      <aside class="application-heatmap" aria-label="月度投递数量热力图">
        <div class="heatmap-grid"><i v-for="cell in applicationHeatCells" :key="cell.key" :class="[`level-${cell.level}`,{ outside:!cell.inMonth }]" :title="cell.inMonth ? `${cell.key}：${cell.count} 条投递` : ''"></i></div>
        <div class="heatmap-controls"><strong>{{ applicationHeatTitle }}</strong><div><button type="button" :disabled="!canHeatPrevious" aria-label="上一个月份" @click="moveApplicationHeatMonth(-1)"><span aria-hidden="true">‹</span></button><button type="button" :disabled="!canHeatNext" aria-label="下一个月份" @click="moveApplicationHeatMonth(1)"><span aria-hidden="true">›</span></button></div></div>
      </aside>
    </div>

  </div>
</Teleport>
<section class="card workspace">
  <div v-if="store.user.value&&filtered.length" class="grid">
    <button v-for="item in filtered" :key="item.id" class="application" :class="`tone-${cardTone(item)}`" @click="selected=item">
      <div class="application-overview"><div class="application-title"><strong>{{text(item.company,'未填写公司')}}</strong><i>·</i><span>{{text(item.position,'未填写岗位')}}</span><i>·</i><small>{{text(item.city,'地点未填')}}</small><i>·</i><small>{{text(item.channel,'渠道未填')}}</small></div><em v-if="health(item)" :class="`health-${health(item)?.tone}`">{{health(item)?.label}} · {{health(item)?.days}}天</em></div>
      <div class="flow" :style="{'--flow-slots':flowSlots}" aria-label="投递流程"><span v-for="(node,index) in flow(item)" :key="`${node.label}-${index}`" class="flow-node" :class="[node.kind,`flow-${node.style}`]"><i>{{node.icon}}</i><b>{{node.label}}</b><small>{{node.at}}</small></span></div>
    </button>
  </div>
  <p v-else-if="store.user.value" class="empty">没有符合条件的投递记录。</p><p v-else>登录后查看和管理投递。</p>
</section>
<div v-if="message||error" class="feedback" :class="{danger:error}">{{error||message}} <button v-if="undo" @click="undoDelete">撤销删除</button></div>

<div v-if="selected&&!editing&&!eventEditor" class="backdrop" @click.self="selected=null">
  <section class="modal detail-modal">
    <button class="close" aria-label="关闭" @click="selected=null">×</button>
    <h2 class="detail-title">投递详情</h2>
    <div class="detail-company-row">
      <div class="company-identity">
        <div class="company-name-line"><span class="company-icon" aria-hidden="true">◎</span><strong>{{text(selected.company)}}</strong><a :href="officialUrl(selected)" target="_blank" rel="noreferrer" title="打开招聘官网">↗</a></div>
        <p>{{text(selected.position)}} · {{text(selected.city,'地点未填')}}</p>
      </div>
      <div class="badges"><b>{{selected.stage}}</b><i>{{selected.status}}</i></div>
    </div>
    <div v-if="!store.readOnly.value" class="actions detail-actions">
      <button :disabled="busy" @click="openEdit(selected)">编辑</button><button class="secondary" :disabled="busy" @click="openEvent()">日程</button><button class="offer" :disabled="busy" @click="quickUpdate('Offer','已通过')">Offer</button><button class="abandon" :disabled="busy" @click="quickUpdate('已结束','已放弃')">放弃</button><button class="reject" :disabled="busy" @click="quickUpdate('已结束','未通过')">未通过</button><button class="danger-button push-right" :disabled="busy" @click="removeApplication">删除</button>
    </div>
    <h3 class="section-heading">基本信息</h3>
    <div class="basic-info"><div><strong>投递日期</strong><span>{{text(selected.appliedDate)}}</span><strong>投递渠道</strong><span>{{text(selected.channel)}}</span></div><p>{{text(selected.notes,'暂无岗位备注')}}</p></div>
    <h3 class="section-heading">安排记录</h3>
    <div v-if="selectedEvents.length" class="event-records">
      <article v-for="item in selectedEvents" :key="item.id" class="event-record">
        <div class="event-rail"><i></i></div>
        <div class="event-date"><strong>{{eventDateLabel(item)}}</strong><span>{{eventTimeLabel(item)}}</span></div>
        <div class="event-body"><strong>{{text(selected.company)}} · {{text(item.title||item.type)}}</strong><div class="event-meta"><span>{{text(selected.position)}}</span><a v-if="eventLink(item.location)" :href="eventLink(item.location)" target="_blank" rel="noreferrer">打开链接 ↗</a><span v-else-if="item.location">{{item.location}}</span><b>{{text(item.type,'日程')}}</b><i>{{eventState(item)}}</i></div><p>备注：{{text(item.notes,'暂无备注')}}</p></div>
        <div v-if="!store.readOnly.value" class="event-row-actions"><button class="secondary" :disabled="busy" @click="openEvent(item)">编辑</button><button class="event-delete" :disabled="busy" @click="removeEvent(item)">删除</button><button :disabled="busy" @click="resolveEvent(item,item.completed?'restore':'complete')">{{item.completed?'恢复':'完成'}}</button></div>
      </article>
    </div><p v-else class="empty">暂无安排。</p>
    <h3 class="section-heading">状态历史</h3>
    <div v-if="selectedTimeline.length" class="history-list"><article v-for="(item,index) in selectedTimeline" :key="String(item.id||index)"><i></i><div><strong>{{text(item.title,'状态更新')}}</strong><span>{{text(item.at,'')}}</span></div></article></div><p v-else class="empty">暂无历史。</p>
  </section>
</div>
<div v-if="editing" class="backdrop"><form class="modal form" @submit.prevent="saveApplication"><button type="button" class="close" @click="closeEditors">×</button><h2>{{selected?'编辑投递':'新建投递'}}</h2>
<label><span>公司 *</span><input v-model="form.company" required maxlength="120"></label><label><span>岗位 *</span><input v-model="form.position" required maxlength="160"></label><label><span>地点</span><input v-model="form.city"></label><label><span>渠道</span><select v-model="form.channel"><option v-for="item in channels" :key="item">{{item}}</option></select></label><label><span>投递日期</span><input v-model="form.appliedDate" type="date"></label><label><span>阶段</span><select v-model="form.stage"><option v-for="item in stages" :key="item">{{item}}</option></select></label><label><span>状态</span><select v-model="form.status"><option v-for="item in statuses" :key="item">{{item}}</option></select></label><label class="wide"><span>备注</span><textarea v-model="form.notes" rows="4"></textarea></label><div v-if="aiChanges.length||aiWarnings.length" class="ai-review wide"><p v-for="item in aiChanges" :key="item">✓ {{item}}</p><p v-for="item in aiWarnings" :key="item" class="warn">请核对：{{item}}</p></div><div class="actions wide"><button type="button" class="ai-button" :disabled="busy" @click="normalizeApplication">✦ AI 规范</button><button :disabled="busy">保存</button><button type="button" class="secondary" @click="closeEditors">取消</button></div></form></div>

<div v-if="eventEditor&&selected" class="backdrop"><form class="modal form" @submit.prevent="saveEvent"><button type="button" class="close" @click="closeEditors">×</button><h2>{{editingEvent?'编辑日程':'新增关联日程'}}</h2>
<label><span>类型</span><select v-model="eventForm.type"><option v-for="item in eventTypes" :key="item">{{item}}</option></select></label><label><span>名称 *</span><input v-model="eventForm.title" required placeholder="如：一面"></label><label><span>开始时间 *</span><input v-model="eventForm.startsAt" type="datetime-local" required></label><label><span>结束时间</span><input v-model="eventForm.endsAt" type="datetime-local"></label><label class="wide"><span>地点 / 链接</span><input v-model="eventForm.location"></label><label class="wide"><span>备注</span><textarea v-model="eventForm.notes" rows="3"></textarea></label><div class="actions wide"><button :disabled="busy">{{editingEvent?'保存日程':'创建日程'}}</button><button type="button" class="secondary" @click="closeEditors">取消</button></div></form></div>
</template>

<style scoped>
.application-overview{display:flex;align-items:center;justify-content:space-between;gap:18px}.application-title{display:flex;align-items:center;flex-wrap:wrap;row-gap:4px;color:#667085;font-size:13px}.application-title strong{color:#172033;font-size:16px}.application-title>span{color:#526159}.application-title>i{margin:0 9px;color:#a0aaa4;font-style:normal}.application-title>small{color:#667085;font-size:13px}.application-overview>em{flex:0 0 auto;padding:4px 7px;border-radius:999px;font-size:11px;font-style:normal}.health-good{color:#167647;background:#e9f8ef}.health-watch{color:#8a5608;background:#fff3d6}.health-risk{color:#a52d2d;background:#fceaea}.application.tone-pending{border-left:5px solid #d89226}.application.tone-interview{border-left:5px solid #7a60d1}.application.tone-assessment{border-left:5px solid #4382c4}.application.tone-offer{border-left:5px solid #248459}.application.tone-stopped{border-left:5px solid #a5acb9}.flow{display:grid!important;grid-template-columns:repeat(var(--flow-slots,1),minmax(76px,1fr));align-items:flex-start;min-width:0;margin-top:17px;padding:2px 3px 4px;overflow-x:auto}.flow-node{--node-color:#718078;--node-soft:#eef2ef;position:relative;display:grid!important;width:100%;min-width:76px;justify-items:center;gap:0!important;text-align:center}.flow-node:not(:first-child)::before{content:"";position:absolute;left:calc(-50% + 15px);top:14px;width:calc(100% - 30px);height:2px;border-radius:99px;background:#dce3df}.flow-node>i{position:relative;z-index:1;display:grid;width:30px;height:30px;place-items:center;padding:0!important;border:2px solid color-mix(in srgb,var(--node-color) 72%,white);border-radius:10px;background:var(--node-soft)!important;color:var(--node-color)!important;font:800 16px "Segoe UI Symbol","Microsoft YaHei UI",sans-serif;box-shadow:0 0 0 4px #eef6ff,0 3px 8px color-mix(in srgb,var(--node-color) 16%,transparent)}.flow-node>b{max-width:124px;margin-top:7px;padding:0!important;overflow:hidden;background:none!important;color:var(--node-color)!important;font-size:13px!important;text-overflow:ellipsis;white-space:nowrap}.flow-node>small{margin-top:3px;color:#667085;font-size:11px;white-space:nowrap}.flow-applied{--node-color:#4775be;--node-soft:#eaf1fc}.flow-assessment{--node-color:#7a57ad;--node-soft:#f1ebfa}.flow-test{--node-color:#b77718;--node-soft:#fff2d9}.flow-interview{--node-color:#24828b;--node-soft:#e3f5f5}.flow-phone{--node-color:#596bc2;--node-soft:#ebedfb}.flow-offer{--node-color:#258254;--node-soft:#e3f5e9}.flow-waiting{--node-color:#b06424;--node-soft:#fff0e1}.flow-failed{--node-color:#bc4c48;--node-soft:#fde9e8}.flow-other{--node-color:#69766f;--node-soft:#edf1ef}.flow-node.done>i::after{content:"✓";position:absolute;right:-5px;top:-6px;display:grid;width:14px;height:14px;place-items:center;border:2px solid #eef6ff;border-radius:50%;background:var(--node-color);color:#fff;font-size:9px}.flow-node.done>i{opacity:.82}.flow-node.current>i,.flow-node.upcoming>i{border-color:var(--node-color);box-shadow:0 0 0 4px #eef6ff,0 0 0 6px color-mix(in srgb,var(--node-color) 13%,transparent),0 5px 12px color-mix(in srgb,var(--node-color) 20%,transparent)}.flow-node.upcoming>i{border-style:dashed}.flow-node.failed>i,.flow-node.success>i{background:var(--node-color)!important;color:#fff!important}.official{display:inline-flex;align-items:center;padding:10px 14px;border-radius:10px;color:#344054;background:#eef2f8;text-decoration:none}.head,.toolbar,.detail-head,.actions{display:flex;align-items:center;justify-content:space-between;gap:12px}.head span{color:#526ddd;font-size:11px;font-weight:800;letter-spacing:.1em}.head h2{margin:4px 0}.toolbar{margin:18px 0}.toolbar input{flex:1}.toolbar select{width:150px}.secondary{color:#344054;background:#eef2f8}.grid{display:grid;grid-template-columns:1fr;gap:12px}.application{display:block;padding:17px 21px 16px;color:#172033;border:1px solid #b9d3fa;background:#eef6ff;text-align:left}.badges{display:flex;gap:7px}.badges b,.badges i{padding:5px 8px;border-radius:999px;background:#edf1ff;color:#3d55bd;font-size:12px;font-style:normal}.application i,.badges i{color:#475467;background:#eef2f6}.backdrop{position:fixed;inset:0;z-index:40;display:grid;place-items:center;padding:20px;background:rgba(17,24,39,.58)}.modal{position:relative;width:min(760px,100%);max-height:90vh;overflow:auto;padding:28px;border-radius:18px;background:#fff}.close{position:absolute;top:12px;right:12px;padding:4px 11px;color:#475467;background:#eef2f6;font-size:22px}.detail-head{padding-right:35px}.detail-head h2{margin:0}.actions{justify-content:flex-start;flex-wrap:wrap;margin:18px 0}.ai-button{background:#6b4fd3}.ai-review{padding:12px;border-radius:10px;background:#f4f1ff}.ai-review p{margin:4px 0;color:#476050;font-size:12px}.ai-review .warn{color:#8a5608}.offer{background:#17804b}.abandon{background:#c47a1b}.reject,.danger-button{background:#bd3434}dl{display:grid;grid-template-columns:1fr 1fr;gap:10px}dl div{padding:12px;border-radius:10px;background:#f7f9fc}dl .wide,.form .wide{grid-column:1/-1}dt{color:#667085;font-size:11px}dd{margin:5px 0 0;white-space:pre-wrap}.timeline{display:grid;gap:8px}.timeline article{display:grid;gap:4px;padding:11px;border-left:3px solid #8396e9;background:#f7f9fc}.timeline span{color:#667085;font-size:12px}.form{display:grid;grid-template-columns:1fr 1fr;gap:14px}.form h2{grid-column:1/-1}.form label{display:grid;gap:7px;color:#475467;font-size:13px;font-weight:700}.form select,.form textarea{width:100%;padding:12px 14px;border:1px solid #d4dbea;border-radius:10px;background:#fff;font:inherit}.feedback{position:sticky;bottom:16px;z-index:20;margin:14px auto;padding:12px 16px;border-radius:12px;color:#167647;background:#e9f8ef;box-shadow:0 8px 30px rgba(0,0,0,.12)}.feedback.danger{color:#a52d2d;background:#fceaea}.feedback button{margin-left:12px;padding:7px 11px}.empty{color:#667085}.application-toolbar-portal{display:grid;width:100%;min-width:0;align-content:center}.application-toolbar-portal .head span{font-size:9px}.application-toolbar-portal .head h2{margin:0;font-size:17px}.application-toolbar-portal .toolbar{margin:4px 0}.application-toolbar-portal .toolbar input,.application-toolbar-portal .toolbar select{padding:8px 11px}.application-toolbar-portal .toolbar button{padding:8px 13px}.application-toolbar-portal .application-legend{margin:1px 2px 0}.application-filter-stack{width:fit-content;max-width:100%;min-width:0;flex:0 1 auto}.application-filter-fields{display:grid;grid-template-columns:minmax(0,3fr) minmax(0,2fr);gap:12px}.application-filter-fields input,.application-filter-fields select{width:100%;height:38px;min-width:0;padding:0 12px;border:1px solid #cbd5e1;border-radius:9px;outline:none;color:#25324a;background:#fff;font:inherit;font-size:13px;line-height:38px;box-shadow:0 1px 2px rgba(31,48,78,.04);transition:border-color .16s ease,box-shadow .16s ease,background .16s ease}.application-filter-fields input::placeholder{color:#8b96a8}.application-filter-fields select{padding:0 34px;appearance:none;text-align:center;text-align-last:center;line-height:normal;background-color:#fff;background-image:linear-gradient(45deg,transparent 50%,#65728a 50%),linear-gradient(135deg,#65728a 50%,transparent 50%);background-position:calc(100% - 16px) 16px,calc(100% - 11px) 16px;background-repeat:no-repeat;background-size:5px 5px;cursor:pointer}.application-filter-fields select option{text-align:center}.application-filter-fields input:hover,.application-filter-fields select:hover{border-color:#9eacc1;background-color:#fbfcff}.application-filter-fields input:focus,.application-filter-fields select:focus{border-color:#526ddd;box-shadow:0 0 0 3px rgba(82,109,221,.13)}.application-toolbar-portal .toolbar{align-items:flex-start}.application-filter-stack .application-legend{width:100%;justify-content:flex-start}.application-heatmap{display:flex;min-width:225px;align-items:center;justify-content:flex-end;gap:12px;padding-left:14px;border-left:1px solid #d9e0eb}.heatmap-grid{display:grid;grid-template-columns:repeat(7,8px);grid-template-rows:repeat(6,8px);gap:2px}.heatmap-grid i{width:8px;height:8px;border-radius:2px;background:#e8edf5}.heatmap-grid i.outside{opacity:.28}.heatmap-grid i.level-1{background:#cddcff}.heatmap-grid i.level-2{background:#94b5f4}.heatmap-grid i.level-3{background:#5d8ddd}.heatmap-grid i.level-4{background:#2e61b5}.heatmap-controls{display:grid;min-width:82px;gap:7px;justify-items:center}.heatmap-controls strong{color:#344054;font-size:12px;white-space:nowrap}.heatmap-controls>div{display:flex;gap:5px}.heatmap-controls button{display:flex;width:26px;height:24px;align-items:center;justify-content:center;padding:0;border:1px solid #ccd6e6;border-radius:7px;color:#42526c;background:#f6f8fc;font-size:16px}.heatmap-controls button>span{display:block;width:12px;height:16px;transform:translateY(-1px);font-family:Arial,sans-serif;font-size:17px;font-weight:700;line-height:16px;text-align:center}.heatmap-controls button:disabled{cursor:not-allowed;opacity:.3}.workspace{margin-top:12px!important}.application-legend{display:flex;align-items:center;gap:15px;margin:0 2px 10px;padding:0 4px;overflow-x:auto;color:#667085;font-size:11px;line-height:1.4;scrollbar-width:none;white-space:nowrap}.application-legend::-webkit-scrollbar{display:none}.application-legend .legend-title{padding-right:2px;color:#8a94a3;font-size:10px;font-weight:700;letter-spacing:.04em}.application-legend>span{display:inline-flex;align-items:center;gap:6px;flex:0 0 auto}.application-legend i{width:9px;height:9px;flex:0 0 9px;border:1px solid transparent;border-radius:3px}.application-legend .pending{background:#4b87cf;border-color:#3774bc}.application-legend .interview{background:#8b6cc7;border-color:#7657b3}.application-legend .assessment{background:#dc9b32;border-color:#c5841e}.application-legend .applied{background:#8b9891;border-color:#74827b}.application-legend .stopped{background:#d5665e;border-color:#bd5049}.application-legend .offer{background:#35b870;border-color:#249d5b}
.application{--card-bg:#f7f8f8;background:var(--card-bg);transition:border-color .16s ease,box-shadow .16s ease,background .16s ease}.application.tone-pending{--card-bg:color-mix(in srgb,#4388d0 14%,#fff);border-color:color-mix(in srgb,#4388d0 38%,#dbe3ec);border-left:5px solid #4388d0}.application.tone-interview{--card-bg:color-mix(in srgb,#8968c4 13%,#fff);border-color:color-mix(in srgb,#8968c4 37%,#dbe3ec);border-left:5px solid #8968c4}.application.tone-assessment{--card-bg:color-mix(in srgb,#dc9629 14%,#fff);border-color:color-mix(in srgb,#dc9629 37%,#dbe3ec);border-left:5px solid #dc9629}.application.tone-applied{--card-bg:color-mix(in srgb,#7f8e87 8%,#fff);border-color:color-mix(in srgb,#7f8e87 27%,#dbe3ec);border-left:5px solid #7f8e87}.application.tone-stopped{--card-bg:color-mix(in srgb,#d45f57 13%,#fff);border-color:color-mix(in srgb,#d45f57 37%,#dbe3ec);border-left:5px solid #d45f57}.application.tone-offer{--card-bg:color-mix(in srgb,#2eaf69 15%,#fff);border-color:color-mix(in srgb,#2eaf69 42%,#dbe3ec);border-left:5px solid #2eaf69}.application:hover{box-shadow:0 7px 20px rgba(44,61,86,.1)}.application.tone-pending:hover{border-color:color-mix(in srgb,#4388d0 58%,#dbe3ec)}.application.tone-interview:hover{border-color:color-mix(in srgb,#8968c4 56%,#dbe3ec)}.application.tone-assessment:hover{border-color:color-mix(in srgb,#dc9629 55%,#dbe3ec)}.application.tone-applied:hover{border-color:color-mix(in srgb,#7f8e87 43%,#dbe3ec)}.application.tone-stopped:hover{border-color:color-mix(in srgb,#d45f57 56%,#dbe3ec)}.application.tone-offer:hover{border-color:color-mix(in srgb,#27ad5d 46%,#dbe3ec);box-shadow:0 8px 24px color-mix(in srgb,#45c878 13%,transparent)}.flow-node>i{box-shadow:0 0 0 4px var(--card-bg),0 3px 8px color-mix(in srgb,var(--node-color) 16%,transparent)}.flow-node.done>i::after{border-color:var(--card-bg)}.flow-node.current>i,.flow-node.upcoming>i{box-shadow:0 0 0 4px var(--card-bg),0 0 0 6px color-mix(in srgb,var(--node-color) 13%,transparent),0 5px 12px color-mix(in srgb,var(--node-color) 20%,transparent)}
.detail-modal{width:min(1180px,calc(100vw - 48px));max-height:94vh;padding:32px;border-radius:18px}.detail-title{margin:0 0 34px;font-size:22px}.detail-company-row{display:flex;align-items:center;justify-content:space-between;gap:24px}.company-identity{min-width:0}.company-name-line{display:flex;align-items:center;gap:10px}.company-name-line strong{font-size:25px}.company-name-line>a{color:#315fb6;font-size:18px;text-decoration:none}.company-icon{display:grid;width:28px;height:28px;place-items:center;border-radius:50%;color:#315fb6;background:#e9f1ff;font-size:18px;font-weight:800}.company-identity p{margin:8px 0 0;color:#596579;font-size:16px}.detail-actions{padding:18px 0;border-bottom:1px solid #d8e0eb}.detail-actions button{min-width:76px}.detail-actions .push-right{margin-left:auto}.section-heading{margin:26px 0 12px}.basic-info{padding:16px;border-radius:12px;background:#f2f8f5}.basic-info>div{display:flex;align-items:center;gap:12px}.basic-info strong{color:#263329;font-size:13px}.basic-info span{margin-right:12px;color:#66756d}.basic-info p{margin:15px 0 0;color:#78847e}.event-records{display:grid;gap:12px}.event-record{position:relative;display:grid;grid-template-columns:22px 105px minmax(0,1fr) auto;align-items:center;min-height:110px;padding:14px 16px 14px 0;border:1px solid #bcd0ec;border-radius:13px;background:#fff;box-shadow:0 8px 22px rgba(49,83,131,.07)}.event-rail{position:relative;align-self:stretch}.event-rail::before{content:"";position:absolute;left:7px;top:-27px;bottom:-27px;width:1px;background:#c5d5eb}.event-record:first-child .event-rail::before{top:50%}.event-record:last-child .event-rail::before{bottom:50%}.event-rail i{position:absolute;z-index:1;left:1px;top:50%;width:12px;height:12px;transform:translateY(-50%);border:2px solid #fff;border-radius:50%;background:#3f75c5;box-shadow:0 0 0 1px #7ca1d8}.event-date{display:grid;gap:4px;padding-right:14px;border-right:1px solid #dce4ee}.event-date strong{color:#2f63b3;font-size:17px}.event-date span{color:#687587;font-size:12px}.event-body{display:grid;min-width:0;gap:7px;padding:0 16px}.event-body>strong{font-size:15px}.event-meta{display:flex;align-items:center;flex-wrap:wrap;gap:8px;color:#627086;font-size:12px}.event-meta a{color:#205fc1}.event-meta b{padding:4px 8px;border:1px solid #9bbac3;border-radius:8px;color:#346b72;background:#eef8f8}.event-meta i{font-style:normal;color:#687587}.event-body p{margin:0;padding:7px 10px;border-radius:7px;color:#748079;background:#f3f6f4;font-size:12px}.event-row-actions{display:flex;align-items:center;gap:8px}.event-row-actions button{padding:9px 12px}.event-row-actions .event-delete{color:#b83d36;background:#fff;border:1px solid #e5b9b5}.history-list{display:grid;margin-left:8px}.history-list article{position:relative;display:flex;gap:18px;padding:4px 0 18px 20px;border-left:2px solid #d4e4dc}.history-list article:last-child{padding-bottom:4px}.history-list article>i{position:absolute;left:-7px;top:7px;width:12px;height:12px;border:2px solid #fff;border-radius:50%;background:#5eb288}.history-list article>div{display:grid;gap:5px}.history-list article strong{font-size:14px}.history-list article span{color:#76817b;font-size:12px}@media(min-width:721px) and (min-height:620px){.workspace{display:flex;height:calc(100vh - 124px);min-height:0;flex-direction:column;overflow:hidden}.workspace>.head,.workspace>.toolbar,.workspace>.application-legend{flex:0 0 auto}.workspace>.grid{min-height:0;flex:1 1 auto;align-content:start;grid-auto-rows:max-content;overflow-y:auto;overscroll-behavior:contain;padding:12px 7px 18px 2px;border-top:1px solid #aebaca;scrollbar-width:thin;scrollbar-color:#b9c5d5 transparent}.workspace>.grid::-webkit-scrollbar{width:7px}.workspace>.grid::-webkit-scrollbar-track{background:transparent}.workspace>.grid::-webkit-scrollbar-thumb{border-radius:99px;background:#b9c5d5}.workspace>.empty{min-height:0;flex:1 1 auto;padding-top:18px;border-top:1px solid #aebaca}}
@media(max-width:720px){.application-heatmap{width:100%;min-width:0;justify-content:flex-start;padding:8px 0 0;border-top:1px solid #d9e0eb;border-left:0}.application-legend{margin-top:10px;gap:12px;padding-bottom:4px}.toolbar{align-items:stretch;flex-direction:column}.toolbar select{width:auto}.grid,.form,dl{grid-template-columns:1fr}.form .wide,dl .wide{grid-column:auto}.detail-modal{width:100%;padding:22px 16px}.detail-title{margin-bottom:24px}.detail-company-row{align-items:flex-start}.company-name-line strong{font-size:21px}.detail-actions .push-right{margin-left:0}.basic-info>div{align-items:flex-start;flex-direction:column;gap:5px}.event-record{grid-template-columns:16px 78px minmax(0,1fr);padding-right:10px}.event-body{padding:0 10px}.event-row-actions{grid-column:2/-1;justify-content:flex-end;margin-top:10px}.event-date strong{font-size:14px}}
</style>
