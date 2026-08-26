(function () {
  let companyLinks = [];
  let companyLinksReady = Promise.resolve();

  function normalizeCompanyName(value) {
    return String(value || '').trim().replace(/\s+/g, ' ').toLocaleLowerCase('zh-CN');
  }

  function searchUrl(application) {
    return `https://www.bing.com/search?q=${encodeURIComponent(`${application.company} 校园招聘`)}`;
  }

  function companyLink(application) {
    const key = normalizeCompanyName(application.company);
    return companyLinks.find(item => normalizeCompanyName(item.company) === key) || null;
  }

  function customUrl(application) {
    return String(companyLink(application)?.url || application.officialUrl || '').trim();
  }

  function destinationUrl(application) {
    return customUrl(application) || searchUrl(application);
  }

  function mergedCompanyLinks() {
    const merged = companyLinks.map(item => ({ company: item.company, url: item.url }));
    state.applications.forEach(application => {
      const url = String(application.officialUrl || '').trim();
      const key = normalizeCompanyName(application.company);
      if (url && key && !merged.some(item => normalizeCompanyName(item.company) === key)) merged.push({ company: application.company, url });
    });
    return merged.sort((a, b) => a.company.localeCompare(b.company, 'zh-CN'));
  }

  async function loadCompanyLinks() {
    try {
      const response = await fetch('/api/company-links', { cache: 'no-store' });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const result = await response.json();
      companyLinks = Array.isArray(result.items) ? result.items : [];
    } catch (error) {
      console.warn('公司官网库暂不可用：', error);
    }
  }

  async function persistCompanyLinks(items) {
    const response = await fetch('/api/company-links', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ items }) });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(result.error || '保存公司官网库失败');
    companyLinks = Array.isArray(result.items) ? result.items : items;
  }

  function replaceCompanyLink(company, url) {
    const key = normalizeCompanyName(company);
    const next = companyLinks.filter(item => normalizeCompanyName(item.company) !== key);
    next.push({ company: String(company).trim(), url });
    return next.sort((a, b) => a.company.localeCompare(b.company, 'zh-CN'));
  }

  function clearLegacyLinks() {
    let changed = false;
    state.applications.forEach(application => {
      if (Object.prototype.hasOwnProperty.call(application, 'officialUrl')) {
        delete application.officialUrl;
        changed = true;
      }
    });
    if (changed) save();
  }

  function companyLinkRow(item = {}) {
    return `<div class="company-link-row">
      <input name="company" value="${esc(item.company || '')}" placeholder="公司名称" required>
      <input name="url" type="url" value="${esc(item.url || '')}" placeholder="暂未填写">
      <button type="button" class="secondary company-link-open" onclick="openCompanyWebsite(this)" title="${item.url ? '打开公司官网' : '搜索公司校园招聘'}">前往</button>
      <button type="button" class="ghost" onclick="removeCompanyLinkRow(this)">删除</button>
    </div>`;
  }

  function renderCompanyLinksManager() {
    const items = mergedCompanyLinks();
    openModal('公司官网库', `<form id="companyLinksForm" class="company-links-form">
      <p class="company-links-intro">同一公司只需维护一次，之后所有投递都会自动使用这里的官网链接。</p>
      <div class="company-links-list">${items.map(companyLinkRow).join('') || companyLinkRow()}</div>
      <div class="company-links-actions">
        <button type="button" class="secondary" onclick="addCompanyLinkRow()">＋ 添加公司</button>
        <button type="button" class="ghost" onclick="closeModal()">取消</button>
        <button class="primary">保存官网库</button>
      </div>
    </form>`);
    document.querySelector('#companyLinksForm').onsubmit = async event => {
      event.preventDefault();
      const rows = [...event.target.querySelectorAll('.company-link-row')];
      const items = [], seen = new Set();
      for (const row of rows) {
        const company = row.querySelector('[name="company"]').value.trim();
        const url = row.querySelector('[name="url"]').value.trim();
        if (!company && !url) continue;
        if (!company) return toast('请填写公司名称', { type: 'error' });
        if (url && !/^https?:\/\//i.test(url)) return toast('官网链接需以 http:// 或 https:// 开头', { type: 'error' });
        const key = normalizeCompanyName(company);
        if (seen.has(key)) return toast(`“${company}”重复，请合并后再保存`, { type: 'error' });
        seen.add(key);items.push({ company, url });
      }
      const button = event.target.querySelector('button[type="submit"],button.primary');
      button.disabled = true;button.textContent = '正在保存…';
      try {
        await persistCompanyLinks(items);
        clearLegacyLinks();
        renderCompanyLinksManager();
        toast('公司官网库已保存');
      } catch (error) {
        button.disabled = false;button.textContent = '保存官网库';
        toast(error.message, { type: 'error', duration: 5000 });
      }
    };
  }

  window.openCompanyLinksManager = async function () { await companyLinksReady;renderCompanyLinksManager(); };
  window.addCompanyLinkRow = function () {
    const list = document.querySelector('.company-links-list');
    list?.insertAdjacentHTML('beforeend', companyLinkRow());
    list?.lastElementChild?.querySelector('[name="company"]')?.focus();
  };
  window.removeCompanyLinkRow = function (button) {
    const row = button.closest('.company-link-row'), list = row?.parentElement;
    row?.remove();
    if (list && !list.children.length) list.insertAdjacentHTML('beforeend', companyLinkRow());
  };
window.openCompanyWebsite = function (button) {
    const row = button.closest('.company-link-row');
    const company = row?.querySelector('[name="company"]')?.value.trim() || '';
    const url = row?.querySelector('[name="url"]')?.value.trim() || '';
    if (!company) return toast('请先填写公司名称', { type: 'error' });
    if (url && !/^https?:\/\//i.test(url)) return toast('官网链接需以 http:// 或 https:// 开头', { type: 'error' });
    window.open(url || `https://www.bing.com/search?q=${encodeURIComponent(`${company} 校园招聘`)}`, '_blank', 'noopener,noreferrer');
  };

  async function openOfficialUrlEditor(applicationId) {
    await companyLinksReady;
    const application = appById(applicationId);
    if (!application) return;
    openModal('编辑公司官网', `<form id="officialUrlForm" class="form-grid">
      <div class="field full">
        <label>${esc(application.company)}的官网链接</label>
        <input name="officialUrl" type="url" value="${esc(customUrl(application))}" placeholder="https://careers.example.com" autocomplete="url">
        <small class="official-url-help">保存后，同一公司的其他投递也会直接打开这个网址；留空则恢复浏览器搜索。</small>
      </div>
      <div class="form-actions">
        <button type="button" class="ghost" onclick="openDetail('${application.id}')">取消</button>
        <button class="primary">保存链接</button>
      </div>
    </form>`);
    document.querySelector('#officialUrlForm').onsubmit = async event => {
      event.preventDefault();
      const value = String(new FormData(event.target).get('officialUrl') || '').trim();
      if (value && !/^https?:\/\//i.test(value)) {
        toast('请输入以 http:// 或 https:// 开头的完整链接', { type: 'error' });
        return;
      }
      const button = event.target.querySelector('.primary');button.disabled = true;button.textContent = '正在保存…';
      try {
        await persistCompanyLinks(replaceCompanyLink(application.company, value));
        state.applications.filter(item => normalizeCompanyName(item.company) === normalizeCompanyName(application.company)).forEach(item => delete item.officialUrl);
        save();openDetail(application.id);
        toast(value ? '公司官网链接已保存并自动复用' : '已恢复为搜索公司官网');
      } catch (error) {
        button.disabled = false;button.textContent = '保存链接';toast(error.message, { type: 'error', duration: 5000 });
      }
    };
  }

  window.openOfficialUrlEditor = openOfficialUrlEditor;
  const originalOpenDetail = openDetail;
  openDetail = function (applicationId) {
    originalOpenDetail(applicationId);
    const application = appById(applicationId);
    if (!application) return;
    const hasCustomUrl = Boolean(customUrl(application));
    if (application.channel !== '官网' && !hasCustomUrl) return;
    const companyTitle = document.querySelector('#modalBody .detail-head h3');
    if (companyTitle) {
      const indicator = hasCustomUrl
        ? '<span class="official-destination-indicator is-website" title="当前直接打开公司官网" aria-label="当前直接打开公司官网"><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9"></circle><path d="M3 12h18M12 3a15 15 0 0 1 0 18M12 3a15 15 0 0 0 0 18"></path></svg></span>'
        : '<span class="official-destination-indicator is-search" title="当前使用浏览器搜索" aria-label="当前使用浏览器搜索"><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.5" cy="10.5" r="6.5"></circle><path d="m15.5 15.5 5 5"></path></svg></span>';
      companyTitle.innerHTML = `<span class="official-company-title">${indicator}<a class="official-company-link" href="${esc(destinationUrl(application))}" target="_blank" rel="noopener noreferrer" title="${hasCustomUrl ? '打开公司官网库中的网址' : `使用 Bing 搜索：${esc(application.company)} 校园招聘`}">${esc(application.company)}</a><button type="button" class="official-url-edit" onclick="openOfficialUrlEditor('${application.id}')" title="${hasCustomUrl ? '编辑公司官网链接' : '填写公司官网链接'}" aria-label="${hasCustomUrl ? '编辑公司官网链接' : '填写公司官网链接'}">编辑链接</button></span>`;
    }
  };
  window.openDetail = openDetail;

  const originalRenderApplications = renderApplications;
  renderApplications = function () {
    originalRenderApplications();
    const tools = document.querySelector('.application-tools');
    if (tools && !document.querySelector('.company-links-button')) tools.insertAdjacentHTML('beforeend', '<button type="button" class="secondary company-links-button" onclick="openCompanyLinksManager()" title="管理公司官网链接">官网库</button>');
  };

  companyLinksReady = loadCompanyLinks();
})();