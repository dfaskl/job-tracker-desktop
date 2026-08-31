chrome.runtime.onMessage.addListener(message=>{
  if(message?.type==='open-official'&&/^https?:\/\//i.test(message.url||''))chrome.tabs.create({url:message.url});
});
