(function(){
  const REVIEW_INTERVAL_DAYS=3;
  let currentReviewBatch=[];
  const terminal=application=>application.stage==='已结束'||['未通过','已放弃','已结束'].includes(application.status);
  const activeApplications=()=>state.applications.filter(application=>!terminal(application));
  function localDateKey(value){const date=value?new Date(value):new Date();if(!Number.isFinite(date.getTime()))return'';return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`}
  function dayNumber(value){const key=localDateKey(value);if(!key)return NaN;const [year,month,day]=key.split('-').map(Number);return Math.floor(Date.UTC(year,month-1,day)/86400000)}
  function checkedAt(applicationId){return state.applicationReviewChecks?.[applicationId]||state.officialReviewConfirmedAt||''}
  function reviewBatch(){
    const applications=activeApplications(),size=Math.ceil(applications.length/REVIEW_INTERVAL_DAYS),todayNumber=dayNumber();
    if(state.applicationReviewBatchDate===localDateKey())return[];
    return applications
      .map(application=>({application,checkedAt:checkedAt(application.id)}))
      .filter(item=>!item.checkedAt||todayNumber-dayNumber(item.checkedAt)>=REVIEW_INTERVAL_DAYS)
      .sort((left,right)=>{if(!left.checkedAt&&right.checkedAt)return-1;if(left.checkedAt&&!right.checkedAt)return 1;return String(left.checkedAt).localeCompare(String(right.checkedAt))||String(left.application.appliedDate||'').localeCompare(String(right.application.appliedDate||''))})
      .slice(0,size)
      .map(item=>item.application);
  }
  function reminderHtml(){
    const active=activeApplications();currentReviewBatch=reviewBatch();
    if(!active.length||!currentReviewBatch.length)return'';
    const names=currentReviewBatch.map(application=>`${esc(application.company)} · ${esc(application.position)}`).join('、');
    return `<section class="panel review-reminder" role="status"><div class="review-reminder-icon" aria-hidden="true">✓</div><div class="review-reminder-copy"><small>今日进展确认</small><strong>今天检查 ${currentReviewBatch.length} 条投递</strong><p>共 ${active.length} 条进行中；优先安排最久未确认的岗位，确保每条最多间隔三天。</p><div class="review-reminder-jobs">${names}</div></div><div class="review-reminder-actions"><button class="ghost" type="button" onclick="navigate('applications')">查看投递</button><button class="primary" type="button" onclick="confirmApplicationReviewBatch()">本批已确认</button></div></section>`;
  }
  window.confirmApplicationReviewBatch=function(){
    if(!currentReviewBatch.length)return;
    const checkedAt=new Date().toISOString();state.applicationReviewChecks={...(state.applicationReviewChecks||{})};currentReviewBatch.forEach(application=>{state.applicationReviewChecks[application.id]=checkedAt});state.applicationReviewBatchDate=localDateKey();
    const count=currentReviewBatch.length;save();render();toast(`已确认 ${count} 条投递，这些岗位三天后再提醒`);
  };
  const baseHome=renderHome;
  renderHome=function(){baseHome();const html=reminderHtml();if(html)content.insertAdjacentHTML('afterbegin',html)};
})();
