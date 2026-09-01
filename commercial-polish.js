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
    const apps=state.applications,active=apps.filter(item=>!['未通过','已放弃','已结束','已通过'].includes(item.status)&&item.stage!=='Offer'),stale=active.filter(item=>{const health=progressHealth(item);return health&&health.days>=PROGRESS_STALE_DAYS;});
    const upcomingCount=state.events.filter(event=>!event.completed&&!event.missed&&new Date(String(eventDeadlineAt(event)).replace(' ','T')).getTime()>=Date.now()).length;
    const attentionThreshold=Math.max(3,Math.ceil(active.length*.2));
    const hasResult=apps.some(item=>item.stage==='Offer'||item.status==='已通过');
    const status=active.length?(stale.length>=attentionThreshold?{label:'需要关注',tone:'attention'}:{label:'稳步推进',tone:'steady'}):hasResult?{label:'阶段收获',tone:'success'}:apps.length?{label:'暂时休整',tone:'idle'}:{label:'等待开始',tone:'idle'};
    const attentionAction=stale.length?"document.querySelector('.manual-confirm-panel')?.scrollIntoView({behavior:'smooth',block:'start'})":"navigate('applications')";
    return `<div class="panel smart-insight"><div class="insight-head"><div class="insight-heading"><div class="insight-icon">✦</div><div><span>今日求职简报</span><small>数据更新至今天</small></div></div><span class="insight-status insight-status-${status.tone}"><i></i>${status.label}</span></div><div class="insight-metrics"><button onclick="navigate('applications')"><b>${apps.length}</b><small>总岗位</small></button><button onclick="navigate('applications')"><b>${active.length}</b><small>推进中</small></button><button onclick="navigate('calendar')"><b>${upcomingCount}</b><small>待参加</small></button><button class="${stale.length?'has-attention':''}" onclick="${attentionAction}"><b>${stale.length}</b><small>需关注</small></button></div><div class="insight-summary"><button class="link-btn" onclick="navigate('stats')">查看统计 →</button></div></div>`;
  }
  const baseHome=renderHome;
  renderHome=function(){baseHome();content.insertAdjacentHTML('afterbegin',insightHtml());};
  const baseStats=renderStats;
  renderStats=function(){baseStats();const charts=content.querySelector('.stats-charts');(charts||content).insertAdjacentHTML(charts?'beforebegin':'beforeend',funnelHtml());};

  async function loadBackups(){
    const host=document.querySelector('#backupList');if(!host)return;
    try{const response=await fetch('/api/backups',{cache:'no-store'}),result=await response.json();if(!response.ok)throw new Error(result.error||'读取失败');const items=result.items||[];host.innerHTML=items.length?items.map(item=>`<div class="backup-row"><div><b>${new Date(item.createdAt).toLocaleString('zh-CN')}</b><small>${Number(item.applicationCount||0)} 条投递 · ${Number(item.eventCount||0)} 项日程 · ${Math.max(1,Math.round(item.size/1024))} KB</small></div><button class="ghost" onclick="restoreLocalBackup('${esc(item.name)}')">恢复</button></div>`).join(''):'<div class="empty compact">还没有可恢复的云端备份</div>';}catch{host.innerHTML='<div class="empty compact">暂时无法读取备份列表</div>';}
  }
  window.restoreLocalBackup=function(name){confirmAction('恢复历史备份','当前数据会先自动备份，然后恢复所选版本。',async()=>{try{const response=await fetch('/api/backups/restore',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name})}),result=await response.json();if(!response.ok)throw new Error(result.error||'恢复失败');state={...initial(),...result.data,settings:{...state.settings,...(result.data.settings||{})}};save();render();toast('历史备份已恢复');}catch(error){toast(error.message,{type:'error'});}});};
  exportData=function(){const clean=JSON.parse(JSON.stringify(state));if(clean.settings)delete clean.settings.apiKey;const blob=new Blob([JSON.stringify(clean,null,2)],{type:'application/json'}),link=document.createElement('a');link.href=URL.createObjectURL(blob);link.download=`求职进度本备份_${today()}.json`;link.click();URL.revokeObjectURL(link.href);toast('备份文件已导出');};window.exportData=exportData;
    importData=function(input){const file=input.files?.[0];if(!file)return;const reader=new FileReader();reader.onload=()=>{try{const data=JSON.parse(reader.result);if(!Array.isArray(data.applications)||!Array.isArray(data.events))throw new Error('备份格式不正确');confirmAction('导入备份',`将用备份中的 ${data.applications.length} 条投递和 ${data.events.length} 项日程替换当前数据。`,()=>{const apiKey=state.settings.apiKey||data.settings?.apiKey||'';state={...initial(),...data,settings:{...initial().settings,...(data.settings||{}),apiKey}};normalizeApplicationRecords(state);save();render();toast('备份已成功导入');});}catch(error){toast(error.message,{type:'error'});}finally{input.value='';}};reader.readAsText(file);};window.importData=importData;
  clearData=function(){confirmAction('清空全部数据','将清空所有投递和日程。操作完成后可通过自动备份恢复。',()=>{createUndoSnapshot('全部数据');state.applications=[];state.events=[];save();render();toast('全部数据已清空',{type:'warning',action:'撤销',onAction:undoLastChange,duration:8000});},{danger:true});};window.clearData=clearData;
  const baseSettings=renderSettings;
  renderSettings=function(){
    baseSettings();
    document.querySelectorAll('.settings-note').forEach(note=>{note.innerHTML=note.innerHTML.replace(/API Key[^。]*。/,'API Key 单独保存在本机私有配置文件中，不会写入投递数据或导出备份。');});
    const settingsGrid=content.querySelector('.grid.two-col'),apiPanel=settingsGrid?.children[0],dataPanel=settingsGrid?.children[1];if(settingsGrid)settingsGrid.classList.add('settings-main-grid');
    if(settingsGrid&&apiPanel&&dataPanel){const leftColumn=document.createElement('div'),rightColumn=document.createElement('div');leftColumn.className='settings-left-column';rightColumn.className='settings-right-column';settingsGrid.replaceChildren(leftColumn,rightColumn);leftColumn.appendChild(apiPanel);rightColumn.appendChild(dataPanel);leftColumn.insertAdjacentHTML('beforeend',`<div class="panel settings-section about-settings-section"><div class="about-brand"><span>✓</span><div><h2>求职进度本</h2><p>私密、专注、可同步的求职管理工具</p></div></div><dl><div><dt>数据位置</dt><dd>云端 PostgreSQL（按账号隔离）</dd></div><div><dt>API 配置</dt><dd>服务端加密存储</dd></div><div><dt>更新渠道</dt><dd><a href="https://github.com/dfaskl/job-tracker-desktop" target="_blank" rel="noreferrer">GitHub 项目主页 ↗</a></dd></div></dl></div>`);rightColumn.insertAdjacentHTML('beforeend',`<div class="panel settings-section backup-settings-section"><div class="panel-head"><div><h2>云端历史备份</h2><p>最多保留最近 30 份；恢复前会自动保存当前数据。</p></div><button class="link-btn" type="button" onclick="loadCloudBackups()">刷新</button></div><div class="backup-list" id="backupList"><div class="empty compact">正在读取备份…</div></div></div>`);const syncColumnHeight=()=>{rightColumn.style.height=matchMedia('(min-width:901px)').matches?`${leftColumn.offsetHeight}px`:''};window.settingsColumnsObserver?.disconnect();window.settingsColumnsObserver=new ResizeObserver(syncColumnHeight);window.settingsColumnsObserver.observe(leftColumn);requestAnimationFrame(syncColumnHeight);loadBackups();}
  };
  window.loadCloudBackups=loadBackups;

  function enhanceForm(form){
    if(!form||form.dataset.commercialValidation)return;form.dataset.commercialValidation='true';
    form.querySelectorAll('[required]').forEach(field=>{field.addEventListener('invalid',()=>{field.closest('.field')?.classList.add('field-invalid');toast(`请检查“${field.closest('.field')?.querySelector('label')?.textContent.replace('*','').trim()||'必填项'}”`,{type:'error'});});field.addEventListener('input',()=>field.closest('.field')?.classList.remove('field-invalid'));});
  }
  const iconPaths={add:'<path d="M12 5v14M5 12h14"/>',spark:'<path d="m12 3 1.5 5.5L19 10l-5.5 1.5L12 17l-1.5-5.5L5 10l5.5-1.5Z"/><path d="m18 16 .7 2.3L21 19l-2.3.7L18 22l-.7-2.3L15 19l2.3-.7Z"/>',download:'<path d="M12 3v12m0 0 4-4m-4 4-4-4"/><path d="M5 19h14"/>',upload:'<path d="M12 16V4m0 0 4 4m-4-4L8 8"/><path d="M5 20h14"/>',edit:'<path d="m4 16-.8 4 4-.8L18 8.4 14.6 5Z"/><path d="m13.5 6.1 3.4 3.4"/>',calendar:'<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 10h18"/>',trash:'<path d="M4 7h16M9 7V4h6v3M7 7l1 14h8l1-14M10 11v6M14 11v6"/>',external:'<path d="M14 4h6v6M20 4l-9 9"/><path d="M18 13v6a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h6"/>',check:'<path d="m5 12 4 4L19 6"/>',applied:'<path d="M5 19 19 5M9 5h10v10"/>',assessment:'<path d="m12 3 8 9-8 9-8-9Z"/>',test:'<path d="M5 20h14M8 16 16 8M14 6l4 4M7 17l-1 3 3-1"/>',interview:'<circle cx="12" cy="9" r="4"/><path d="M5 21c.6-4 3-6 7-6s6.4 2 7 6"/>',phone:'<path d="M7 3h3l1.5 4-2 1.5a15 15 0 0 0 6 6l1.5-2L21 14v3c0 2-1 4-4 4C9 20 4 15 3 7c0-3 2-4 4-4Z"/>',offer:'<path d="m12 3 2.7 5.5 6.1.9-4.4 4.3 1 6.1-5.4-2.9-5.4 2.9 1-6.1-4.4-4.3 6.1-.9Z"/>',waiting:'<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',failed:'<path d="m7 7 10 10M17 7 7 17"/>',other:'<path d="M12 5v14M5 12h14"/>'};
  function svgIcon(name){return `<svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true">${iconPaths[name]||iconPaths.other}</svg>`;}
  function upgradeVisualIcons(){
    const flowMap={applied:'applied',assessment:'assessment',test:'test',interview:'interview',phone:'phone',offer:'offer',waiting:'waiting',failed:'failed',other:'other'};
    document.querySelectorAll('.flow-node i').forEach(holder=>{if(holder.querySelector('svg'))return;const type=Object.keys(flowMap).find(key=>holder.closest('.flow-node')?.classList.contains(`flow-${key}`))||'other';holder.innerHTML=svgIcon(flowMap[type]);});
    const buttonRules=[['新建投递','add'],['新增日程','add'],['追加安排','calendar'],['使用大模型识别','spark'],['AI 校正','spark'],['编辑','edit'],['删除','trash'],['导出数据','download'],['导入数据','upload'],['打开链接','external']];
    document.querySelectorAll('button,label.ghost').forEach(button=>{if(button.querySelector('.ui-icon'))return;const text=button.textContent.replace(/[＋✦]/g,'').trim(),rule=buttonRules.find(([label])=>text.includes(label));if(rule){Array.from(button.childNodes).filter(node=>node.nodeType===Node.TEXT_NODE).forEach(node=>node.textContent=node.textContent.replace(/[＋✦]/g,'').trim());button.insertAdjacentHTML('afterbegin',svgIcon(rule[1]));}});
    const statIcons={'投递总数':'applied','进行中 · 有进展':'assessment','进行中 · 仅投递':'waiting','Offer':'offer','长期无消息':'failed'};
    document.querySelectorAll('.stat-card').forEach(card=>{if(card.querySelector('.stat-svg'))return;const name=statIcons[card.querySelector('small')?.textContent.trim()];if(name){card.dataset.icon='';card.insertAdjacentHTML('afterbegin',`<span class="stat-svg">${svgIcon(name)}</span>`);}});
  }
  const baseOpenModal=openModal;
  openModal=function(...args){baseOpenModal(...args);requestAnimationFrame(()=>{enhanceForm(document.querySelector('#modalBody form'));upgradeVisualIcons();});};window.openModal=openModal;
  const baseRender=render;render=function(){baseRender();requestAnimationFrame(upgradeVisualIcons);};
  requestAnimationFrame(upgradeVisualIcons);
  document.addEventListener('keydown',event=>{if(event.key==='Escape'&&!document.querySelector('#modalMask')?.classList.contains('hidden'))closeModal();});
})();
