(function(){
  const REVIEW_INTERVAL_DAYS=3;
  let currentReviewItem=null;
  const terminal=application=>application.stage==='已结束'||['未通过','已放弃','已结束'].includes(application.status);
  const activeApplications=()=>state.applications.filter(application=>!terminal(application));
  function localDateKey(value){const date=value?new Date(value):new Date();if(!Number.isFinite(date.getTime()))return'';return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`}
  function dayNumber(value){const key=localDateKey(value);if(!key)return NaN;const [year,month,day]=key.split('-').map(Number);return Math.floor(Date.UTC(year,month-1,day)/86400000)}
  function checkedAt(applicationId){return state.applicationReviewChecks?.[applicationId]||''}
  function candidates(applications){
    const todayNumber=dayNumber();
    return applications
      .map(application=>({application,checkedAt:checkedAt(application.id)}))
      .filter(item=>!item.checkedAt||todayNumber-dayNumber(item.checkedAt)>=REVIEW_INTERVAL_DAYS)
      .sort((left,right)=>{if(!left.checkedAt&&right.checkedAt)return-1;if(left.checkedAt&&!right.checkedAt)return 1;return String(left.checkedAt).localeCompare(String(right.checkedAt))||String(left.application.appliedDate||'').localeCompare(String(right.application.appliedDate||''))})
      .map(item=>item.application);
  }
  function dailyQueue(){
    const applications=activeApplications(),date=localDateKey(),saved=state.applicationReviewDailyBatch;
    if(saved?.date===date&&Array.isArray(saved.ids)){const valid=saved.ids.map(id=>appById(id)).filter(Boolean).filter(application=>!terminal(application));return {total:valid.length,items:valid.filter(application=>localDateKey(state.applicationReviewChecks?.[application.id])!==date)}}
    const size=Math.ceil(applications.length/REVIEW_INTERVAL_DAYS),items=candidates(applications).slice(0,size);
    state.applicationReviewDailyBatch={date,ids:items.map(application=>application.id)};
    return {total:items.length,items};
  }
  function linkHtml(application){
    const url=window.applicationOfficialReviewUrl?.(application.id)||'';
    return url?`<a class="review-reminder-url" href="${esc(url)}" target="_blank" rel="noopener noreferrer" title="打开官网投递记录页">${esc(url)} ↗</a>`:`<span class="review-reminder-no-url">官网库暂未填写链接</span><button class="link-btn" type="button" onclick="openOfficialUrlEditor('${application.id}')">补充链接</button>`;
  }
  function reminderHtml(){
    const queue=dailyQueue();currentReviewItem=queue.items[0]||null;
    if(!currentReviewItem)return'';
    return `<section class="panel review-reminder" role="status"><div class="review-reminder-icon" aria-hidden="true">✓</div><div class="review-reminder-copy"><small>今日进展确认</small><strong>今天需要检查 ${queue.total} 个，还剩 ${queue.items.length} 个</strong><p>每次只处理一个；点击下方官网链接查看进展，回来后再确认这条已经检查。</p><div class="review-reminder-current"><b>${esc(currentReviewItem.company)}</b><span>${esc(currentReviewItem.position)}</span></div><div class="review-reminder-link-row">${linkHtml(currentReviewItem)}</div></div><div class="review-reminder-actions"><button class="primary" type="button" onclick="confirmCurrentApplicationReview('${currentReviewItem.id}')">这条已检查</button></div></section>`;
  }
  window.confirmCurrentApplicationReview=function(applicationId){
    if(!currentReviewItem||currentReviewItem.id!==applicationId)return;
    state.applicationReviewChecks={...(state.applicationReviewChecks||{}),[applicationId]:new Date().toISOString()};save();render();
    const remaining=state.applicationReviewDailyBatch.ids.filter(id=>localDateKey(state.applicationReviewChecks?.[id])!==localDateKey()).length;
    toast(remaining?`已确认，今天还剩 ${remaining} 条`:'今天的投递进展已全部检查完成');
  };
  const baseHome=renderHome;
  renderHome=function(){baseHome();const html=reminderHtml();if(html)content.insertAdjacentHTML('afterbegin',html)};
})();
