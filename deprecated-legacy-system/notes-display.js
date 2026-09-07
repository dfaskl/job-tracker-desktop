(function () {
  const originalOpenDetail = openDetail;
  openDetail = function (applicationId) {
    originalOpenDetail(applicationId);
    const application = appById(applicationId);
    if (!application) return;
    const labels = Array.from(document.querySelectorAll('#modalBody .section-label'));
    const basicLabel = labels.find(label => label.textContent.trim() === '基本信息');
    const info = basicLabel?.nextElementSibling;
    if (!info?.classList.contains('settings-note')) return;
    info.innerHTML = `<div class="application-info-line"><span><b>投递日期</b>${esc(application.appliedDate||'未填写')}</span><span><b>投递渠道</b>${esc(application.channel||'未填写')}</span></div>${application.notes?`<div class="application-note-block"><b>岗位备注</b><p>${esc(application.notes)}</p></div>`:'<div class="application-note-empty">暂无岗位备注</div>'}`;
  };
  window.openDetail = openDetail;
})();
