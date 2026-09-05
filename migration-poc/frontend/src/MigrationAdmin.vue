<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

type AdminStatus={enabled:boolean;requested:boolean;sandboxEnabled:boolean;message:string}
type Summary={totalUsers:number;enabledUsers:number;totalApplications:number;activeSessions:number;configuredApiKeys:number;registrationOpen:boolean;registrationCodeEnabled:boolean;adminEmailConfigured:boolean}
type User={id:string;email:string;isAdmin:boolean;disabled:boolean;disabledAt:string;createdAt:string;lastLoginAt:string;applicationCount:number;eventCount:number;hasApiKey:boolean}
type Audit={id:string;action:string;targetEmail:string;createdAt:string}
type ApplicationDetail={id:string;company:string;position:string;stage:string;status:string;appliedDate:string;city:string;channel:string;flow:{at:string;title:string}[]}
type UserDetails={user:{id:string;email:string};applications:ApplicationDetail[];totalApplications:number;truncated:boolean}
type Overview={currentUser:{id:string;email:string};summary:Summary;users:User[];usersTruncated:boolean;audit:Audit[]}

const status=ref<AdminStatus|null>(null),overview=ref<Overview|null>(null),selected=ref<User|null>(null),detail=ref<UserDetails|null>(null)
const query=ref(''),stateFilter=ref('all'),loading=ref(false),busyUser=ref(''),error=ref(''),message=ref('')
const actionLabels:Record<string,string>={'disable-user':'停用账号','enable-user':'启用账号','delete-user':'删除账号','open-registration':'开放注册','close-registration':'关闭注册','view-user-details':'查看用户详情','revoke-sessions':'撤销登录会话'}
const users=computed(()=>overview.value?.users||[])
const filteredUsers=computed(()=>{const keyword=query.value.trim().toLowerCase();return users.value.filter(user=>(!keyword||user.email.toLowerCase().includes(keyword))&&(stateFilter.value==='all'||stateFilter.value==='admin'&&user.isAdmin||stateFilter.value==='enabled'&&!user.disabled||stateFilter.value==='disabled'&&user.disabled))})
const totalEvents=computed(()=>users.value.reduce((sum,user)=>sum+user.eventCount,0))
const disabledUsers=computed(()=>users.value.filter(user=>user.disabled).length)
const recentUsers=computed(()=>{const edge=Date.now()-30*86400000;return users.value.filter(user=>Date.parse(user.lastLoginAt)>edge).length})
const emptyDataUsers=computed(()=>users.value.filter(user=>!user.applicationCount&&!user.eventCount).length)

onMounted(checkStatus)
async function requestJson(url:string,init?:RequestInit){const response=await fetch(url,{cache:'no-store',...init});const body=await response.json().catch(()=>({}));if(!response.ok)throw new Error(body.message||'操作失败');return body}
async function checkStatus(){loading.value=true;error.value='';try{status.value=await requestJson('/api/poc/admin-sandbox/status') as AdminStatus;if(status.value.enabled)await loadOverview()}catch(cause){error.value=failure(cause,'管理员服务检查失败')}finally{loading.value=false}}
async function loadOverview(){overview.value=await requestJson('/api/poc/admin-sandbox/overview') as Overview}
async function refresh(){loading.value=true;error.value='';try{await loadOverview();message.value='管理员数据已刷新'}catch(cause){error.value=failure(cause,'刷新失败')}finally{loading.value=false}}
async function toggleRegistration(){if(!overview.value)return;loading.value=true;error.value='';const enabled=!overview.value.summary.registrationOpen;try{await requestJson('/api/poc/admin-sandbox/settings/registration',{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({enabled})});await loadOverview();message.value=enabled?'注册入口已开放':'注册入口已关闭'}catch(cause){error.value=failure(cause,'修改注册状态失败')}finally{loading.value=false}}
async function openDetails(user:User){selected.value=user;detail.value=null;busyUser.value=user.id;error.value='';try{detail.value=await requestJson(`/api/poc/admin-sandbox/users/${user.id}/details`) as UserDetails;await loadOverview()}catch(cause){selected.value=null;error.value=failure(cause,'读取用户详情失败')}finally{busyUser.value=''}}
async function setDisabled(user:User){busyUser.value=user.id;error.value='';try{await requestJson(`/api/poc/admin-sandbox/users/${user.id}`,{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({disabled:!user.disabled})});await loadOverview();message.value=user.disabled?'账号已启用':'账号已停用并撤销会话'}catch(cause){error.value=failure(cause,'修改账号状态失败')}finally{busyUser.value=''}}
async function revokeSessions(user:User){if(!confirm(`确认让 ${user.email} 的所有设备重新登录吗？`))return;busyUser.value=user.id;error.value='';try{const result=await requestJson(`/api/poc/admin-sandbox/users/${user.id}/sessions/revoke`,{method:'PATCH'});await loadOverview();message.value=`已撤销 ${Number(result.revoked||0)} 个登录会话`}catch(cause){error.value=failure(cause,'撤销会话失败')}finally{busyUser.value=''}}
async function deleteUser(user:User){const confirmEmail=prompt(`删除后无法恢复。请输入 ${user.email} 确认：`,'');if(confirmEmail===null)return;busyUser.value=user.id;error.value='';try{await requestJson(`/api/poc/admin-sandbox/users/${user.id}`,{method:'DELETE',headers:{'Content-Type':'application/json'},body:JSON.stringify({confirmEmail})});if(selected.value?.id===user.id){selected.value=null;detail.value=null}await loadOverview();message.value='用户及其业务数据已删除'}catch(cause){error.value=failure(cause,'删除用户失败')}finally{busyUser.value=''}}
function failure(cause:unknown,fallback:string){return cause instanceof Error?cause.message:fallback}
function formatDate(value:string){if(!value)return '从未';const date=new Date(value);return Number.isNaN(date.getTime())?value:date.toLocaleString('zh-CN',{hour12:false})}
function relativeDate(value:string){if(!value)return '从未登录';const time=Date.parse(value);if(!Number.isFinite(time))return value;const days=Math.floor((Date.now()-time)/86400000);return days<=0?'今天':days===1?'昨天':`${days} 天前`}
</script>

