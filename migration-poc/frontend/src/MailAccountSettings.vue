<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api } from './api'
import BaseSelect from './BaseSelect.vue'
type Account={id:number;email:string;provider:string;lastSyncedAt:string;lastError:string}
type Inbox={accounts:Account[];messages:unknown[]}
const accounts=ref<Account[]>([]),loading=ref(false),message=ref(''),error=ref('')
const form=reactive({email:'',provider:'qq',password:''})
onMounted(load)
async function load(){try{accounts.value=(await api<Inbox>('/api/poc/mail-inbox')).accounts||[]}catch(cause){error.value=cause instanceof Error?cause.message:'读取邮箱配置失败'}}
async function save(){loading.value=true;message.value='';error.value='';try{await api('/api/poc/mail-inbox/accounts',{method:'POST',body:JSON.stringify(form)});form.password='';message.value='邮箱已连接，并已收取最近邮件';await load()}catch(cause){error.value=cause instanceof Error?cause.message:'邮箱连接失败'}finally{loading.value=false}}
async function remove(account:Account){if(!confirm(`移除 ${account.email}？系统内由该邮箱收集的卡片也会删除，但不会删除邮箱中的原邮件。`))return;loading.value=true;try{await api(`/api/poc/mail-inbox/accounts/${account.id}`,{method:'DELETE'});await load()}catch(cause){error.value=cause instanceof Error?cause.message:'移除邮箱失败'}finally{loading.value=false}}
function date(value:string){return value?new Date(value).toLocaleString('zh-CN',{hour12:false}):'尚未同步'}
</script>
<template>
<section class="card mail-account-card">
  <div class="section-head"><div><span>邮件接入</span><h2>邮件收集箱</h2></div><small>仅收集，不会自动识别</small></div>
  <p>开启邮箱的 IMAP 服务后，使用客户端授权码连接。系统不会保存邮箱登录密码。</p>
  <form class="connect-form" @submit.prevent="save">
    <label><span>邮箱类型</span><BaseSelect v-model="form.provider" :options="[{value:'qq',label:'QQ 邮箱'},{value:'163',label:'网易 163 邮箱'}]" /></label>
    <label><span>邮箱地址</span><input v-model.trim="form.email" type="email" autocomplete="username" required placeholder="name@example.com"></label>
    <label class="password"><span>客户端授权码</span><input v-model="form.password" type="password" autocomplete="new-password" required placeholder="不是邮箱登录密码"></label>
    <button :disabled="loading">{{loading?'正在验证…':'连接邮箱'}}</button>
  </form>
  <div v-if="accounts.length" class="account-list">
    <article v-for="account in accounts" :key="account.id"><div><strong>{{account.email}}</strong><small :class="{bad:account.lastError}">{{account.lastError||`上次同步：${date(account.lastSyncedAt)}`}}</small></div><button class="danger-outline" :disabled="loading" @click="remove(account)">移除</button></article>
  </div>
  <p v-if="message" class="success">{{message}}</p><p v-if="error" class="danger" role="alert">{{error}}</p>
</section>
</template>
<style scoped>
.mail-account-card{display:grid;grid-area:mail;gap:14px}.section-head{display:flex;align-items:flex-end;justify-content:space-between;gap:12px}.section-head span{color:var(--color-primary);font-size:11px;font-weight:800;letter-spacing:.1em}.section-head h2{margin:4px 0 0}.section-head small,.mail-account-card>p,.account-list small{color:var(--color-muted-foreground)}.mail-account-card>p{margin:0}.connect-form{display:grid;grid-template-columns:160px 1fr;gap:10px}.connect-form label{display:grid;gap:6px;color:var(--color-muted-foreground);font-size:13px;font-weight:700}.connect-form .password{grid-column:1/-1}.connect-form>button{grid-column:1/-1}.account-list{display:grid;gap:8px}.account-list article{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 12px;border:1px solid var(--color-border);border-radius:10px;background:#f8fbfd}.account-list article>div{display:grid;gap:3px;min-width:0}.account-list strong,.account-list small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.account-list .bad{color:#b42318}.danger-outline{color:#a52d2d;background:#fff4f2}@media(max-width:560px){.connect-form{grid-template-columns:1fr}.connect-form .password,.connect-form>button{grid-column:auto}.section-head{align-items:flex-start;flex-direction:column}}
</style>
