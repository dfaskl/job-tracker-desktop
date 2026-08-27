(function () {
  window.isOnlineMode = true;
  const nativeFetch = window.fetch.bind(window);
  const nativeGetItem = Storage.prototype.getItem;
  const nativeSetItem = Storage.prototype.setItem;
  const nativeRemoveItem = Storage.prototype.removeItem;
  const scopedKeys = new Set(['job_tracker_desktop_v1', 'job_tracker_daily_quote_v1']);
  let resolveAuth;
  let authResolved = false;
  window.authReady = new Promise(resolve => { resolveAuth = resolve; });

  function scopedKey(key) {
    if (!scopedKeys.has(String(key))) return key;
    return `${key}__user_${window.currentUser?.id || 'locked'}`;
  }
  Storage.prototype.getItem = function (key) { return nativeGetItem.call(this, scopedKey(key)); };
  Storage.prototype.setItem = function (key, value) { return nativeSetItem.call(this, scopedKey(key), value); };
  Storage.prototype.removeItem = function (key) { return nativeRemoveItem.call(this, scopedKey(key)); };

  window.fetch = async function (input, init) {
    const url = typeof input === 'string' ? input : input?.url || '';
    if (url.startsWith('/api/') && !url.startsWith('/api/auth/') && url !== '/api/healthz') await window.authReady;
    if (url === '/api/data' && init?.method === 'POST' && typeof init.body === 'string') {
      try { window.onlineApiSettings = JSON.parse(init.body).settings || window.onlineApiSettings; } catch {}
    }
    if (url === '/api/config' && init?.method === 'POST' && typeof init.body === 'string' && window.onlineApiSettings) {
      try {
        const payload = JSON.parse(init.body);
        init = { ...init, body:JSON.stringify({ apiUrl:window.onlineApiSettings.apiUrl, model:window.onlineApiSettings.model, ...payload }) };
      } catch {}
    }
    const response = await nativeFetch(input, init);
    if (response.status === 401 && authResolved && url.startsWith('/api/') && !url.startsWith('/api/auth/')) {
      nativeRemoveItem.call(localStorage, scopedKey('job_tracker_desktop_v1'));
      location.reload();
    }
    return response;
  };

  function completeAuth(user) {
    window.currentUser = user;
    authResolved = true;
    document.querySelector('#authGate')?.remove();
    renderAccount(user);
    resolveAuth(user);
  }

  function renderGate(mode = 'login', message = '') {
    let gate = document.querySelector('#authGate');
    if (!gate) {
      gate = document.createElement('div');
      gate.id = 'authGate';
      gate.className = 'auth-gate';
      document.body.appendChild(gate);
    }
    const registering = mode === 'register';
    gate.innerHTML = `<div class="auth-card">
      <div class="auth-brand"><span>✓</span><div><h1>求职进度本</h1><p>安全同步每一次求职进展</p></div></div>
      <div class="auth-tabs"><button class="${registering ? '' : 'active'}" data-mode="login">登录</button><button class="${registering ? 'active' : ''}" data-mode="register">注册</button></div>
      <form id="authForm">
        <label>邮箱<input type="email" name="email" autocomplete="email" required maxlength="254" placeholder="name@example.com"></label>
        <label>密码<input type="password" name="password" autocomplete="${registering ? 'new-password' : 'current-password'}" required minlength="10" maxlength="128" placeholder="至少 10 位"></label>
        ${registering ? '<label>邀请码（如管理员已设置）<input type="text" name="registrationCode" autocomplete="off" maxlength="100" placeholder="未设置时可留空"></label>' : ''}
        <p class="auth-message ${message ? 'visible' : ''}" role="alert">${escapeHtml(message)}</p>
        <button class="primary auth-submit">${registering ? '创建账号' : '登录'}</button>
      </form>
      <small class="auth-security">数据按账号隔离；API Key 仅以加密形式保存在服务器。</small>
    </div>`;
    gate.querySelectorAll('[data-mode]').forEach(button => button.onclick = () => renderGate(button.dataset.mode));
    gate.querySelector('#authForm').onsubmit = async event => {
      event.preventDefault();
      const button = gate.querySelector('.auth-submit');
      const payload = Object.fromEntries(new FormData(event.target));
      button.disabled = true; button.textContent = registering ? '正在创建…' : '正在登录…';
      try {
        const response = await nativeFetch(`/api/auth/${registering ? 'register' : 'login'}`, { method:'POST', headers:{ 'Content-Type':'application/json' }, body:JSON.stringify(payload) });
        const result = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(result.error || '操作失败');
        completeAuth(result.user);
      } catch (error) { renderGate(mode, error.message); }
    };
  }

  function renderAccount(user) {
    const sidebar = document.querySelector('.sidebar');
    if (!sidebar || sidebar.querySelector('.account-panel')) return;
    const panel = document.createElement('div');
    panel.className = 'account-panel';
    panel.innerHTML = `<div><span>${escapeHtml(user.email.slice(0, 1).toUpperCase())}</span><p><b>${escapeHtml(user.email)}</b><small>云端数据已同步</small></p></div><button type="button" title="退出当前账号">退出</button>`;
    panel.querySelector('button').onclick = async () => {
      panel.querySelector('button').disabled = true;
      await nativeFetch('/api/auth/logout', { method:'POST', headers:{ 'Content-Type':'application/json' }, body:'{}' }).catch(() => {});
      nativeRemoveItem.call(localStorage, scopedKey('job_tracker_desktop_v1'));
      nativeRemoveItem.call(localStorage, scopedKey('job_tracker_daily_quote_v1'));
      location.reload();
    };
    sidebar.appendChild(panel);
  }

  function escapeHtml(value = '') {
    return String(value).replace(/[&<>"']/g, char => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#039;' })[char]);
  }

  const observer = new MutationObserver(() => {
    document.querySelectorAll('.settings-note').forEach(note => {
      note.innerHTML = note.innerHTML
        .replace(/API Key[^。]*。/, 'API Key 使用 AES-256-GCM 加密后保存，页面不会再次显示完整密钥。')
        .replace('本地数据', '账号数据');
    });
    document.querySelectorAll('.about-settings-section dd').forEach(item => {
      if (item.textContent.includes('data/job-tracker.json')) item.textContent = '云端 PostgreSQL（按账号隔离）';
      if (item.textContent.includes('data/local-config.json')) item.textContent = '服务端加密存储';
    });
    document.querySelectorAll('.about-brand p').forEach(item => { item.textContent = '私密、专注、可同步的求职管理工具'; });
    const apiKey = document.querySelector('#settingsForm input[name="apiKey"]');
    if (apiKey && !apiKey.dataset.onlineHint) {
      apiKey.dataset.onlineHint = 'true';
      if (apiKey.value === '••••••••') {
        apiKey.value = '••••••••';
        apiKey.placeholder = '已加密保存；输入新值可替换';
      } else apiKey.placeholder = '输入后将加密保存';
    }
  });
  observer.observe(document.documentElement, { childList:true, subtree:true });

  nativeFetch('/api/auth/session', { cache:'no-store' }).then(async response => {
    if (!response.ok) throw new Error('not-authenticated');
    const result = await response.json(); completeAuth(result.user);
  }).catch(() => renderGate('login'));
})();
