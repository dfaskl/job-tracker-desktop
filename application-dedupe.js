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
  function matchScore(application, company, position) {
    const appCompany=normalizeCompany(application.company),appPosition=normalizeText(application.position);
    const targetCompany=normalizeCompany(company),targetPosition=normalizeText(position);
    let score=0;
    if(appCompany&&targetCompany){
      if(appCompany===targetCompany)score+=70;
      else if(appCompany.includes(targetCompany)||targetCompany.includes(appCompany))score+=35;
    }
    if(appPosition&&targetPosition){
      if(appPosition===targetPosition)score+=50;
      else if(appPosition.includes(targetPosition)||targetPosition.includes(appPosition))score+=24;
    }
    return score;
  }
  function existingApplicationOptions(company, position) {
    const exact=findExisting(company,position);
    const items=state.applications.slice().sort((a,b)=>matchScore(b,company,position)-matchScore(a,company,position)||String(b.updatedAt||'').localeCompare(String(a.updatedAt||'')));
    return `<option value="__new__" ${exact?'':'selected'}>未关联现有岗位（新建投递）</option>${items.map(application=>{const score=matchScore(application,company,position),recommended=score>0?'〔可能匹配〕 ':'';return `<option value="${application.id}" ${exact?.id===application.id?'selected':''}>${recommended}${esc(application.company)} · ${esc(application.position)}</option>`;}).join('')}`;
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
        confirmAction('检测到重复投递',`已经存在“${existing.company} · ${existing.position}”，不会创建重复记录。是否直接为该岗位追加日程？`,()=>{
          closeModal();
          openEventForm(existing.id);
        });
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

  mailResultHtml = function () {
    const result=mailResult;
    return `<div class="field"><label>公司</label><input id="mrCompany" value="${esc(result.company)}"></div><div class="field"><label>岗位</label><input id="mrPosition" value="${esc(result.position)}"></div><div class="field full"><label>关联现有投递</label><select id="mrExistingApp">${existingApplicationOptions(result.company,result.position)}</select><small class="mail-match-hint">已按公司和岗位自动检索。没有准确匹配时，可在这里手动选择已有岗位，或保留“新建投递”。</small></div><div class="field"><label>通知类型</label><select id="mrType">${options(TYPES,result.noticeType)}</select></div><div class="field"><label>时间</label><input id="mrStarts" value="${esc(result.startsAt)}" placeholder="YYYY-MM-DD HH:mm"></div><div class="field"><label>面试地点 / 视频链接</label><input id="mrLocation" value="${esc(result.location)}" placeholder="视频会议链接或线下面试地址"></div><div class="field"><label>备注</label><textarea id="mrSummary" placeholder="如有需要，可在这里手动填写"></textarea></div><div class="form-actions"><button class="primary" onclick="useMailResult()">使用识别结果</button></div>`;
  };

  useMailResult = function () {
    const company = document.querySelector('#mrCompany')?.value.trim() || '';
    const position = document.querySelector('#mrPosition')?.value.trim() || '';
    const type = document.querySelector('#mrType')?.value || mailResult?.noticeType || '其他';
    const startsAt = document.querySelector('#mrStarts')?.value.trim() || '';
    const location = document.querySelector('#mrLocation')?.value.trim() || '';
    const summary = document.querySelector('#mrSummary')?.value.trim() || '';
    if (!company || !position) { toast('请先补充公司名称和岗位名称'); return; }
    const schedule = { type, title: type, startsAt, location, summary, status: mailResult?.suggestedStatus || '进行中' };
    const selectedApplicationId=document.querySelector('#mrExistingApp')?.value||'';
    const existing = selectedApplicationId&&selectedApplicationId!=='__new__'?appById(selectedApplicationId):findExisting(company, position);
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
