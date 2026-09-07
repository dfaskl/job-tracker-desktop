(function () {
  const themes = [
    ['blue', '海军蓝', '理性清爽'],
    ['purple', '暮紫', '沉稳柔和'],
    ['orange', '暖橙', '温暖醒目'],
    ['green', '森林绿', '自然克制']
  ];

  function applyTheme(name, persist = true) {
    const valid = themes.some(item => item[0] === name) ? name : 'blue';
    document.documentElement.dataset.theme = valid;
    state.settings.theme = valid;
    if (persist) save();
  }

  window.chooseTheme = function (name) {
    applyTheme(name);
    document.querySelectorAll('.theme-option').forEach(button => button.classList.toggle('active', button.dataset.theme === name));
    toast(`已切换为${themes.find(item => item[0] === name)[1]}主题`);
  };

  applyTheme(state.settings.theme || 'blue', false);
  const originalRenderSettings = renderSettings;
  renderSettings = function () {
    originalRenderSettings();
    document.querySelectorAll('.settings-note').forEach(note => {
      note.innerHTML = note.innerHTML.replace('API Key 仅保存在本机浏览器中', 'API Key 保存在本机 data/job-tracker.json 中');
    });
    const current = state.settings.theme || 'blue';
    content.insertAdjacentHTML('afterbegin', `
      <div class="panel theme-panel">
        <h2>整体色调</h2>
        <p>选择后立即应用，岗位状态的语义颜色保持不变。</p>
        <div class="theme-options">
          ${themes.map(([value, title, subtitle]) => `
            <button class="theme-option ${value === current ? 'active' : ''}" data-theme="${value}" onclick="chooseTheme('${value}')">
              <span class="theme-swatch ${value}"></span>
              <span><b>${title}</b><small>${subtitle}</small></span>
            </button>`).join('')}
        </div>
      </div>`);

    const form = document.querySelector('#settingsForm');
    form.onsubmit = event => {
      event.preventDefault();
      state.settings = { ...state.settings, ...Object.fromEntries(new FormData(event.target)) };
      save();
      toast('API 配置已保存');
    };
  };
})();
