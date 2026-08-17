(function () {
  function stageCategory(application) {
    if (application.stage === '已结束' || ['未通过', '已放弃', '已结束'].includes(application.status)) return '已结束';
    const health = progressHealth(application);
    if (health && health.days >= 7) return '无消息';
    if (['测评', '笔试', '面试', 'Offer'].includes(application.stage)) return application.stage;
    return '已投递';
  }

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
    const stageLabels = ['已投递', '测评', '笔试', '面试', 'Offer', '已结束', '无消息'];
    const stageItems = stageLabels.map(label => [label, state.applications.filter(item => stageCategory(item) === label).length]);
    const channelMap = state.applications.reduce((result, item) => {
      const channel = item.channel || '未填写';
      result[channel] = (result[channel] || 0) + 1;
      return result;
    }, {});
    const channelItems = Object.entries(channelMap).sort((a, b) => b[1] - a[1]);
    const noMessage = stageItems.find(item => item[0] === '无消息')[1];

    content.innerHTML = `
      <div class="grid stats-grid">
        <div class="stat-card"><small>投递总数</small><strong>${state.applications.length}</strong></div>
        <div class="stat-card"><small>面试阶段</small><strong>${stageItems.find(item => item[0] === '面试')[1]}</strong></div>
        <div class="stat-card"><small>Offer</small><strong>${stageItems.find(item => item[0] === 'Offer')[1]}</strong></div>
        <div class="stat-card"><small>长期无消息</small><strong>${noMessage}</strong></div>
      </div>
      <div class="grid stats-charts">
        <div class="panel"><h2>阶段分布</h2>${verticalChart(stageItems, '暂无阶段数据')}</div>
        <div class="panel"><h2>渠道分布</h2>${verticalChart(channelItems, '暂无渠道数据')}</div>
      </div>`;
  };
})();