<template>
  <section class="admin-shell">
    <div v-if="status&&!status.enabled" class="card disabled-panel"><h2>管理员功能暂不可用</h2><p>{{status.message}}</p><small>请检查业务数据库连接与写入配置。</small></div>
    <template v-else-if="overview">
      <header class="admin-head">
        <div><span>管理中心</span><h2>系统运行与账号管理</h2><p>当前管理员：{{overview.currentUser.email}}</p></div>
        <button class="secondary" :disabled="loading" @click="refresh">{{loading?'刷新中…':'刷新数据'}}</button>
      </header>

      <div class="summary-grid">
        <article><i>用户</i><strong>{{overview.summary.totalUsers}}</strong><small>近 30 天活跃 {{recentUsers}}</small></article>
        <article><i>业务数据</i><strong>{{overview.summary.totalApplications}}</strong><small>{{totalEvents}} 项日程</small></article>
        <article><i>有效会话</i><strong>{{overview.summary.activeSessions}}</strong><small>{{disabledUsers}} 个停用账号</small></article>
        <article><i>AI 配置</i><strong>{{overview.summary.configuredApiKeys}}</strong><small>已配置独立密钥</small></article>
      </div>

      <div class="admin-grid">
        <div class="left-column">
          <section class="card control-card">
            <div class="section-title"><div><span>访问控制</span><h3>注册与系统状态</h3></div><b :class="overview.summary.registrationOpen?'ok':'muted'">{{overview.summary.registrationOpen?'允许注册':'停止注册'}}</b></div>
            <div class="registration"><div><strong>新用户注册</strong><small>{{overview.summary.registrationCodeEnabled?'已配置注册码':'未配置注册码'}}</small></div><button :class="overview.summary.registrationOpen?'danger-outline':''" @click="toggleRegistration">{{overview.summary.registrationOpen?'关闭':'开放'}}</button></div>
            <div class="health-grid">
              <div><i class="dot ok"></i><span>数据库</span><b>连接正常</b></div>
              <div><i :class="['dot',status?.sandboxEnabled?'ok':'warn']"></i><span>业务写入</span><b>{{status?.sandboxEnabled?'正常':'受限'}}</b></div>
              <div><i :class="['dot',overview.summary.adminEmailConfigured?'ok':'warn']"></i><span>管理员配置</span><b>{{overview.summary.adminEmailConfigured?'完整':'待配置'}}</b></div>
              <div><i :class="['dot',emptyDataUsers?'warn':'ok']"></i><span>空数据账号</span><b>{{emptyDataUsers}} 个</b></div>
            </div>
          </section>

          <section class="card audit-card">
            <div class="section-title"><div><span>操作审计</span><h3>最近操作</h3></div><small>最近 {{overview.audit.length}} 条</small></div>
            <div class="audit-scroll"><div v-for="item in overview.audit" :key="item.id"><i></i><span><b>{{actionLabels[item.action]||item.action}}</b><small>{{item.targetEmail||'系统设置'}}</small></span><time>{{formatDate(item.createdAt)}}</time></div><p v-if="!overview.audit.length">暂无管理操作</p></div>
          </section>
        </div>

        <section class="card users-card">
          <div class="section-title"><div><span>账号管理</span><h3>用户列表</h3></div><small>{{filteredUsers.length}} / {{overview.summary.totalUsers}}</small></div>
          <div class="user-tools"><input v-model="query" placeholder="搜索用户邮箱" /><select v-model="stateFilter"><option value="all">全部状态</option><option value="enabled">正常</option><option value="disabled">已停用</option><option value="admin">管理员</option></select></div>
          <p v-if="overview.usersTruncated" class="hint">列表仅展示前 500 个账号。</p>
          <div class="user-scroll">
            <article v-for="user in filteredUsers" :key="user.id" :class="{disabled:user.disabled}">
              <div class="avatar">{{user.email.slice(0,1).toUpperCase()}}</div>
              <div class="identity"><strong>{{user.email}}</strong><span><b v-if="user.isAdmin">管理员</b><b v-else-if="user.disabled" class="bad">已停用</b><b v-else class="good">正常</b> · 注册于 {{formatDate(user.createdAt)}}</span></div>
              <div class="counts"><span><b>{{user.applicationCount}}</b> 投递</span><span><b>{{user.eventCount}}</b> 日程</span><span>{{relativeDate(user.lastLoginAt)}}</span></div>
              <div class="actions"><button class="secondary" :disabled="busyUser===user.id" @click="openDetails(user)">详情</button><template v-if="!user.isAdmin"><button class="secondary" :disabled="busyUser===user.id" @click="revokeSessions(user)">下线</button><button class="secondary" :disabled="busyUser===user.id" @click="setDisabled(user)">{{user.disabled?'启用':'停用'}}</button><button class="danger-button" :disabled="busyUser===user.id" @click="deleteUser(user)">删除</button></template></div>
            </article>
            <p v-if="!filteredUsers.length" class="empty">没有符合条件的用户</p>
          </div>
        </section>
      </div>
    </template>
    <div v-else-if="loading" class="card loading-panel">正在读取管理员数据…</div>
    <p v-if="message" class="success feedback">{{message}}</p><p v-if="error" class="danger feedback">{{error}}</p>

    <div v-if="selected" class="modal-backdrop" @click.self="selected=null">
      <section class="detail-modal"><button class="modal-close" @click="selected=null">×</button><header><div class="avatar large">{{selected.email.slice(0,1).toUpperCase()}}</div><div><span>用户详情</span><h2>{{selected.email}}</h2><p>{{selected.applicationCount}} 条投递 · {{selected.eventCount}} 项日程 · API Key {{selected.hasApiKey?'已配置':'未配置'}}</p></div></header><div v-if="!detail" class="loading-panel">正在读取详情…</div><div v-else class="detail-scroll"><p v-if="detail.truncated" class="hint">共 {{detail.totalApplications}} 条投递，当前展示前 500 条。</p><article v-for="application in detail.applications" :key="application.id"><div><strong>{{application.company||'未填写公司'}} · {{application.position||'未填写岗位'}}</strong><span>{{application.stage||'—'}} / {{application.status||'—'}} · {{application.city||'地点未填'}} · {{application.channel||'渠道未填'}}</span></div><ol><li v-for="step in application.flow" :key="step.at+step.title"><time>{{step.at||'时间未知'}}</time><span>{{step.title}}</span></li></ol></article><p v-if="!detail.applications.length" class="empty">该用户暂无投递记录</p></div></section>
    </div>
  </section>
