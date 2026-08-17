(function () {
  let calendarMonth = new Date();
  calendarMonth = new Date(calendarMonth.getFullYear(), calendarMonth.getMonth(), 1);

  function dateKey(year, month, day) {
    return `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
  }
  function renderCalendarPanel() {
    const year = calendarMonth.getFullYear();
    const month = calendarMonth.getMonth();
    const firstOffset = (new Date(year, month, 1).getDay() + 6) % 7;
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const counts = {};
    const bucket = key => counts[key] ||= { applications: 0, tests: 0, interviews: 0 };
    state.applications.forEach(application => {
      if (/^\d{4}-\d{2}-\d{2}$/.test(application.appliedDate || '')) bucket(application.appliedDate).applications += 1;
    });
    state.events.forEach(event => {
      const key = String(event.startsAt || '').slice(0, 10);
      if (!/^\d{4}-\d{2}-\d{2}$/.test(key)) return;
      if (event.type === '笔试') bucket(key).tests += 1;
      if (event.type === '面试') bucket(key).interviews += 1;
    });
    const todayKey = dateKey(new Date().getFullYear(), new Date().getMonth(), new Date().getDate());
    const cells = [];
    for (let index = 0; index < 42; index++) {
      const day = index - firstOffset + 1;
      if (day < 1 || day > daysInMonth) {
        cells.push('<div class="delivery-day outside"></div>');
        continue;
      }
      const key = dateKey(year, month, day);
      const count = counts[key] || { applications: 0, tests: 0, interviews: 0 };
      const total = count.applications + count.tests + count.interviews;
      cells.push(`<div class="delivery-day ${total ? 'has-activity' : ''} ${key === todayKey ? 'today' : ''}" title="${key}：投递 ${count.applications}，笔试 ${count.tests}，面试 ${count.interviews}"><span>${day}</span>${total ? `<div class="day-metrics"><b class="metric-application"><i></i>投递 <strong>${count.applications}</strong></b><b class="metric-test"><i></i>笔试 <strong>${count.tests}</strong></b><b class="metric-interview"><i></i>面试 <strong>${count.interviews}</strong></b></div>` : ''}</div>`);
    }
    const monthPrefix = `${year}-${String(month + 1).padStart(2, '0')}`;
    const monthTotal = Object.entries(counts).filter(([key]) => key.startsWith(monthPrefix)).reduce((sum, [, value]) => ({ applications: sum.applications + value.applications, tests: sum.tests + value.tests, interviews: sum.interviews + value.interviews }), { applications: 0, tests: 0, interviews: 0 });
    const panel = document.createElement('div');
    panel.className = 'panel delivery-calendar-panel';
    panel.innerHTML = `
      <div class="panel-head calendar-panel-head">
        <div><h2>求职活动日历</h2><p>${year}年${month + 1}月：投递 <b>${monthTotal.applications}</b> · 笔试 <b>${monthTotal.tests}</b> · 面试 <b>${monthTotal.interviews}</b></p></div>
        <div class="calendar-controls">
          <button class="ghost" onclick="changeStatsMonth(-1)" title="上个月">‹</button>
          <button class="secondary" onclick="resetStatsMonth()">本月</button>
          <button class="ghost" onclick="changeStatsMonth(1)" title="下个月">›</button>
        </div>
      </div>
      <div class="delivery-weekdays">${['周一','周二','周三','周四','周五','周六','周日'].map(day => `<b>${day}</b>`).join('')}</div>
      <div class="delivery-calendar-grid">${cells.join('')}</div>`;
    const charts = content.querySelector('.stats-charts');
    content.insertBefore(panel, charts || null);
  }

  const originalRenderStats = renderStats;
  renderStats = function () { originalRenderStats(); renderCalendarPanel(); };
  window.changeStatsMonth = function (offset) {
    calendarMonth = new Date(calendarMonth.getFullYear(), calendarMonth.getMonth() + offset, 1);
    render();
    requestAnimationFrame(() => window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' }));
  };
  window.resetStatsMonth = function () {
    const now = new Date();
    calendarMonth = new Date(now.getFullYear(), now.getMonth(), 1);
    render();
    requestAnimationFrame(() => window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' }));
  };
})();
