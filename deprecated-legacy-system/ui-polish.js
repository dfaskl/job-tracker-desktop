(function () {
  const icons={
    home:'<path d="M3 11.5 12 4l9 7.5"/><path d="M5.5 10.5V20h13v-9.5"/><path d="M9 20v-6h6v6"/>',
    applications:'<rect x="4" y="3" width="16" height="18" rx="2"/><path d="M8 8h8M8 12h8M8 16h5"/>',
    calendar:'<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 10h18"/>',
    mail:'<path d="m4 4 8 8 8-8"/><rect x="3" y="4" width="18" height="16" rx="2"/>',
    stats:'<path d="M5 20V10M12 20V4M19 20v-7"/>',
    settings:'<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-4V21a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H2.8v-4H3a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1a1.7 1.7 0 0 0 1.9.3A1.7 1.7 0 0 0 10 3V2.8h4V3a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2v4H21a1.7 1.7 0 0 0-1.6 1Z"/>'
  };
  function svg(path){return `<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${path}</svg>`;}
  function titleText(element){return Array.from(element?.childNodes||[]).filter(node=>node.nodeType===Node.TEXT_NODE).map(node=>node.textContent).join('').trim();}
  function unifyIcons(){
    document.querySelectorAll('#nav button').forEach(button=>{const key=button.dataset.page,holder=button.querySelector('span');if(holder&&icons[key])holder.innerHTML=svg(icons[key]);});
    const mark=document.querySelector('.brand-mark');if(mark)mark.innerHTML=svg('<path d="m6 12 4 4 8-9"/>');
  }
  function dateKey(value){return String(value||'').slice(0,10);}
  function polishHome(){
    const isHome=page==='home';
    content.classList.toggle('home-workbench',isHome);
    if(!isHome)return;
    const todayKey=typeof today==='function'?today():dateKey(new Date().toISOString()),nextDate=new Date();nextDate.setDate(nextDate.getDate()+1);const tomorrow=`${nextDate.getFullYear()}-${String(nextDate.getMonth()+1).padStart(2,'0')}-${String(nextDate.getDate()).padStart(2,'0')}`;
    content.querySelectorAll('.event-card').forEach((card,index)=>{const key=dateKey(card.dataset.startsAt);card.classList.toggle('is-next',index===0);card.classList.toggle('is-today',key===todayKey);card.classList.toggle('is-tomorrow',key===tomorrow);});
    content.querySelectorAll('.empty').forEach(empty=>{empty.classList.add('polished-empty');const panel=empty.closest('.panel'),title=titleText(panel?.querySelector('h2'));if(title==='近期日程'&&!empty.querySelector('button'))empty.insertAdjacentHTML('beforeend','<button class="secondary" onclick="openEventForm()">＋ 新增日程</button>');});
  }
  function polishMail(){
    if(page!=='mail')return;
    const layout=content.querySelector('.mail-layout');
    if(layout&&!layout.querySelector('.mail-stepper'))layout.insertAdjacentHTML('afterbegin','<div class="mail-stepper"><span class="active"><b>1</b>粘贴邮件</span><i></i><span><b>2</b>检查结果</span><i></i><span><b>3</b>关联岗位</span></div>');
    const textarea=document.querySelector('#mailBody'),button=document.querySelector('#recognizeBtn');
    if(textarea&&!document.querySelector('.mail-input-meta')){
      textarea.insertAdjacentHTML('afterend','<div class="mail-input-meta"><span>等待粘贴邮件正文</span><b>0 字</b></div>');
      const update=()=>{const value=textarea.value.trim(),meta=document.querySelector('.mail-input-meta');meta.querySelector('span').textContent=value?'邮件正文已就绪':'等待粘贴邮件正文';meta.querySelector('b').textContent=`${textarea.value.length} 字`;button.disabled=!value;};
      textarea.addEventListener('input',update);content.querySelector('.mail-box .link-btn')?.addEventListener('click',()=>setTimeout(update));update();
    }
    const match=document.querySelector('#mrExistingApp');
    if(match){const field=match.closest('.field'),third=document.querySelector('.mail-stepper span:nth-of-type(3)');field?.classList.add('mail-match-field');const refresh=()=>{const matched=match.value!=='__new__';field?.classList.toggle('matched',matched);field?.setAttribute('data-match',matched?'已匹配':'需要确认');third?.classList.toggle('active',matched);};match.addEventListener('change',refresh);refresh();document.querySelector('.mail-stepper span:nth-of-type(2)')?.classList.add('active');}
  }
  function polishStats(){
    if(page!=='stats')return;
    const statIcons={'投递总数':'↗','进行中 · 有进展':'⌁','进行中 · 仅投递':'○','Offer':'★','长期无消息':'!'};
    content.querySelectorAll('.stat-card').forEach((card,index)=>{const label=card.querySelector('small')?.textContent.trim();card.classList.add(`stat-tone-${index+1}`);card.dataset.icon=statIcons[label]||'•';});
    content.querySelectorAll('.stats-charts .panel').forEach(panel=>{const title=titleText(panel.querySelector('h2'));panel.classList.add(title.includes('阶段')?'stage-chart-panel':'channel-chart-panel');panel.querySelectorAll('.vertical-column').forEach((column,index)=>{column.dataset.index=index;column.dataset.label=column.querySelector('.column-label')?.textContent.trim()||'';});});
    const calendar=content.querySelector('.delivery-calendar-panel .calendar-panel-head');
    if(calendar&&!calendar.querySelector('.activity-legend'))calendar.insertAdjacentHTML('beforeend','<div class="activity-legend"><span><i class="application"></i>投递</span><span><i class="test"></i>笔试</span><span><i class="interview"></i>面试</span></div>');
  }
  function polishSettings(){
    if(page!=='settings')return;
    content.querySelectorAll('.panel').forEach(panel=>{const title=titleText(panel.querySelector('h2'));if(title==='整体色调')panel.classList.add('settings-section','theme-settings-section');if(title==='大模型 API'){panel.classList.add('settings-section','api-settings-section');const configured=Boolean(state.settings.apiUrl&&state.settings.model&&state.settings.apiKey);panel.querySelector('h2')?.insertAdjacentHTML('afterend',`<div class="api-status ${configured?'ready':'warning'}"><i></i>${configured?'API 配置完整':'API 配置尚未完成'}</div>`);const form=panel.querySelector('#settingsForm');if(form&&!form.dataset.statusBound){form.dataset.statusBound='true';form.addEventListener('submit',()=>setTimeout(()=>{const ready=Boolean(state.settings.apiUrl&&state.settings.model&&state.settings.apiKey),status=panel.querySelector('.api-status');if(status){status.className=`api-status ${ready?'ready':'warning'}`;status.innerHTML=`<i></i>${ready?'API 配置完整':'API 配置尚未完成'}`;}},0));}}if(title==='数据管理')panel.classList.add('settings-section','data-settings-section');});
  }
  function polishModal(){
    const body=document.querySelector('#modalBody');if(!body)return;
    if(body.querySelector('.detail-head')){document.querySelector('.modal')?.classList.add('application-detail-modal');const actions=body.querySelector('.detail-actions');actions?.querySelector('.ghost:last-child')?.classList.add('detail-delete-action');body.querySelectorAll('.event-card').forEach(card=>card.classList.add('timeline-event-card'));}
    if(body.querySelector('.schedule-detail-list'))document.querySelector('.modal')?.classList.add('schedule-day-modal');
  }
  function polish(){unifyIcons();polishHome();polishMail();polishStats();polishSettings();polishModal();}
  const originalRender=render;render=function(){originalRender();requestAnimationFrame(polish);};
  const originalOpenModal=openModal;openModal=function(...args){document.querySelector('.modal')?.classList.remove('application-detail-modal','schedule-day-modal');originalOpenModal(...args);requestAnimationFrame(polishModal);};window.openModal=openModal;
  requestAnimationFrame(polish);
})();
