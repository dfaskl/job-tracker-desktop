(function () {
  function searchUrl(application) {
    return `https://www.bing.com/search?q=${encodeURIComponent(`${application.company} 校园招聘`)}`;
  }
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
