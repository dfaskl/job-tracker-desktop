(function(){
  window.addEventListener('message',event=>{
    if(event.source!==window||event.origin!==location.origin||event.data?.type!=='job-tracker-start-official-scan')return;
    const {company,url,appUrl}=event.data;
    if(!company||!/^https?:\/\//i.test(url||''))return;
    chrome.storage.local.set({inspectionContext:{company,url,appUrl,createdAt:Date.now()}},()=>{
      window.postMessage({type:'job-tracker-start-official-scan-ack'},location.origin);
      chrome.runtime.sendMessage({type:'open-official',url});
    });
  });
  window.addEventListener('message',event=>{
    if(event.source!==window||event.origin!==location.origin||event.data?.type!=='job-tracker-official-capture-ack')return;
    chrome.storage.local.get('pendingCapture',result=>{if(result.pendingCapture?.id===event.data.id)chrome.storage.local.remove('pendingCapture')});
  });
  function deliver(){chrome.storage.local.get('pendingCapture',result=>{const capture=result.pendingCapture;if(!capture)return;window.dispatchEvent(new CustomEvent('job-tracker-official-capture',{detail:capture}))})}
  setTimeout(deliver,500);setTimeout(deliver,1800);
})();
