<script setup lang="ts">
import { ref } from 'vue'
import { useJobTrackerStore } from './jobTrackerStore'
const props=withDefaults(defineProps<{compact?:boolean}>(),{compact:false})
const store=useJobTrackerStore()
const email=ref(''),password=ref(''),code=ref(''),mode=ref<'login'|'register'>('login'),submitting=ref(false),message=ref('')
async function submit(){
 submitting.value=true;message.value=''
 try{if(mode.value==='login')await store.login(email.value,password.value);else await store.register(email.value,password.value,code.value);password.value='';code.value=''}
 catch(cause){message.value=cause instanceof Error?cause.message:'操作失败'}finally{submitting.value=false}
}
async function logout(){submitting.value=true;try{await store.logout()}finally{submitting.value=false}}
</script>
<template>
<section v-if="store.initialized.value&&!store.user.value&&!props.compact" class="access">
 <div><strong>{{mode==='login'?'登录账号':'创建测试账号'}}</strong><span>{{mode==='login'?'使用现有账号访问完整数据':'注册仅在独立测试数据库开放时可用'}}</span></div>
 <form @submit.prevent="submit"><input v-model="email" type="email" autocomplete="username" placeholder="邮箱" required><input v-model="password" type="password" :autocomplete="mode==='login'?'current-password':'new-password'" :placeholder="mode==='login'?'密码':'密码（10–128 位）'" required minlength="10"><input v-if="mode==='register'" v-model="code" placeholder="邀请码（如需要）"><button :disabled="submitting">{{submitting?'处理中…':mode==='login'?'登录':'注册并登录'}}</button></form>
 <button class="switch" @click="mode=mode==='login'?'register':'login';message=''">{{mode==='login'?'没有账号？注册':'已有账号？登录'}}</button><p v-if="message" class="danger">{{message}}</p>
</section>
<section v-else-if="store.user.value" class="signed" :class="{compact:props.compact}"><div><strong>{{store.user.value.email}}</strong><span>{{store.readOnly.value?'只读模式':'数据可读写'}}</span></div><button class="secondary" :disabled="submitting" @click="logout">退出登录</button></section>
</template>
<style scoped>
.access,.signed{display:flex;align-items:center;gap:18px;margin-top:22px;padding:16px 20px;border:1px solid #dbe3f1;border-radius:15px;background:#fff}.access>div,.signed>div{display:grid;gap:4px;min-width:190px}.access span,.signed span{color:#667085;font-size:12px}.access form{display:grid;grid-template-columns:1fr 1fr 1fr auto;flex:1;gap:9px}.switch{padding:8px;color:#4461d8;background:transparent;white-space:nowrap}.access p{width:100%;margin:0}.signed{justify-content:space-between}.secondary{color:#344054;background:#eef2f8}.signed.compact{display:grid;gap:10px;margin:0;padding:11px;color:#dce5ff;border-color:#344362;background:#202c43}.signed.compact>div{min-width:0}.signed.compact strong{overflow:hidden;font-size:12px;text-overflow:ellipsis;white-space:nowrap}.signed.compact span{color:#91a0c5;font-size:10px}.signed.compact button{width:100%;padding:7px;color:#dce5ff;border:1px solid #40506d;background:transparent}@media(max-width:900px){.access{align-items:stretch;flex-direction:column}.access form{grid-template-columns:1fr}.switch{text-align:left}.signed{margin-top:14px}}
</style>
