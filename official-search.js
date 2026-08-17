(function () {
  function searchUrl(application) {
    return `https://www.bing.com/search?q=${encodeURIComponent(`${application.company} 校园招聘`)}`;
  }
  function searchLink(application, compact = false) {
    if (application.channel !== '官网') return '';
    return `<a class="official-search-link ${compact ? 'compact' : ''}" href="${searchUrl(application)}" target="_blank" rel="noopener noreferrer" onclick="event.stopPropagation()" title="搜索：${esc(application.company)} 校园招聘">搜索校招官网 <span>↗</span></a>`;
  }

  appCard = function (application) {
    return `<div class="application-card" onclick="openDetail('${application.id}')"><div><div class="card-title" title="${esc(application.company)}">${esc(application.company)}</div><div class="card-sub">${esc(application.position)}</div><div class="card-meta"><span>${esc(application.city||'地点未填')}</span><span>·</span><span>${esc(application.channel||'渠道未填')}</span><span>·</span><span>${esc(application.appliedDate||'日期未填')}</span>${searchLink(application,true)}</div></div><div class="card-side">${badge(application.stage)}${badge(application.status)}${healthBadge(application)}</div></div>`;
  };

  const originalOpenDetail = openDetail;
  openDetail = function (applicationId) {
    originalOpenDetail(applicationId);
    const application = appById(applicationId);
    if (!application || application.channel !== '官网') return;
    const companyTitle = document.querySelector('#modalBody .detail-head h3');
    if (companyTitle) companyTitle.innerHTML = `<a class="official-company-link" href="${searchUrl(application)}" target="_blank" rel="noopener noreferrer" title="使用 Bing 搜索：${esc(application.company)} 校园招聘">${esc(application.company)} <span>↗</span></a>`;
  };
  window.openDetail = openDetail;
})();
