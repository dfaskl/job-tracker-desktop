<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from './api'
import { useJobTrackerStore, type JobApplication } from './jobTrackerStore'

const store = useJobTrackerStore()
const query = ref('')
const stageFilter = ref('全部')
const selected = ref<JobApplication | null>(null)
const editing = ref(false)
const eventEditor = ref(false)
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

const filtered = computed(() => {
  const keyword=query.value.trim().toLowerCase()
  return store.applications.value.filter(item => (stageFilter.value==='全部'||stageCategory(item)===stageFilter.value)
    && (!keyword || Object.values(item).join(' ').toLowerCase().includes(keyword)))
    .sort(compareApplications)
})
const selectedEvents = computed(() => selected.value ? store.events.value.filter(event=>event.applicationId===selected.value?.id) : [])
const selectedTimeline = computed(() => Array.isArray(selected.value?.timeline) ? selected.value.timeline as Record<string,unknown>[] : [])
onMounted(async()=>{try{const result=await api<{items:{company:string;url:string}[]}>('/api/poc/company-links');companyLinks.value=result.items||[]}catch{/* 登录前使用搜索兜底 */}})
function stageCategory(item:JobApplication){if(item.stage==='已结束'||['未通过','已放弃','已结束'].includes(String(item.status||'')))return '已结束';return ['测评','笔试','面试','Offer'].includes(String(item.stage||''))?String(item.stage):'仅投递'}
function eventDeadline(item:Record<string,unknown>){return String(item.endsAt||item.end||item.startsAt||item.start||item.date||'')}
function isInterview(item:Record<string,unknown>){return item.type==='面试'||/面试|[一二三四五六七八九]面|HR|电话/i.test(`${item.type||''} ${item.title||''}`)}
function health(item:JobApplication){if(['Offer','已结束'].includes(String(item.stage))||['已通过','未通过','已放弃','已结束'].includes(String(item.status)))return null;const related=store.events.value.filter(e=>e.applicationId===item.id&&isInterview(e));if(related.some(e=>!e.completed&&!e.missed&&new Date(eventDeadline(e).replace(' ','T')).getTime()>=Date.now()))return null;const times=related.filter(e=>e.completed&&!e.missed).map(e=>new Date(String(e.completedAt||eventDeadline(e)).replace(' ','T')).getTime()).filter(Number.isFinite);if(!times.length)return null;const days=Math.max(0,Math.floor((Date.now()-Math.max(...times))/86400000));return {days,label:days<=3?'进展正常':days<10?'等待较久':'建议确认',tone:days<=3?'good':days<10?'watch':'risk'}}
function cardTone(item:JobApplication){if(item.stage==='Offer'||item.status==='已通过')return 'offer';if(stageCategory(item)==='已结束')return 'stopped';const events=store.events.value.filter(e=>e.applicationId===item.id&&!e.completed&&!e.missed);if(events.some(e=>new Date(eventDeadline(e).replace(' ','T')).getTime()>=Date.now()))return 'pending';if(store.events.value.some(e=>e.applicationId===item.id&&isInterview(e)))return 'interview';if(['测评','笔试'].includes(String(item.stage))||store.events.value.some(e=>e.applicationId===item.id&&['测评','笔试'].includes(String(e.type))))return 'assessment';return 'applied'}
function compareApplications(a:JobApplication,b:JobApplication){const rank=(x:JobApplication)=>({pending:0,interview:1,assessment:2,applied:3,offer:4,stopped:5}[cardTone(x)]??3);return rank(a)-rank(b)||String(b.updatedAt||b.appliedDate||'').localeCompare(String(a.updatedAt||a.appliedDate||''))}
function flow(item:JobApplication){const nodes=[{label:'已投递',at:String(item.appliedDate||'')}];store.events.value.filter(e=>e.applicationId===item.id).sort((a,b)=>eventDeadline(a).localeCompare(eventDeadline(b))).forEach(e=>nodes.push({label:String(e.title||e.type||'日程'),at:eventDeadline(e)}));const terminal=item.stage==='Offer'||stageCategory(item)==='已结束'?String(item.stage==='Offer'?'Offer':item.status||item.stage):'';if(terminal&&nodes[nodes.length-1]?.label!==terminal)nodes.push({label:terminal,at:'当前状态'});return nodes}
function officialUrl(item:JobApplication){const company=String(item.company||'').trim().toLowerCase();const direct=companyLinks.value.find(link=>link.company.trim().toLowerCase()===company)?.url;return direct||'https://www.bing.com/search?q='+encodeURIComponent(String(item.company||'')+' 校园招聘 官网')}
function localParts(date=new Date()){const pad=(v:number)=>String(v).padStart(2,'0');return date.getFullYear()+'-'+pad(date.getMonth()+1)+'-'+pad(date.getDate())+'T'+pad(date.getHours())+':'+pad(date.getMinutes())}
function today(){return localParts().slice(0,10)}
function emptyApplication(){return {company:'',position:'',city:'',channel:'官网',appliedDate:today(),stage:'已投递',status:'等待结果',notes:''}}
function text(value:unknown,fallback='未填写'){return String(value||fallback)}
function openCreate(){selected.value=null;Object.assign(form,emptyApplication());editing.value=true;message.value='';error.value='';aiChanges.value=[];aiWarnings.value=[]}
function openEdit(item:JobApplication){selected.value=item;aiChanges.value=[];aiWarnings.value=[];Object.assign(form,{company:item.company||'',position:item.position||'',city:item.city||'',channel:item.channel||'其他',appliedDate:item.appliedDate||today(),stage:item.stage||'已投递',status:item.status||'等待结果',notes:item.notes||''});editing.value=true}
function closeEditors(){editing.value=false;eventEditor.value=false}
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
function openEvent(){
  const tomorrow=new Date();tomorrow.setDate(tomorrow.getDate()+1);tomorrow.setHours(9,0,0,0)
  Object.assign(eventForm,{type:'面试',title:'',startsAt:localParts(tomorrow),endsAt:'',location:'',notes:''});eventEditor.value=true
}
async function saveEvent(){
  if(!selected.value)return
  busy.value=true;error.value=''
  try{
    await api('/api/poc/event-sandbox/events',{method:'POST',body:JSON.stringify({applicationId:selected.value.id,...eventForm,startsAt:eventForm.startsAt.replace('T',' '),endsAt:eventForm.endsAt.replace('T',' '),expectedUpdatedAt:''})})
    eventEditor.value=false;await store.refresh();message.value='关联日程已创建'
  }catch(cause){error.value=cause instanceof Error?cause.message:'创建日程失败'}finally{busy.value=false}
}
</script>

