<script setup lang="ts">
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, reactive, ref, watch } from 'vue'
import { api, apiCached, invalidateApiCache } from './api'
import { summarizeInterviewReviews } from './interviewSummary'
import { isFormalInterview } from './eventClassification'
import { useJobTrackerStore, type JobApplication, type JobEvent } from './jobTrackerStore'
import ScheduleTimeModeNotice from './ScheduleTimeModeNotice.vue'

const store = useJobTrackerStore()
const props = defineProps<{ focusApplicationId?: string }>()
const query = ref('')
const applicationGrid = ref<HTMLElement | null>(null)
const highlightedApplicationId = ref('')
const revealActive = ref(false)
let revealObserver: IntersectionObserver | null = null
let nextRevealStart = 0
let focusFrame = 0
let highlightTimer = 0
let lastFocusedId = ''
let pendingFocusId = ''
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
const applicationCategories = [
  { label:'待参加日程', tone:'pending' },
  { label:'有面试进展', tone:'interview' },
  { label:'测评 / 笔试', tone:'assessment' },
  { label:'仅投递', tone:'applied' },
  { label:'未通过 / 已结束', tone:'stopped' },
  { label:'Offer / 已通过', tone:'offer' }
]
const stageCategories = applicationCategories.map(category=>category.label)
const companyLinks = ref<{company:string;url:string}[]>([])
const officialChoice = ref('__manual__')
const officialManual = ref('')
const officialLinkEditing = ref(false)
const officialLinkDraft = ref('')
const statuses = ['等待结果','已通过','未通过','已放弃','已结束']
const channels = ['官网','Boss直聘','实习僧','牛客','猎聘','智联招聘','前程无忧','国聘','校园招聘平台','内推','其他']
const eventTypes = ['测评','笔试','面试','Offer','其他']
const form = reactive(emptyApplication())
const eventForm = reactive<{type:string;title:string;timeMode:'point'|'range';startsAt:string;endsAt:string;location:string;notes:string;interviewQuestions:string}>({ type:'面试', title:'', timeMode:'point', startsAt:'', endsAt:'', location:'', notes:'', interviewQuestions:'' })
watch(()=>eventForm.timeMode,mode=>{if(mode==='point')eventForm.endsAt=''})
watch(store.newApplicationRequest, () => { if (!store.readOnly.value) openCreate() })
watch(store.applicationDetailRequest, request => {
  if (!request.applicationId) return
  const application = store.applications.value.find(item => item.id === request.applicationId)
  if (!application) return
  query.value = ''
  stageFilter.value = '全部'
  selected.value = application
  editing.value = false
  eventEditor.value = false
  editingEvent.value = null
}, { immediate: true })

async function focusApplicationInList() {
  const id = props.focusApplicationId || ''
  if (!id || id === lastFocusedId || id === pendingFocusId) return
  stopApplicationReveal()
  pendingFocusId = id
  query.value = ''
  stageFilter.value = '全部'
  selected.value = null
  await nextTick()
  await new Promise<void>(resolve => requestAnimationFrame(() => resolve()))
  const grid = applicationGrid.value
  const row = Array.from(grid?.querySelectorAll<HTMLElement>('[data-application-id]') || []).find(item => item.dataset.applicationId === id)
  if (!grid || !row || !row.isConnected || props.focusApplicationId !== id) { pendingFocusId = ''; return }
  lastFocusedId = id
  pendingFocusId = ''
  cancelAnimationFrame(focusFrame)
  window.clearTimeout(highlightTimer)
  highlightedApplicationId.value = ''

  const innerScroll = grid.scrollHeight > grid.clientHeight + 2
  const start = innerScroll ? (grid.scrollTop = 0) : (window.scrollTo(0, 0), window.scrollY)
  const rowTop = row.getBoundingClientRect().top
  const top = innerScroll
    ? Math.max(0, Math.min(grid.scrollHeight - grid.clientHeight, rowTop - grid.getBoundingClientRect().top + grid.scrollTop - grid.clientHeight * .28))
    : Math.max(0, rowTop + window.scrollY - window.innerHeight * .28)
  const distance = Math.abs(top - start)
  const duration = Math.min(1400, Math.max(650, distance * .28))
  const started = performance.now()
  function step(now: number) {
    const progress = Math.min(1, (now - started) / duration)
    const eased = progress < .5 ? 4 * progress ** 3 : 1 - (-2 * progress + 2) ** 3 / 2
    const position = start + (top - start) * eased
    if (innerScroll) grid!.scrollTop = position
    else window.scrollTo(0, position)
    if (progress < 1) focusFrame = requestAnimationFrame(step)
    else {
      highlightedApplicationId.value = id
      highlightTimer = window.setTimeout(() => { highlightedApplicationId.value = '' }, 1500)
    }
  }
  focusFrame = requestAnimationFrame(step)
}
function stopApplicationReveal() {
  revealObserver?.disconnect()
  revealObserver = null
  revealActive.value = false
  applicationGrid.value?.querySelectorAll<HTMLElement>('.application-fade-in').forEach(card => {
    card.classList.remove('application-fade-in')
    card.style.removeProperty('--application-reveal-delay')
  })
}
function observeApplicationCards() {
  const grid = applicationGrid.value
  if (!grid || !revealObserver || !revealActive.value) return
  revealObserver.disconnect()
  grid.querySelectorAll<HTMLElement>('.application:not(.application-fade-in)').forEach(card => revealObserver?.observe(card))
}
async function startApplicationReveal() {
  stopApplicationReveal()
  if (props.focusApplicationId) return
  revealActive.value = true
  await nextTick()
  const grid = applicationGrid.value
  if (!grid || !revealActive.value || typeof IntersectionObserver === 'undefined') {
    revealActive.value = false
    return
  }
  const duration = 520
  const interval = duration * .3
  grid.style.setProperty('--application-reveal-duration', `${duration}ms`)
  nextRevealStart = performance.now()
  const scrollRoot = grid.scrollHeight > grid.clientHeight + 2 ? grid : null
  revealObserver = new IntersectionObserver(entries => {
    const cards = Array.from(grid.querySelectorAll('.application'))
    const visible = entries.filter(entry => entry.isIntersecting)
      .sort((left, right) => cards.indexOf(left.target) - cards.indexOf(right.target))
    const now = performance.now()
    nextRevealStart = Math.max(now, Math.min(nextRevealStart, now + duration))
    visible.forEach(entry => {
      const card = entry.target as HTMLElement
      card.style.setProperty('--application-reveal-delay', `${Math.max(0, nextRevealStart - now)}ms`)
      card.classList.add('application-fade-in')
      nextRevealStart += interval
      revealObserver?.unobserve(card)
    })
  }, { root: scrollRoot, threshold: .05 })
  observeApplicationCards()
}
onActivated(() => { window.addEventListener('resize', stopApplicationReveal); void startApplicationReveal(); void focusApplicationInList(); void loadCompanyLinks() })
function stopFocusAnimation() { cancelAnimationFrame(focusFrame); window.clearTimeout(highlightTimer); highlightedApplicationId.value = ''; lastFocusedId = ''; pendingFocusId = '' }
onDeactivated(() => { window.removeEventListener('resize', stopApplicationReveal); stopApplicationReveal(); stopFocusAnimation() })
onBeforeUnmount(() => { window.removeEventListener('resize', stopApplicationReveal); stopApplicationReveal(); stopFocusAnimation() })

