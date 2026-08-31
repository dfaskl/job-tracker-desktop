(function(){
  window.addEventListener('message',event=>{
    if(event.source!==window||event.origin!==location.origin||event.data?.type!=='job-tracker-start-official-batch')return;
    chrome.runtime.sendMessage({type:'prepare-batch',targets:event.data.targets,appUrl:event.data.appUrl},response=>{if(response?.ok)window.postMessage({type:'job-tracker-start-official-batch-ack'},location.origin)});
  });
  window.addEventListener('message',event=>{
    if(event.source!==window||event.origin!==location.origin||event.data?.type!=='job-tracker-official-batch-ack')return;
    chrome.storage.local.get('pendingBatch',result=>{if(result.pendingBatch?.id===event.data.id)chrome.storage.local.remove('pendingBatch')});
  });
  function deliver(){chrome.storage.local.get('pendingBatch',result=>{if(result.pendingBatch)window.dispatchEvent(new CustomEvent('job-tracker-official-batch',{detail:result.pendingBatch}))})}
  chrome.runtime.onMessage.addListener(message=>{if(message?.type==='deliver-official-batch')deliver()});
  setTimeout(deliver,500);setTimeout(deliver,1800);
})();
