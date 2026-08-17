(function () {
  let scheduleMonth = new Date();
  scheduleMonth = new Date(scheduleMonth.getFullYear(), scheduleMonth.getMonth(), 1);
  let selectedDate = `${new Date().getFullYear()}-${String(new Date().getMonth()+1).padStart(2,'0')}-${String(new Date().getDate()).padStart(2,'0')}`;

  function key(year, month, day) { return `${year}-${String(month+1).padStart(2,'0')}-${String(day).padStart(2,'0')}`; }
  function eventsOn(date) { return state.events.filter(event => String(event.startsAt||'').slice(0,10)===date).sort((a,b)=>a.startsAt.localeCompare(b.startsAt)); }
  function statusText(event) { return event.missed?'已错过':event.completed?'已完成':'待完成'; }
  function detailRow(event) {
    const time=String(event.startsAt||'').slice(11,16)||'时间未填';
    const link=/^https?:\/\//i.test(event.location||'')?`<a href="${esc(event.location)}" target="_blank" rel="noreferrer" onclick="event.stopPropagation()">打开链接 ↗</a>`:esc(event.location||'地点未填');
    return `<div class="schedule-detail-row ${event.completed?'completed':''} ${event.missed?'missed':''}">
      <div class="schedule-detail-time">${time}</div>
      <div class="schedule-detail-main"><div><b>${esc(event.company)} · ${esc(event.title||event.type)}</b>${badge(event.type)}</div><span>${esc(event.position)} · ${link}</span>${event.notes?`<small class="schedule-note"><b>备注</b>${esc(event.notes)}</small>`:''}</div>
      <span class="schedule-result ${event.missed?'is-missed':event.completed?'is-completed':''}">${statusText(event)}</span>
      <div class="schedule-row-actions"><button class="secondary" onclick="editSchedule('${event.id}')">编辑</button>${event.completed?`<button class="ghost" onclick="restoreSchedule('${event.id}')">恢复</button>`:`<button class="success" onclick="scheduleComplete('${event.id}')">完成</button><button class="danger" onclick="scheduleMiss('${event.id}')">错过</button>`}<button class="ghost delete-schedule" onclick="deleteSchedule('${event.id}')">删除</button></div>
    </div>`;
  }
  function renderScheduleCalendar(){
    const year=scheduleMonth.getFullYear(),month=scheduleMonth.getMonth();
    const offset=(new Date(year,month,1).getDay()+6)%7,days=new Date(year,month+1,0).getDate();
    const today=key(new Date().getFullYear(),new Date().getMonth(),new Date().getDate());
    const cells=[];
    for(let index=0;index<42;index++){
      const day=index-offset+1;
      if(day<1||day>days){cells.push('<div class="schedule-day outside"></div>');continue;}
      const date=key(year,month,day),items=eventsOn(date),pending=items.filter(item=>!item.completed).length;
      cells.push(`<button class="schedule-day ${items.length?'has-events':''} ${date===today?'today':''} ${date===selectedDate?'selected':''}" onclick="selectScheduleDay('${date}',this)"><span class="schedule-day-number">${day}</span><div class="schedule-day-events">${items.slice(0,3).map(item=>`<i class="event-chip ${item.completed?'done':''} ${item.missed?'missed':''}"><em>${String(item.startsAt).slice(11,16)}</em>${esc(item.company)} · ${esc(item.title||item.type)}</i>`).join('')}${items.length>3?'<small class="more-ellipsis">•••</small>':''}</div>${pending?`<b class="pending-count">${pending}</b>`:''}</button>`);
    }
    content.innerHTML=`<div class="panel schedule-calendar-panel"><div class="panel-head"><div><h2>日程月历</h2><p>${year}年${month+1}月 · 共 ${state.events.filter(event=>String(event.startsAt||'').startsWith(`${year}-${String(month+1).padStart(2,'0')}`)).length} 项安排</p></div><div class="schedule-calendar-actions"><div class="calendar-controls"><button class="ghost" onclick="changeScheduleMonth(-1)">‹</button><button class="secondary" onclick="resetScheduleMonth()">本月</button><button class="ghost" onclick="changeScheduleMonth(1)">›</button></div><button class="primary" onclick="openEventForm()">＋ 新增日程</button></div></div><div class="delivery-weekdays">${['周一','周二','周三','周四','周五','周六','周日'].map(day=>`<b>${day}</b>`).join('')}</div><div class="schedule-calendar-grid">${cells.join('')}</div></div>`;
  }

  renderCalendar=renderScheduleCalendar;
  window.showScheduleDayModal=function(date){selectedDate=date;const items=eventsOn(date),parts=date.split('-').map(Number);openModal(`${parts[1]}月${parts[2]}日安排`, `<div class="schedule-modal-summary">共 ${items.length} 项日程</div><div class="schedule-detail-list">${items.map(detailRow).join('')||'<div class="empty compact-empty">当天没有安排</div>'}</div><div class="schedule-modal-actions"><button class="primary" onclick="closeModal();openEventForm()">＋ 新增日程</button></div>`);};
  window.selectScheduleDay=function(date,button){document.querySelectorAll('.schedule-day.selected').forEach(item=>item.classList.remove('selected'));button?.classList.add('selected');showScheduleDayModal(date);};
  window.changeScheduleMonth=function(offset){scheduleMonth=new Date(scheduleMonth.getFullYear(),scheduleMonth.getMonth()+offset,1);selectedDate=key(scheduleMonth.getFullYear(),scheduleMonth.getMonth(),1);render();};
  window.resetScheduleMonth=function(){const now=new Date();scheduleMonth=new Date(now.getFullYear(),now.getMonth(),1);selectedDate=key(now.getFullYear(),now.getMonth(),now.getDate());render();};
  window.scheduleComplete=function(eventId){const event=state.events.find(item=>item.id===eventId);if(!event)return;event.completed=true;event.missed=false;save();render();showScheduleDayModal(selectedDate);toast('已标记完成');};
  window.scheduleMiss=function(eventId){const event=state.events.find(item=>item.id===eventId);if(!event)return;event.completed=true;event.missed=true;save();render();showScheduleDayModal(selectedDate);toast('已标记错过');};
  window.restoreSchedule=function(eventId){const event=state.events.find(item=>item.id===eventId);if(!event)return;event.completed=false;event.missed=false;save();render();showScheduleDayModal(selectedDate);toast('日程已恢复为待完成');};
  window.editSchedule=function(eventId){const item=state.events.find(event=>event.id===eventId);if(!item)return;openModal('编辑日程',`<form id="scheduleEditForm" class="form-grid"><div class="field"><label>类型</label><select name="type">${options(TYPES,item.type)}</select></div><div class="field"><label>安排名称</label><input name="title" required value="${esc(item.title||'')}"></div><div class="field"><label>开始时间</label><input type="datetime-local" name="startsAt" required value="${esc(String(item.startsAt||'').replace(' ','T'))}"></div><div class="field"><label>地点 / 会议方式</label><input name="location" value="${esc(item.location||'')}"></div><div class="field full"><label>备注</label><textarea name="notes" placeholder="邮件摘要、时长或其他补充信息">${esc(item.notes||'')}</textarea></div><div class="form-actions"><button type="button" class="ghost" onclick="closeModal()">取消</button><button class="primary">保存修改</button></div></form>`);document.querySelector('#scheduleEditForm').onsubmit=event=>{event.preventDefault();const values=Object.fromEntries(new FormData(event.target));Object.assign(item,values,{startsAt:values.startsAt.replace('T',' ')});save();closeModal();render();toast('日程已更新');};};
  window.deleteSchedule=function(eventId){if(!confirm('确定删除这条日程吗？此操作不可恢复。'))return;state.events=state.events.filter(item=>item.id!==eventId);save();render();showScheduleDayModal(selectedDate);toast('日程已删除');};
})();
