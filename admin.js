(function () {
  const accessState = document.querySelector('#accessState');
  const dashboard = document.querySelector('#dashboard');
  const userRows = document.querySelector('#userRows');
  const deleteDialog = document.querySelector('#deleteDialog');
  const actionLabels = { 'disable-user':'停用了账号', 'enable-user':'启用了账号', 'delete-user':'永久删除了账号', 'open-registration':'开放了用户注册', 'close-registration':'关闭了用户注册' };
  let currentUser = null;
  let users = [];
  let deleteTarget = null;
  let noticeTimer = null;

  function escapeHtml(value = '') {
    return String(value).replace(/[&<>"']/g, char => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#039;' })[char]);
  }

  function formatDate(value) {
    if (!value) return '从未';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '未知' : date.toLocaleString('zh-CN', { year:'numeric', month:'2-digit', day:'2-digit', hour:'2-digit', minute:'2-digit' });
  }

  async function api(path, options = {}) {
    const response = await fetch(path, { ...options, headers:{ 'Content-Type':'application/json', ...(options.headers || {}) } });
    const result = await response.json().catch(() => ({}));
    if (response.status === 401) { location.replace('/'); throw Object.assign(new Error('登录已过期'), { status:401 }); }
    if (!response.ok) throw Object.assign(new Error(result.error || '操作失败'), { status:response.status });
    return result;
  }

  function notify(message) {
    const host = document.querySelector('#notice');
    clearTimeout(noticeTimer); host.textContent = message; host.hidden = false;
    noticeTimer = setTimeout(() => { host.hidden = true; }, 3200);
  }

  function renderUsers() {
    document.querySelector('#usersEmpty').hidden = users.length > 0;
    userRows.innerHTML = users.map(user => {
      const protectedAccount = user.isAdmin || user.id === currentUser.id;
      return `<tr>
        <td class="user-cell"><b>${escapeHtml(user.email)}</b><small>ID ${escapeHtml(user.id)}</small></td>
        <td><span class="badge ${user.isAdmin ? 'admin' : ''}">${user.isAdmin ? '管理员' : '普通用户'}</span><span class="badge ${user.disabled ? 'disabled' : 'enabled'}">${user.disabled ? '已停用' : '正常'}</span></td>
        <td class="data-count"><b>${user.applicationCount}</b> 条投递<br><small>${user.eventCount} 项日程</small></td>
        <td><span class="badge ${user.hasApiKey ? 'enabled' : ''}">${user.hasApiKey ? '已配置' : '未配置'}</span></td>
        <td class="date-cell"><small>注册 ${formatDate(user.createdAt)}</small><span>登录 ${formatDate(user.lastLoginAt)}</span></td>
        <td>${protectedAccount ? '<span class="protected">受保护账号</span>' : `<div class="row-actions"><button data-action="toggle" data-id="${user.id}">${user.disabled ? '启用' : '停用'}</button><button class="danger" data-action="delete" data-id="${user.id}">删除</button></div>`}</td>
      </tr>`;
    }).join('');
  }

  function renderAudit(items) {
    const host = document.querySelector('#auditList');
    host.innerHTML = items.length ? items.map(item => `<div class="audit-item"><div><b>${escapeHtml(actionLabels[item.action] || item.action)}</b>　${escapeHtml(item.targetEmail)}</div><span>${formatDate(item.createdAt)}</span></div>`).join('') : '<p class="empty-row">暂无管理操作。</p>';
  }

  function renderOverview(result) {
    currentUser = result.currentUser;
    users = result.users;
    document.querySelector('#adminEmail').textContent = currentUser.email;
    document.querySelector('[data-summary="totalUsers"]').textContent = result.summary.totalUsers;
    document.querySelector('[data-summary-note="enabledUsers"]').textContent = `${result.summary.enabledUsers} 个可用账号`;
    ['totalApplications','activeSessions','configuredApiKeys'].forEach(key => { document.querySelector(`[data-summary="${key}"]`).textContent = result.summary[key]; });
    document.querySelector('#registrationStatus').textContent = result.summary.registrationOpen ? '开放' : '已关闭';
    const registrationToggle = document.querySelector('#registrationToggle');
    registrationToggle.dataset.open = String(result.summary.registrationOpen);
    registrationToggle.textContent = result.summary.registrationOpen ? '关闭注册' : '开启注册';
    registrationToggle.disabled = false;
    document.querySelector('#registrationCodeStatus').textContent = result.summary.registrationCodeEnabled ? '已启用' : '未启用';
    document.querySelector('#adminEmailStatus').textContent = result.summary.adminEmailConfigured ? '已配置' : '未配置';
    renderUsers(); renderAudit(result.audit);
  }

  async function loadOverview(showNotice = false) {
    try {
      const result = await api('/api/admin/overview');
      renderOverview(result); accessState.hidden = true; dashboard.hidden = false;
      if (showNotice) notify('管理数据已刷新');
    } catch (error) {
      if (error.status === 401) return;
      dashboard.hidden = true; accessState.hidden = false;
      accessState.innerHTML = `<div class="danger-mark">!</div><h2>${error.status === 403 ? '无管理员权限' : '管理后台暂时不可用'}</h2><p>${escapeHtml(error.message)}</p><a class="back-link" href="/">返回普通页面</a>`;
    }
  }

  userRows.addEventListener('click', async event => {
    const button = event.target.closest('button[data-action]');
    if (!button) return;
    const user = users.find(item => item.id === button.dataset.id);
    if (!user) return;
    if (button.dataset.action === 'toggle') {
      const nextDisabled = !user.disabled;
      if (!confirm(`${nextDisabled ? '停用' : '启用'}账号 ${user.email}？`)) return;
      button.disabled = true;
      try { await api(`/api/admin/users/${user.id}`, { method:'PATCH', body:JSON.stringify({ disabled:nextDisabled }) }); notify(nextDisabled ? '账号已停用，现有会话已撤销' : '账号已重新启用'); await loadOverview(); }
      catch (error) { notify(error.message); button.disabled = false; }
    } else {
      deleteTarget = user;
      document.querySelector('#deleteTargetEmail').textContent = user.email;
      document.querySelector('#deleteConfirmEmail').value = '';
      document.querySelector('#deleteError').textContent = '';
      deleteDialog.showModal();
    }
  });

  document.querySelector('#deleteForm').addEventListener('submit', async event => {
    event.preventDefault();
    if (!deleteTarget) return;
    const confirmEmail = document.querySelector('#deleteConfirmEmail').value.trim().toLowerCase();
    if (confirmEmail !== deleteTarget.email) { document.querySelector('#deleteError').textContent = '输入的邮箱不匹配'; return; }
    const submit = event.submitter; submit.disabled = true;
    try { await api(`/api/admin/users/${deleteTarget.id}`, { method:'DELETE', body:JSON.stringify({ confirmEmail }) }); deleteDialog.close(); notify('用户及其全部云端数据已永久删除'); deleteTarget = null; await loadOverview(); }
    catch (error) { document.querySelector('#deleteError').textContent = error.message; }
    finally { submit.disabled = false; }
  });

  document.querySelector('#cancelDelete').onclick = () => { deleteDialog.close(); deleteTarget = null; };
  document.querySelector('#refreshButton').onclick = () => loadOverview(true);
  document.querySelector('#registrationToggle').onclick = async event => {
    const button = event.currentTarget;
    const nextEnabled = button.dataset.open !== 'true';
    if (!confirm(nextEnabled ? '确认重新开放新用户注册？' : '确认关闭新用户注册？现有用户仍可正常登录。')) return;
    button.disabled = true;
    try {
      await api('/api/admin/settings/registration', { method:'PATCH', body:JSON.stringify({ enabled:nextEnabled }) });
      notify(nextEnabled ? '新用户注册已开放' : '新用户注册已关闭');
      await loadOverview();
    } catch (error) {
      notify(error.message);
      button.disabled = false;
    }
  };
  document.querySelector('#logoutButton').onclick = async () => { await fetch('/api/auth/logout', { method:'POST', headers:{ 'Content-Type':'application/json' }, body:'{}' }).catch(() => {}); location.replace('/'); };
  loadOverview();
})();