<template>
<section class="card workspace">
  <div class="head"><div><span>APPLICATIONS</span><h2>全部投递</h2></div><button v-if="!store.readOnly.value" @click="openCreate">＋ 新建投递</button></div>
  <div v-if="store.user.value" class="toolbar"><input v-model="query" type="search" placeholder="搜索公司、岗位、地点、渠道或备注"><select v-model="stageFilter"><option>全部</option><option v-for="item in stageCategories" :key="item">{{item}}</option></select><button class="secondary" @click="store.refresh">刷新</button></div>
  <div v-if="store.user.value&&filtered.length" class="grid">
    <button v-for="item in filtered" :key="item.id" class="application" :class="`tone-${cardTone(item)}`" @click="selected=item">
      <div class="application-title"><strong>{{text(item.company,'未填写公司')}}</strong><span>{{text(item.position,'未填写岗位')}}</span><em v-if="health(item)" :class="`health-${health(item)?.tone}`">{{health(item)?.label}} · {{health(item)?.days}}天</em></div><div><b>{{text(item.stage,'未标记')}}</b><i>{{text(item.status,'未标记')}}</i></div><small>{{text(item.city,'地点未填')}} · {{text(item.channel,'渠道未填')}} · {{item.appliedDate||'日期未填'}}</small><div class="flow"><span v-for="(node,index) in flow(item)" :key="`${node.label}-${index}`"><i>{{index===0?'↗':'●'}}</i><b>{{node.label}}</b><small>{{node.at}}</small></span></div>
    </button>
  </div>
  <p v-else-if="store.user.value" class="empty">没有符合条件的投递记录。</p><p v-else>登录后查看和管理投递。</p>
</section>
<div v-if="message||error" class="feedback" :class="{danger:error}">{{error||message}} <button v-if="undo" @click="undoDelete">撤销删除</button></div>

