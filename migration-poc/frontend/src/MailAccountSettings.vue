<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { api } from './api'
import BaseSelect from './BaseSelect.vue'
type Account={id:number;email:string;provider:string;lastSyncedAt:string;lastError:string}
type Inbox={accounts:Account[];messages:unknown[]}
const accounts=ref<Account[]>([]),loading=ref(false),message=ref(''),error=ref('')
const form=reactive({email:'',provider:'qq',password:''})
const helpDialog=ref<HTMLDialogElement|null>(null),passwordInput=ref<HTMLInputElement|null>(null)
onMounted(load)
async function load(){try{accounts.value=(await api<Inbox>('/api/poc/mail-inbox')).accounts||[]}catch(cause){error.value=cause instanceof Error?cause.message:'读取邮箱配置失败'}}
async function save(){loading.value=true;message.value='';error.value='';try{await api('/api/poc/mail-inbox/accounts',{method:'POST',body:JSON.stringify(form)});form.password='';message.value='邮箱已连接，之后收到的新邮件会进入收集箱';await load()}catch(cause){error.value=cause instanceof Error?cause.message:'邮箱连接失败'}finally{loading.value=false}}
async function remove(account:Account){if(!confirm(`移除 ${account.email}？系统内由该邮箱收集的卡片也会删除，但不会删除邮箱中的原邮件。`))return;loading.value=true;try{await api(`/api/poc/mail-inbox/accounts/${account.id}`,{method:'DELETE'});await load()}catch(cause){error.value=cause instanceof Error?cause.message:'移除邮箱失败'}finally{loading.value=false}}
function date(value:string){return value?new Date(value).toLocaleString('zh-CN',{hour12:false}):'尚未同步'}
function openHelp(){helpDialog.value?.showModal()}
function closeHelp(){helpDialog.value?.close()}
async function useAuthorizationCode(provider:'qq'|'163'){form.provider=provider;closeHelp();await nextTick();passwordInput.value?.focus()}
function closeFromBackdrop(event:MouseEvent){if(event.target===helpDialog.value)closeHelp()}
</script>
<template>
<section class="card mail-account-card">
  <div class="section-head">
    <div><span>邮件接入</span><h2>邮件收集箱</h2></div>
    <div class="head-actions"><small>仅收集，不会自动识别</small><button type="button" class="help-trigger" aria-label="查看邮箱接入帮助" title="邮箱接入帮助" @click="openHelp">?</button></div>
  </div>
  <p>开启邮箱的 IMAP 服务后，使用客户端授权码连接。系统不会保存邮箱登录密码。</p>
  <form class="connect-form" @submit.prevent="save">
    <label><span>邮箱类型</span><BaseSelect v-model="form.provider" :options="[{value:'qq',label:'QQ 邮箱'},{value:'163',label:'网易 163 邮箱'}]" /></label>
    <label><span>邮箱地址</span><input v-model.trim="form.email" type="email" autocomplete="username" required placeholder="name@example.com"></label>
    <label class="password"><span>客户端授权码</span><input ref="passwordInput" v-model="form.password" type="password" autocomplete="new-password" required placeholder="不是邮箱登录密码"></label>
    <button :disabled="loading">{{loading?'正在验证…':'连接邮箱'}}</button>
  </form>
  <div v-if="accounts.length" class="account-list">
    <article v-for="account in accounts" :key="account.id"><div><strong>{{account.email}}</strong><small :class="{bad:account.lastError}">{{account.lastError||`上次同步：${date(account.lastSyncedAt)}`}}</small></div><button class="danger-outline" :disabled="loading" @click="remove(account)">移除</button></article>
  </div>
  <p v-if="message" class="success">{{message}}</p><p v-if="error" class="danger" role="alert">{{error}}</p>

  <dialog ref="helpDialog" class="mail-help-dialog" aria-labelledby="mail-help-title" @click="closeFromBackdrop">
    <div class="dialog-content">
      <button type="button" class="modal-close" aria-label="关闭邮箱接入帮助" @click="closeHelp">×</button>
      <header><span>接入指南</span><h2 id="mail-help-title">获取客户端授权码</h2><p>授权码是邮箱为第三方应用生成的专用密码，不是邮箱登录密码。网页入口名称可能随邮箱版本略有变化。</p></header>
      <div class="provider-guides">
        <article>
          <div class="provider-title"><b>QQ</b><div><h3>QQ 邮箱</h3><small>IMAP 服务器：imap.qq.com</small></div></div>
          <ol>
            <li>登录 QQ 邮箱网页版，打开“设置”。</li>
            <li>进入“账号”或“账号与安全”，找到 POP3 / IMAP / SMTP 服务。</li>
            <li>开启“IMAP/SMTP 服务”，按提示完成安全验证。</li>
            <li>生成授权码，并立即复制保存；返回本页粘贴到“客户端授权码”。</li>
          </ol>
          <div class="guide-actions"><a href="https://mail.qq.com/" target="_blank" rel="noopener noreferrer">打开 QQ 邮箱 ↗</a><button type="button" class="secondary" @click="useAuthorizationCode('qq')">已复制，去填写</button></div>
        </article>
        <article>
          <div class="provider-title netease"><b>163</b><div><h3>网易 163 邮箱</h3><small>IMAP 服务器：imap.163.com</small></div></div>
          <ol>
            <li>登录网易邮箱网页版，打开“设置”。</li>
            <li>进入“POP3/SMTP/IMAP”，确认“IMAP/SMTP 服务”已开启。</li>
            <li>找到“客户端授权密码”，新增一个授权密码并完成安全验证。</li>
            <li>复制新生成的授权密码；返回本页粘贴到“客户端授权码”。</li>
          </ol>
          <p class="security-note">如果仍被拒绝，请先在网页版完成安全验证，再重新生成授权密码。</p>
          <div class="guide-actions"><a href="https://mail.163.com/" target="_blank" rel="noopener noreferrer">打开网易邮箱 ↗</a><button type="button" class="secondary" @click="useAuthorizationCode('163')">已复制，去填写</button></div>
        </article>
      </div>
      <footer><p>首次连接只会记录当前邮件位置，系统仅收集连接成功后收到的新邮件。</p><button type="button" @click="closeHelp">知道了</button></footer>
    </div>
  </dialog>
