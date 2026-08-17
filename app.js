const KEY='job_tracker_desktop_v1';
const STAGES=['准备投递','已投递','测评','笔试','面试','Offer','已结束'];
const STATUSES=['进行中','等待安排','等待结果','已通过','未通过','已放弃','已结束'];
const TYPES=['测评','笔试','面试','Offer','其他'];
const CHANNELS=['官网','Boss直聘','实习僧','牛客','猎聘','智联招聘','前程无忧','校园招聘平台','内推','其他'];
const tones={'进行中':'green','等待安排':'blue','等待结果':'amber','已通过':'cyan','未通过':'red','已放弃':'gray','已结束':'gray','准备投递':'gray','已投递':'blue','测评':'purple','笔试':'amber','面试':'cyan','Offer':'green'};
let state=load(), page='home', selectedId=null, mailResult=null;
function initial(){return{applications:[],events:[],settings:{apiUrl:'https://api.deepseek.com',model:'deepseek-chat',apiKey:''}}}
function load(){try{return Object.assign(initial(),JSON.parse(localStorage.getItem(KEY)||'{}'))}catch{return initial()}}
function save(){
  localStorage.setItem(KEY,JSON.stringify(state));
  fetch('/api/data',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(state),keepalive:true})
    .catch(()=>{});
}
async function loadFromLocalFile(){
  try{
    const response=await fetch('/api/data',{cache:'no-store'});
    if(!response.ok)throw new Error(`HTTP ${response.status}`);
    const result=await response.json();
    if(result.exists&&result.data){
      state={...initial(),...result.data,settings:{...initial().settings,...(result.data.settings||{})}};
      localStorage.setItem(KEY,JSON.stringify(state));
      document.documentElement.dataset.theme=state.settings.theme||'blue';
      render();
    }else{
      save();
    }
  }catch(error){
    console.warn('本地文件暂不可用，已使用浏览器缓存：',error);
  }
}
function id(prefix){return prefix+'_'+Date.now()+'_'+Math.random().toString(16).slice(2,7)}
function esc(v=''){return String(v).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#039;'}[c]))}
function nowText(){const d=new Date(),p=n=>String(n).padStart(2,'0');return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`}
function today(){return nowText().slice(0,10)}
function formatDate(v){if(!v)return '未设置';const d=new Date(v.replace(' ','T'));if(isNaN(d))return v;return `${d.getMonth()+1}月${d.getDate()}日 ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`}
function badge(v){const kind=STAGES.includes(v)?'badge-stage':(STATUSES.includes(v)?'badge-status':'');return `<span class="badge ${kind} tone-${tones[v]||'gray'}">${esc(v)}</span>`}
function appById(id){return state.applications.find(x=>x.id===id)}
function upcoming(){return state.events.filter(e=>!e.completed).sort((a,b)=>a.startsAt.localeCompare(b.startsAt))}
function pendingFollowups(){const now=Date.now();return upcoming().filter(e=>{const t=new Date(e.startsAt.replace(' ','T')).getTime();return t<now-2*3600e3&&!e.missed})}
function progressHealth(a){
  if(['Offer','已结束'].includes(a.stage)||['已通过','未通过','已放弃','已结束'].includes(a.status))return null;
  const related=state.events.filter(e=>e.applicationId===a.id&&['笔试','面试'].includes(e.type));
  const hasUpcoming=related.some(e=>!e.completed&&!e.missed&&new Date(e.startsAt.replace(' ','T')).getTime()>=Date.now());
  if(hasUpcoming)return null;
  const completed=related.filter(e=>e.completed&&!e.missed);
  if(!completed.length)return null;
  const reference=latestProgressTime(a);
  const days=Math.max(0,Math.floor((Date.now()-reference)/86400000));
  if(days<=3)return{label:'进展正常',className:'health-good',days};
  if(days<=6)return{label:'等待较久',className:'health-watch',days};
  if(days<=13)return{label:'长期无进展',className:'health-stale',days};
  return{label:'建议确认',className:'health-risk',days};
}
function healthBadge(a){const health=progressHealth(a);return health?`<span class="badge badge-health ${health.className}" title="已经 ${health.days} 天没有新进展">${health.label}<b>${health.days}天</b></span>`:''}
function latestProgressTime(a){
  const timelineTimes=(a.timeline||[])
    .filter(item=>item.title!=='创建投递记录')
    .map(item=>item.at);
  const completedEventTimes=state.events
    .filter(e=>e.applicationId===a.id&&e.completed)
    .map(e=>e.startsAt);
  const times=[a.appliedDate]
    .concat(timelineTimes,completedEventTimes)
    .map(value=>new Date(String(value||'').replace(' ','T')).getTime())
    .filter(Number.isFinite);
  return times.length?Math.max(...times):0;
}
function hasSelectionProgress(a){
  const progressedStages=['测评','笔试','面试','Offer'];
  if(progressedStages.includes(a.stage))return true;
  return state.events.some(e=>e.applicationId===a.id&&['测评','笔试','面试','Offer'].includes(e.type));
}
function compareApplications(a,b){
  const aRejected=a.status==='未通过',bRejected=b.status==='未通过';
  if(aRejected!==bRejected)return aRejected?1:-1;
  if(aRejected&&bRejected){
    const updated=value=>new Date(String(value.updatedAt||value.createdAt||'').replace(' ','T')).getTime()||0;
    return updated(b)-updated(a);
  }
  const aProgressed=hasSelectionProgress(a),bProgressed=hasSelectionProgress(b);
  if(aProgressed!==bProgressed)return aProgressed?-1:1;
  return latestProgressTime(b)-latestProgressTime(a);
}
function toast(msg){const t=document.querySelector('#toast');t.textContent=msg;t.classList.remove('hidden');setTimeout(()=>t.classList.add('hidden'),2200)}
const content=document.querySelector('#content');
// 持久连接用于识别页面是否真正关闭；普通刷新会自动重新连接。
const pageSession=new EventSource('/api/session');
const pageMeta={home:['首页','掌握每一次机会的进展'],applications:['投递记录','查看并更新岗位的当前状态'],calendar:['日程',''],mail:['邮件识别','粘贴通知正文，让大模型提取关键信息'],stats:['统计','了解投递转化与当前分布'],settings:['设置','配置大模型及管理本地数据']};
document.querySelector('#nav').onclick=e=>{const b=e.target.closest('button[data-page]');if(b)navigate(b.dataset.page)};
document.querySelector('#quickAdd').onclick=()=>openApplicationForm();
document.querySelector('#modalClose').onclick=closeModal;document.querySelector('#modalMask').onclick=e=>{if(e.target.id==='modalMask')closeModal()};
function navigate(p){page=p;document.querySelectorAll('#nav button').forEach(b=>b.classList.toggle('active',b.dataset.page===p));document.querySelector('#pageTitle').textContent=pageMeta[p][0];document.querySelector('#pageSubtitle').textContent=pageMeta[p][1];document.querySelector('#quickAdd').classList.toggle('hidden',!['home','applications'].includes(p));render()}
function render(){({home:renderHome,applications:renderApplications,calendar:renderCalendar,mail:renderMail,stats:renderStats,settings:renderSettings}[page]||renderHome)()}
function eventLocation(e){if(!e.location)return '';if(/^https?:\/\//i.test(e.location))return ` · <a href="${esc(e.location)}" target="_blank" rel="noreferrer" onclick="event.stopPropagation()">打开链接</a>`;return ` · ${esc(e.location)}`}
function eventCard(e){const past=new Date(e.startsAt.replace(' ','T'))<new Date();return `<div class="event-card ${e.completed?'done':''} ${past&&!e.completed?'overdue':''}"><div class="event-time">${formatDate(e.startsAt).split(' ')[0]}<small>${formatDate(e.startsAt).split(' ')[1]||''}</small></div><div><div class="event-title">${esc(e.company)} · ${esc(e.title||e.type)}</div><div class="event-sub">${esc(e.position)}${eventLocation(e)} ${badge(e.missed?'已错过':(e.result||e.type))}</div><div class="event-sub event-note ${e.notes?'':'empty-note'}"><b>备注：</b>${esc(e.notes||'暂无备注')}</div></div><div class="event-card-actions"><button class="ghost" onclick="editSchedule('${e.id}')">编辑</button>${e.completed?'<span class="event-done-mark">✓</span>':`<button class="secondary" onclick="completeEvent('${e.id}')">完成</button>`}</div></div>`}
function renderHome(){const active=state.applications.filter(a=>!['未通过','已放弃','已结束'].includes(a.status)).length,offers=state.applications.filter(a=>a.stage==='Offer'||a.status==='已通过').length,events=upcoming(),follow=pendingFollowups();content.innerHTML=`<div class="grid stats-grid"><div class="stat-card"><small>全部投递</small><strong>${state.applications.length}</strong></div><div class="stat-card"><small>进行中的岗位</small><strong>${active}</strong></div><div class="stat-card"><small>待完成日程</small><strong>${events.length}</strong></div><div class="stat-card"><small>Offer / 已通过</small><strong>${offers}</strong></div></div>${follow.length?`<div class="checkin"><strong>有 ${follow.length} 项日程待跟进</strong>这些日程已开始超过两小时，请标记为完成或错过。<button class="link-btn" onclick="navigate('calendar')">立即处理</button></div>`:''}<div class="grid two-col"><div class="panel"><div class="panel-head"><h2>最近投递</h2><button class="link-btn" onclick="navigate('applications')">查看全部</button></div><div class="cards">${state.applications.slice().sort((a,b)=>b.updatedAt.localeCompare(a.updatedAt)).slice(0,5).map(appCard).join('')||'<div class="empty">还没有投递记录，点击右上角开始添加</div>'}</div></div><div class="panel"><div class="panel-head"><h2>近期日程</h2><button class="link-btn" onclick="navigate('calendar')">查看全部</button></div><div class="cards">${events.slice(0,4).map(eventCard).join('')||'<div class="empty">暂无待完成日程</div>'}</div></div></div>`}
function appCard(a){return `<div class="application-card" onclick="openDetail('${a.id}')"><div><div class="card-title" title="${esc(a.company)}">${esc(a.company)}</div><div class="card-sub">${esc(a.position)}</div><div class="card-meta"><span>${esc(a.city||'地点未填')}</span><span>·</span><span>${esc(a.channel||'渠道未填')}</span><span>·</span><span>${esc(a.appliedDate||'日期未填')}</span></div></div><div class="card-side">${badge(a.stage)}${badge(a.status)}${healthBadge(a)}</div></div>`}
function renderApplications(){content.innerHTML=`<div class="toolbar"><input class="search" id="search" placeholder="搜索公司或岗位"><select class="search" id="statusFilter"><option value="">全部状态</option>${STATUSES.map(x=>`<option>${x}</option>`).join('')}</select></div><div class="cards" id="applicationList"></div>`;const refresh=()=>{const q=document.querySelector('#search').value.trim().toLowerCase(),s=document.querySelector('#statusFilter').value;const items=state.applications.filter(a=>(!q||(a.company+a.position).toLowerCase().includes(q))&&(!s||a.status===s)).sort(compareApplications);document.querySelector('#applicationList').innerHTML=items.map(appCard).join('')||'<div class="panel empty">没有符合条件的投递记录</div>'};document.querySelector('#search').oninput=refresh;document.querySelector('#statusFilter').onchange=refresh;refresh()}
function renderCalendar(){const events=state.events.slice().sort((a,b)=>a.startsAt.localeCompare(b.startsAt)),todo=events.filter(e=>!e.completed),done=events.filter(e=>e.completed);content.innerHTML=`<div class="panel-head"><div></div><button class="primary" onclick="openEventForm()">＋ 新增日程</button></div><div class="panel"><div class="panel-head"><h2>待完成 · ${todo.length}</h2></div><div class="cards">${todo.map(e=>eventCard(e)+(pendingFollowups().some(x=>x.id===e.id)?`<div class="form-actions"><button class="success" onclick="completeEvent('${e.id}')">已经完成</button><button class="danger" onclick="missEvent('${e.id}')">已经错过</button></div>`:'')).join('')||'<div class="empty">暂无待完成日程</div>'}</div></div><div class="panel" style="margin-top:18px"><div class="panel-head"><h2>已完成 / 已错过</h2></div><div class="cards">${done.map(eventCard).join('')||'<div class="empty">暂无历史日程</div>'}</div></div>`}
function openModal(title,html){document.querySelector('#modalTitle').textContent=title;document.querySelector('#modalBody').innerHTML=html;document.querySelector('#modalMask').classList.remove('hidden')}
function closeModal(){document.querySelector('#modalMask').classList.add('hidden')}
function options(arr,value){return arr.map(x=>`<option ${x===value?'selected':''}>${x}</option>`).join('')}
function openApplicationForm(idValue='',prefill={}){const a=idValue?appById(idValue):{company:'',position:'',city:'',channel:'官网',appliedDate:today(),stage:'已投递',status:'进行中',notes:'',...prefill};openModal(idValue?'编辑投递':'新建投递',`<form id="appForm" class="form-grid"><div class="field"><label>公司名称 *</label><input name="company" required value="${esc(a.company)}"></div><div class="field"><label>岗位名称 *</label><input name="position" required value="${esc(a.position)}"></div><div class="field"><label>工作地点</label><input name="city" value="${esc(a.city)}"></div><div class="field"><label>投递渠道</label><select name="channel">${options(CHANNELS,a.channel)}</select></div><div class="field"><label>投递日期</label><input type="date" name="appliedDate" value="${esc(a.appliedDate)}"></div><div class="field"><label>当前阶段</label><select name="stage">${options(STAGES,a.stage)}</select></div><div class="field"><label>当前状态</label><select name="status">${options(STATUSES,a.status)}</select></div><div class="field full"><label>备注</label><textarea name="notes" placeholder="岗位编号、联系人或其他简短信息">${esc(a.notes)}</textarea></div><div class="form-actions"><button type="button" class="ghost" onclick="closeModal()">取消</button><button class="primary">保存</button></div></form>`);document.querySelector('#appForm').onsubmit=e=>{e.preventDefault();const data=Object.fromEntries(new FormData(e.target));const old=idValue?appById(idValue):null;const at=nowText(),timeline=old?.timeline||[];if(!old)timeline.unshift({id:id('tl'),at,title:'创建投递记录'});else if(old.stage!==data.stage||old.status!==data.status)timeline.unshift({id:id('tl'),at,title:`更新为 ${data.stage} · ${data.status}`});const record={...old,...data,id:idValue||id('app'),timeline,createdAt:old?.createdAt||at,updatedAt:at};if(old)state.applications=state.applications.map(x=>x.id===idValue?record:x);else state.applications.unshift(record);save();closeModal();render();toast('投递记录已保存')}}
function openDetail(idValue){const a=appById(idValue);if(!a)return;selectedId=idValue;const events=state.events.filter(e=>e.applicationId===idValue).sort((x,y)=>y.startsAt.localeCompare(x.startsAt));openModal('投递详情',`<div class="detail-head"><div><h3>${esc(a.company)}</h3><div>${esc(a.position)} · ${esc(a.city||'地点未填')}</div></div><div>${badge(a.stage)} ${badge(a.status)}</div></div><div class="detail-actions"><button class="secondary" onclick="openApplicationForm('${a.id}')">编辑信息 / 状态</button><button class="primary" onclick="openEventForm('${a.id}')">追加安排</button><button class="success" onclick="quickStatus('${a.id}','offer')">收到 Offer</button><button class="danger" onclick="quickStatus('${a.id}','reject')">未通过</button><button class="ghost" onclick="removeApplication('${a.id}')">删除</button></div><div class="section-label">基本信息</div><div class="settings-note">投递日期：${esc(a.appliedDate||'未填写')}　投递渠道：${esc(a.channel||'未填写')}<br>备注：${esc(a.notes||'无')}</div><div class="section-label">安排记录</div><div class="cards">${events.map(eventCard).join('')||'<div class="empty">还没有笔试或面试安排</div>'}</div><div class="section-label">状态历史</div><div class="timeline">${(a.timeline||[]).map(t=>`<div class="timeline-item"><b>${esc(t.title)}</b><br><small>${esc(t.at)}</small></div>`).join('')||'<div class="empty">暂无历史</div>'}</div>`)}
function quickStatus(idValue,type){const a=appById(idValue),at=nowText();if(type==='offer'){a.stage='Offer';a.status='已通过';a.timeline.unshift({id:id('tl'),at,title:'更新为 Offer · 已通过'})}else{a.stage='已结束';a.status='未通过';a.timeline.unshift({id:id('tl'),at,title:'更新为 已结束 · 未通过'})}a.updatedAt=at;save();closeModal();render();toast('岗位状态已更新')}
function removeApplication(idValue){if(!confirm('确定删除该投递以及它的全部日程吗？此操作不可恢复。'))return;state.applications=state.applications.filter(a=>a.id!==idValue);state.events=state.events.filter(e=>e.applicationId!==idValue);save();closeModal();render();toast('已删除')}
function openEventForm(appId=''){if(!state.applications.length){toast('请先新建一条投递记录');return}const a=appById(appId)||state.applications[0];openModal('新增日程',`<form id="eventForm" class="form-grid"><div class="field full"><label>关联岗位</label><select name="applicationId">${state.applications.map(x=>`<option value="${x.id}" ${x.id===a.id?'selected':''}>${esc(x.company)} · ${esc(x.position)}</option>`).join('')}</select></div><div class="field"><label>类型</label><select name="type">${options(TYPES,'面试')}</select></div><div class="field"><label>安排名称</label><input name="title" placeholder="如：一面、在线笔试" required></div><div class="field"><label>开始时间</label><input type="datetime-local" name="startsAt" required></div><div class="field"><label>地点 / 会议方式</label><input name="location" placeholder="如：线上、腾讯会议"></div><div class="form-actions"><button type="button" class="ghost" onclick="closeModal()">取消</button><button class="primary">保存安排</button></div></form>`);document.querySelector('#eventForm').onsubmit=e=>{e.preventDefault();const d=Object.fromEntries(new FormData(e.target)),app=appById(d.applicationId);d.startsAt=d.startsAt.replace('T',' ');state.events.push({...d,id:id('evt'),company:app.company,position:app.position,completed:false,missed:false,createdAt:nowText()});if(['测评','笔试','面试','Offer'].includes(d.type)){app.stage=d.type;app.status=d.type==='Offer'?'已通过':'进行中';app.timeline.unshift({id:id('tl'),at:nowText(),title:`新增${d.type}安排：${d.title}`});app.updatedAt=nowText()}save();closeModal();render();toast('日程已添加')}}
function completeEvent(idValue){const e=state.events.find(x=>x.id===idValue);if(e){e.completed=true;e.missed=false;save();closeModal();render();toast('已标记完成')}}
function missEvent(idValue){const e=state.events.find(x=>x.id===idValue);if(e){e.completed=true;e.missed=true;save();render();toast('已标记错过')}}
function renderMail(){content.innerHTML=`<div class="grid mail-layout"><div class="panel mail-box"><div class="panel-head"><h2>邮件正文</h2><button class="link-btn" onclick="document.querySelector('#mailBody').value=''">清空</button></div><textarea id="mailBody" class="search" placeholder="将完整的笔试、面试或 Offer 通知正文粘贴到这里……"></textarea><div class="form-actions"><button class="primary" id="recognizeBtn">✦ 使用大模型识别</button></div></div><div class="panel"><div class="panel-head"><h2>识别结果</h2></div><div id="recognition" class="recognition">${mailResult?mailResultHtml():`<div class="empty">识别出的公司、岗位、时间等信息会显示在这里，未识别内容可手动补充。</div>`}</div></div></div>`;document.querySelector('#recognizeBtn').onclick=recognizeMail}
function mailResultHtml(){const r=mailResult;return `<div class="field"><label>公司</label><input id="mrCompany" value="${esc(r.company)}"></div><div class="field"><label>岗位</label><input id="mrPosition" value="${esc(r.position)}"></div><div class="field"><label>通知类型</label><select id="mrType">${options(TYPES,r.noticeType)}</select></div><div class="field"><label>时间</label><input id="mrStarts" value="${esc(r.startsAt)}" placeholder="YYYY-MM-DD HH:mm"></div><div class="field"><label>面试地点 / 视频链接</label><input id="mrLocation" value="${esc(r.location)}" placeholder="视频会议链接或线下面试地址"></div><div class="field"><label>备注 / 摘要</label><textarea id="mrSummary" placeholder="部门、会议密码、联系人及注意事项">${esc(r.summary)}</textarea></div><div class="form-actions"><button class="primary" onclick="useMailResult()">使用识别结果</button></div>`}
async function recognizeMail(){const body=document.querySelector('#mailBody').value.trim(),btn=document.querySelector('#recognizeBtn');if(!body)return toast('请先粘贴邮件正文');btn.disabled=true;btn.textContent='正在识别…';try{const res=await fetch('/api/recognize',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({...state.settings,body})}),data=await res.json();if(!res.ok)throw new Error(data.error||'识别失败');mailResult=data;document.querySelector('#recognition').innerHTML=mailResultHtml();toast('识别完成')}catch(e){alert('识别失败：'+e.message)}finally{btn.disabled=false;btn.textContent='✦ 使用大模型识别'}}
function useMailResult(){const r={company:document.querySelector('#mrCompany').value,position:document.querySelector('#mrPosition').value,stage:mailResult.suggestedStage||'已投递',status:mailResult.suggestedStatus||'进行中',notes:document.querySelector('#mrSummary').value};closeModal();openApplicationForm('',r)}
function renderStats(){const byStage=STAGES.map(s=>[s,state.applications.filter(a=>a.stage===s).length]),max=Math.max(1,...byStage.map(x=>x[1]));content.innerHTML=`<div class="grid stats-grid"><div class="stat-card"><small>投递总数</small><strong>${state.applications.length}</strong></div><div class="stat-card"><small>面试阶段</small><strong>${state.applications.filter(a=>a.stage==='面试').length}</strong></div><div class="stat-card"><small>Offer</small><strong>${state.applications.filter(a=>a.stage==='Offer').length}</strong></div><div class="stat-card"><small>结束 / 未通过</small><strong>${state.applications.filter(a=>['已结束','未通过'].includes(a.status)).length}</strong></div></div><div class="grid two-col"><div class="panel"><h2>阶段分布</h2>${byStage.map(([s,n])=>`<div class="chart-row"><span>${s}</span><div class="bar"><i style="width:${n/max*100}%"></i></div><b>${n}</b></div>`).join('')}</div><div class="panel"><h2>渠道分布</h2>${Object.entries(state.applications.reduce((m,a)=>(m[a.channel||'未填写']=(m[a.channel||'未填写']||0)+1,m),{})).sort((a,b)=>b[1]-a[1]).map(([s,n])=>`<div class="chart-row"><span>${esc(s)}</span><div class="bar"><i style="width:${n/Math.max(1,state.applications.length)*100}%"></i></div><b>${n}</b></div>`).join('')||'<div class="empty">暂无数据</div>'}</div></div>`}
function renderSettings(){const s=state.settings;content.innerHTML=`<div class="grid two-col"><div class="panel"><h2>大模型 API</h2><p class="settings-note">支持 OpenAI 兼容接口。可填写服务根地址、以 /v1 结尾的地址，或完整的 /chat/completions 地址。API Key 仅保存在本机浏览器中。</p><form id="settingsForm" class="form-grid"><div class="field full"><label>API 地址</label><input name="apiUrl" value="${esc(s.apiUrl)}"></div><div class="field"><label>模型名称</label><input name="model" value="${esc(s.model)}"></div><div class="field"><label>API Key</label><input type="password" name="apiKey" value="${esc(s.apiKey)}"></div><div class="form-actions"><button class="primary">保存配置</button></div></form></div><div class="panel"><h2>数据管理</h2><p class="settings-note">建议定期导出备份。导入会合并或替换当前数据，请妥善保管包含求职信息的备份文件。</p><div class="detail-actions"><button class="secondary" onclick="exportData()">导出备份</button><label class="ghost" style="cursor:pointer">导入备份<input type="file" accept=".json" hidden onchange="importData(this)"></label><button class="danger" onclick="clearData()">清空全部数据</button></div></div></div>`;document.querySelector('#settingsForm').onsubmit=e=>{e.preventDefault();state.settings=Object.fromEntries(new FormData(e.target));save();toast('API 配置已保存')}}
function exportData(){const blob=new Blob([JSON.stringify(state,null,2)],{type:'application/json'}),a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download=`求职进度本备份_${today()}.json`;a.click();URL.revokeObjectURL(a.href)}
function importData(input){const f=input.files[0];if(!f)return;const reader=new FileReader();reader.onload=()=>{try{const data=JSON.parse(reader.result);if(!Array.isArray(data.applications)||!Array.isArray(data.events))throw new Error('备份格式不正确');if(confirm('确定用备份文件替换当前全部投递和日程吗？')){state={...initial(),...data};save();render();toast('备份已恢复')}}catch(e){alert(e.message)}};reader.readAsText(f)}
function clearData(){if(confirm('确定清空所有投递和日程吗？建议先导出备份。')){state.applications=[];state.events=[];save();render();toast('数据已清空')}}
Object.assign(window,{navigate,openApplicationForm,openDetail,openEventForm,closeModal,quickStatus,removeApplication,completeEvent,missEvent,useMailResult,exportData,importData,clearData});
render();
loadFromLocalFile();
