(function () {
  const pageHelp = {
    home: `查看近期日程和达到${PROGRESS_STALE_DAYS}天无进展的待确认岗位。`,
    applications: '未来日程优先，其次按最近进展排序；仅投递和未通过排在后面。',
    calendar: '按月管理日程；开始2小时后未处理会提示完成或错过。不会发送系统通知。',
    mail: '粘贴邮件正文，由大模型提取岗位和日程；确认后再保存。',
    stats: '查看岗位概况、累计转化、阶段分布和渠道分布。',
    settings: '设置主题和大模型接口，并导入或导出本地数据。'
  };
  const sectionHelp = {
    '近期日程': '按时间展示尚未完成的安排。',
    '日程月历': '点击日期查看当天日程及可用操作；角标为待完成数量。',
    '人工确认': `展示达到${PROGRESS_STALE_DAYS}天无进展且未结束的岗位，由你决定是否标记未通过。`,
    '阶段分布': `按当前分类统计；达到${PROGRESS_STALE_DAYS}天无进展的岗位归入“无消息”。`,
    '渠道分布': '按官网、Boss直聘、内推等投递渠道统计。',
    '求职转化漏斗': '累计统计至少到达各阶段的岗位，并计算相对上一阶段的比例。',
    '邮件正文': '粘贴完整通知正文，支持长文本。',
    '识别结果': '可修改识别内容，并选择关联已有岗位或新建投递。',
    '整体色调': '切换工具主色，状态语义色保持不变。',
    '大模型 API': '配置 OpenAI 兼容接口。API Key 单独保存在本机私有配置文件。',
    '数据管理': '数据位于 data/job-tracker.json；导入会替换当前投递和日程。',
    '求职进度本': '查看本地数据位置和项目更新地址。'
  };
  const statHelp = {
    '投递总数': '当前保存的全部岗位。',
    '进行中 · 有进展': '未结束且已有日程或进入后续阶段的岗位。',
    '进行中 · 仅投递': '未结束、没有后续日程且仍处于投递阶段的岗位。',
    'Offer': '当前分类为 Offer 的岗位。',
    '长期无消息': `最近一次实际进展距今达到${PROGRESS_STALE_DAYS}天的岗位。`
  };

  const tooltip = document.createElement('div');
  tooltip.className = 'floating-help';
  tooltip.setAttribute('role', 'tooltip');
  document.body.appendChild(tooltip);

  function showHelp(icon) {
    tooltip.textContent = icon.dataset.help;
    tooltip.classList.add('visible');
    const rect = icon.getBoundingClientRect();
    const width = Math.min(300, window.innerWidth - 28);
    let left = rect.left + rect.width / 2 - width / 2;
    left = Math.max(14, Math.min(left, window.innerWidth - width - 14));
    tooltip.style.width = `${width}px`;
    tooltip.style.left = `${left}px`;
    tooltip.style.top = `${rect.bottom + 9}px`;
    requestAnimationFrame(() => {
      const tipRect = tooltip.getBoundingClientRect();
      if (tipRect.bottom > window.innerHeight - 10) tooltip.style.top = `${Math.max(10, rect.top - tipRect.height - 9)}px`;
    });
  }
  function hideHelp() { tooltip.classList.remove('visible'); }
  function icon(help) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'help-icon';
    button.textContent = '!';
    button.dataset.help = help;
    button.setAttribute('aria-label', '查看板块说明');
    button.addEventListener('mouseenter', () => showHelp(button));
    button.addEventListener('mouseleave', hideHelp);
    button.addEventListener('focus', () => showHelp(button));
    button.addEventListener('blur', hideHelp);
    return button;
  }
  function addToTitle(element, help) {
    if (!element || !help) return;
    const existing=element.querySelector('.help-icon');if(existing){existing.dataset.help=help;return;}
    element.classList.add('title-with-help');
    element.appendChild(icon(help));
  }
  function decorate() {
    addToTitle(document.querySelector('header h1'), pageHelp[page]);
    document.querySelectorAll('.panel h2').forEach(title => {
      const label = Array.from(title.childNodes).filter(node => node.nodeType === Node.TEXT_NODE).map(node => node.textContent).join('').trim();
      addToTitle(title, sectionHelp[label]);
    });
    document.querySelectorAll('.stat-card').forEach(card => {
      const label = card.querySelector('small')?.textContent.trim();
      if (statHelp[label] && !card.querySelector('.help-icon')) card.appendChild(icon(statHelp[label]));
    });
  }

  const originalRender = render;
  render = function () { originalRender(); requestAnimationFrame(decorate); };
  requestAnimationFrame(decorate);
})();
