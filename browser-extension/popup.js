let context=null;
const contextNode=document.querySelector('#context'),button=document.querySelector('#capture'),statusNode=document.querySelector('#status');
chrome.storage.local.get('inspectionContext',result=>{
  context=result.inspectionContext;
  if(!context||Date.now()-Number(context.createdAt||0)>24*60*60*1000){contextNode.textContent='请先在求职进度本的官网库中点击“检查”。';return}
  contextNode.textContent=`正在检查：${context.company}`;button.disabled=false;
});
button.onclick=async()=>{
  button.disabled=true;statusNode.textContent='正在读取当前页面…';
  try{
    const [tab]=await chrome.tabs.query({active:true,currentWindow:true});
    if(!tab?.id||!/^https?:\/\//i.test(tab.url||''))throw new Error('当前页面不是可读取的网页');
    const [{result}]=await chrome.scripting.executeScript({target:{tabId:tab.id},func:()=>({title:document.title,url:location.href,text:String(document.body?.innerText||'').slice(0,180000)})});
    if(!result?.text)throw new Error('当前页面没有可读取的内容');
    const capture={id:`capture_${Date.now()}`,company:context.company,expectedUrl:context.url,...result,capturedAt:new Date().toISOString()};
    await chrome.storage.local.set({pendingCapture:capture});
    statusNode.textContent='读取完成，正在返回求职进度本…';statusNode.className='ok';
    await chrome.tabs.create({url:`${context.appUrl||'https://job-tracker-web-c8b0.onrender.com'}/`});
    setTimeout(()=>window.close(),500);
  }catch(error){statusNode.textContent=error.message||'读取失败';button.disabled=false}
};