</template>

<style scoped>
.admin-shell{display:flex;height:calc(100vh - 128px);min-height:560px;flex-direction:column;gap:14px;padding:14px 0 0;overflow:hidden}.admin-head,.section-title,.registration,.user-tools,.users-card article,.actions,.detail-modal header{display:flex;align-items:center;justify-content:space-between;gap:12px}.admin-head h2,.admin-head p,.section-title h3,.detail-modal h2,.detail-modal p{margin:0}.admin-head>div>span,.section-title>div>span{color:var(--accent,#4461d8);font-size:11px;font-weight:800;letter-spacing:.09em}.admin-head h2{margin:3px 0;font-size:22px}.admin-head p{color:#667085;font-size:12px}.summary-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}.summary-grid article{display:grid;gap:4px;padding:13px 16px;border:1px solid #dfe5ef;border-radius:12px;background:#fff}.summary-grid i{color:#667085;font-size:11px;font-style:normal}.summary-grid strong{font-size:24px}.summary-grid small{color:#7a8699}.admin-grid{display:grid;min-height:0;flex:1;grid-template-columns:minmax(320px,.76fr) minmax(600px,1.65fr);gap:14px}.left-column{display:grid;min-height:0;grid-template-rows:auto minmax(0,1fr);gap:14px}.card{min-width:0;margin:0;padding:17px;border-radius:14px}.section-title h3{margin-top:3px;font-size:17px}.section-title>small{color:#667085}.section-title>b{padding:5px 8px;border-radius:999px;font-size:11px}.section-title>b.ok{color:#147847;background:#e8f7ee}.section-title>b.muted{color:#83591a;background:#fff2d7}.registration{margin-top:14px;padding:12px;border-radius:10px;background:#f6f8fb}.registration>div{display:grid;gap:3px}.registration small{color:#667085}.registration button{padding:8px 12px}.danger-outline{border:1px solid #efc4c0;color:#a63731;background:#fff}.health-grid{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:11px}.health-grid>div{display:grid;grid-template-columns:10px 1fr auto;align-items:center;gap:7px;padding:9px;border:1px solid #e7ebf2;border-radius:9px}.health-grid span,.health-grid b{font-size:11px}.health-grid b{color:#667085}.dot{width:8px;height:8px;border-radius:50%}.dot.ok{background:#32a66a}.dot.warn{background:#e2a336}.audit-card,.users-card{display:flex;min-height:0;flex-direction:column}.audit-scroll,.user-scroll,.detail-scroll{min-height:0;overflow:auto;scrollbar-width:thin}.audit-scroll{margin-top:10px}.audit-scroll>div{display:grid;grid-template-columns:8px 1fr auto;align-items:center;gap:8px;padding:10px 2px;border-top:1px solid #edf0f5}.audit-scroll>div>i{width:7px;height:7px;border-radius:50%;background:var(--accent,#4461d8)}.audit-scroll span{display:grid;gap:2px}.audit-scroll small,.audit-scroll time{color:#7b8798;font-size:10px}.user-tools{margin:12px 0}.user-tools input{flex:1}.user-tools select{width:130px}.users-card article{padding:11px 4px;border-top:1px solid #e9edf3}.users-card article.disabled{opacity:.65}.avatar{display:grid;width:36px;height:36px;flex:none;place-items:center;border-radius:10px;color:#fff;background:var(--accent,#4461d8);font-weight:800}.avatar.large{width:48px;height:48px;border-radius:13px}.identity{display:grid;min-width:0;flex:1;gap:4px}.identity strong{overflow:hidden;text-overflow:ellipsis}.identity span{color:#788496;font-size:11px}.identity span b{color:#6a4a96}.identity span b.good{color:#197348}.identity span b.bad{color:#a63833}.counts{display:flex;min-width:235px;gap:14px;color:#697587;font-size:11px}.counts b{color:#26364e}.actions{justify-content:flex-end}.actions button{padding:7px 9px;font-size:11px}.danger-button{background:#c33b36}.hint,.empty{color:#758195;font-size:11px}.empty{text-align:center}.feedback{position:fixed;top:18px;left:50%;z-index:70;transform:translateX(-50%);padding:11px 18px;border-radius:10px;box-shadow:0 8px 28px rgba(0,0,0,.14)}.modal-backdrop{position:fixed;inset:0;z-index:50;display:grid;place-items:center;padding:24px;background:rgba(18,28,45,.58)}.detail-modal{position:relative;display:flex;width:min(960px,94vw);height:min(760px,88vh);flex-direction:column;padding:24px;border-radius:18px;background:#fff;box-shadow:0 24px 80px rgba(0,0,0,.26)}.detail-modal header{justify-content:flex-start;padding-bottom:16px;border-bottom:1px solid #e7ebf1}.detail-modal header>div:last-child{display:grid;gap:3px}.detail-modal header>div>span{color:var(--accent,#4461d8);font-size:11px;font-weight:800}.modal-close{position:absolute;top:16px;right:16px;width:38px;height:38px;padding:0;color:#506078;background:#eef2f7;font-size:22px}.detail-scroll{margin-top:10px}.detail-scroll>article{padding:14px 2px;border-bottom:1px solid #e8edf3}.detail-scroll>article>div{display:grid;gap:4px}.detail-scroll>article>div span{color:#687689;font-size:12px}.detail-scroll ol{margin:10px 0 0;padding-left:20px}.detail-scroll li{display:grid;grid-template-columns:150px 1fr;gap:10px;margin:6px 0;color:#445166}.detail-scroll time{color:#798698;font-size:11px}.disabled-panel,.loading-panel{text-align:center}@media(max-width:1100px){.admin-shell{height:auto;overflow:visible}.admin-grid{grid-template-columns:1fr}.audit-scroll,.user-scroll{max-height:520px}}@media(max-width:720px){.summary-grid{grid-template-columns:1fr 1fr}.users-card article{align-items:flex-start;flex-wrap:wrap}.counts{width:100%;min-width:0;margin-left:48px}.actions{width:100%;margin-left:48px;justify-content:flex-start}.health-grid{grid-template-columns:1fr}.detail-scroll li{grid-template-columns:1fr}}
</style>