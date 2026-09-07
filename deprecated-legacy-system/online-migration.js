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

  async function mergeCompanyLinks(imported) {
    const currentResponse = await fetch('/api/company-links', { cache:'no-store' });
    const current = currentResponse.ok ? (await currentResponse.json()).items || [] : [];
    const merged = new Map(current.map(item => [normalizeCompany(item.company), item]));
    imported.forEach(item => merged.set(normalizeCompany(item.company), item));
    await saveCompanyLinks([...merged.values()]);
  }

  async function importOnlineData(input) {
    try {
      const data = await readJsonFile(input);
      const isBusinessBackup = Array.isArray(data?.applications) && Array.isArray(data?.events);
      if (!isBusinessBackup) {
        if (data && typeof data === 'object' && ('applications' in data || 'events' in data)) throw new Error('投递备份格式不完整，必须同时包含 applications 和 events 数组');
        const importedLinks = normalizedLinks(data);
        confirmAction('导入官网库', `将合并导入 ${importedLinks.length} 条公司官网链接；同名公司使用导入文件中的链接。`, async () => {
          try {
            await mergeCompanyLinks(importedLinks);
            toast(`已导入 ${importedLinks.length} 条官网链接`);
            setTimeout(() => location.reload(), 700);
          } catch (error) {
            toast(error.message, { type:'error', duration:5000 });
          }
        });
        return;
      }
      if (Object.prototype.hasOwnProperty.call(data, 'companyLinks') && !Array.isArray(data.companyLinks)) throw new Error('完整备份中的 companyLinks 必须是数组');
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

  function enhanceImportDescription() {
    const input = document.querySelector('input[onchange="importData(this)"]');
    const note = input?.closest('.panel')?.querySelector('.settings-note');
    if (!note) return;
    const text = '支持线上完整备份、旧版 job-tracker.json 和 company-links.json。投递与日程会替换当前数据，单独导入官网库时会合并。';
    if (note.textContent !== text) note.textContent = text;
  }

  const observer = new MutationObserver(enhanceImportDescription);
  observer.observe(document.querySelector('#content'), { childList:true, subtree:true });
  enhanceImportDescription();
})();
