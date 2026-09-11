<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api, apiCached } from './api'
type ConfigView={apiUrl:string;model:string;hasApiKey:boolean;lastFour:string}
const config=ref<ConfigView|null>(null)
const form=reactive({apiUrl:'https://api.deepseek.com',model:'deepseek-chat',apiKey:'',clearApiKey:false})
const loading=ref(false),message=ref(''),error=ref('')
onMounted(loadConfig)
async function loadConfig(){try{config.value=await apiCached<ConfigView>('/api/poc/ai-sandbox/config');Object.assign(form,{apiUrl:config.value.apiUrl,model:config.value.model,apiKey:'',clearApiKey:false})}catch(cause){error.value=cause instanceof Error?cause.message:'读取 AI 配置失败'}}
async function saveConfig(){loading.value=true;message.value='';error.value='';try{config.value=await api<ConfigView>('/api/poc/ai-sandbox/config',{method:'POST',body:JSON.stringify(form)});form.apiKey='';form.clearApiKey=false;message.value='大模型 API 配置已保存'}catch(cause){error.value=cause instanceof Error?cause.message:'保存 AI 配置失败'}finally{loading.value=false}}
</script>
<template>
<section class="card preference-card api-card"><div class="section-head"><div><span>智能能力</span><h2>大模型 API</h2></div><i title="用于邮件识别、投递信息规范化和每日一句">ⓘ</i></div><p>支持 OpenAI 兼容接口。API Key 经加密后保存在服务器中，保存后不再显示完整内容。</p><form class="api-form" @submit.prevent="saveConfig"><label class="wide"><span>API 地址</span><input v-model="form.apiUrl" type="url" maxlength="2048" required /></label><label><span>模型名称</span><input v-model="form.model" maxlength="200" required /></label><label><span>API Key {{config?.hasApiKey?'（已配置，末四位 '+config.lastFour+'）':''}}</span><input v-model="form.apiKey" type="password" autocomplete="off" placeholder="留空则保留现有密钥" /></label><div class="api-actions wide"><label v-if="config?.hasApiKey" class="check"><input v-model="form.clearApiKey" type="checkbox" /><span>清除现有 API Key</span></label><span v-else></span><button :disabled="loading">{{loading?'保存中…':'保存配置'}}</button></div></form><p v-if="message" class="success">{{message}}</p><p v-if="error" class="danger" role="alert">{{error}}</p></section>
</template>
<style scoped>
.preference-card{display:grid;gap:14px}.api-card{grid-area:api}.section-head{display:flex;align-items:center;justify-content:space-between;gap:12px}.section-head h2{margin:4px 0 0}.section-head span{color:var(--accent,var(--color-primary));font-size:11px;font-weight:800;letter-spacing:.1em}.section-head>i{color:#758198;font-style:normal}.preference-card>p{margin:0}.api-form{display:grid;grid-template-columns:1fr 1fr;gap:12px}.api-form label{display:grid;gap:7px;color:var(--color-muted-foreground);font-size:13px;font-weight:700}.api-form .wide{grid-column:1/-1}.api-form .check{display:flex;align-items:center}.check input{flex:none;width:18px}.api-actions{display:flex;align-items:center;justify-content:space-between;gap:16px;padding-top:2px}.api-actions button{flex:0 0 auto}@media(max-width:560px){.api-form{grid-template-columns:1fr}.api-form .wide{grid-column:auto}.api-actions{align-items:stretch;flex-direction:column}.api-actions button{width:100%}}
</style>
