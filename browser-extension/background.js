let runnerActive=false;
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const getStore=key=>new Promise(resolve=>chrome.storage.local.get(key,result=>resolve(result[key])));
const setStore=value=>new Promise(resolve=>chrome.storage.local.set(value,resolve));
const removeStore=key=>new Promise(resolve=>chrome.storage.local.remove(key,resolve));
async function waitForTab(tabId){for(let count=0;count<30;count++){try{const tab=await chrome.tabs.get(tabId);if(tab.status==='complete')return}catch{}await sleep(500)}}
async function readRelevantText(tabId,targets){
  const [{result}]=await chrome.scripting.executeScript({target:{tabId},args:[targets],func:items=>{
    const body=String(document.body?.innerText||''),lower=body.toLowerCase();
    const excerpts=items.map(item=>{const key=String(item.position||'').trim(),index=key?lower.indexOf(key.toLowerCase()):-1;return {...item,found:index>=0,evidence:index>=0?body.slice(Math.max(0,index-140),Math.min(body.length,index+key.length+180)):''}});
    return {title:document.title,url:location.href,loginText:body.slice(0,1800),bodyLength:body.length,excerpts};
  }});return result;
}
function loginRequired(capture){const text=String(capture?.loginText||'');return Number(capture?.bodyLength||0)<180||(/登录|扫码登录|手机验证|验证码/.test(text)&&!/投递|申请记录|应聘记录/.test(text))}
async function focusApp(appUrl){const tabs=await chrome.tabs.query({url:`${appUrl}/*`});if(tabs[0]){try{await chrome.tabs.sendMessage(tabs[0].id,{type:'deliver-official-batch'})}catch{await chrome.tabs.reload(tabs[0].id)}await chrome.tabs.update(tabs[0].id,{active:true})}else await chrome.tabs.create({url:`${appUrl}/`,active:true})}
async function runJob(){
  if(runnerActive)return;runnerActive=true;
  try{
    let job=await getStore('officialInspectionJob');
    while(job&&job.index<job.groups.length){
      const group=job.groups[job.index];job.status='running';job.currentCompany=group.targets[0]?.company||'';job.error='';await setStore({officialInspectionJob:job});
      let tabId=job.tabId;try{if(tabId)await chrome.tabs.get(tabId);else throw new Error()}catch{tabId=(await chrome.tabs.create({url:group.url,active:false})).id;job.tabId=tabId;await setStore({officialInspectionJob:job})}
      await waitForTab(tabId);await sleep(2200);
      let capture;try{capture=await readRelevantText(tabId,group.targets)}catch{job.status='paused';job.error='页面暂时无法读取，请确认页面正常后继续';await chrome.tabs.update(tabId,{active:true});await setStore({officialInspectionJob:job});return}
      if(loginRequired(capture)){job.status='paused';job.error='登录状态可能已失效，请在当前页面完成登录后继续';await chrome.tabs.update(tabId,{active:true});await setStore({officialInspectionJob:job});return}
      delete capture.loginText;job.captures.push(capture);job.index+=1;job.tabId=null;await setStore({officialInspectionJob:job});await chrome.tabs.remove(tabId).catch(()=>{});job=await getStore('officialInspectionJob');
    }
    if(job){await setStore({pendingBatch:{id:job.id,captures:job.captures,completedAt:new Date().toISOString()}});if(job.approvedOrigins?.length)await chrome.permissions.remove({origins:job.approvedOrigins});await removeStore('officialInspectionJob');await focusApp(job.appUrl)}
  }finally{runnerActive=false}
}
chrome.runtime.onMessage.addListener((message,sender,sendResponse)=>{
  if(message?.type==='prepare-batch'){
    const grouped=new Map();for(const target of message.targets||[]){if(!/^https?:\/\//i.test(target.url||''))continue;if(!grouped.has(target.url))grouped.set(target.url,{url:target.url,targets:[]});grouped.get(target.url).targets.push(target)}
    const job={id:`batch_${Date.now()}`,appUrl:message.appUrl,groups:[...grouped.values()],index:0,captures:[],status:'awaiting-permission',tabId:null,createdAt:Date.now()};
    setStore({officialInspectionJob:job}).then(()=>sendResponse({ok:true,count:job.groups.length}));return true;
  }
  if(message?.type==='run-batch'){getStore('officialInspectionJob').then(job=>{if(job){job.status='running';job.approvedOrigins=message.origins||[];setStore({officialInspectionJob:job}).then(runJob)}});sendResponse({ok:true});return}
  if(message?.type==='resume-batch'){getStore('officialInspectionJob').then(job=>{if(job){job.status='running';setStore({officialInspectionJob:job}).then(runJob)}});sendResponse({ok:true});return}
  if(message?.type==='cancel-batch'){getStore('officialInspectionJob').then(async job=>{if(job?.tabId)await chrome.tabs.remove(job.tabId).catch(()=>{});if(job?.approvedOrigins?.length)await chrome.permissions.remove({origins:job.approvedOrigins});await removeStore('officialInspectionJob')});sendResponse({ok:true})}
});
chrome.runtime.onStartup.addListener(()=>getStore('officialInspectionJob').then(job=>{if(job?.status==='running')runJob()}));
