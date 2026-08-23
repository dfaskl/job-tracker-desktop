(function () {
  const pageHelp = {
    home: '首页是每日工作台，集中展示近期日程和需要人工确认的长期无进展岗位；投递数量、阶段和渠道等汇总信息统一放在统计页面。',
    applications: '集中查看所有岗位及完整流程。有未来日程的岗位排在最前，并按最近一场日程从近到远排列；其余有进展岗位按最近一次实际进展排序；只有投递、没有后续环节的岗位靠后；未通过统一放在最末尾。',
    calendar: '管理测评、笔试、面试和 Offer 日程。完成后可标记“完成”；日程开始两小时后仍未处理会进入待跟进，可选择已经完成或已经错过。这里不会发送系统消息。',
    mail: '粘贴招聘通知邮件正文后，使用设置页配置的大模型提取公司、岗位、通知类型、时间和地点。若已有相同公司和岗位，日程会追加到原记录；只有找不到匹配岗位时才新建投递。',
    stats: '查看投递阶段与渠道分布。进行中的岗位分为“有进展”和“仅投递”：前者已有后续日程或进入测评、笔试、面试阶段，后者尚无任何后续安排。“无消息”指最近一次实际进展距今达到7天。',
    settings: '管理整体主题、大模型接口和本地数据。正式数据保存在项目 data 目录的 JSON 文件中，浏览器仅保留回退缓存；建议定期导出独立备份。'
  };
  const sectionHelp = {
    '近期日程': '按时间展示尚未完成的安排，包括笔试、面试、测评和其他事项。点击“完成”后会转入日程页的历史区域。',
    '日程月历': '按日历汇总所有笔试、面试、测评和其他安排。日期右上角数字表示当天待完成数量；点击日期后，会在中央小窗中展开当天日程，可编辑或执行完成、错过、恢复和删除。',
    '最近投递': '展示最近更新的岗位摘要。当前首页已隐藏此区块，完整记录请前往“投递记录”。',
    '人工确认': '仅显示达到10天没有实际进展、且尚未结束的岗位。系统不会自动判定结果，只有点击“标记未通过”才会修改岗位状态。',
    '待完成': '尚未标记完成或错过的所有日程，按安排时间从早到晚排列。',
    '已完成 / 已错过': '保存已经处理过的历史日程；“错过”与“完成”分开记录，不会删除原安排。',
    '阶段分布': '只统计仅投递、测评、笔试、面试、Offer、已结束和无消息。“仅投递”表示没有任何后续日程；达到7天无实际进展的岗位归入“无消息”，不再重复计入原阶段。',
    '渠道分布': '按照岗位记录中的投递平台统计，例如官网、Boss直聘、内推或其他；不统计具体岗位链接。',
    '求职活动日历': '分别汇总每天的投递、笔试和面试数量。投递使用岗位的“投递日期”，笔试和面试使用日程开始日期；待参加和已完成的日程都会计入。',
    '邮件正文': '只需粘贴邮件正文，可粘贴长文本；邮件中的指令不会被当作系统指令执行。',
    '识别结果': '展示模型提取的信息。没有识别出的字段可以手动补充，确认后才会建立投递记录。',
    '整体色调': '切换整个工具的主色调，选择会立即保存，下次打开自动沿用；红、黄等状态语义色不会随主题改变。',
    '大模型 API': '配置 OpenAI 兼容接口地址、模型名称和 API Key，用于邮件正文识别。API Key 以普通文本保存在本机 data/job-tracker.json，不会进入 Git。',
    '数据管理': '正式数据保存在 data/job-tracker.json。这里可以再导出一份独立 JSON 备份，或从备份恢复；导入恢复会替换当前投递和日程。'
  };
  const statHelp = {
    '全部投递': '当前保存的岗位总数，包括进行中和已经结束的岗位。',
    '进行中的岗位': '排除未通过、已放弃和已结束后，仍在推进的岗位数量。',
    '待完成日程': '尚未标记完成或错过的日程数量。',
    'Offer / 已通过': '当前阶段为 Offer 或当前状态为已通过的岗位数量。',
    '投递总数': '当前保存的全部岗位数量。',
    '进行中 · 有进展': '尚未结束且已经出现后续日程，或已进入测评、笔试、面试等阶段的岗位数量。',
    '进行中 · 仅投递': '尚未结束、没有任何后续日程，且仍停留在准备投递或已投递阶段的岗位数量。',
    '面试阶段': '当前统计分类仍处于面试的岗位数量；达到7天无进展后会转入“无消息”。',
    'Offer': '当前统计分类为 Offer 的岗位数量。',
    '长期无消息': '最近一次实际进展距今达到7天的岗位数量。'
  };

  const tooltip = document.createElement('div');
  tooltip.className = 'floating-help';
  tooltip.setAttribute('role', 'tooltip');
  document.body.appendChild(tooltip);

  function showHelp(icon) {
    tooltip.textContent = icon.dataset.help;
    tooltip.classList.add('visible');
    const rect = icon.getBoundingClientRect();
    const width = Math.min(340, window.innerWidth - 28);
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
    if (!element || !help || element.querySelector('.help-icon')) return;
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
