(function(){
  const END_WORDS=['流程已结束','流程终止','招聘结束','已结束','未通过','不合适','已淘汰','职位关闭','岗位关闭','停止招聘','申请终止'];
  const ACTIVE_WORDS=['已投递','处理中','筛选中','流程中','面试中','测评中','待处理','申请成功'];
  const normalize=value=>String(value||'').toLowerCase().replace(/[\s·•_\-—（）()【】\[\]]+/g,'');
  const inspections=()=>state.officialInspections||(state.officialInspections=[]);
  const receivedBatchIds=new Set();
  const inspectionById=id=>inspections().find(item=>item.id===id);
  const unresolved=()=>inspections().flatMap(scan=>(scan.findings||[]).filter(item=>!item.resolution).map(item=>({...item,scanId:scan.id,checkedAt:scan.checkedAt})));
  function analyzeCapture(capture){
    const findings=(capture.excerpts||[]).map(excerpt=>{
      const application=appById(excerpt.applicationId);if(!application)return null;
      const found=Boolean(excerpt.found),sample=String(excerpt.evidence||''),sampleFlat=normalize(sample);
      const ended=found&&END_WORDS.find(word=>sampleFlat.includes(normalize(word))),active=found&&ACTIVE_WORDS.find(word=>sampleFlat.includes(normalize(word)));
      return {applicationId:application.id,company:application.company,position:application.position,kind:ended?'ended':found?(active?'active':'found'):'missing',message:ended?`页面显示“${ended}”`:found?'已在页面中找到该岗位':'当前页面未找到该岗位，需要人工核对',evidence:sample.slice(0,420)};
    }).filter(Boolean);
    return {id:`scan_${Date.now()}_${Math.random().toString(16).slice(2,8)}`,company:String(capture.excerpts?.[0]?.company||''),url:String(capture.url||''),title:String(capture.title||''),checkedAt:nowText(),findings};
  }
  async function receiveBatch(batch){
    if(!batch?.id||receivedBatchIds.has(batch.id))return;
    receivedBatchIds.add(batch.id);
    if(window.authReady)await window.authReady;
    while(typeof localDataReady!=='undefined'&&!localDataReady)await new Promise(resolve=>setTimeout(resolve,100));
    const scans=(batch.captures||[]).map(analyzeCapture);state.officialInspections=[...scans,...inspections()].slice(0,50);save();render();
    const findings=scans.flatMap(scan=>scan.findings),attention=findings.filter(item=>['ended','missing'].includes(item.kind)).length;
    toast(`巡检完成：检查 ${findings.length} 条投递，${attention} 项待确认`,{duration:7000});
    window.postMessage({type:'job-tracker-official-batch-ack',id:batch.id},location.origin);
  }
  window.addEventListener('job-tracker-official-batch',event=>receiveBatch(event.detail));
  function installHelp(){openModal('安装官网巡检扩展',`<div class="official-extension-help"><p>批量巡检需要安装浏览器扩展。扩展只访问投递记录关联的官网地址，不读取密码和 Cookie。</p><p><a class="primary official-extension-download" href="/job-tracker-official-inspector.zip" download>下载官网巡检扩展</a></p><ol><li>解压下载的 ZIP 文件。</li><li>在 Chrome 或 Edge 打开“扩展程序”，开启开发者模式。</li><li>选择“加载已解压的扩展程序”，选择刚解压的文件夹。</li><li>安装后刷新求职进度本，再点击“巡检投递”。</li></ol></div>`)}
  window.startApplicationInspection=async function(){
    const targets=await window.getOfficialInspectionTargets();
    if(!targets.length)return toast('进行中的投递记录没有可用的官网投递页链接',{type:'warning',duration:5000});
    let acknowledged=false;const listener=event=>{if(event.data?.type==='job-tracker-start-official-batch-ack'){acknowledged=true;window.removeEventListener('message',listener);toast(`已准备 ${targets.length} 条投递，请点击浏览器扩展图标确认开始`,{duration:7000})}};window.addEventListener('message',listener);
    window.postMessage({type:'job-tracker-start-official-batch',targets,appUrl:location.origin},location.origin);
    setTimeout(()=>{window.removeEventListener('message',listener);if(!acknowledged)installHelp()},900);
  };
  window.resolveOfficialFinding=function(scanId,applicationId,action){
    const scan=inspectionById(scanId),finding=scan?.findings?.find(item=>item.applicationId===applicationId);if(!finding)return;
    if(action==='confirm'){const application=appById(applicationId);if(application){const at=nowText();application.stage='已结束';application.status='已结束';application.updatedAt=at;application.timeline=application.timeline||[];application.timeline.push({at,title:'官网巡检确认：流程已结束'});}}
    finding.resolution=action;finding.resolvedAt=nowText();save();render();toast(action==='confirm'?'已将岗位标记为已结束':'已忽略本次提醒');
  };
  function panel(){const items=unresolved().filter(item=>['ended','missing'].includes(item.kind));if(!items.length)return'';return `<div class="panel official-review-panel"><div class="panel-head"><div><h2>官网巡检待确认</h2><p>${items.length} 项官网变化需要你核对，确认前不会修改投递状态。</p></div></div><div class="official-review-list">${items.map(item=>`<article class="official-review-item ${item.kind}"><div><b>${esc(item.company)} · ${esc(item.position)}</b><span>${esc(item.message)} · ${esc(item.checkedAt)}</span><details><summary>查看页面片段</summary><pre>${esc(item.evidence||'没有可显示的页面片段')}</pre></details></div><div><button class="ghost" onclick="resolveOfficialFinding('${item.scanId}','${item.applicationId}','ignore')">忽略</button><button class="danger" onclick="resolveOfficialFinding('${item.scanId}','${item.applicationId}','confirm')">确认已结束</button></div></article>`).join('')}</div></div>`;}
  const baseHome=renderHome;renderHome=function(){baseHome();const html=panel();if(html)content.insertAdjacentHTML('afterbegin',html)};
})();
