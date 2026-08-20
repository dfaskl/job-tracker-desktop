(function () {
  function normalizeText(value) {
    return String(value || '').trim().toLowerCase().replace(/[\s·•\-—_（）()]/g, '');
  }
  function normalizeCompany(value) {
    return normalizeText(value);
  }
  function findExisting(company, position) {
    const normalizedCompany = normalizeCompany(company);
    const normalizedPosition = normalizeText(position);
    if (!normalizedCompany || !normalizedPosition) return null;
    return state.applications.find(item => normalizeCompany(item.company) === normalizedCompany && normalizeText(item.position) === normalizedPosition) || null;
  }
  function statusForSchedule(type, startsAt, suggestedStatus) {
    if (type === 'Offer') return '已通过';
    const time = new Date(String(startsAt || '').replace(' ', 'T')).getTime();
    if (Number.isFinite(time) && time > Date.now()) return '进行中';
    return suggestedStatus && STATUSES.includes(suggestedStatus) ? suggestedStatus : '等待安排';
  }
  function appendMailEvent(application, schedule) {
    const startsAt = String(schedule.startsAt || '').trim();
    const type = TYPES.includes(schedule.type) ? schedule.type : '其他';
    if (!startsAt) return false;
    const duplicate = state.events.some(event => event.applicationId === application.id && event.startsAt === startsAt && event.type === type);
    if (duplicate) {
      toast('该岗位已有相同时间和类型的日程，未重复添加');
      return true;
    }
    const eventId = id('evt');
    const createdAt = nowText();
    state.events.push({
      id: eventId, applicationId: application.id, company: application.company, position: application.position,
      type, title: schedule.title || type, startsAt, location: schedule.location || '', notes: schedule.summary || '',
      completed: false, missed: false, createdAt
    });
    if (['测评','笔试','面试','Offer'].includes(type)) application.stage = type;
    application.status = statusForSchedule(type, startsAt, schedule.status);
    application.updatedAt = createdAt;
    application.timeline = Array.isArray(application.timeline) ? application.timeline : [];
    application.timeline.unshift({ id: id('tl'), eventId, at: createdAt, title: `邮件识别追加${type}安排：${schedule.title || type}` });
    save();
    return true;
  }

  let pendingMailSchedule = null;
  const originalOpenApplicationForm = openApplicationForm;
  openApplicationForm = function (...args) {
    const editingId = args[0] || '';
    originalOpenApplicationForm(...args);
    const form = document.querySelector('#appForm');
    if (!form || editingId) return;
    const originalSubmit = form.onsubmit;
    form.onsubmit = event => {
      event.preventDefault();
      const values = Object.fromEntries(new FormData(form));
      const existing = findExisting(values.company, values.position);
      if (existing) {
        pendingMailSchedule = null;
        if (confirm(`检测到已有“${existing.company} · ${existing.position}”。\n\n不会新建重复投递，是否直接为该岗位追加日程？`)) {
          closeModal();
          openEventForm(existing.id);
        }
        return;
      }
      const beforeIds = new Set(state.applications.map(item => item.id));
      originalSubmit.call(form, event);
      const created = state.applications.find(item => !beforeIds.has(item.id));
      if (created && pendingMailSchedule) {
        appendMailEvent(created, pendingMailSchedule);
        pendingMailSchedule = null;
        render();
        toast('投递及识别出的日程已保存');
      }
    };
  };
  window.openApplicationForm = openApplicationForm;

  useMailResult = function () {
    const company = document.querySelector('#mrCompany')?.value.trim() || '';
    const position = document.querySelector('#mrPosition')?.value.trim() || '';
    const type = document.querySelector('#mrType')?.value || mailResult?.noticeType || '其他';
    const startsAt = document.querySelector('#mrStarts')?.value.trim() || '';
    const location = document.querySelector('#mrLocation')?.value.trim() || '';
    const summary = document.querySelector('#mrSummary')?.value.trim() || '';
    if (!company || !position) { toast('请先补充公司名称和岗位名称'); return; }
    const schedule = { type, title: type, startsAt, location, summary, status: mailResult?.suggestedStatus || '进行中' };
    const existing = findExisting(company, position);
    if (existing) {
      if (startsAt) {
        appendMailEvent(existing, schedule);
        navigate('calendar');
        toast(`已追加到“${existing.company} · ${existing.position}”`);
      } else {
        toast('已找到原岗位，请补充本次日程信息');
        openEventForm(existing.id);
      }
      return;
    }
    pendingMailSchedule = startsAt ? schedule : null;
    openApplicationForm('', {
      company, position, stage: mailResult?.suggestedStage || '已投递',
      status: mailResult?.suggestedStatus || '进行中', notes: summary
    });
  };
  window.useMailResult = useMailResult;
})();
