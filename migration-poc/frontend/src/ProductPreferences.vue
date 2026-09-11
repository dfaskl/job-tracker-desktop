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
<section class="card preference-card api-card"><div class="section-head"><div><span>智能能力</span><h2>大模型 API</h2></div><i title="用于邮件识别、投递信息规范化和每日一句">ⓘ</i></div><p>支持 OpenAI 兼容接口。API Key 经加密后保存在服务器中，保存后不再显示完整内容。</p><form class="api-form" @submit.prevent="saveConfig"><label class="wide"><span>API 地址</span><input v-model="form.apiUrl" type="url" maxlength="2048" required /></label><label><span>模型名称</span><input v-model="form.model" maxlength="200" required /></label><label><span>API Key {{config?.hasApiKey?'（已配置，末四位 '+config.lastFour+'）':''}}</span><input v-model="form.apiKey" type="password" autocomplete="off" placeholder="留空则保留现有密钥" /></label><label v-if="config?.hasApiKey" class="check wide"><input v-model="form.clearApiKey" type="checkbox" /><span>清除现有 API Key</span></label><button :disabled="loading">{{loading?'保存中…':'保存配置'}}</button></form><p v-if="message" class="success">{{message}}</p><p v-if="error" class="danger" role="alert">{{error}}</p></section>
<section class="card about-card"><div class="about-brand"><img src="/favicon.svg" alt="" /><div><h2>求职进度本</h2><p>私密、专注、可同步的求职管理工具</p></div></div><dl><div><dt>数据位置</dt><dd>云端数据库，按账号隔离</dd></div><div><dt>API 配置</dt><dd>服务端加密存储</dd></div><div><dt>项目主页</dt><dd><a href="https://github.com/dfaskl/job-tracker-desktop" target="_blank" rel="noreferrer">GitHub ↗</a></dd></div></dl></section>
</template>
<style scoped>
.preference-card,.about-card{display:grid;gap:14px}.api-card{grid-area:api}.about-card{grid-area:about}.section-head{display:flex;align-items:center;justify-content:space-between;gap:12px}.section-head h2{margin:4px 0 0}.section-head span{color:var(--accent,var(--color-primary));font-size:11px;font-weight:800;letter-spacing:.1em}.section-head>i{color:#758198;font-style:normal}.preference-card>p{margin:0}.api-form{display:grid;grid-template-columns:1fr 1fr;gap:12px}.api-form label{display:grid;gap:7px;color:var(--color-muted-foreground);font-size:13px;font-weight:700}.api-form .wide{grid-column:1/-1}.api-form .check{display:flex;align-items:center}.check input{flex:none;width:18px}.api-form button{justify-self:start}.about-brand{display:flex;align-items:center;gap:12px}.about-brand img{width:42px;height:42px}.about-brand h2,.about-brand p{margin:0}.about-card dl{margin:0}.about-card a{color:var(--accent);text-decoration:none}@media(max-width:560px){.api-form{grid-template-columns:1fr}.api-form .wide{grid-column:auto}}
</style>
