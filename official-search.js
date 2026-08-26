(function () {
  function searchUrl(application) {
    return `https://www.bing.com/search?q=${encodeURIComponent(`${application.company} 校园招聘`)}`;
  }

  function destinationUrl(application) {
    return String(application.officialUrl || '').trim() || searchUrl(application);
  }

  function openOfficialUrlEditor(applicationId) {
    const application = appById(applicationId);
    if (!application) return;
    openModal('编辑公司官网', `<form id="officialUrlForm" class="form-grid">
      <div class="field full">
        <label>公司官网链接</label>
        <input name="officialUrl" type="url" value="${esc(application.officialUrl || '')}" placeholder="https://careers.example.com" autocomplete="url">
        <small class="official-url-help">保存后，点击公司名称会直接打开此链接；留空则继续搜索“${esc(application.company)} 校园招聘”。</small>
      </div>
      <div class="form-actions">
        <button type="button" class="ghost" onclick="openDetail('${application.id}')">取消</button>
        <button class="primary">保存链接</button>
      </div>
    </form>`);
    document.querySelector('#officialUrlForm').onsubmit = event => {
      event.preventDefault();
      const value = String(new FormData(event.target).get('officialUrl') || '').trim();
      if (value && !/^https?:\/\//i.test(value)) {
        toast('请输入以 http:// 或 https:// 开头的完整链接', { type: 'error' });
        return;
      }
      application.officialUrl = value;
      application.updatedAt = nowText();
      save();
      openDetail(application.id);
      toast(value ? '公司官网链接已保存' : '已恢复为搜索公司官网');
    };
  }

  window.openOfficialUrlEditor = openOfficialUrlEditor;
  const originalOpenDetail = openDetail;
  openDetail = function (applicationId) {
    originalOpenDetail(applicationId);
    const application = appById(applicationId);
    if (!application || application.channel !== '官网') return;
    const companyTitle = document.querySelector('#modalBody .detail-head h3');
    if (companyTitle) {
      const hasCustomUrl = Boolean(String(application.officialUrl || '').trim());
      const indicator = hasCustomUrl
        ? '<span class="official-destination-indicator is-website" title="当前直接打开公司官网" aria-label="当前直接打开公司官网"><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9"></circle><path d="M3 12h18M12 3a15 15 0 0 1 0 18M12 3a15 15 0 0 0 0 18"></path></svg></span>'
        : '<span class="official-destination-indicator is-search" title="当前使用浏览器搜索" aria-label="当前使用浏览器搜索"><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5"></circle><path d="m15.5 15.5 5 5"></path></svg></span>';
      companyTitle.innerHTML = `<span class="official-company-title">${indicator}<a class="official-company-link" href="${esc(destinationUrl(application))}" target="_blank" rel="noopener noreferrer" title="${hasCustomUrl ? '打开已保存的公司官网' : `使用 Bing 搜索：${esc(application.company)} 校园招聘`}">${esc(application.company)}</a><button type="button" class="official-url-edit" onclick="openOfficialUrlEditor('${application.id}')" title="${hasCustomUrl ? '编辑公司官网链接' : '填写公司官网链接'}" aria-label="${hasCustomUrl ? '编辑公司官网链接' : '填写公司官网链接'}">编辑链接</button></span>`;
    }
  };
  window.openDetail = openDetail;
})();