const filtered = computed(() => {
  const keyword=query.value.trim().toLowerCase()
  const inCurrentStage = store.applications.value.filter(item => stageFilter.value==='全部'||applicationCategory(item)===stageFilter.value)
  return inCurrentStage.filter(item => !keyword || Object.values(item).join(' ').toLowerCase().includes(keyword))
    .sort(compareApplications)
})
watch(filtered, () => {
  if (!revealActive.value) return
  void nextTick(() => {
    if (!applicationGrid.value) stopApplicationReveal()
    else observeApplicationCards()
  })
})
watch([query, stageFilter], stopApplicationReveal)
watch([() => props.focusApplicationId, () => filtered.value.some(item => item.id === props.focusApplicationId)], ([id, found]) => {
  if (!id) { lastFocusedId = ''; return }
  if (found) void focusApplicationInList()
}, { immediate: true })
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
const officialMatches = computed(() => {
  const key=normalizeCompanyName(form.company)
  if(!key)return []
  return companyLinks.value.map(item=>{const itemKey=normalizeCompanyName(item.company),exact=itemKey===key;return{...item,exact,related:exact||itemKey.includes(key)||key.includes(itemKey)}}).filter(item=>item.related&&item.url).sort((a,b)=>Number(b.exact)-Number(a.exact)||a.company.localeCompare(b.company,'zh-CN')).slice(0,8)
})
const officialMatchStatus = computed(() => {
  if(!form.company.trim())return '输入公司名称后自动检索官网库'
  if(officialMatches.value.some(item=>item.exact))return '已找到该公司的官网链接'
  return officialMatches.value.length?`找到 ${officialMatches.value.length} 个相近公司链接，可选择或手动填写`:'官网库暂无该公司链接，请手动填写'
})
async function loadCompanyLinks(){try{const result=await apiCached<{items:{company:string;url:string}[]}>('/api/poc/company-links');companyLinks.value=result.items||[]}catch{/* 官网搜索仍可用 */}}
function applicationMonthValue(date:Date){return date.getFullYear()*12+date.getMonth()}
function applicationDateKey(date:Date){const pad=(value:number)=>String(value).padStart(2,'0');return `${date.getFullYear()}-${pad(date.getMonth()+1)}-${pad(date.getDate())}`}
function clampApplicationMonth(date:Date,bounds=applicationMonthBounds.value){const value=applicationMonthValue(date);return value<applicationMonthValue(bounds.first)?bounds.first:value>applicationMonthValue(bounds.last)?bounds.last:date}
function moveApplicationHeatMonth(offset:number){applicationHeatMonth.value=clampApplicationMonth(new Date(applicationHeatMonth.value.getFullYear(),applicationHeatMonth.value.getMonth()+offset,1))}
function stageCategory(item:JobApplication){if(item.stage==='已结束'||['未通过','已放弃','已结束'].includes(String(item.status||'')))return '已结束';return ['测评','笔试','面试','Offer'].includes(String(item.stage||''))?String(item.stage):'仅投递'}
function eventDeadline(item:Record<string,unknown>){return String(item.endsAt||item.end||item.startsAt||item.start||item.date||'')}
function eventOccurrenceTime(item:Record<string,unknown>){return String(item.startsAt||item.start||item.date||item.at||'')}
function eventRecordTime(item:Record<string,unknown>){return eventOccurrenceTime(item)}
function interviewParticipationTime(event:JobEvent){
  const end=String(event.endsAt||event.end||'').trim()
  if(!end)return timeOf(event.startsAt||event.start||event.date)
  return event.completed?timeOf(event.completedAt):0
}
function health(item:JobApplication){
  if(String(item.stage)!=='面试'||String(item.status)!=='等待结果')return null
  const interviews=store.events.value.filter(event=>event.applicationId===item.id&&isFormalInterview(event)&&!event.missed)
  if(interviews.some(event=>!event.completed))return null
  const times=interviews
    .map(interviewParticipationTime)
    .filter(Boolean)
  if(!times.length)return null
  const latest=Math.max(...times)
  const today=new Date(),scheduled=new Date(latest)
  const todayDate=new Date(today.getFullYear(),today.getMonth(),today.getDate()).getTime()
  const scheduledDate=new Date(scheduled.getFullYear(),scheduled.getMonth(),scheduled.getDate()).getTime()
  const days=Math.max(0,Math.floor((todayDate-scheduledDate)/86400000))
  return {days,label:days<=3?'进展正常':days<10?'等待较久':'建议确认',tone:days<=3?'good':days<10?'watch':'risk'}
}
function cardTone(item:JobApplication){if(item.stage==='Offer'||item.status==='已通过')return 'offer';if(stageCategory(item)==='已结束')return 'stopped';const events=store.events.value.filter(e=>e.applicationId===item.id&&!e.completed&&!e.missed);if(events.some(e=>new Date(eventDeadline(e).replace(' ','T')).getTime()>=Date.now()))return 'pending';if(store.events.value.some(e=>e.applicationId===item.id&&isFormalInterview(e)))return 'interview';if(['测评','笔试'].includes(String(item.stage))||store.events.value.some(e=>e.applicationId===item.id&&['测评','笔试'].includes(String(e.type))))return 'assessment';return 'applied'}
function applicationCategory(item:JobApplication){const tone=cardTone(item);return applicationCategories.find(category=>category.tone===tone)?.label||'仅投递'}
function timeOf(value:unknown){const time=new Date(String(value||'').replace(' ','T')).getTime();return Number.isFinite(time)?time:0}
function hasInterviewProgress(item:JobApplication){return ['面试','Offer'].includes(String(item.stage))||item.status==='已通过'||store.events.value.some(event=>event.applicationId===item.id&&!event.missed&&(isFormalInterview(event)||event.type==='Offer'))}
function currentStageRank(item:JobApplication){
  const stage=stageCategory(item)
  if(stage==='Offer')return 0
  if(stage==='面试')return 1
  if(stage==='笔试'||stage==='测评')return 2
  if(stage==='仅投递')return 3
  return 4
}
function latestScheduleTime(item:JobApplication){
  const scheduleTimes=store.events.value
    .filter(event=>event.applicationId===item.id)
    .map(event=>timeOf(eventDeadline(event)))
    .filter(Boolean)
  return scheduleTimes.length?Math.max(...scheduleTimes):timeOf(item.appliedDate||item.createdAt)
}
function pendingScheduleTime(item:JobApplication){
  if(item.stage==='Offer'||item.status==='已通过'||stageCategory(item)==='已结束')return Infinity
  const now=Date.now()
  const pendingTimes=store.events.value
    .filter(event=>event.applicationId===item.id&&!event.completed&&!event.missed)
    .map(event=>timeOf(eventDeadline(event)))
    .filter(time=>time>=now)
  return pendingTimes.length?Math.min(...pendingTimes):Infinity
}
function compareApplications(a:JobApplication,b:JobApplication){
  const aPendingTime=pendingScheduleTime(a),bPendingTime=pendingScheduleTime(b)
  const aPending=Number.isFinite(aPendingTime),bPending=Number.isFinite(bPendingTime)
  if(aPending!==bPending)return aPending?-1:1
  if(aPending&&bPending&&aPendingTime!==bPendingTime)return aPendingTime-bPendingTime
  const stageDifference=currentStageRank(a)-currentStageRank(b)
  if(stageDifference)return stageDifference
  const scheduleDifference=latestScheduleTime(b)-latestScheduleTime(a)
  if(scheduleDifference)return scheduleDifference
  const appliedDifference=timeOf(b.appliedDate||b.createdAt)-timeOf(a.appliedDate||a.createdAt)
  if(appliedDifference)return appliedDifference
  return String(a.id).localeCompare(String(b.id),'zh-CN')
}
function flowVisual(label:unknown,type:unknown=''){const value=`${type||''} ${label||''}`;if(/未通过|错过|放弃/.test(value))return{style:'failed',icon:'×'};if(/Offer|录用|通过/.test(value))return{style:'offer',icon:'★'};if(value.includes('测评'))return{style:'assessment',icon:'◇'};if(value.includes('笔试'))return{style:'test',icon:'✎'};if(/面试|[一二三四五六七八九]面|HR/.test(value))return{style:'interview',icon:'◎'};if(value.includes('电话'))return{style:'phone',icon:'☎'};if(value.includes('等待'))return{style:'waiting',icon:'◷'};if(value.includes('投递'))return{style:'applied',icon:'↗'};return{style:'other',icon:'＋'}}
function eventFlowDate(item:Record<string,unknown>){const start=eventOccurrenceTime(item),end=String(item.endsAt||item.end||'');return end?`${start.slice(0,10)} 至 ${end.slice(0,10)}`:start.slice(0,10)}
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
function normalizeCompanyName(value:unknown){return String(value||'').trim().replace(/\s+/g,' ').toLocaleLowerCase('zh-CN')}
function configuredOfficialUrl(item:JobApplication){const company=normalizeCompanyName(item.company);return companyLinks.value.find(link=>normalizeCompanyName(link.company)===company)?.url||''}
function officialUrl(item:JobApplication){return configuredOfficialUrl(item)||'https://www.bing.com/search?q='+encodeURIComponent(String(item.company||'')+' 校园招聘 官网')}
function localParts(date=new Date()){const pad=(v:number)=>String(v).padStart(2,'0');return date.getFullYear()+'-'+pad(date.getMonth()+1)+'-'+pad(date.getDate())+'T'+pad(date.getHours())+':'+pad(date.getMinutes())}
function today(){return localParts().slice(0,10)}
function emptyApplication(){return {company:'',position:'',city:'',channel:'官网',appliedDate:today(),stage:'已投递',status:'等待结果',notes:'',jobDescription:''}}
function text(value:unknown,fallback='未填写'){return String(value||fallback)}
function openCreate(){selected.value=null;Object.assign(form,emptyApplication());officialChoice.value='__manual__';officialManual.value='';editing.value=true;message.value='';error.value='';aiChanges.value=[];aiWarnings.value=[]}
function openEdit(item:JobApplication){selected.value=item;aiChanges.value=[];aiWarnings.value=[];Object.assign(form,{company:item.company||'',position:item.position||'',city:item.city||'',channel:item.channel||'其他',appliedDate:item.appliedDate||today(),stage:item.stage||'已投递',status:item.status||'等待结果',notes:item.notes||'',jobDescription:item.jobDescription||''});editing.value=true}
function closeEditors(){editing.value=false;eventEditor.value=false;editingEvent.value=null}
function openOfficialLinkEditor(){if(!selected.value)return;officialLinkDraft.value=configuredOfficialUrl(selected.value);officialLinkEditing.value=true;error.value='';message.value=''}
function closeOfficialLinkEditor(){officialLinkEditing.value=false;officialLinkDraft.value=''}
async function saveSelectedOfficialLink(){
  if(!selected.value)return
  const company=String(selected.value.company||'').trim(),url=officialLinkDraft.value.trim()
  if(!company){error.value='当前投递没有可用于保存官网链接的公司名称';return}
  if(!/^https?:\/\//i.test(url)){error.value='官网链接需以 http:// 或 https:// 开头';return}
  busy.value=true;error.value='';message.value=''
  try{
    const key=normalizeCompanyName(company)
    const items=companyLinks.value.filter(item=>normalizeCompanyName(item.company)!==key&&Boolean(item.company.trim())&&/^https?:\/\//i.test(item.url.trim())).map(item=>({company:item.company.trim(),url:item.url.trim()}))
    items.push({company,url})
    const result=await api<{items:{company:string;url:string}[]}>('/api/poc/company-links',{method:'POST',body:JSON.stringify({items:items.sort((a,b)=>a.company.localeCompare(b.company,'zh-CN'))})})
    invalidateApiCache('/api/poc/company-links')
    companyLinks.value=result.items||items;officialLinkEditing.value=false;officialLinkDraft.value='';message.value='公司官网链接已更新'
  }catch(cause){error.value=cause instanceof Error?cause.message:'保存公司官网链接失败'}finally{busy.value=false}
}
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
function applyOfficialCompany(){
  if(officialChoice.value==='__manual__')return
  const matched=officialMatches.value.find(item=>item.url===officialChoice.value)
  if(matched)form.company=matched.company
}
async function saveCompanyOfficialLink(){
  const company=form.company.trim(),url=(officialChoice.value==='__manual__'?officialManual.value:officialChoice.value).trim()
  if(!url)return
  if(!/^https?:\/\//i.test(url))throw new Error('官网链接需以 http:// 或 https:// 开头')
  const key=normalizeCompanyName(company)
  const items=companyLinks.value.filter(item=>normalizeCompanyName(item.company)!==key&&Boolean(item.company.trim())&&/^https?:\/\//i.test(item.url.trim())).map(item=>({company:item.company.trim(),url:item.url.trim()}))
  items.push({company,url})
  const result=await api<{items:{company:string;url:string}[]}>('/api/poc/company-links',{method:'POST',body:JSON.stringify({items:items.sort((a,b)=>a.company.localeCompare(b.company,'zh-CN'))})})
  invalidateApiCache('/api/poc/company-links')
  companyLinks.value=result.items||items
}
async function saveApplication(){
  if(!selected.value)applyOfficialCompany()
  if(!selected.value){const duplicate=store.applications.value.find(item=>String(item.company||'').trim().toLowerCase()===form.company.trim().toLowerCase()&&String(item.position||'').trim().toLowerCase()===form.position.trim().toLowerCase());if(duplicate){editing.value=false;selected.value=duplicate;error.value='';message.value='已存在相同公司和岗位的投递，不会重复创建；你可以直接追加日程或编辑原记录';return}}
  busy.value=true;error.value='';message.value=''
  try{
    const current=selected.value
    if(!current)await saveCompanyOfficialLink()
    const response=current
      ? await api<{application:JobApplication}>(`/api/poc/application-sandbox/applications/${encodeURIComponent(current.id)}`,{method:'PUT',body:JSON.stringify({...form,expectedUpdatedAt:current.updatedAt||''})})
      : await api<{application:JobApplication}>('/api/poc/application-sandbox/applications',{method:'POST',body:JSON.stringify({...form,expectedUpdatedAt:''})})
    await store.refresh();selected.value=store.applications.value.find(item=>item.id===response.application.id)||response.application;editing.value=false;message.value=current?'投递已更新':'投递已创建';undo.value=null
  }catch(cause){error.value=cause instanceof Error?cause.message:'保存失败'}finally{busy.value=false}
}
async function quickUpdate(stage:string,status:string){
  if(!selected.value)return
  Object.assign(form,{company:selected.value.company||'',position:selected.value.position||'',city:selected.value.city||'',channel:selected.value.channel||'其他',appliedDate:selected.value.appliedDate||today(),notes:selected.value.notes||'',jobDescription:selected.value.jobDescription||'',stage,status})
  await saveApplication()
}
async function removeApplication(){
  const item=selected.value;if(!item||!confirm(`确认删除“${item.company} / ${item.position}”及其关联日程吗？`))return
  busy.value=true;error.value='';message.value=''
  try{
    await api(`/api/poc/application-sandbox/applications/${encodeURIComponent(item.id)}`,{method:'DELETE',body:JSON.stringify({expectedUpdatedAt:item.updatedAt||''})})
    const backups=await api<{items:{id:number}[];currentUpdatedAt:string}>('/api/poc/backup-sandbox/backups')
    undo.value=backups.items.length?{backupId:backups.items[0].id,expected:backups.currentUpdatedAt}:null
    selected.value=null;await store.refresh();message.value='投递及关联日程已删除'
  }catch(cause){error.value=cause instanceof Error?cause.message:'删除失败'}finally{busy.value=false}
}
async function undoDelete(){
  if(!undo.value)return
  busy.value=true;error.value='';message.value=''
  try{
    await api(`/api/poc/backup-sandbox/backups/${undo.value.backupId}/restore`,{method:'POST',body:JSON.stringify({expectedCurrentUpdatedAt:undo.value.expected})})
    undo.value=null;await store.refresh();message.value='刚才删除的投递和日程已恢复'
  }catch(cause){error.value=cause instanceof Error?cause.message:'撤销失败'}finally{busy.value=false}
}
function toInputTime(value:unknown){return String(value||'').replace(' ','T').slice(0,16)}
function eventDateLabel(item:JobEvent){const value=eventOccurrenceTime(item);const match=value.match(/^\d{4}-(\d{2})-(\d{2})/);return match?`${Number(match[1])}月${Number(match[2])}日`:'日期未填'}
function eventTimeLabel(item:JobEvent){const start=eventOccurrenceTime(item),end=String(item.endsAt||item.end||'');const startTime=start.slice(11,16)||'时间未填';return end?`${startTime} 至 ${end.slice(0,10)===start.slice(0,10)?end.slice(11,16):end.slice(0,16).replace('T',' ')}`:startTime}
function eventState(item:JobEvent){return item.abandoned||(!item.completed&&selected.value?.status==='已放弃')?'已放弃':item.missed?'已错过':item.completed?'已完成':'待处理'}
function eventLink(value:unknown){const text=String(value||'').trim();return /^https?:\/\//i.test(text)?text:''}
function eventVersion(item:JobEvent){return String(item.updatedAt||item.createdAt||'')}
async function refreshSelected(){const id=selected.value?.id;await store.refresh();if(id)selected.value=store.applications.value.find(item=>item.id===id)||selected.value}
function openEvent(item?:JobEvent){
  editingEvent.value=item||null
  if(item){Object.assign(eventForm,{type:item.type||'面试',title:item.title||'',timeMode:item.endsAt||item.end?'range':'point',startsAt:toInputTime(item.startsAt||item.start||item.date),endsAt:toInputTime(item.endsAt||item.end),location:item.location||'',notes:item.notes||'',interviewQuestions:item.interviewQuestions||''})}
  else{const tomorrow=new Date();tomorrow.setDate(tomorrow.getDate()+1);tomorrow.setHours(9,0,0,0);Object.assign(eventForm,{type:'面试',title:'',timeMode:'point',startsAt:localParts(tomorrow),endsAt:'',location:'',notes:'',interviewQuestions:''})}
  eventEditor.value=true
}
async function saveEvent(){
  if(!selected.value)return
  busy.value=true;error.value='';message.value=''
  try{
    const current=editingEvent.value
    const changedReview=Boolean(current?.completed&&eventForm.interviewQuestions.trim()&&String(current.interviewQuestions||'').trim()!==eventForm.interviewQuestions.trim())
    if(eventForm.timeMode==='range'&&!eventForm.endsAt){error.value='时间段日程必须填写结束时间';return}
    if(eventForm.timeMode==='range'&&new Date(eventForm.endsAt).getTime()<=new Date(eventForm.startsAt).getTime()){error.value='结束时间必须晚于开始时间';return}
    await api(current?`/api/poc/event-sandbox/events/${encodeURIComponent(current.id)}`:'/api/poc/event-sandbox/events',{method:current?'PUT':'POST',body:JSON.stringify({applicationId:selected.value.id,type:eventForm.type,title:eventForm.title,startsAt:eventForm.startsAt.replace('T',' '),endsAt:eventForm.timeMode==='range'?eventForm.endsAt.replace('T',' '):'',location:eventForm.location,notes:eventForm.notes,...(current?.completed&&!current.missed&&!current.abandoned?{interviewQuestions:eventForm.interviewQuestions}:{}),expectedUpdatedAt:current?eventVersion(current):''})})
    eventEditor.value=false;editingEvent.value=null;await refreshSelected();message.value=current?'日程已更新':'关联日程已创建'
    if(changedReview)void summarizeInterviewReviews().catch(()=>{ /* The workbench offers a retry when AI is unavailable. */ })
  }catch(cause){error.value=cause instanceof Error?cause.message:'保存日程失败'}finally{busy.value=false}
}
async function resolveEvent(item:JobEvent,action:'complete'|'abandon'|'restore'){
  busy.value=true;error.value='';message.value=''
  try{await api(`/api/poc/event-sandbox/events/${encodeURIComponent(item.id)}/resolution`,{method:'POST',body:JSON.stringify({action,expectedUpdatedAt:eventVersion(item)})});await refreshSelected();message.value=action==='complete'?'日程已完成':action==='abandon'?'日程已放弃':'日程已恢复为待处理'}
  catch(cause){error.value=cause instanceof Error?cause.message:'更新日程状态失败'}finally{busy.value=false}
}
async function removeEvent(item:JobEvent){
  if(!confirm(`确认删除“${item.title||item.type||'日程'}”吗？`))return
  busy.value=true;error.value='';message.value=''
  try{await api(`/api/poc/event-sandbox/events/${encodeURIComponent(item.id)}`,{method:'DELETE',body:JSON.stringify({expectedUpdatedAt:eventVersion(item)})});await refreshSelected();message.value='日程已删除'}
  catch(cause){error.value=cause instanceof Error?cause.message:'删除日程失败'}finally{busy.value=false}
}
</script>

<template>
<Teleport defer to="#application-toolbar-slot">
  <div class="application-toolbar-portal">

    <div v-if="store.user.value" class="toolbar">
      <div class="application-filter-stack">
        <div class="application-filter-fields"><input v-model="query" type="search" aria-label="在当前筛选范围内搜索投递记录" placeholder="搜索公司、岗位、地点、渠道或备注"></div>
        <div class="application-stage-tabs" role="group" aria-label="投递阶段筛选">
          <button v-for="category in ['全部',...stageCategories]" :key="category" type="button" :aria-pressed="stageFilter===category" :class="{active:stageFilter===category}" @click="stageFilter=category">{{category}}</button>
        </div>
        <div v-if="stageFilter==='全部'" class="application-legend" aria-label="投递卡片颜色说明"><span v-for="category in applicationCategories" :key="category.tone"><i :class="category.tone"></i>{{category.label}}</span></div>
      </div>
      <aside class="application-heatmap" aria-label="月度投递数量热力图">
        <div class="heatmap-calendar"><span class="heatmap-caption">每日投递概览</span><div class="heatmap-grid"><i v-for="cell in applicationHeatCells" :key="cell.key" :class="[`level-${cell.level}`,{ outside:!cell.inMonth }]" :title="cell.inMonth ? `${cell.key}：${cell.count} 条投递` : ''"></i></div></div>
        <div class="heatmap-controls"><strong>{{ applicationHeatTitle }}</strong><div><button type="button" :disabled="!canHeatPrevious" aria-label="上一个月份" title="上一个月" @click="moveApplicationHeatMonth(-1)"><AppIcon name="chevron-left" :size="14" /></button><button type="button" :disabled="!canHeatNext" aria-label="下一个月份" title="下一个月" @click="moveApplicationHeatMonth(1)"><AppIcon name="chevron-right" :size="14" /></button></div></div>
      </aside>
    </div>

  </div>
</Teleport>
<section class="card workspace">
  <div v-if="store.user.value&&filtered.length" ref="applicationGrid" class="grid" :class="{ 'application-revealing': revealActive }">
    <button v-for="item in filtered" :key="item.id" :data-application-id="item.id" class="application" :class="[`tone-${cardTone(item)}`, { 'application-arrival': highlightedApplicationId === item.id }]" @click="selected=item">
      <div class="application-overview"><div class="application-title"><strong>{{text(item.company,'未填写公司')}}</strong><i>·</i><span>{{text(item.position,'未填写岗位')}}</span><i>·</i><small>{{text(item.city,'地点未填')}}</small><i>·</i><small>{{text(item.channel,'渠道未填')}}</small></div><span v-if="String(item.notes||'').trim()" class="application-note" :title="text(item.notes)"><b>备注：</b>{{text(item.notes)}}</span><em v-if="health(item)" :class="`health-${health(item)?.tone}`">{{health(item)?.label}} · {{health(item)?.days}}天</em></div>
      <div class="flow" :style="{'--flow-slots':flowSlots}" aria-label="投递流程"><span v-for="(node,index) in flow(item)" :key="`${node.label}-${index}`" class="flow-node" :class="[node.kind,`flow-${node.style}`]"><i>{{node.icon}}</i><b>{{node.label}}</b><small>{{node.at}}</small></span></div>
    </button>
  </div>
  <p v-else-if="store.user.value" class="empty">没有符合条件的投递记录。</p><p v-else>登录后查看和管理投递。</p>
</section>
<div v-if="message||error" class="feedback" :class="{danger:error}">{{error||message}} <button v-if="undo" @click="undoDelete">撤销删除</button></div>

<Teleport to="body">
<div v-if="selected&&!editing&&!eventEditor" class="backdrop" @click.self="selected=null">
  <section class="modal detail-modal" role="dialog" aria-modal="true" aria-labelledby="application-detail-title">
    <div class="detail-fixed-header">
    <button class="close icon-button" aria-label="关闭投递详情" title="关闭" @click="selected=null"><AppIcon name="close" /></button>
    <h2 id="application-detail-title" class="detail-title">投递详情</h2>
    <div class="detail-company-row">
      <div class="company-identity">
        <div class="company-name-line"><a :href="officialUrl(selected)" target="_blank" rel="noreferrer" title="打开招聘官网"><span class="company-icon" aria-hidden="true">◎</span><strong>{{text(selected.company)}}</strong></a><button v-if="!store.readOnly.value" type="button" class="secondary icon-button compact-icon official-link-edit" :disabled="busy" aria-label="编辑公司官网链接" title="编辑官网链接" @click="openOfficialLinkEditor"><AppIcon name="edit" :size="13" /></button></div>
        <p>{{text(selected.position)}} · {{text(selected.city,'地点未填')}}</p>
      </div>
      <div class="badges"><b>{{selected.stage}}</b><i>{{selected.status}}</i></div>
    </div>
    <div v-if="!store.readOnly.value" class="actions detail-actions">
      <button class="icon-button" :disabled="busy" aria-label="编辑投递" title="编辑投递" @click="openEdit(selected)"><AppIcon name="edit" /></button><button class="secondary icon-button" :disabled="busy" aria-label="新增关联日程" title="新增日程" @click="openEvent()"><AppIcon name="calendar" /></button><button class="offer" :disabled="busy" @click="quickUpdate('Offer','已通过')">Offer</button><button class="abandon" :disabled="busy" @click="quickUpdate('已结束','已放弃')">放弃</button><button class="reject" :disabled="busy" @click="quickUpdate('已结束','未通过')">未通过</button><button class="danger-button push-right icon-button" :disabled="busy" aria-label="删除投递" title="删除投递" @click="removeApplication"><AppIcon name="trash" /></button>
    </div>
    </div>
    <div class="detail-scroll">
    <h3 class="section-heading">基本信息</h3>
    <div class="basic-info"><div><strong>投递日期</strong><span>{{text(selected.appliedDate)}}</span><strong>投递渠道</strong><span>{{text(selected.channel)}}</span></div><p v-if="String(selected.notes||'').trim()"><b>备注</b>{{selected.notes}}</p></div>
    <section v-if="String(selected.jobDescription||'').trim()" class="job-description"><h3 class="section-heading">岗位快照 · 岗位描述</h3><p>{{selected.jobDescription}}</p></section>
    <button v-else-if="!store.readOnly.value" type="button" class="secondary add-description" @click="openEdit(selected)">添加岗位描述</button>
    <h3 class="section-heading">安排记录</h3>
    <div v-if="selectedEvents.length" class="event-records">
      <article v-for="item in selectedEvents" :key="item.id" class="event-record">
        <div class="event-rail"><i></i></div>
        <div class="event-date"><strong>{{eventDateLabel(item)}}</strong><span>{{eventTimeLabel(item)}}</span></div>
        <div class="event-body"><strong>{{text(selected.company)}} · {{text(item.title||item.type)}}</strong><div class="event-meta"><span>{{text(selected.position)}}</span><a v-if="eventLink(item.location)" :href="eventLink(item.location)" target="_blank" rel="noreferrer">打开链接 ↗</a><span v-else-if="item.location">{{item.location}}</span><b>{{text(item.type,'日程')}}</b><i>{{eventState(item)}}</i></div><p v-if="String(item.notes||'').trim()">备注：{{item.notes}}</p><details v-if="String(item.interviewQuestions||'').trim()" class="event-review"><summary>面试回顾</summary><p>{{item.interviewQuestions}}</p></details></div>
        <div v-if="!store.readOnly.value" class="event-row-actions"><button class="secondary icon-button compact-icon" :disabled="busy" aria-label="编辑日程" title="编辑" @click="openEvent(item)"><AppIcon name="edit" :size="16" /></button><button class="event-delete icon-button compact-icon" :disabled="busy" aria-label="删除日程" title="删除" @click="removeEvent(item)"><AppIcon name="trash" :size="16" /></button><button v-if="item.completed" class="icon-button compact-icon" :disabled="busy" aria-label="恢复日程" title="恢复" @click="resolveEvent(item,'restore')"><AppIcon name="undo" :size="16" /></button><template v-else><button class="icon-button compact-icon" :disabled="busy" aria-label="完成日程" title="完成" @click="resolveEvent(item,'complete')"><AppIcon name="check" :size="16" /></button><button class="abandon icon-button compact-icon" :disabled="busy" aria-label="放弃日程" title="放弃" @click="resolveEvent(item,'abandon')"><AppIcon name="ban" :size="16" /></button></template></div>
      </article>
    </div><p v-else class="empty">暂无安排。</p>
    <h3 class="section-heading">状态历史</h3>
    <div v-if="selectedTimeline.length" class="history-list"><article v-for="(item,index) in selectedTimeline" :key="String(item.id||index)"><i></i><div><strong>{{text(item.title,'状态更新')}}</strong><span>{{text(item.at,'')}}</span></div></article></div><p v-else class="empty">暂无历史。</p>
    </div>
  </section>
</div>
</Teleport>
<Teleport to="body">
<div v-if="officialLinkEditing&&selected" class="backdrop official-link-backdrop" @click.self="closeOfficialLinkEditor"><form class="modal official-link-modal" role="dialog" aria-modal="true" aria-labelledby="official-link-editor-title" @submit.prevent="saveSelectedOfficialLink"><button type="button" class="close icon-button" aria-label="关闭官网链接编辑窗口" title="关闭" @click="closeOfficialLinkEditor"><AppIcon name="close" /></button><h2 id="official-link-editor-title">编辑官网链接</h2><p>{{text(selected.company)}} 的招聘官网地址</p><label><span>官网链接 *</span><input v-model="officialLinkDraft" type="url" required autofocus placeholder="https://careers.example.com" autocomplete="url"></label><p v-if="error" class="field-error" role="alert">{{error}}</p><div class="actions"><button :disabled="busy">{{busy?'正在保存…':'保存链接'}}</button><button type="button" class="secondary" :disabled="busy" @click="closeOfficialLinkEditor">取消</button></div></form></div>
</Teleport>
<Teleport to="body">
<div v-if="editing" class="backdrop"><form class="modal form" role="dialog" aria-modal="true" aria-labelledby="application-editor-title" @submit.prevent="saveApplication"><button type="button" class="close icon-button" aria-label="关闭投递编辑窗口" title="关闭" @click="closeEditors"><AppIcon name="close" /></button><h2 id="application-editor-title">{{selected?'编辑投递':'新建投递'}}</h2>
<label><span>公司 *</span><input v-model="form.company" required maxlength="120" @input="officialChoice=officialMatches.find(item=>item.exact)?.url||'__manual__'"></label><label><span>岗位 *</span><input v-model="form.position" required maxlength="160"></label><label v-if="!selected" class="wide official-link-field"><span>公司官网链接 <small>{{officialMatchStatus}}</small></span><BaseSelect v-model="officialChoice" :options="[{value:'__manual__',label:officialMatches.length?'手动填写其他链接':'手动填写官网链接'},...officialMatches.map(item=>({value:item.url,label:`${item.exact?'〔已匹配〕':'〔相近公司〕'} ${item.company} · ${item.url}`}))]" @update:model-value="applyOfficialCompany" /><input v-if="officialChoice==='__manual__'" v-model="officialManual" type="url" placeholder="https://careers.example.com" autocomplete="url"><small>链接按公司统一保存，同一公司的其他投递会自动复用。</small></label><label><span>地点</span><input v-model="form.city"></label><label><span>渠道</span><BaseSelect v-model="form.channel" :options="channels" /></label><label><span>投递日期</span><input v-model="form.appliedDate" type="date"></label><label><span>阶段</span><BaseSelect v-model="form.stage" :options="stages" /></label><label><span>状态</span><BaseSelect v-model="form.status" :options="statuses" /></label><label class="wide"><span>岗位快照 · 岗位描述</span><textarea v-model="form.jobDescription" maxlength="20000" rows="7" placeholder="粘贴招聘页面中的职责、要求和其他岗位信息"></textarea><small>这是投递时的岗位快照，与个人备注分开保存。</small></label><label class="wide"><span>备注</span><textarea v-model="form.notes" maxlength="4000" rows="3"></textarea></label><div v-if="aiChanges.length||aiWarnings.length" class="ai-review wide"><p v-for="item in aiChanges" :key="item">✓ {{item}}</p><p v-for="item in aiWarnings" :key="item" class="warn">请核对：{{item}}</p></div><div class="actions wide"><button type="button" class="ai-button" :disabled="busy" @click="normalizeApplication">✦ AI 规范</button><button :disabled="busy">保存</button><button type="button" class="secondary" @click="closeEditors">取消</button></div></form></div>
</Teleport>

<Teleport to="body">
<div v-if="eventEditor&&selected" class="backdrop"><form class="modal form" role="dialog" aria-modal="true" aria-labelledby="schedule-editor-title" @submit.prevent="saveEvent"><button type="button" class="close icon-button" aria-label="关闭日程编辑窗口" title="关闭" @click="closeEditors"><AppIcon name="close" /></button><h2 id="schedule-editor-title">{{editingEvent?'编辑日程':'新增关联日程'}}</h2>
<ScheduleTimeModeNotice class="wide" :mode="eventForm.timeMode" selectable @select="eventForm.timeMode=$event" /><label><span>类型</span><BaseSelect v-model="eventForm.type" :options="eventTypes" /></label><label><span>名称 *</span><input v-model="eventForm.title" required placeholder="如：一面"></label><div class="schedule-time-fields wide" :class="{'is-point':eventForm.timeMode==='point'}"><label><span>{{eventForm.timeMode==='range'?'开始时间 *':'时间 *'}}</span><input v-model="eventForm.startsAt" type="datetime-local" required></label><label v-if="eventForm.timeMode==='range'"><span>结束时间 *</span><input v-model="eventForm.endsAt" type="datetime-local" :min="eventForm.startsAt" required></label></div><label class="wide"><span>地点 / 链接</span><input v-model="eventForm.location"></label><label class="wide"><span>备注</span><textarea v-model="eventForm.notes" maxlength="4000" rows="3"></textarea></label><label v-if="editingEvent?.completed&&!editingEvent.missed&&!editingEvent.abandoned" class="wide"><span>面试回顾 · 面试官问题清单</span><textarea v-model="eventForm.interviewQuestions" maxlength="8000" rows="6" placeholder="每行记录一个面试官提出的问题"></textarea><small>与日程备注独立保存，可在面试后随时补充。</small></label><div class="actions wide"><button :disabled="busy">{{editingEvent?'保存日程':'创建日程'}}</button><button type="button" class="secondary" @click="closeEditors">取消</button></div></form></div>
</Teleport>
</template>

<style scoped>
.application-title {
  min-width: 0;
}

.application-note {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: var(--color-muted-foreground);
  font-size: 12px;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.application-note b {
  color: var(--color-card-foreground);
  font-weight: 700;
}
.field-error{margin:0;padding:9px 12px;border:1px solid color-mix(in srgb,var(--color-destructive) 35%,var(--color-border));border-radius:9px;color:var(--color-destructive);background:color-mix(in srgb,var(--color-destructive) 9%,var(--color-card));font-size:12px;font-weight:700}

.application-overview{display:flex;align-items:center;justify-content:space-between;gap:18px}.application-title{display:flex;align-items:center;flex-wrap:wrap;row-gap:4px;color:var(--color-muted-foreground);font-size:13px}.application-title strong{color:var(--color-foreground);font-size:16px}.application-title>span{color:#526159}.application-title>i{margin:0 9px;color:#a0aaa4;font-style:normal}.application-title>small{color:var(--color-muted-foreground);font-size:13px}.application-overview>em{flex:0 0 auto;padding:4px 7px;border-radius:999px;font-size:11px;font-style:normal}.health-good{color:#167647;background:#e9f8ef}.health-watch{color:#8a5608;background:#fff3d6}.health-risk{color:#a52d2d;background:#fceaea}.application.tone-pending{border-left:5px solid #d89226}.application.tone-interview{border-left:5px solid #7a60d1}.application.tone-assessment{border-left:5px solid #4382c4}.application.tone-offer{border-left:5px solid #248459}.application.tone-stopped{border-left:5px solid #a5acb9}.flow{display:grid!important;grid-template-columns:repeat(var(--flow-slots,1),minmax(76px,1fr));align-items:flex-start;min-width:0;margin-top:17px;padding:2px 3px 4px;overflow-x:auto}.flow-node{--node-color:#718078;--node-soft:#eef2ef;position:relative;display:grid!important;width:100%;min-width:76px;justify-items:center;gap:0!important;text-align:center}.flow-node:not(:first-child)::before{content:"";position:absolute;left:calc(-50% + 15px);top:14px;width:calc(100% - 30px);height:2px;border-radius:99px;background:#dce3df}.flow-node>i{position:relative;z-index:1;display:grid;width:30px;height:30px;place-items:center;padding:0!important;border:2px solid color-mix(in srgb,var(--node-color) 72%,white);border-radius:10px;background:var(--node-soft)!important;color:var(--node-color)!important;font:800 16px "Segoe UI Symbol","Microsoft YaHei UI",sans-serif;box-shadow:0 0 0 4px #eef6ff,0 3px 8px color-mix(in srgb,var(--node-color) 16%,transparent)}.flow-node>b{max-width:124px;margin-top:7px;padding:0!important;overflow:hidden;background:none!important;color:var(--node-color)!important;font-size:13px!important;text-overflow:ellipsis;white-space:nowrap}.flow-node>small{margin-top:3px;color:var(--color-muted-foreground);font-size:11px;white-space:nowrap}.flow-applied{--node-color:#4775be;--node-soft:#eaf1fc}.flow-assessment{--node-color:#7a57ad;--node-soft:#f1ebfa}.flow-test{--node-color:#b77718;--node-soft:#fff2d9}.flow-interview{--node-color:#24828b;--node-soft:#e3f5f5}.flow-phone{--node-color:#596bc2;--node-soft:#ebedfb}.flow-offer{--node-color:#258254;--node-soft:#e3f5e9}.flow-waiting{--node-color:#b06424;--node-soft:#fff0e1}.flow-failed{--node-color:#bc4c48;--node-soft:#fde9e8}.flow-other{--node-color:#69766f;--node-soft:#edf1ef}.flow-node.done>i::after{content:"✓";position:absolute;right:-5px;top:-6px;display:grid;width:14px;height:14px;place-items:center;border:2px solid #eef6ff;border-radius:50%;background:var(--node-color);color:#fff;font-size:9px}.flow-node.done>i{opacity:.82}.flow-node.current>i,.flow-node.upcoming>i{border-color:var(--node-color);box-shadow:0 0 0 4px #eef6ff,0 0 0 6px color-mix(in srgb,var(--node-color) 13%,transparent),0 5px 12px color-mix(in srgb,var(--node-color) 20%,transparent)}.flow-node.upcoming>i{border-style:dashed}.flow-node.failed>i,.flow-node.success>i{background:var(--node-color)!important;color:#fff!important}.official{display:inline-flex;align-items:center;padding:10px 14px;border-radius:10px;color:var(--color-card-foreground);background:var(--color-muted);text-decoration:none}.head,.toolbar,.detail-head,.actions{display:flex;align-items:center;justify-content:space-between;gap:12px}.head span{color:var(--color-primary);font-size:11px;font-weight:800;letter-spacing:.1em}.head h2{margin:4px 0}.toolbar{margin:18px 0}.toolbar input{flex:1}.toolbar select{width:150px}.secondary{color:var(--color-card-foreground);background:var(--color-muted)}.grid{display:grid;grid-template-columns:1fr;gap:12px}.application{display:block;padding:17px 21px 16px;color:var(--color-foreground);border:1px solid #b9d3fa;background:#eef6ff;text-align:left}.badges{display:flex;gap:7px}.badges b,.badges i{padding:5px 8px;border-radius:999px;background:#edf1ff;color:#3d55bd;font-size:12px;font-style:normal}.application i,.badges i{color:var(--color-muted-foreground);background:#eef2f6}.backdrop{position:fixed;inset:0;z-index:40;display:grid;place-items:center;padding:20px;background:rgba(17,24,39,.58)}.modal{position:relative;width:min(760px,100%);max-height:90vh;overflow:auto;padding:28px;border-radius:18px;background:#fff}.close{position:absolute;top:12px;right:12px;padding:4px 11px;color:var(--color-muted-foreground);background:#eef2f6;font-size:22px}.detail-head{padding-right:35px}.detail-head h2{margin:0}.actions{justify-content:flex-start;flex-wrap:wrap;margin:18px 0}.ai-button{background:#6b4fd3}.ai-review{padding:12px;border-radius:10px;background:#f4f1ff}.ai-review p{margin:4px 0;color:#476050;font-size:12px}.ai-review .warn{color:#8a5608}.offer{background:var(--color-success)}.abandon{background:#c47a1b}.reject,.danger-button{background:var(--color-destructive)}dl{display:grid;grid-template-columns:1fr 1fr;gap:10px}dl div{padding:12px;border-radius:10px;background:#f7f9fc}dl .wide,.form .wide{grid-column:1/-1}dt{color:var(--color-muted-foreground);font-size:11px}dd{margin:5px 0 0;white-space:pre-wrap}.timeline{display:grid;gap:8px}.timeline article{display:grid;gap:4px;padding:11px;border-left:3px solid #8396e9;background:#f7f9fc}.timeline span{color:var(--color-muted-foreground);font-size:12px}.form{display:grid;grid-template-columns:1fr 1fr;gap:14px}.form h2{grid-column:1/-1}.form label{display:grid;gap:7px;color:var(--color-muted-foreground);font-size:13px;font-weight:700}.form select,.form textarea{width:100%;padding:12px 14px;border:1px solid #d4dbea;border-radius:10px;background:#fff;font:inherit}.official-link-field{padding:14px;border:1px solid #d9e2ef;border-radius:12px;background:#f7f9fc}.official-link-field>span{display:flex;align-items:center;justify-content:space-between;gap:12px}.official-link-field>span small,.official-link-field>small{color:#758196;font-size:11px;font-weight:500}.official-link-field input{width:100%}.feedback{position:sticky;bottom:16px;z-index:20;margin:14px auto;padding:12px 16px;border-radius:12px;color:#167647;background:#e9f8ef;box-shadow:0 8px 30px rgba(0,0,0,.12)}.feedback.danger{color:#a52d2d;background:#fceaea}.feedback button{margin-left:12px;padding:7px 11px}.empty{color:var(--color-muted-foreground)}.application-toolbar-portal{display:grid;width:100%;min-width:0;align-content:center}.application-toolbar-portal .head span{font-size:9px}.application-toolbar-portal .head h2{margin:0;font-size:17px}.application-toolbar-portal .toolbar{margin:4px 0}.application-toolbar-portal .toolbar input,.application-toolbar-portal .toolbar select{padding:8px 11px}.application-toolbar-portal .toolbar button{padding:8px 13px}.application-toolbar-portal .application-legend{margin:1px 2px 0}.application-filter-stack{width:fit-content;max-width:100%;min-width:0;flex:0 1 auto}.application-filter-fields{display:grid;grid-template-columns:minmax(0,3fr) minmax(0,2fr);gap:12px}.application-filter-fields input,.application-filter-fields select{width:100%;height:38px;min-width:0;padding:0 12px;border:1px solid #cbd5e1;border-radius:9px;outline:none;color:#25324a;background:#fff;font:inherit;font-size:13px;line-height:38px;box-shadow:0 1px 2px rgba(31,48,78,.04);transition:border-color .16s ease,box-shadow .16s ease,background .16s ease}.application-filter-fields :deep(.select-trigger){height:38px}
.application-filter-fields input::placeholder{color:#8b96a8}.application-filter-fields select{padding:0 34px;appearance:none;text-align:center;text-align-last:center;line-height:normal;background-color:#fff;background-image:linear-gradient(45deg,transparent 50%,#65728a 50%),linear-gradient(135deg,#65728a 50%,transparent 50%);background-position:calc(100% - 16px) 16px,calc(100% - 11px) 16px;background-repeat:no-repeat;background-size:5px 5px;cursor:pointer}.application-filter-fields select option{text-align:center}.application-filter-fields input:hover,.application-filter-fields select:hover{border-color:#9eacc1;background-color:#fbfcff}.application-filter-fields input:focus,.application-filter-fields select:focus{border-color:var(--color-primary);box-shadow:0 0 0 3px rgba(82,109,221,.13)}.application-toolbar-portal .toolbar{align-items:flex-start}.application-filter-stack .application-legend{width:100%;justify-content:flex-start}.application-heatmap{display:flex;min-width:225px;align-items:center;justify-content:flex-end;gap:12px;padding-left:14px;border-left:1px solid #d9e0eb}.heatmap-grid{display:grid;grid-template-columns:repeat(7,8px);grid-template-rows:repeat(6,8px);gap:2px}.heatmap-grid i{width:8px;height:8px;border-radius:2px;background:#e8edf5}.heatmap-grid i.outside{opacity:.28}.heatmap-grid i.level-1{background:#cddcff}.heatmap-grid i.level-2{background:#94b5f4}.heatmap-grid i.level-3{background:#5d8ddd}.heatmap-grid i.level-4{background:#2e61b5}.heatmap-controls{display:grid;min-width:82px;gap:7px;justify-items:center}.heatmap-controls strong{color:var(--color-card-foreground);font-size:12px;white-space:nowrap}.heatmap-controls>div{display:flex;gap:5px}.heatmap-controls button{display:flex;width:26px;height:24px;align-items:center;justify-content:center;padding:0;border:1px solid #ccd6e6;border-radius:7px;color:#42526c;background:#f6f8fc;font-size:16px}.heatmap-controls button>span{display:block;width:12px;height:16px;transform:translateY(-1px);font-family:Arial,sans-serif;font-size:17px;font-weight:700;line-height:16px;text-align:center}.heatmap-controls button:disabled{cursor:not-allowed;opacity:.3}.workspace{margin-top:12px!important}.application-legend{display:flex;align-items:center;gap:15px;margin:0 2px 10px;padding:0 4px;overflow-x:auto;color:var(--color-muted-foreground);font-size:11px;line-height:1.4;scrollbar-width:none;white-space:nowrap}.application-legend::-webkit-scrollbar{display:none}.application-legend .legend-title{padding-right:2px;color:#8a94a3;font-size:10px;font-weight:700;letter-spacing:.04em}.application-legend>span{display:inline-flex;align-items:center;gap:6px;flex:0 0 auto}.application-legend i{width:9px;height:9px;flex:0 0 9px;border:1px solid transparent;border-radius:3px}.application-legend .pending{background:#4b87cf;border-color:#3774bc}.application-legend .interview{background:#8b6cc7;border-color:#7657b3}.application-legend .assessment{background:#dc9b32;border-color:#c5841e}.application-legend .applied{background:#8b9891;border-color:#74827b}.application-legend .stopped{background:#d5665e;border-color:#bd5049}.application-legend .offer{background:#35b870;border-color:#249d5b}
.application{--card-bg:#f7f8f8;background:var(--card-bg);transition:border-color .16s ease,box-shadow .16s ease,background .16s ease}.application.tone-pending{--card-bg:color-mix(in srgb,#4388d0 14%,#fff);border-color:color-mix(in srgb,#4388d0 38%,var(--color-border));border-left:5px solid #4388d0}.application.tone-interview{--card-bg:color-mix(in srgb,#8968c4 13%,#fff);border-color:color-mix(in srgb,#8968c4 37%,var(--color-border));border-left:5px solid #8968c4}.application.tone-assessment{--card-bg:color-mix(in srgb,#dc9629 14%,#fff);border-color:color-mix(in srgb,#dc9629 37%,var(--color-border));border-left:5px solid #dc9629}.application.tone-applied{--card-bg:color-mix(in srgb,#7f8e87 8%,#fff);border-color:color-mix(in srgb,#7f8e87 27%,var(--color-border));border-left:5px solid #7f8e87}.application.tone-stopped{--card-bg:color-mix(in srgb,#d45f57 13%,#fff);border-color:color-mix(in srgb,#d45f57 37%,var(--color-border));border-left:5px solid #d45f57}.application.tone-offer{--card-bg:color-mix(in srgb,#2eaf69 15%,#fff);border-color:color-mix(in srgb,#2eaf69 42%,var(--color-border));border-left:5px solid #2eaf69}.application:hover{box-shadow:0 7px 20px rgba(44,61,86,.1)}.application.tone-pending:hover{border-color:color-mix(in srgb,#4388d0 58%,var(--color-border))}.application.tone-interview:hover{border-color:color-mix(in srgb,#8968c4 56%,var(--color-border))}.application.tone-assessment:hover{border-color:color-mix(in srgb,#dc9629 55%,var(--color-border))}.application.tone-applied:hover{border-color:color-mix(in srgb,#7f8e87 43%,var(--color-border))}.application.tone-stopped:hover{border-color:color-mix(in srgb,#d45f57 56%,var(--color-border))}.application.tone-offer:hover{border-color:color-mix(in srgb,#27ad5d 46%,var(--color-border));box-shadow:0 8px 24px color-mix(in srgb,#45c878 13%,transparent)}.flow-node>i{box-shadow:0 0 0 4px var(--card-bg),0 3px 8px color-mix(in srgb,var(--node-color) 16%,transparent)}.flow-node.done>i::after{border-color:var(--card-bg)}.flow-node.current>i,.flow-node.upcoming>i{box-shadow:0 0 0 4px var(--card-bg),0 0 0 6px color-mix(in srgb,var(--node-color) 13%,transparent),0 5px 12px color-mix(in srgb,var(--node-color) 20%,transparent)}
:global(#app .application){--card-border:#b9d3fa;--card-accent:#b9d3fa;border-color:var(--card-border);border-left-color:var(--card-accent);transition:transform .14s ease,box-shadow .14s ease;transform:translateY(0) scale(1)}
:global(#app .application.tone-pending){--card-border:color-mix(in srgb,#4388d0 38%,var(--color-border));--card-accent:#4388d0}
:global(#app .application.tone-interview){--card-border:color-mix(in srgb,#8968c4 37%,var(--color-border));--card-accent:#8968c4}
:global(#app .application.tone-assessment){--card-border:color-mix(in srgb,#dc9629 37%,var(--color-border));--card-accent:#dc9629}
:global(#app .application.tone-applied){--card-border:color-mix(in srgb,#7f8e87 27%,var(--color-border));--card-accent:#7f8e87}
:global(#app .application.tone-stopped){--card-border:color-mix(in srgb,#d45f57 37%,var(--color-border));--card-accent:#d45f57}
:global(#app .application.tone-offer){--card-border:color-mix(in srgb,#2eaf69 42%,var(--color-border));--card-accent:#2eaf69}
@media(hover:hover) and (pointer:fine){:global(#app .application:hover){border-color:var(--card-border);border-left-color:var(--card-accent);background-color:var(--card-bg);transform:translateY(2px) scale(.997);box-shadow:inset 0 2px 5px rgba(31,48,78,.1),0 1px 2px rgba(31,48,78,.05)}}
:global(#app .application:active){border-color:var(--card-border);border-left-color:var(--card-accent);background-color:var(--card-bg);transform:translateY(3px) scale(.994);box-shadow:inset 0 3px 7px rgba(31,48,78,.14)}
.detail-modal{width:min(1180px,calc(100vw - 48px));max-height:94vh;padding:32px;border-radius:18px}.detail-title{margin:0 0 34px;font-size:22px}.detail-company-row{display:flex;align-items:center;justify-content:space-between;gap:24px}.company-identity{min-width:0}.company-name-line{display:flex;align-items:center;gap:8px}.company-name-line>a{display:flex;min-width:0;align-items:center;gap:10px;color:var(--color-foreground);text-decoration:none}.company-name-line strong{font-size:25px}.company-name-line>a:hover strong{color:#315fb6}.company-icon{display:grid;width:28px;height:28px;place-items:center;border-radius:50%;color:#315fb6;background:#e9f1ff;font-size:18px;font-weight:800}.company-name-line .official-link-edit{width:26px;min-width:26px;height:26px;min-height:26px;flex:0 0 26px;padding:0;border-radius:7px}.company-identity p{margin:8px 0 0;color:#596579;font-size:16px}.official-link-backdrop{z-index:60}.official-link-modal{display:grid;width:min(540px,100%);gap:16px}.official-link-modal h2,.official-link-modal p{margin:0}.official-link-modal label{display:grid;gap:7px;color:var(--color-muted-foreground);font-size:13px;font-weight:700}.official-link-modal input{width:100%}.official-link-modal .actions{margin:2px 0 0}.detail-actions{padding:18px 0;border-bottom:1px solid #d8e0eb}.detail-actions button{min-width:76px}.detail-actions .push-right{margin-left:auto}.section-heading{margin:26px 0 12px}.basic-info{padding:16px;border-radius:12px;background:var(--color-muted)}.basic-info>div{display:flex;align-items:center;gap:12px}.basic-info strong{color:var(--color-card-foreground);font-size:13px}.basic-info span{margin-right:12px;color:var(--color-muted-foreground)}.basic-info p{margin:15px 0 0;color:var(--color-muted-foreground)}.event-records{display:grid;gap:12px}.event-record{position:relative;display:grid;grid-template-columns:22px 105px minmax(0,1fr) auto;align-items:center;min-height:110px;padding:14px 16px 14px 0;border:1px solid var(--color-border);border-radius:13px;background:var(--color-card);box-shadow:var(--shadow-sm)}.event-rail{position:relative;align-self:stretch}.event-rail::before{content:"";position:absolute;left:7px;top:-27px;bottom:-27px;width:1px;background:var(--color-border)}.event-record:first-child .event-rail::before{top:50%}.event-record:last-child .event-rail::before{bottom:50%}.event-rail i{position:absolute;z-index:1;left:1px;top:50%;width:12px;height:12px;transform:translateY(-50%);border:2px solid var(--color-card);border-radius:50%;background:#3f75c5;box-shadow:0 0 0 1px #7ca1d8}.event-date{display:grid;gap:4px;padding-right:14px;border-right:1px solid var(--color-border)}.event-date strong{color:#2f63b3;font-size:17px}.event-date span{color:var(--color-muted-foreground);font-size:12px}.event-body{display:grid;min-width:0;gap:7px;padding:0 16px}.event-body>strong{font-size:15px}.event-meta{display:flex;align-items:center;flex-wrap:wrap;gap:8px;color:var(--color-muted-foreground);font-size:12px}.event-meta a{color:#205fc1}.event-meta b{padding:4px 8px;border:1px solid var(--color-border-strong);border-radius:8px;color:var(--color-card-foreground);background:var(--color-muted)}.event-meta i{font-style:normal;color:var(--color-muted-foreground)}.event-body p{margin:0;padding:7px 10px;border-radius:7px;color:var(--color-muted-foreground);background:var(--color-muted);font-size:12px}.event-row-actions{display:flex;align-items:center;gap:8px}.event-row-actions button{padding:9px 12px}.event-row-actions .event-delete{color:#b83d36;background:var(--color-card);border:1px solid #e5b9b5}.history-list{display:grid;margin-left:8px}.history-list article{position:relative;display:flex;gap:18px;padding:4px 0 18px 20px;border-left:2px solid var(--color-border)}.history-list article:last-child{padding-bottom:4px}.history-list article>i{position:absolute;left:-7px;top:7px;width:12px;height:12px;border:2px solid var(--color-card);border-radius:50%;background:#5eb288}.history-list article>div{display:grid;gap:5px}.history-list article strong{font-size:14px}.history-list article span{color:var(--color-muted-foreground);font-size:12px}@media(min-width:721px) and (min-height:620px){.workspace{display:flex;height:calc(100vh - 108px);min-height:0;flex-direction:column;overflow:hidden}.workspace>.head,.workspace>.toolbar,.workspace>.application-legend{flex:0 0 auto}.workspace>.grid{min-height:0;flex:1 1 auto;align-content:start;grid-auto-rows:max-content;overflow-y:auto;overscroll-behavior:contain;padding:12px 7px 18px 2px;border-top:1px solid var(--color-border);scrollbar-width:thin;scrollbar-color:var(--color-border-strong) transparent}.workspace>.grid::-webkit-scrollbar{width:7px}.workspace>.grid::-webkit-scrollbar-track{background:transparent}.workspace>.grid::-webkit-scrollbar-thumb{border-radius:99px;background:var(--color-border-strong)}.workspace>.empty{min-height:0;flex:1 1 auto;padding-top:18px;border-top:1px solid var(--color-border)}}
@media(max-width:720px){.application-toolbar-portal,.application-filter-stack{box-sizing:border-box;width:100%;max-width:100%;min-width:0}.application-toolbar-portal{overflow:visible}.application-toolbar-portal .toolbar{margin:4px 0 0}.application-filter-fields{width:100%;grid-template-columns:minmax(0,1fr)}.application-filter-fields>*{width:100%;max-width:100%;min-width:0}.application-filter-fields :deep(.base-select),.application-filter-fields :deep(.select-trigger){width:100%;max-width:100%;min-width:0}.application-heatmap{width:100%;min-width:0;justify-content:space-between;gap:16px;padding:12px 2px 2px;border-top:1px solid #d9e0eb;border-left:0}.application-legend{width:100%;max-width:100%;flex-wrap:wrap;gap:6px 12px;margin:9px 0 0;padding:0 2px 3px;overflow:visible;white-space:normal}.application-legend>span{white-space:nowrap}.toolbar{width:100%;max-width:100%;min-width:0;align-items:stretch;flex-direction:column}.toolbar select{width:auto}.grid,.form,dl{min-width:0;grid-template-columns:1fr}.application{width:100%;min-width:0;padding:16px 14px}.application-overview{align-items:flex-start;flex-direction:column}.application-title{min-width:0}.application-title strong{font-size:15px}.application-title>i{margin-inline:5px}.flow{width:100%;max-width:100%;scroll-snap-type:x proximity}.flow-node{scroll-snap-align:start}.form .wide,dl .wide{grid-column:auto}.detail-modal{width:100%;padding:22px 16px}.detail-title{margin-bottom:24px}.detail-company-row{align-items:flex-start}.company-name-line strong{font-size:21px}.detail-actions .push-right{margin-left:0}.basic-info>div{align-items:flex-start;flex-direction:column;gap:5px}.event-record{grid-template-columns:16px 78px minmax(0,1fr);padding-right:10px}.event-body{padding:0 10px}.event-row-actions{grid-column:2/-1;justify-content:flex-end;margin-top:10px}.event-date strong{font-size:14px}}
@media(min-width:721px){.application-toolbar-portal .application-legend{transform:translateY(7px)}}
</style>
<style scoped>
.detail-modal {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.detail-fixed-header { flex: none; border-bottom: 1px solid var(--color-border); }
.detail-fixed-header .detail-actions { margin-bottom: 0; border-bottom: 0; }
.detail-scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-right: 8px;
  scrollbar-gutter: stable;
}
</style>
<style scoped>
.application-overview {
  display: grid;
  grid-template-columns: minmax(0, auto) minmax(160px, 1fr) auto;
  align-items: center;
  gap: 18px;
}

.application-title {
  grid-column: 1;
}

.application-note {
  grid-column: 2;
}

.application-overview > em {
  grid-column: 3;
}

.application-title > i {
  background: transparent !important;
}

:global(:root[data-theme="dark"] .heatmap-controls button) {
  border-color: var(--color-border-strong);
  color: var(--color-foreground);
  background: #24282e;
}

:global(:root[data-theme="dark"] .heatmap-controls button:disabled) {
  border-color: var(--color-border);
  color: #777b82;
  background: #1b1e23;
  opacity: 1;
}

:global(:root[data-theme="dark"] .application-title > i) {
  color: var(--color-muted-foreground);
}

@media (max-width: 720px) {
  .application-overview {
    display: flex;
  }

  .application-note {
    width: 100%;
  }
}
.job-description { margin: 18px 0 24px; }
.job-description p { margin: 10px 0 0; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.7; }
.add-description { margin: 14px 0 24px; }
.basic-info p { white-space: pre-wrap; overflow-wrap: anywhere; }
.basic-info p b { display: block; margin-bottom: 5px; color: var(--color-muted-foreground); font-size: 12px; }
.event-review { margin-top: 12px; border-top: 1px solid var(--color-border); }
.event-review summary { display: inline-flex; align-items: center; gap: 8px; min-height: 44px; color: var(--color-primary); font-size: 12px; font-weight: 700; cursor: pointer; list-style: none; }
.event-review summary::-webkit-details-marker { display: none; }
.event-review summary::after { content: ''; width: 7px; height: 7px; flex: none; border-right: 1.5px solid currentColor; border-bottom: 1.5px solid currentColor; transform: rotate(45deg); transition: transform .18s ease; }
.event-review[open] summary::after { transform: rotate(225deg); }
.event-review summary:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 2px; border-radius: 3px; }
.event-review p { margin: 5px 0 0; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.6; }
.form label > small { color: var(--color-muted-foreground); font-size: 12px; font-weight: 400; line-height: 1.5; }
.schedule-time-fields { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:14px; min-width:0; }
.schedule-time-fields.is-point { grid-template-columns:minmax(0,1fr); }
.schedule-time-fields input { width:100%; min-width:0; }
.application-filter-stack { width:min(100%, 920px); flex:1 1 620px; }
.application-filter-fields { grid-template-columns:minmax(0,1fr); }
.application-heatmap { align-items:flex-start; }
.heatmap-calendar { display:grid; gap:5px; justify-items:center; }
.heatmap-caption { color:var(--color-muted-foreground); font-size:10px; line-height:1; white-space:nowrap; }
.application-stage-tabs { display:flex; flex-wrap:wrap; align-items:center; gap:7px; margin-top:8px; }
.application-stage-tabs button { min-height:32px; padding:6px 11px; border:1px solid var(--color-border); border-radius:999px; color:var(--color-muted-foreground); background:var(--color-card); font-size:12px; font-weight:650; line-height:1.25; white-space:nowrap; transition:color .16s ease,border-color .16s ease,background .16s ease,box-shadow .16s ease; }
.application-stage-tabs button:hover { border-color:var(--color-primary); color:var(--color-primary); }
.application-stage-tabs button.active { border-color:color-mix(in srgb,var(--color-primary) 58%,var(--color-border)); color:var(--color-primary); background:color-mix(in srgb,var(--color-primary) 12%,var(--color-card)); box-shadow:inset 0 0 0 1px color-mix(in srgb,var(--color-primary) 12%,transparent); }
.application-stage-tabs button:focus-visible { outline:2px solid var(--color-primary); outline-offset:2px; }
@media(max-width:600px) { .schedule-time-fields { grid-template-columns:minmax(0,1fr); } }
</style>