<div v-if="selected&&!editing&&!eventEditor" class="backdrop" @click.self="selected=null">
  <section class="modal"><button class="close" @click="selected=null">×</button><div class="detail-head"><div><h2>{{text(selected.company)}}</h2><p>{{text(selected.position)}} · {{text(selected.city,'地点未填')}}</p></div><div class="badges"><b>{{selected.stage}}</b><i>{{selected.status}}</i></div></div>
    <div class="actions"><a class="official" :href="officialUrl(selected)" target="_blank" rel="noreferrer">打开招聘官网 ↗</a><button v-if="!store.readOnly.value" @click="openEdit(selected)">编辑</button><button v-if="!store.readOnly.value" class="secondary" @click="openEvent">＋ 日程</button><button v-if="!store.readOnly.value" class="offer" @click="quickUpdate('Offer','已通过')">标记 Offer</button><button v-if="!store.readOnly.value" class="reject" @click="quickUpdate('已结束','未通过')">标记未通过</button><button v-if="!store.readOnly.value" class="danger-button" @click="removeApplication">删除</button></div>
    <dl><div><dt>投递日期</dt><dd>{{text(selected.appliedDate)}}</dd></div><div><dt>渠道</dt><dd>{{text(selected.channel)}}</dd></div><div class="wide"><dt>备注</dt><dd>{{text(selected.notes,'暂无备注')}}</dd></div></dl>
    <h3>安排记录</h3><div v-if="selectedEvents.length" class="timeline"><article v-for="item in selectedEvents" :key="item.id"><strong>{{text(item.title||item.type)}}</strong><span>{{text(item.startsAt||item.start||item.date,'时间未填')}} · {{item.completed?'已完成':'待处理'}}</span></article></div><p v-else class="empty">暂无安排。</p>
    <h3>状态历史</h3><div v-if="selectedTimeline.length" class="timeline"><article v-for="(item,index) in selectedTimeline" :key="String(item.id||index)"><strong>{{text(item.title,'状态更新')}}</strong><span>{{text(item.at,'')}}</span></article></div><p v-else class="empty">暂无历史。</p>
  </section>
</div>

<div v-if="editing" class="backdrop"><form class="modal form" @submit.prevent="saveApplication"><button type="button" class="close" @click="closeEditors">×</button><h2>{{selected?'编辑投递':'新建投递'}}</h2>
<label><span>公司 *</span><input v-model="form.company" required maxlength="120"></label><label><span>岗位 *</span><input v-model="form.position" required maxlength="160"></label><label><span>地点</span><input v-model="form.city"></label><label><span>渠道</span><select v-model="form.channel"><option v-for="item in channels" :key="item">{{item}}</option></select></label><label><span>投递日期</span><input v-model="form.appliedDate" type="date"></label><label><span>阶段</span><select v-model="form.stage"><option v-for="item in stages" :key="item">{{item}}</option></select></label><label><span>状态</span><select v-model="form.status"><option v-for="item in statuses" :key="item">{{item}}</option></select></label><label class="wide"><span>备注</span><textarea v-model="form.notes" rows="4"></textarea></label><div v-if="aiChanges.length||aiWarnings.length" class="ai-review wide"><p v-for="item in aiChanges" :key="item">✓ {{item}}</p><p v-for="item in aiWarnings" :key="item" class="warn">请核对：{{item}}</p></div><div class="actions wide"><button type="button" class="ai-button" :disabled="busy" @click="normalizeApplication">✦ AI 规范</button><button :disabled="busy">保存</button><button type="button" class="secondary" @click="closeEditors">取消</button></div></form></div>

<div v-if="eventEditor&&selected" class="backdrop"><form class="modal form" @submit.prevent="saveEvent"><button type="button" class="close" @click="closeEditors">×</button><h2>新增关联日程</h2>
<label><span>类型</span><select v-model="eventForm.type"><option v-for="item in eventTypes" :key="item">{{item}}</option></select></label><label><span>名称 *</span><input v-model="eventForm.title" required placeholder="如：一面"></label><label><span>开始时间 *</span><input v-model="eventForm.startsAt" type="datetime-local" required></label><label><span>结束时间</span><input v-model="eventForm.endsAt" type="datetime-local"></label><label class="wide"><span>地点 / 链接</span><input v-model="eventForm.location"></label><label class="wide"><span>备注</span><textarea v-model="eventForm.notes" rows="3"></textarea></label><div class="actions wide"><button :disabled="busy">创建日程</button><button type="button" class="secondary" @click="closeEditors">取消</button></div></form></div>
</template>

