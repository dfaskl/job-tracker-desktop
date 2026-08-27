(function () {
  const MAX_IMPORT_BYTES = 6 * 1024 * 1024;

  async function readJsonFile(input) {
    const file = input.files?.[0];
    if (!file) return null;
    if (file.size > MAX_IMPORT_BYTES) throw new Error('文件过大，最大支持 6 MB');
    try { return JSON.parse(await file.text()); }
    catch { throw new Error('JSON 文件格式不正确'); }
  }

  function normalizeCompany(value) {
    return String(value || '').trim().replace(/\s+/g, ' ').toLocaleLowerCase('zh-CN');
  }

  function normalizedLinks(data) {
    const source = Array.isArray(data) ? data : data?.items;
    if (!Array.isArray(source)) throw new Error('官网库格式不正确，请选择 company-links.json');
    return source.map(item => ({ company:String(item?.company || '').trim(), url:String(item?.url || '').trim() })).filter(item => item.company);
  }

  async function saveCompanyLinks(items) {
    const response = await fetch('/api/company-links', { method:'POST', headers:{ 'Content-Type':'application/json' }, body:JSON.stringify({ items }) });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(result.error || '官网库保存失败');
    return result.items || items;
  }

  async function saveImportedBusinessData() {
    const businessData = {
      applications:state.applications,
      events:state.events
    };
    const cached = { ...businessData, settings:{ ...state.settings } };
    delete cached.settings.apiKey;
    localStorage.setItem(KEY, JSON.stringify(cached));
    const response = await fetch('/api/data', {
      method:'POST',
      headers:{ 'Content-Type':'application/json' },
      body:JSON.stringify(businessData)
    });
    if (!response.ok) {
      const result = await response.json().catch(() => ({}));
      throw new Error(result.error || '业务数据保存失败');
    }
  }

  window.importLegacyCompanyLinks = async function (input) {
    try {
      const imported = normalizedLinks(await readJsonFile(input));
      if (!confirm(`将导入 ${imported.length} 条公司官网链接；同名公司将使用导入文件中的链接。是否继续？`)) return;
      const currentResponse = await fetch('/api/company-links', { cache:'no-store' });
      const current = currentResponse.ok ? (await currentResponse.json()).items || [] : [];
      const merged = new Map(current.map(item => [normalizeCompany(item.company), item]));
      imported.forEach(item => merged.set(normalizeCompany(item.company), item));
      await saveCompanyLinks([...merged.values()]);
      toast(`已导入 ${imported.length} 条官网链接`);
      setTimeout(() => location.reload(), 700);
    } catch (error) {
      toast(error.message, { type:'error', duration:5000 });
    } finally { input.value = ''; }
  };

  async function importOnlineData(input) {
    try {
      const data = await readJsonFile(input);
      if (!Array.isArray(data?.applications) || !Array.isArray(data?.events)) throw new Error('投递备份格式不正确，请选择 job-tracker.json 或线上完整备份');
      const companyLinks = Array.isArray(data.companyLinks) ? normalizedLinks({ items:data.companyLinks }) : null;
      const message = '将用备份中的 ' + data.applications.length + ' 条投递和 ' + data.events.length + ' 项日程替换当前数据' + (companyLinks ? '，并恢复 ' + companyLinks.length + ' 条官网链接' : '') + '。';
      confirmAction('导入备份', message, async () => {
        try {
          if (companyLinks) await saveCompanyLinks(companyLinks);
          state = {
            ...initial(),
            applications:data.applications,
            events:data.events,
            settings:{ ...state.settings }
          };
          normalizeApplicationRecords(state);
          await saveImportedBusinessData(); render();
          toast(companyLinks ? '投递、日程和官网库已恢复' : '投递与日程已恢复');
          if (companyLinks) setTimeout(() => location.reload(), 700);
        } catch (error) {
          toast(error.message, { type:'error', duration:5000 });
        }
      });
    } catch (error) {
      toast(error.message, { type:'error', duration:5000 });
    } finally { input.value = ''; }
  }
  importData = importOnlineData;
  window.importData = importOnlineData;

  async function exportOnlineBackup() {
    const clean = {
      backupVersion:2,
      applications:JSON.parse(JSON.stringify(state.applications)),
      events:JSON.parse(JSON.stringify(state.events)),
      companyLinks:[]
    };
    try {
      const response = await fetch('/api/company-links', { cache:'no-store' });
      if (response.ok) clean.companyLinks = (await response.json()).items || [];
    } catch {}
    const blob = new Blob([JSON.stringify(clean, null, 2)], { type:'application/json' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `求职进度本完整备份_${today()}.json`;
    link.click(); URL.revokeObjectURL(link.href);
    toast('完整备份已导出（不包含 API Key）');
  }
  exportData = exportOnlineBackup;
  window.exportData = exportOnlineBackup;

  function enhanceMigrationPanel() {
    const form = document.querySelector('#settingsForm');
    if (!form || document.querySelector('.legacy-migration-panel')) return;
    const host = document.querySelector('.settings-right-column') || form.closest('.grid') || document.querySelector('#content');
    if (!host) return;
    host.insertAdjacentHTML('beforeend', `<div class="panel settings-section legacy-migration-panel">
      <h2>旧版数据迁移</h2>
      <p>旧版的两类业务数据可分别导入。以后使用上方“导出数据”，会把它们合并保存到一个完整备份文件中。</p>
      <div class="legacy-migration-list">
        <label><span><b>投递与日程</b><small>job-tracker.json</small></span><em>选择文件</em><input type="file" accept=".json,application/json" hidden onchange="importData(this)"></label>
        <label><span><b>公司官网库</b><small>company-links.json · 合并导入</small></span><em>选择文件</em><input type="file" accept=".json,application/json" hidden onchange="importLegacyCompanyLinks(this)"></label>
      </div>
      <small class="legacy-security-note">API 地址、模型和 API Key 只能在设置表单中手动填写，不会进入任何导入或导出文件。</small>
    </div>`);
  }

  const observer = new MutationObserver(enhanceMigrationPanel);
  observer.observe(document.querySelector('#content'), { childList:true, subtree:true });
  enhanceMigrationPanel();
})();
