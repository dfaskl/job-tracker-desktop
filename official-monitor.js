(function(){
  const END_WORDS=['流程已结束','流程终止','招聘结束','已结束','未通过','不合适','已淘汰','职位关闭','岗位关闭','停止招聘','申请终止'];
  const ACTIVE_WORDS=['已投递','处理中','筛选中','流程中','面试中','测评中','待处理','申请成功'];
  const normalize=value=>String(value||'').toLowerCase().replace(/[\s·•_\-—（）()【】\[\]]+/g,'');
  const inspections=()=>state.officialInspections||(state.officialInspections=[]);
  const receivingCaptureIds=new Set();
  const inspectionById=id=>inspections().find(item=>item.id===id);
  const unresolved=()=>inspections().flatMap(scan=>(scan.findings||[]).filter(item=>!item.resolution).map(item=>({...item,scanId:scan.id,checkedAt:scan.checkedAt})));
  function evidence(text,position){const raw=String(text||''),key=String(position||'').trim(),index=key?raw.toLowerCase().indexOf(key.toLowerCase()):-1;if(index<0)return raw.slice(0,260);return raw.slice(Math.max(0,index-140),Math.min(raw.length,index+key.length+180));}
  function analyzeCapture(capture){
    const body=String(capture.text||'').slice(0,180000),flat=normalize(body),company=String(capture.company||'').trim();
    const loginRequired=body.length<180||(/登录|扫码登录|手机验证|验证码/.test(body.slice(0,1800))&&!/投递|申请记录|应聘记录/.test(body));
    const applications=state.applications.filter(item=>normalize(item.company)===normalize(company)&&!['未通过','已放弃','已结束'].includes(item.status));
    const findings=loginRequired?[]:applications.map(application=>{
      const position=normalize(application.position),found=position&&flat.includes(position),sample=evidence(body,application.position),sampleFlat=normalize(sample);
      const ended=found&&END_WORDS.find(word=>sampleFlat.includes(normalize(word))),active=found&&ACTIVE_WORDS.find(word=>sampleFlat.includes(normalize(word)));
      return {applicationId:application.id,company:application.company,position:application.position,kind:ended?'ended':found?(active?'active':'found'):'missing',message:ended?`页面显示“${ended}”`:found?'已在页面中找到该岗位':'当前页面未找到该岗位，需要人工核对',evidence:sample.slice(0,420)};
    });
    return {id:`scan_${Date.now()}_${Math.random().toString(16).slice(2,8)}`,company,url:String(capture.url||''),title:String(capture.title||''),checkedAt:nowText(),loginRequired,findings};
  }
  function receiveCapture(capture){
    if(!capture?.company||!capture?.text)return;
    const scan=analyzeCapture(capture);inspections().unshift(scan);state.officialInspections=inspections().slice(0,50);save();render();
    if(scan.loginRequired)toast(`${scan.company} 可能需要重新登录，请登录后再次检查`,{type:'warning',duration:6000});
    else toast(`已检查 ${scan.company}：${scan.findings.length} 个岗位，${scan.findings.filter(item=>['ended','missing'].includes(item.kind)).length} 项待确认`,{duration:6000});
  }
  window.addEventListener('job-tracker-official-capture',async event=>{
    const capture=event.detail;
    if(!capture?.id||receivingCaptureIds.has(capture.id))return;
    receivingCaptureIds.add(capture.id);
    if(window.authReady)await window.authReady;
    while(typeof localDataReady!=='undefined'&&!localDataReady)await new Promise(resolve=>setTimeout(resolve,100));
    receiveCapture(capture);window.postMessage({type:'job-tracker-official-capture-ack',id:capture?.id},location.origin);
  });
  window.beginOfficialInspection=function(button){
    const row=button.closest('.company-link-row'),company=row?.querySelector('[name="company"]')?.value.trim(),url=row?.querySelector('[name="url"]')?.value.trim();
    if(!company||!url)return toast('请先保存完整的公司名称和投递记录页链接',{type:'error'});
    let acknowledged=false;const listener=event=>{if(event.data?.type==='job-tracker-start-official-scan-ack'){acknowledged=true;window.removeEventListener('message',listener)}};window.addEventListener('message',listener);
    window.postMessage({type:'job-tracker-start-official-scan',company,url,appUrl:location.origin},location.origin);
    setTimeout(()=>{window.removeEventListener('message',listener);if(!acknowledged)openModal('安装官网巡检扩展',`<div class="official-extension-help"><p>首次使用需要安装浏览器扩展。它只读取你主动检查的当前页面，不读取密码和 Cookie。</p><p><a class="primary official-extension-download" href="/job-tracker-official-inspector.zip" download>下载官网巡检扩展</a></p><ol><li>解压下载的 ZIP 文件。</li><li>在 Chrome 或 Edge 打开“扩展程序”，开启开发者模式。</li><li>选择“加载已解压的扩展程序”，选择刚解压的文件夹。</li><li>安装后刷新求职进度本，再点击“检查”。</li></ol></div>`)},800);
  };
  window.resolveOfficialFinding=function(scanId,applicationId,action){
    const scan=inspectionById(scanId),finding=scan?.findings?.find(item=>item.applicationId===applicationId);if(!finding)return;
    if(action==='confirm'){const application=appById(applicationId);if(application){const at=nowText();application.stage='已结束';application.status='已结束';application.updatedAt=at;application.timeline=application.timeline||[];application.timeline.push({at,title:'官网巡检确认：流程已结束'});}}
    finding.resolution=action;finding.resolvedAt=nowText();save();render();toast(action==='confirm'?'已将岗位标记为已结束':'已忽略本次提醒');
  };
  function panel(){const items=unresolved().filter(item=>['ended','missing'].includes(item.kind));if(!items.length)return'';return `<div class="panel official-review-panel"><div class="panel-head"><div><h2>官网巡检待确认</h2><p>${items.length} 项官网变化需要你核对，确认前不会修改投递状态。</p></div></div><div class="official-review-list">${items.map(item=>`<article class="official-review-item ${item.kind}"><div><b>${esc(item.company)} · ${esc(item.position)}</b><span>${esc(item.message)} · ${esc(item.checkedAt)}</span><details><summary>查看页面片段</summary><pre>${esc(item.evidence||'没有可显示的页面片段')}</pre></details></div><div><button class="ghost" onclick="resolveOfficialFinding('${item.scanId}','${item.applicationId}','ignore')">忽略</button><button class="danger" onclick="resolveOfficialFinding('${item.scanId}','${item.applicationId}','confirm')">确认已结束</button></div></article>`).join('')}</div></div>`;}
  const baseHome=renderHome;renderHome=function(){baseHome();const html=panel();if(html)content.insertAdjacentHTML('afterbegin',html)};
})();
