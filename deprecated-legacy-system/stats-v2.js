(function () {
  function verticalChart(items, emptyText) {
    const max = Math.max(1, ...items.map(item => item[1]));
    if (!items.length) return `<div class="empty">${emptyText}</div>`;
    return `<div class="vertical-chart">${items.map(([label, value]) => `
      <div class="vertical-column">
        <b class="column-value">${value}</b>
        <div class="column-track"><i style="height:${value / max * 100}%"></i></div>
        <span class="column-label" title="${esc(label)}">${esc(label)}</span>
      </div>`).join('')}</div>`;
  }

  renderStats = function () {
    const stageItems = STAGE_CATEGORIES.map(label => [label, state.applications.filter(item => applicationStageCategory(item) === label).length]);
    const channelMap = state.applications.reduce((result, item) => {
      const channel = item.channel || '未填写';
      result[channel] = (result[channel] || 0) + 1;
      return result;
    }, {});
    const channelItems = Object.entries(channelMap).sort((a, b) => b[1] - a[1]);
    const activeApplications=state.applications.filter(item=>item.stage!=='Offer'&&!['已通过','未通过','已放弃','已结束'].includes(item.status));
    const noMessage=activeApplications.filter(item=>{const health=progressHealth(item);return health&&health.days>=PROGRESS_STALE_DAYS}).length;
    const activeWithProgress=activeApplications.filter(item=>item.stage!=='已投递'||state.events.some(event=>event.applicationId===item.id)).length;
    const activeOnlyApplied=activeApplications.length-activeWithProgress;

    content.innerHTML = `
      <div class="grid stats-grid stats-grid-five">
        <div class="stat-card"><small>投递总数</small><strong>${state.applications.length}</strong></div>
        <div class="stat-card"><small>进行中 · 有进展</small><strong>${activeWithProgress}</strong></div>
        <div class="stat-card"><small>进行中 · 仅投递</small><strong>${activeOnlyApplied}</strong></div>
        <div class="stat-card"><small>Offer</small><strong>${stageItems.find(item => item[0] === 'Offer')[1]}</strong></div>
        <div class="stat-card"><small>长期无消息</small><strong>${noMessage}</strong></div>
      </div>
      <div class="grid stats-charts">
        <div class="panel"><h2>阶段分布</h2>${verticalChart(stageItems, '暂无阶段数据')}</div>
        <div class="panel"><h2>渠道分布</h2>${verticalChart(channelItems, '暂无渠道数据')}</div>
      </div>`;
  };
})();