</section>
</template>
<style scoped>
.mail-account-card{display:grid;grid-area:mail;gap:14px}.section-head{display:flex;align-items:flex-end;justify-content:space-between;gap:12px}.section-head span,.mail-help-dialog header>span{color:var(--color-primary);font-size:11px;font-weight:800;letter-spacing:.1em}.section-head h2{margin:4px 0 0}.section-head small,.mail-account-card>p,.account-list small{color:var(--color-muted-foreground)}.head-actions{display:flex;align-items:center;gap:10px}.help-trigger{display:grid;width:44px;height:44px;min-height:44px;flex:none;place-items:center;padding:0;border:1px solid var(--color-border-strong);border-radius:50%;color:var(--color-primary);background:#edf8fe;font-size:20px;font-weight:800}.help-trigger:hover{background:#dff3fd}.mail-account-card>p{margin:0}.connect-form{display:grid;grid-template-columns:160px 1fr;gap:10px}.connect-form label{display:grid;gap:6px;color:var(--color-muted-foreground);font-size:13px;font-weight:700}.connect-form .password{grid-column:1/-1}.connect-form>button{grid-column:1/-1}.account-list{display:grid;gap:8px}.account-list article{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 12px;border:1px solid var(--color-border);border-radius:10px;background:#f8fbfd}.account-list article>div{display:grid;gap:3px;min-width:0}.account-list strong,.account-list small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.account-list .bad{color:#b42318}.danger-outline{color:#a52d2d;background:#fff4f2}
.mail-help-dialog{width:min(820px,calc(100vw - 32px));max-height:min(780px,calc(100dvh - 32px));padding:0;border:1px solid var(--color-border);border-radius:18px;color:var(--color-card-foreground);background:var(--color-card);box-shadow:var(--shadow-lg)}.mail-help-dialog::backdrop{background:rgba(7,37,58,.58);backdrop-filter:blur(4px)}.dialog-content{position:relative;display:grid;gap:20px;padding:28px}.modal-close{position:absolute;top:14px;right:14px;width:44px;height:44px;padding:0;font-size:24px}.mail-help-dialog header{padding-right:48px}.mail-help-dialog header h2{margin:4px 0 8px}.mail-help-dialog header p,.mail-help-dialog footer p{margin:0}.provider-guides{display:grid;grid-template-columns:1fr 1fr;gap:14px}.provider-guides>article{display:flex;min-width:0;flex-direction:column;padding:18px;border:1px solid var(--color-border);border-radius:14px;background:#f8fbfd}.provider-title{display:flex;align-items:center;gap:12px}.provider-title>b{display:grid;width:46px;height:46px;flex:none;place-items:center;border-radius:12px;color:#fff;background:#1684c7;font-family:var(--font-button);font-size:17px}.provider-title.netease>b{background:#c43b35}.provider-title h3{margin:0}.provider-title small{color:var(--color-muted-foreground)}.provider-guides ol{margin:16px 0;padding-left:22px;color:#334155;line-height:1.75}.provider-guides li+li{margin-top:5px}.security-note{margin:0 0 12px;padding:9px 11px;border-left:3px solid var(--color-warning);border-radius:7px;color:#7a4707;background:#fff5dc;font-size:12px}.guide-actions{display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:10px;margin-top:auto}.guide-actions a{font-weight:700}.guide-actions button{padding-inline:14px}.mail-help-dialog footer{display:flex;align-items:center;justify-content:space-between;gap:16px;padding-top:16px;border-top:1px solid var(--color-border)}.mail-help-dialog footer button{flex:none;padding-inline:22px;color:#fff;background:var(--color-primary)}
@media(max-width:680px){.connect-form{grid-template-columns:1fr}.connect-form .password,.connect-form>button{grid-column:auto}.section-head{align-items:center}.section-head .head-actions>small{display:none}.provider-guides{grid-template-columns:1fr}.mail-help-dialog{width:calc(100vw - 20px);max-height:calc(100dvh - 20px)}.dialog-content{gap:16px;padding:22px 14px}.provider-guides>article{padding:16px}.mail-help-dialog footer{align-items:stretch;flex-direction:column}.mail-help-dialog footer button{width:100%}}
</style>
