(function(){
  let undoSnapshot=null,toastTimer=null;

  toast=function(message,options={}){
    const config=typeof options==='string'?{type:options}:options;
    const host=document.querySelector('#toast');
    clearTimeout(toastTimer);
    host.className=`toast toast-${config.type||(/失败|错误/.test(message)?'error':/删除|警告/.test(message)?'warning':'success')}`;
    host.innerHTML=`<span class="toast-symbol">${config.type==='error'?'×':config.type==='warning'?'!':'✓'}</span><span>${esc(message)}</span>${config.action?`<button type="button">${esc(config.action)}</button>`:''}`;
    if(config.action)host.querySelector('button').onclick=()=>{config.onAction?.();host.classList.add('hidden');};
    host.classList.remove('hidden');
    toastTimer=setTimeout(()=>host.classList.add('hidden'),config.duration||3000);
  };
  window.toast=toast;

  window.createUndoSnapshot=function(label){undoSnapshot={label,state:JSON.parse(JSON.stringify(state)),at:Date.now()};};
  window.undoLastChange=function(){if(!undoSnapshot||Date.now()-undoSnapshot.at>30000)return toast('撤销时间已结束',{type:'warning'});state=undoSnapshot.state;undoSnapshot=null;save();closeModal();render();toast('已恢复删除的记录');};
  window.confirmAction=function(title,message,onConfirm,options={}){
    openModal(title,`<div class="confirm-dialog"><span class="confirm-icon ${options.danger?'danger':''}">${options.danger?'!':'?'}</span><p>${esc(message)}</p><div class="form-actions"><button type="button" class="ghost" onclick="closeModal()">取消</button><button type="button" class="${options.danger?'danger':'primary'}" id="confirmActionButton">${options.danger?'确认删除':'确认'}</button></div></div>`);
    document.querySelector('#confirmActionButton').onclick=()=>{closeModal();onConfirm();};
  };

  function applicationReached(application,stage){
    const order=['已投递','测评','笔试','面试','Offer'],target=order.indexOf(stage),current=order.indexOf(application.stage);
    if(current>=target)return true;
    return state.events.some(event=>event.applicationId===application.id&&order.indexOf(event.type)>=target);
  }
  function funnelHtml(){
    const stages=['已投递','测评','笔试','面试','Offer'],values=stages.map(stage=>state.applications.filter(item=>applicationReached(item,stage)).length),total=Math.max(1,values[0]);
    return `<div class="panel conversion-panel"><div class="panel-head"><div><h2>求职转化漏斗</h2><p>按曾经到达过的最高阶段统计</p></div><span class="panel-kicker">转化分析</span></div><div class="conversion-funnel">${stages.map((stage,index)=>`<div class="funnel-step" style="--funnel-width:${Math.max(18,values[index]/total*100)}%"><div><b>${values[index]}</b><span>${stage}</span></div><small>${index?`上阶段转化率 ${values[index-1]?Math.round(values[index]/values[index-1]*100):0}%`:'全部投递'}</small></div>`).join('')}</div></div>`;
  }
  function insightHtml(){
    const apps=state.applications,active=apps.filter(item=>!['未通过','已放弃','已结束','已通过'].includes(item.status)&&item.stage!=='Offer'),stale=active.filter(item=>{const health=progressHealth(item);return health&&health.days>=10;});
    const channelStats={};apps.forEach(item=>{const key=item.channel||'未填写',entry=channelStats[key]||(channelStats[key]={total:0,interviews:0});entry.total++;if(applicationReached(item,'面试'))entry.interviews++;});
    const best=Object.entries(channelStats).filter(([,value])=>value.total).sort((a,b)=>b[1].interviews/b[1].total-a[1].interviews/a[1].total)[0];
    const upcomingCount=state.events.filter(event=>!event.completed&&!event.missed&&new Date(String(event.startsAt).replace(' ','T')).getTime()>=Date.now()).length;
    const sentence=apps.length?`目前记录 ${apps.length} 个岗位，${active.length} 个仍在推进，${upcomingCount} 项日程待参加${stale.length?`，另有 ${stale.length} 个岗位超过 10 天没有进展`:''}${best&&best[1].interviews?`。${best[0]}的面试转化表现最好`:'。'}`:'创建第一条投递后，这里会自动总结当前求职进展。';
    return `<div class="panel smart-insight"><div class="insight-icon">✦</div><div><span>今日求职简报</span><strong>${esc(sentence)}</strong></div><button class="secondary" onclick="navigate('stats')">查看统计</button></div>`;
  }

  const baseHome=renderHome;
  renderHome=function(){baseHome();content.insertAdjacentHTML('afterbegin',insightHtml());};
  const baseStats=renderStats;
  renderStats=function(){baseStats();const charts=content.querySelector('.stats-charts');(charts||content).insertAdjacentHTML(charts?'beforebegin':'beforeend',funnelHtml());};

  async function loadBackups(){
    const host=document.querySelector('#backupList');if(!host)return;
    try{const response=await fetch('/api/backups',{cache:'no-store'}),result=await response.json(),items=result.items||[];host.innerHTML=items.length?items.slice(0,8).map(item=>`<div class="backup-row"><div><b>${new Date(item.createdAt).toLocaleString('zh-CN')}</b><small>${Math.max(1,Math.round(item.size/1024))} KB · 自动备份</small></div><button class="ghost" onclick="restoreLocalBackup('${esc(item.name)}')">恢复</button></div>`).join(''):'<div class="empty compact">保存数据后会自动生成备份</div>';}catch{host.innerHTML='<div class="empty compact">暂时无法读取备份列表</div>';}
  }
  window.restoreLocalBackup=function(name){confirmAction('恢复历史备份','当前数据会先自动备份，然后恢复所选版本。',async()=>{try{const response=await fetch('/api/backups/restore',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name})}),result=await response.json();if(!response.ok)throw new Error(result.error||'恢复失败');state={...initial(),...result.data,settings:{...state.settings,...(result.data.settings||{})}};save();render();toast('历史备份已恢复');}catch(error){toast(error.message,{type:'error'});}});};
  exportData=function(){const clean=JSON.parse(JSON.stringify(state));if(clean.settings)delete clean.settings.apiKey;const blob=new Blob([JSON.stringify(clean,null,2)],{type:'application/json'}),link=document.createElement('a');link.href=URL.createObjectURL(blob);link.download=`求职进度本备份_${today()}.json`;link.click();URL.revokeObjectURL(link.href);toast('备份文件已导出');};window.exportData=exportData;
  importData=function(input){const file=input.files?.[0];if(!file)return;const reader=new FileReader();reader.onload=()=>{try{const data=JSON.parse(reader.result);if(!Array.isArray(data.applications)||!Array.isArray(data.events))throw new Error('备份格式不正确');confirmAction('导入备份',`将用备份中的 ${data.applications.length} 条投递和 ${data.events.length} 项日程替换当前数据。`,()=>{const apiKey=state.settings.apiKey||data.settings?.apiKey||'';state={...initial(),...data,settings:{...initial().settings,...(data.settings||{}),apiKey}};save();render();toast('备份已成功导入');});}catch(error){toast(error.message,{type:'error'});}finally{input.value='';}};reader.readAsText(file);};window.importData=importData;
  clearData=function(){confirmAction('清空全部数据','将清空所有投递和日程。操作完成后可通过自动备份恢复。',()=>{createUndoSnapshot('全部数据');state.applications=[];state.events=[];save();render();toast('全部数据已清空',{type:'warning',action:'撤销',onAction:undoLastChange,duration:8000});},{danger:true});};window.clearData=clearData;
  const baseSettings=renderSettings;
  renderSettings=function(){
    baseSettings();
    document.querySelectorAll('.settings-note').forEach(note=>{note.innerHTML=note.innerHTML.replace(/API Key[^。]*。/,'API Key 单独保存在本机私有配置文件中，不会写入投递数据或导出备份。');});
    const settingsGrid=content.querySelector('.grid.two-col'),dataPanel=settingsGrid?.children[1];if(settingsGrid)settingsGrid.classList.add('settings-main-grid');
    if(settingsGrid&&dataPanel){const rightColumn=document.createElement('div');rightColumn.className='settings-right-column';dataPanel.replaceWith(rightColumn);rightColumn.appendChild(dataPanel);rightColumn.insertAdjacentHTML('beforeend',`<div class="panel settings-section about-settings-section"><div class="about-brand"><span>✓</span><div><h2>求职进度本</h2><p>本地、专注、可掌控的求职管理工具</p></div></div><dl><div><dt>数据位置</dt><dd>data/job-tracker.json</dd></div><div><dt>API 配置</dt><dd>data/local-config.json</dd></div><div><dt>更新渠道</dt><dd><a href="https://github.com/dfaskl/job-tracker-desktop" target="_blank" rel="noreferrer">GitHub 项目主页 ↗</a></dd></div></dl></div>`);}
  };

  function enhanceForm(form){
    if(!form||form.dataset.commercialValidation)return;form.dataset.commercialValidation='true';
    form.querySelectorAll('[required]').forEach(field=>{field.addEventListener('invalid',()=>{field.closest('.field')?.classList.add('field-invalid');toast(`请检查“${field.closest('.field')?.querySelector('label')?.textContent.replace('*','').trim()||'必填项'}”`,{type:'error'});});field.addEventListener('input',()=>field.closest('.field')?.classList.remove('field-invalid'));});
  }
  const baseOpenModal=openModal;
  openModal=function(...args){baseOpenModal(...args);requestAnimationFrame(()=>enhanceForm(document.querySelector('#modalBody form')));};window.openModal=openModal;
  document.addEventListener('keydown',event=>{if(event.key==='Escape'&&!document.querySelector('#modalMask')?.classList.contains('hidden'))closeModal();});
})();
