<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
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
const undo = ref<{ backupId: number; expected: string } | null>(null)
const stages = ['已投递','测评','笔试','面试','Offer','已结束']
const statuses = ['等待结果','已通过','未通过','已放弃','已结束']
const channels = ['官网','Boss直聘','实习僧','牛客','猎聘','智联招聘','前程无忧','校园招聘平台','内推','其他']
const eventTypes = ['测评','笔试','面试','Offer','其他']
const form = reactive(emptyApplication())
const eventForm = reactive({ type:'面试', title:'', startsAt:'', endsAt:'', location:'', notes:'' })

const filtered = computed(() => {
  const keyword=query.value.trim().toLowerCase()
  return store.applications.value.filter(item => (stageFilter.value==='全部'||item.stage===stageFilter.value)
    && (!keyword || Object.values(item).join(' ').toLowerCase().includes(keyword)))
    .sort((a,b)=>String(b.updatedAt||b.appliedDate||'').localeCompare(String(a.updatedAt||a.appliedDate||'')))
})
const selectedEvents = computed(() => selected.value ? store.events.value.filter(event=>event.applicationId===selected.value?.id) : [])
const selectedTimeline = computed(() => Array.isArray(selected.value?.timeline) ? selected.value.timeline as Record<string,unknown>[] : [])
function today(){return new Date().toISOString().slice(0,10)}
function emptyApplication(){return {company:'',position:'',city:'',channel:'官网',appliedDate:today(),stage:'已投递',status:'等待结果',notes:''}}
function text(value:unknown,fallback='未填写'){return String(value||fallback)}
function openCreate(){selected.value=null;Object.assign(form,emptyApplication());editing.value=true;message.value='';error.value=''}
function openEdit(item:JobApplication){selected.value=item;Object.assign(form,{company:item.company||'',position:item.position||'',city:item.city||'',channel:item.channel||'其他',appliedDate:item.appliedDate||today(),stage:item.stage||'已投递',status:item.status||'等待结果',notes:item.notes||''});editing.value=true}
function closeEditors(){editing.value=false;eventEditor.value=false}
async function saveApplication(){
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
  Object.assign(eventForm,{type:'面试',title:'',startsAt:tomorrow.toISOString().slice(0,16),endsAt:'',location:'',notes:''});eventEditor.value=true
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
  <div v-if="store.user.value" class="toolbar"><input v-model="query" type="search" placeholder="搜索公司、岗位、地点、渠道或备注"><select v-model="stageFilter"><option>全部</option><option v-for="item in stages" :key="item">{{item}}</option></select><button class="secondary" @click="store.refresh">刷新</button></div>
  <div v-if="store.user.value&&filtered.length" class="grid">
    <button v-for="item in filtered" :key="item.id" class="application" @click="selected=item">
      <strong>{{text(item.company,'未填写公司')}}</strong><span>{{text(item.position,'未填写岗位')}}</span><div><b>{{text(item.stage,'未标记')}}</b><i>{{text(item.status,'未标记')}}</i></div><small>{{text(item.city,'地点未填')}} · {{text(item.channel,'渠道未填')}} · {{item.appliedDate||'日期未填'}}</small>
    </button>
  </div>
  <p v-else-if="store.user.value" class="empty">没有符合条件的投递记录。</p><p v-else>登录后查看和管理投递。</p>
</section>
<div v-if="message||error" class="feedback" :class="{danger:error}">{{error||message}} <button v-if="undo" @click="undoDelete">撤销删除</button></div>

<div v-if="selected&&!editing&&!eventEditor" class="backdrop" @click.self="selected=null">
  <section class="modal"><button class="close" @click="selected=null">×</button><div class="detail-head"><div><h2>{{text(selected.company)}}</h2><p>{{text(selected.position)}} · {{text(selected.city,'地点未填')}}</p></div><div class="badges"><b>{{selected.stage}}</b><i>{{selected.status}}</i></div></div>
    <div v-if="!store.readOnly.value" class="actions"><button @click="openEdit(selected)">编辑</button><button class="secondary" @click="openEvent">＋ 日程</button><button class="offer" @click="quickUpdate('Offer','已通过')">标记 Offer</button><button class="reject" @click="quickUpdate('已结束','未通过')">标记未通过</button><button class="danger-button" @click="removeApplication">删除</button></div>
    <dl><div><dt>投递日期</dt><dd>{{text(selected.appliedDate)}}</dd></div><div><dt>渠道</dt><dd>{{text(selected.channel)}}</dd></div><div class="wide"><dt>备注</dt><dd>{{text(selected.notes,'暂无备注')}}</dd></div></dl>
    <h3>安排记录</h3><div v-if="selectedEvents.length" class="timeline"><article v-for="item in selectedEvents" :key="item.id"><strong>{{text(item.title||item.type)}}</strong><span>{{text(item.startsAt||item.start||item.date,'时间未填')}} · {{item.completed?'已完成':'待处理'}}</span></article></div><p v-else class="empty">暂无安排。</p>
    <h3>状态历史</h3><div v-if="selectedTimeline.length" class="timeline"><article v-for="(item,index) in selectedTimeline" :key="String(item.id||index)"><strong>{{text(item.title,'状态更新')}}</strong><span>{{text(item.at,'')}}</span></article></div><p v-else class="empty">暂无历史。</p>
  </section>
</div>

<div v-if="editing" class="backdrop"><form class="modal form" @submit.prevent="saveApplication"><button type="button" class="close" @click="closeEditors">×</button><h2>{{selected?'编辑投递':'新建投递'}}</h2>
<label><span>公司 *</span><input v-model="form.company" required maxlength="120"></label><label><span>岗位 *</span><input v-model="form.position" required maxlength="160"></label><label><span>地点</span><input v-model="form.city"></label><label><span>渠道</span><select v-model="form.channel"><option v-for="item in channels" :key="item">{{item}}</option></select></label><label><span>投递日期</span><input v-model="form.appliedDate" type="date"></label><label><span>阶段</span><select v-model="form.stage"><option v-for="item in stages" :key="item">{{item}}</option></select></label><label><span>状态</span><select v-model="form.status"><option v-for="item in statuses" :key="item">{{item}}</option></select></label><label class="wide"><span>备注</span><textarea v-model="form.notes" rows="4"></textarea></label><div class="actions wide"><button :disabled="busy">保存</button><button type="button" class="secondary" @click="closeEditors">取消</button></div></form></div>

<div v-if="eventEditor&&selected" class="backdrop"><form class="modal form" @submit.prevent="saveEvent"><button type="button" class="close" @click="closeEditors">×</button><h2>新增关联日程</h2>
<label><span>类型</span><select v-model="eventForm.type"><option v-for="item in eventTypes" :key="item">{{item}}</option></select></label><label><span>名称 *</span><input v-model="eventForm.title" required placeholder="如：一面"></label><label><span>开始时间 *</span><input v-model="eventForm.startsAt" type="datetime-local" required></label><label><span>结束时间</span><input v-model="eventForm.endsAt" type="datetime-local"></label><label class="wide"><span>地点 / 链接</span><input v-model="eventForm.location"></label><label class="wide"><span>备注</span><textarea v-model="eventForm.notes" rows="3"></textarea></label><div class="actions wide"><button :disabled="busy">创建日程</button><button type="button" class="secondary" @click="closeEditors">取消</button></div></form></div>
</template>

<style scoped>
.head,.toolbar,.detail-head,.actions{display:flex;align-items:center;justify-content:space-between;gap:12px}.head span{color:#526ddd;font-size:11px;font-weight:800;letter-spacing:.1em}.head h2{margin:4px 0}.toolbar{margin:18px 0}.toolbar input{flex:1}.toolbar select{width:150px}.secondary{color:#344054;background:#eef2f8}.grid{display:grid;grid-template-columns:repeat(2,1fr);gap:12px}.application{display:grid;gap:8px;padding:17px;color:#172033;border:1px solid #e2e7f0;background:#fbfcfe;text-align:left}.application>span,.application small{color:#667085}.application div,.badges{display:flex;gap:7px}.application b,.application i,.badges b,.badges i{padding:5px 8px;border-radius:999px;background:#edf1ff;color:#3d55bd;font-size:12px;font-style:normal}.application i,.badges i{color:#475467;background:#eef2f6}.backdrop{position:fixed;inset:0;z-index:40;display:grid;place-items:center;padding:20px;background:rgba(17,24,39,.58)}.modal{position:relative;width:min(760px,100%);max-height:90vh;overflow:auto;padding:28px;border-radius:18px;background:#fff}.close{position:absolute;top:12px;right:12px;padding:4px 11px;color:#475467;background:#eef2f6;font-size:22px}.detail-head{padding-right:35px}.detail-head h2{margin:0}.actions{justify-content:flex-start;flex-wrap:wrap;margin:18px 0}.offer{background:#17804b}.reject,.danger-button{background:#bd3434}dl{display:grid;grid-template-columns:1fr 1fr;gap:10px}dl div{padding:12px;border-radius:10px;background:#f7f9fc}dl .wide,.form .wide{grid-column:1/-1}dt{color:#667085;font-size:11px}dd{margin:5px 0 0;white-space:pre-wrap}.timeline{display:grid;gap:8px}.timeline article{display:grid;gap:4px;padding:11px;border-left:3px solid #8396e9;background:#f7f9fc}.timeline span{color:#667085;font-size:12px}.form{display:grid;grid-template-columns:1fr 1fr;gap:14px}.form h2{grid-column:1/-1}.form label{display:grid;gap:7px;color:#475467;font-size:13px;font-weight:700}.form select,.form textarea{width:100%;padding:12px 14px;border:1px solid #d4dbea;border-radius:10px;background:#fff;font:inherit}.feedback{position:sticky;bottom:16px;z-index:20;margin:14px auto;padding:12px 16px;border-radius:12px;color:#167647;background:#e9f8ef;box-shadow:0 8px 30px rgba(0,0,0,.12)}.feedback.danger{color:#a52d2d;background:#fceaea}.feedback button{margin-left:12px;padding:7px 11px}.empty{color:#667085}@media(max-width:720px){.toolbar{align-items:stretch;flex-direction:column}.toolbar select{width:auto}.grid,.form,dl{grid-template-columns:1fr}.form .wide,dl .wide{grid-column:auto}}
</style>