<style scoped>
.application-title{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.application-title>span{color:#667085}.application-title em{margin-left:auto;padding:4px 7px;border-radius:999px;font-size:11px;font-style:normal}.health-good{color:#167647;background:#e9f8ef}.health-watch{color:#8a5608;background:#fff3d6}.health-risk{color:#a52d2d;background:#fceaea}.application.tone-pending{border-left:5px solid #d89226}.application.tone-interview{border-left:5px solid #7a60d1}.application.tone-assessment{border-left:5px solid #4382c4}.application.tone-offer{border-left:5px solid #248459}.application.tone-stopped{border-left:5px solid #a5acb9}.flow{display:flex!important;align-items:flex-start;overflow-x:auto;padding-top:7px;border-top:1px solid #e9edf4}.flow span{display:grid;min-width:92px;gap:2px;color:#667085}.flow span>i{color:#526ddd;font-style:normal}.flow span>b{color:#344054;font-size:11px}.flow span>small{font-size:9px}.official{display:inline-flex;align-items:center;padding:10px 14px;border-radius:10px;color:#344054;background:#eef2f8;text-decoration:none}.head,.toolbar,.detail-head,.actions{display:flex;align-items:center;justify-content:space-between;gap:12px}.head span{color:#526ddd;font-size:11px;font-weight:800;letter-spacing:.1em}.head h2{margin:4px 0}.toolbar{margin:18px 0}.toolbar input{flex:1}.toolbar select{width:150px}.secondary{color:#344054;background:#eef2f8}.grid{display:grid;grid-template-columns:repeat(2,1fr);gap:12px}.application{display:grid;gap:8px;padding:17px;color:#172033;border:1px solid #e2e7f0;background:#fbfcfe;text-align:left}.application>span,.application small{color:#667085}.application div,.badges{display:flex;gap:7px}.application b,.application i,.badges b,.badges i{padding:5px 8px;border-radius:999px;background:#edf1ff;color:#3d55bd;font-size:12px;font-style:normal}.application i,.badges i{color:#475467;background:#eef2f6}.backdrop{position:fixed;inset:0;z-index:40;display:grid;place-items:center;padding:20px;background:rgba(17,24,39,.58)}.modal{position:relative;width:min(760px,100%);max-height:90vh;overflow:auto;padding:28px;border-radius:18px;background:#fff}.close{position:absolute;top:12px;right:12px;padding:4px 11px;color:#475467;background:#eef2f6;font-size:22px}.detail-head{padding-right:35px}.detail-head h2{margin:0}.actions{justify-content:flex-start;flex-wrap:wrap;margin:18px 0}.ai-button{background:#6b4fd3}.ai-review{padding:12px;border-radius:10px;background:#f4f1ff}.ai-review p{margin:4px 0;color:#476050;font-size:12px}.ai-review .warn{color:#8a5608}.offer{background:#17804b}.reject,.danger-button{background:#bd3434}dl{display:grid;grid-template-columns:1fr 1fr;gap:10px}dl div{padding:12px;border-radius:10px;background:#f7f9fc}dl .wide,.form .wide{grid-column:1/-1}dt{color:#667085;font-size:11px}dd{margin:5px 0 0;white-space:pre-wrap}.timeline{display:grid;gap:8px}.timeline article{display:grid;gap:4px;padding:11px;border-left:3px solid #8396e9;background:#f7f9fc}.timeline span{color:#667085;font-size:12px}.form{display:grid;grid-template-columns:1fr 1fr;gap:14px}.form h2{grid-column:1/-1}.form label{display:grid;gap:7px;color:#475467;font-size:13px;font-weight:700}.form select,.form textarea{width:100%;padding:12px 14px;border:1px solid #d4dbea;border-radius:10px;background:#fff;font:inherit}.feedback{position:sticky;bottom:16px;z-index:20;margin:14px auto;padding:12px 16px;border-radius:12px;color:#167647;background:#e9f8ef;box-shadow:0 8px 30px rgba(0,0,0,.12)}.feedback.danger{color:#a52d2d;background:#fceaea}.feedback button{margin-left:12px;padding:7px 11px}.empty{color:#667085}@media(max-width:720px){.toolbar{align-items:stretch;flex-direction:column}.toolbar select{width:auto}.grid,.form,dl{grid-template-columns:1fr}.form .wide,dl .wide{grid-column:auto}}
</style>
