(function(){
  const svgPaths={empty:'<path d="M5 8.5h14v10H5z"/><path d="M8 8.5V6h8v2.5M9 13h6"/>',search:'<circle cx="10.5" cy="10.5" r="5.5"/><path d="m15 15 4 4"/>',calendar:'<rect x="4" y="5" width="16" height="15" rx="2"/><path d="M8 3v4M16 3v4M4 10h16M9 14h6"/>',mail:'<rect x="3" y="5" width="18" height="14" rx="2"/><path d="m4 7 8 7 8-7"/>',chart:'<path d="M5 19V9M12 19V5M19 19v-7"/>',external:'<path d="M14 4h6v6M20 4l-9 9"/><path d="M18 13v6a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h6"/>'};
  const svg=name=>`<svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true">${svgPaths[name]||svgPaths.empty}</svg>`;
  function emptyKind(element){const text=element.textContent;if(/搜索|符合条件/.test(text))return'search';if(/日程|安排/.test(text))return'calendar';if(/识别|邮件/.test(text))return'mail';if(/统计|数据/.test(text))return'chart';return'empty';}
  function enhanceEmptyStates(){document.querySelectorAll('.empty').forEach(empty=>{empty.classList.add('empty-state-v2');if(!empty.querySelector('.empty-visual'))empty.insertAdjacentHTML('afterbegin',`<span class="empty-visual">${svg(emptyKind(empty))}</span>`);});}
  function enhanceHome(){if(page!=='home')return;const schedule=content.querySelector('.two-col>.panel:nth-child(2)'),manual=content.querySelector('.manual-confirm-panel');schedule?.classList.add('home-priority-section');manual?.classList.add('home-review-section');}
  function enhanceStats(){if(page!=='stats')return;content.querySelectorAll('.vertical-column').forEach(column=>{const label=column.querySelector('.column-label')?.textContent.trim()||'',value=column.querySelector('.column-value')?.textContent.trim()||'0';column.dataset.tooltip=`${label}：${value}`;column.tabIndex=0;});content.querySelectorAll('.funnel-step').forEach((step,index,items)=>{const stage=step.querySelector('span')?.textContent||'',value=Number(step.querySelector('b')?.textContent||0),previous=index?Number(items[index-1].querySelector('b')?.textContent||0):value;step.dataset.tooltip=index?`${stage}：${value} 个岗位，上阶段转化率 ${previous?Math.round(value/previous*100):0}%`:`${stage}：${value} 个岗位`;step.tabIndex=0;});content.querySelectorAll('.stats-charts .panel h2,.conversion-panel h2').forEach(title=>{if(!title.querySelector('.chart-help'))title.insertAdjacentHTML('beforeend','<span class="chart-help" title="将鼠标移到图表上可查看详细数量">?</span>');});}
  function enhanceLinks(){document.querySelectorAll('a[target="_blank"]').forEach(link=>{if(!link.querySelector('.external-icon'))link.insertAdjacentHTML('beforeend',`<span class="external-icon">${svg('external')}</span>`);});}
  function buttonDescription(button){
    const aria=button.getAttribute('aria-label')?.trim();if(aria)return aria;
    const text=button.textContent.replace(/\s+/g,' ').trim();
    const rules=[
      [/^首页$/,'查看首页概览'],[/投递记录/,'查看和管理投递记录'],[/^日程$/,'查看和管理日程'],[/邮件识别/,'从邮件中识别投递信息'],[/^统计$/,'查看求职统计'],[/^设置$/,'打开设置'],
      [/新建投递/,'创建一条投递记录'],[/新增日程|追加安排/,'添加一项日程'],[/编辑信息|编辑投递/,'编辑投递信息和状态'],[/收到 Offer/,'标记为已收到 Offer'],[/未通过/,'标记为未通过'],
      [/保存配置/,'保存 API 配置'],[/保存链接/,'保存公司官网链接'],[/编辑链接/,'编辑公司官网链接'],[/保存/,'保存当前内容'],[/取消/,'取消并返回'],[/删除/,'删除当前内容'],
      [/完成/,'标记为已完成'],[/错过/,'标记为已错过'],[/恢复/,'恢复当前记录'],[/导出/,'导出本地数据'],[/导入/,'导入备份数据'],[/识别/,'开始识别内容'],[/清空/,'清空当前内容'],
      [/查看全部/,'查看全部内容'],[/查看统计/,'查看详细统计'],[/立即处理/,'前往处理超时日程'],[/本月/,'回到本月'],[/^[‹<]$/,'切换到上个月'],[/^[›>]$/,'切换到下个月'],[/^[×X]$/,'关闭窗口']
    ];
    const matched=rules.find(([pattern])=>pattern.test(text));if(matched)return matched[1];
    const clean=text.replace(/[＋+✦◇▣⌁⇩→✓]/g,'').trim();return clean?`执行“${clean}”`:'执行此操作';
  }
  function enhanceButtonTitles(){document.querySelectorAll('button').forEach(button=>{if(button.hasAttribute('title')&&!button.dataset.autoButtonTitle)return;button.title=buttonDescription(button);button.dataset.autoButtonTitle='true';});}
  function enhanceModal(){const modal=document.querySelector('.modal'),body=document.querySelector('#modalBody');if(!modal||!body)return;modal.classList.remove('modal-small','modal-medium','modal-large');if(body.querySelector('.confirm-dialog'))modal.classList.add('modal-small');else if(body.querySelector('.detail-head,.schedule-detail-list'))modal.classList.add('modal-large');else modal.classList.add('modal-medium');const first=body.querySelector('input:not([type="hidden"]):not([disabled]),textarea:not([disabled]),select:not([disabled])');if(first&&!body.querySelector('.confirm-dialog'))setTimeout(()=>first.focus({preventScroll:true}),80);}
  function animateContent(){if(page==='applications')content.querySelectorAll('.application-card').forEach((card,index)=>{card.style.setProperty('--enter-index',Math.min(index,8));card.classList.add('list-enter')});}
  function polish(){enhanceEmptyStates();enhanceHome();enhanceStats();enhanceLinks();enhanceButtonTitles();animateContent();}
  const priorRender=render;render=function(){priorRender();requestAnimationFrame(polish);};
  const priorOpenModal=openModal;openModal=function(...args){priorOpenModal(...args);requestAnimationFrame(()=>{enhanceModal();enhanceLinks();enhanceEmptyStates();enhanceButtonTitles();});};window.openModal=openModal;
  ['changeApplicationHeatmapMonth','changeScheduleMonth'].forEach(name=>{const original=window[name];if(typeof original==='function')window[name]=function(...args){const result=original(...args);requestAnimationFrame(()=>content.querySelector('.heatmap-grid,.schedule-calendar-grid')?.classList.add('calendar-shift'));return result;};});
  document.addEventListener('keydown',event=>{if(event.key==='Tab')document.documentElement.classList.add('keyboard-navigation');});document.addEventListener('pointerdown',()=>document.documentElement.classList.remove('keyboard-navigation'));
  const contentObserver=new MutationObserver(()=>requestAnimationFrame(()=>{enhanceEmptyStates();enhanceLinks();enhanceButtonTitles();}));contentObserver.observe(content,{childList:true,subtree:true});
  requestAnimationFrame(polish);
})();
