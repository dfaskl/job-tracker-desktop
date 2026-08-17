(function () {
  const originalRenderHome = renderHome;

  renderHome = function () {
    originalRenderHome();
    const candidates = state.applications
      .map(application => ({ application, health: progressHealth(application) }))
      .filter(item => item.health && item.health.days >= 10)
      .sort((a, b) => b.health.days - a.health.days);

    const section = document.createElement('div');
    section.className = 'panel manual-confirm-panel';
    section.innerHTML = `
      <div class="panel-head">
        <div>
          <h2>人工确认</h2>
          <p>达到10天没有实际进展的岗位，请确认是否标记为未通过。</p>
        </div>
        <span class="badge ${candidates.length ? 'health-risk' : 'health-good'}">${candidates.length} 个待确认</span>
      </div>
      <div class="cards">
        ${candidates.map(({ application, health }) => `
          <div class="manual-confirm-item" onclick="openDetail('${application.id}')">
            <div class="manual-confirm-info">
              <b title="${esc(application.company)}">${esc(application.company)}</b>
              <span>${esc(application.position)}</span>
            </div>
            <span class="badge health-risk">${health.days}天无进展</span>
            <button class="danger" onclick="event.stopPropagation();quickStatus('${application.id}','reject')">标记未通过</button>
          </div>`).join('') || '<div class="empty compact-empty">目前没有需要人工确认的岗位</div>'}
      </div>`;
    content.appendChild(section);
  };

  if (page === 'home') renderHome();
})();
