(function(){
  const REVIEW_INTERVAL_MS=3*24*60*60*1000;
  const terminal=application=>application.stage==='已结束'||['未通过','已放弃','已结束'].includes(application.status);
  function activeApplications(){return state.applications.filter(application=>!terminal(application))}
  function reminderDue(){
    const last=new Date(String(state.officialReviewConfirmedAt||'')).getTime();
    return !Number.isFinite(last)||Date.now()-last>=REVIEW_INTERVAL_MS;
  }
  function reminderHtml(){
    const applications=activeApplications();
    if(!applications.length||!reminderDue())return'';
    return `<section class="panel review-reminder" role="status"><div class="review-reminder-icon" aria-hidden="true">✓</div><div class="review-reminder-copy"><small>三天进展确认</small><strong>该查看一下投递进展了</strong><p>目前有 ${applications.length} 条进行中的投递。查看各官网或招聘渠道后，回来标记已全部确认。</p></div><div class="review-reminder-actions"><button class="ghost" type="button" onclick="navigate('applications')">查看投递</button><button class="primary" type="button" onclick="confirmOfficialReview()">已全部确认</button></div></section>`;
  }
  window.confirmOfficialReview=function(){state.officialReviewConfirmedAt=new Date().toISOString();save();render();toast('已记录本次确认，三天后会再次提醒')};
  const baseHome=renderHome;
  renderHome=function(){baseHome();const html=reminderHtml();if(html)content.insertAdjacentHTML('afterbegin',html)};
})();
