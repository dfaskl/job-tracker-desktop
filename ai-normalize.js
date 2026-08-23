(function () {
  const originalOpenApplicationForm = openApplicationForm;

  function formValues(form) { return Object.fromEntries(new FormData(form)); }
  function setField(form, name, value) {
    const field = form.elements.namedItem(name);
    if (!field || value === undefined || value === null) return;
    if (field.tagName === 'SELECT' && !Array.from(field.options).some(option => option.value === value)) return;
    field.value = value;
  }
  function renderReview(form, original, result) {
    form.querySelector('.ai-review-panel')?.remove();
    const labels = { company:'公司名称', position:'岗位名称', city:'工作地点', channel:'投递渠道', stage:'当前阶段', status:'当前状态', notes:'备注' };
    const fields = Object.keys(labels).filter(name => String(original[name] ?? '') !== String(result[name] ?? ''));
    const panel = document.createElement('div');
    panel.className = 'ai-review-panel full';
    panel.innerHTML = `
      <div class="ai-review-head">
        <div><b>AI 校正建议</b><span>勾选你想采用的字段，其余内容保持不变</span></div>
        <button type="button" class="icon-btn ai-review-close" aria-label="关闭建议">×</button>
      </div>
      ${fields.length ? `
        <div class="ai-review-columns"><span></span><b>字段</b><b>当前填写</b><b>AI 建议</b></div>
        <div class="ai-review-list">${fields.map(name => `
          <label class="ai-review-row">
            <input type="checkbox" data-field="${name}" checked>
            <b>${labels[name]}</b>
            <span class="ai-old-value">${esc(original[name] || '（空）')}</span>
            <span class="ai-new-value">${esc(result[name] || '（空）')}</span>
          </label>`).join('')}</div>` : '<div class="ai-no-change">没有发现需要修改的字段。</div>'}
      ${result.changes.length ? `<div class="ai-review-summary"><b>修改说明</b>${result.changes.map(item => `<span>• ${esc(item)}</span>`).join('')}</div>` : ''}
      ${result.warnings.length ? `<div class="ai-review-warnings"><b>需要你核对</b>${result.warnings.map(item => `<span>• ${esc(item)}</span>`).join('')}</div>` : ''}
      <div class="ai-review-actions">
        <button type="button" class="ghost ai-review-cancel">暂不采用</button>
        ${fields.length ? '<button type="button" class="primary ai-review-apply">应用所选修改</button>' : ''}
      </div>`;
    form.querySelector('.form-actions').before(panel);
    const close = () => panel.remove();
    panel.querySelector('.ai-review-close').onclick = close;
    panel.querySelector('.ai-review-cancel').onclick = close;
    panel.querySelector('.ai-review-apply')?.addEventListener('click', () => {
      const selected = Array.from(panel.querySelectorAll('input[data-field]:checked')).map(input => input.dataset.field);
      if (!selected.length) { toast('请至少勾选一项修改'); return; }
      selected.forEach(name => setField(form, name, result[name]));
      close();
      toast(`已应用 ${selected.length} 项 AI 建议，请确认后保存`);
    });
  }
  async function normalize(form, button) {
    const application = formValues(form);
    if (!application.company.trim() || !application.position.trim()) {
      toast('请先填写公司名称和岗位名称');
      return;
    }
    if (!state.settings.apiUrl || !state.settings.model) {
      toast('请先前往“设置 → 大模型 API”配置接口地址和模型名称',{type:'warning',duration:5000});
      return;
    }
    button.disabled = true;
    const originalText = button.textContent;
    button.textContent = '正在校正…';
    try {
      const response = await fetch('/api/normalize-application', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...state.settings, application })
      });
      const result = await response.json();
      if (!response.ok) throw new Error(result.error || 'AI 校正失败');
      renderReview(form, application, result);
      toast('AI 校正完成，可逐项选择修改');
    } catch (error) {
      toast(`AI 校正失败：${error.message}`,{type:'error',duration:5000});
    } finally {
      button.disabled = false;
      button.textContent = originalText;
    }
  }

  openApplicationForm = function (...args) {
    originalOpenApplicationForm(...args);
    const form = document.querySelector('#appForm');
    const actions = form?.querySelector('.form-actions');
    if (!form || !actions || actions.querySelector('.ai-normalize-button')) return;
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'ai-normalize-button';
    button.textContent = '✦ AI 校正';
    button.title = '检查公司、岗位、地点、渠道及阶段状态是否规范';
    button.addEventListener('click', () => normalize(form, button));
    actions.prepend(button);
  };
  window.openApplicationForm = openApplicationForm;
})();
