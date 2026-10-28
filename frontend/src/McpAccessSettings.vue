<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onDeactivated, ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'
import BaseSelect from './BaseSelect.vue'
import { api } from './api'
import { confirmDialog } from './modalDialogs'
import { useJobTrackerStore } from './jobTrackerStore'

type TokenInfo = { id:string; name:string; prefix:string; scopes:string[]; createdAt:number; expiresAt:number; lastUsedAt:number|null; revokedAt:number|null }
type AuditInfo = { tool:string; targetId:string; outcome:string; createdAt:number; tokenName:string }
const store = useJobTrackerStore()
const endpoint = `${window.location.origin}/mcp`
const items = ref<TokenInfo[]>([]), audit = ref<AuditInfo[]>([])
const loading = ref(false), busy = ref(false), error = ref(''), message = ref(''), issuedToken = ref('')
const name = ref('我的 Codex'), days = ref('90'), selectedScopes = ref(['applications:read', 'events:read'])
const permissions = [
  {value:'applications:read',label:'读取投递'}, {value:'applications:write',label:'修改投递'},
  {value:'events:read',label:'读取日程'}, {value:'events:write',label:'修改日程'}
]
const expiryOptions = [{value:'30',label:'30 天'},{value:'90',label:'90 天'},{value:'365',label:'365 天'}]
const toolLabels:Record<string,string> = {search_applications:'查询投递',get_application:'读取投递',create_application:'新增投递',update_application:'修改投递',list_events:'查询日程',get_event:'读取日程',create_event:'新增日程',update_event:'修改日程',resolve_event:'更新日程状态'}
const config = computed(() => `[mcp_servers.careerflow]\nurl = ${JSON.stringify(endpoint)}\nhttp_headers = { Authorization = ${JSON.stringify(`Bearer ${issuedToken.value || '替换为你的个人访问令牌'}`)} }\n`)
let revision = 0
let credentialGeneration = 0
let active = true
function date(value:number|null) { return value ? new Date(value).toLocaleString('zh-CN',{hour12:false}) : '尚未使用' }
function tokenStatus(token:TokenInfo) { return token.revokedAt ? '已撤销' : token.expiresAt <= Date.now() ? '已过期' : '有效' }
function scopeLabel(scope:string) { return permissions.find(item=>item.value===scope)?.label || scope }
async function load() {
  const account = store.user.value?.email
  if (!account) return
  const request = ++revision
  loading.value = true
  try {
    const result = await api<{items:TokenInfo[];audit:AuditInfo[]}>('/api/poc/mcp/tokens')
    if (request === revision && store.user.value?.email === account) { items.value=result.items; audit.value=result.audit }
  } catch (exception) {
    if (request === revision) error.value=exception instanceof Error ? exception.message : '无法加载访问令牌'
  } finally { if (request === revision) loading.value=false }
}
async function create() {
  if (!name.value.trim() || !selectedScopes.value.length) { error.value='请填写名称并选择至少一项权限'; return }
  const account = store.user.value?.email
  const generation = credentialGeneration
  busy.value=true; error.value=''; message.value=''; issuedToken.value=''
  try {
    const result=await api<{token:string;metadata:TokenInfo}>('/api/poc/mcp/tokens',{
      method:'POST',body:JSON.stringify({name:name.value.trim(),days:Number(days.value),scopes:selectedScopes.value})
    })
    if (store.user.value?.email !== account || generation !== credentialGeneration || !active) return
    issuedToken.value=result.token
    items.value=[result.metadata,...items.value]
    message.value='令牌已创建，请复制保存'
  } catch (exception) { error.value=exception instanceof Error ? exception.message : '创建失败' }
  finally { busy.value=false }
}
async function revoke(token:TokenInfo) {
  const account=store.user.value?.email
  if (!await confirmDialog(`撤销“${token.name}”后，使用它的 Codex 将无法继续访问。`,{title:'撤销访问令牌',confirmText:'撤销',tone:'danger'})) return
  if (account !== store.user.value?.email) return
  busy.value=true; error.value=''; message.value=''
  try {
    await api(`/api/poc/mcp/tokens/${encodeURIComponent(token.id)}`,{method:'DELETE'})
    if (account === store.user.value?.email) { issuedToken.value=''; message.value='令牌已撤销'; await load() }
  } catch (exception) { error.value=exception instanceof Error ? exception.message : '撤销失败' }
  finally { busy.value=false }
}
async function copy(value:string, label:string) {
  error.value=''
  try {
    if (navigator.clipboard?.writeText) await navigator.clipboard.writeText(value)
    else {
      const previous=document.activeElement as HTMLElement|null
      const input=document.createElement('textarea')
      input.value=value; input.readOnly=true; input.style.position='fixed'; input.style.opacity='0'
      document.body.appendChild(input); input.select()
      try { if (!document.execCommand('copy')) throw new Error('请选中文本后手动复制') }
      finally { input.remove(); previous?.focus() }
    }
    message.value=`${label}已复制`
  } catch { error.value='浏览器未允许自动复制，请选中文本后手动复制' }
}
watch(()=>store.user.value?.email,()=>{
  revision++; credentialGeneration++; items.value=[]; audit.value=[]; issuedToken.value=''; error.value=''; message.value=''
  selectedScopes.value=['applications:read','events:read']; name.value='我的 Codex'; days.value='90'
  if (store.user.value) void load()
},{immediate:true})
onActivated(()=>{ active=true; void load() })
function clearCredentials() { active=false; revision++; credentialGeneration++; issuedToken.value=''; loading.value=false; error.value=''; message.value='' }
onDeactivated(clearCredentials)
onBeforeUnmount(clearCredentials)
</script>

<template>
  <section class="card mcp-settings" :aria-busy="busy || loading">
    <div class="endpoint-row"><div><span class="muted">服务地址</span><code>{{endpoint}}</code></div><button type="button" class="secondary icon-button" title="复制 MCP 地址" aria-label="复制 MCP 地址" @click="copy(endpoint,'地址')"><AppIcon name="copy" /></button></div>
    <p class="muted">在同一内网的 Codex 中连接此地址。每个令牌仅能访问当前账户。</p>
    <p v-if="error" class="form-error" role="alert">{{error}}</p>
    <p v-if="message" class="feedback" role="status">{{message}}</p>
    <form v-if="store.user.value" class="token-form" @submit.prevent="create">
      <div class="form-fields"><label>令牌名称<input v-model="name" maxlength="80" required :disabled="busy" autocomplete="off" /></label><label>有效期<BaseSelect v-model="days" :options="expiryOptions" :disabled="busy" aria-label="令牌有效期" /></label></div>
      <fieldset :disabled="busy"><legend>访问权限</legend><div class="scope-options"><label v-for="permission in permissions" :key="permission.value"><input v-model="selectedScopes" type="checkbox" :value="permission.value" />{{permission.label}}</label></div><small>修改权限会同时授予相应的读取权限。</small></fieldset>
      <button type="submit" :disabled="busy || !selectedScopes.length || !name.trim()"><AppIcon name="key" :size="16" />{{busy?'处理中…':'创建访问令牌'}}</button>
    </form>
    <div v-if="issuedToken" class="issued-token">
      <strong>完整令牌只显示这一次</strong><p class="muted">请仅保存在你信任的设备上。关闭此页面后无法再次查看。</p>
      <label>个人访问令牌<textarea :value="issuedToken" rows="2" readonly spellcheck="false" @focus="($event.target as HTMLTextAreaElement).select()" /></label>
      <div class="token-actions"><button type="button" class="secondary" @click="copy(issuedToken,'令牌')"><AppIcon name="copy" :size="16" />复制令牌</button><button type="button" @click="copy(config,'Codex 配置')">复制 Codex 配置</button><button type="button" class="secondary" @click="issuedToken=''; message=''">我已保存</button></div>
    </div>
    <details class="connection-help"><summary>如何在 Codex 中连接</summary><ol><li>创建令牌后，点击“复制 Codex 配置”。</li><li>在 Codex 的 MCP 设置中打开配置文件，将复制的内容合并到 <code>config.toml</code>，保存后重启客户端。</li><li>若已存在 <code>[mcp_servers.careerflow]</code>，替换该段配置。配置中包含令牌，请仅保存在本机。</li></ol><pre>{{config}}</pre><p class="muted">也可以通过环境变量保存令牌，详见项目文档 <code>docs/mcp.md</code>。</p></details>
    <div class="list-heading"><h3>访问令牌</h3><button type="button" class="secondary icon-button" :disabled="loading || busy" title="刷新令牌列表" aria-label="刷新令牌列表" @click="load"><AppIcon name="refresh" /></button></div>
    <p v-if="loading && !items.length" class="muted" role="status">正在加载…</p><p v-else-if="!items.length" class="muted">尚未创建访问令牌。</p>
    <ul v-else class="token-list"><li v-for="token in items" :key="token.id"><div class="token-info"><div class="token-name"><strong>{{token.name}}</strong><span :class="['status-tag',{inactive:tokenStatus(token)!=='有效'}]">{{tokenStatus(token)}}</span></div><code>{{token.prefix}}…</code><p>{{token.scopes.map(scopeLabel).join(' · ')}}</p><small>到期 {{date(token.expiresAt)}} · 最近使用 {{date(token.lastUsedAt)}}</small></div><button v-if="!token.revokedAt" type="button" class="secondary" :disabled="busy" @click="revoke(token)"><AppIcon name="ban" :size="16" />撤销</button></li></ul>
    <details v-if="audit.length" class="audit"><summary>最近操作</summary><ul><li v-for="(entry,index) in audit" :key="index"><div><strong>{{toolLabels[entry.tool]||entry.tool}}</strong><small>{{entry.tokenName}} · {{date(entry.createdAt)}}</small></div><span>{{entry.outcome==='success'?'成功':entry.outcome==='failed'?'失败':'处理中'}}</span></li></ul></details>
  </section>
</template>

<style scoped>
.mcp-settings{display:grid;gap:18px;padding:22px;min-width:0}.mcp-settings p{margin:0}.muted,.mcp-settings small{color:var(--color-muted-foreground);font-size:13px;line-height:1.6}.endpoint-row,.list-heading,.token-list li,.audit li{display:flex;align-items:center;justify-content:space-between;gap:14px}.endpoint-row>div{display:grid;gap:5px;min-width:0}.endpoint-row code{overflow-wrap:anywhere;font-size:14px;color:var(--color-foreground)}.mcp-settings h3{font-size:15px;margin:0}.token-form{display:grid;gap:16px}.form-fields{display:grid;grid-template-columns:minmax(0,1fr) minmax(120px,180px);gap:14px;max-width:720px}.token-form label,.issued-token>label{display:grid;gap:7px;font-size:13px;font-weight:600}.token-form input:not([type=checkbox]),.issued-token textarea{width:100%;min-height:44px}.token-form fieldset{border:0;padding:0;margin:0;min-width:0}.token-form legend{font-size:13px;font-weight:600;margin-bottom:5px}.scope-options{display:flex;flex-wrap:wrap;gap:4px 20px}.scope-options label{display:flex;align-items:center;gap:8px;min-height:44px;cursor:pointer;font-weight:400}.scope-options input{width:17px;height:17px;accent-color:var(--color-primary)}.token-form>button{justify-self:start}.mcp-settings button{display:inline-flex;align-items:center;justify-content:center;gap:7px;min-height:44px}.icon-button{min-width:44px}.form-error{color:var(--color-destructive,#c33)}.feedback{color:var(--color-primary);font-size:13px}.issued-token{display:grid;gap:12px;padding:18px;border-radius:12px;background:color-mix(in srgb,var(--color-primary) 7%,var(--color-card));border:1px solid color-mix(in srgb,var(--color-primary) 28%,var(--color-border))}.issued-token textarea{font:13px/1.6 ui-monospace,monospace;resize:vertical;overflow-wrap:anywhere}.token-actions{display:flex;flex-wrap:wrap;gap:8px}.mcp-settings details summary{cursor:pointer;min-height:44px;display:flex;align-items:center;font-size:13px;font-weight:600;color:var(--color-primary)}.connection-help ol{padding-left:20px;font-size:13px;line-height:1.9}.connection-help pre{white-space:pre-wrap;overflow-wrap:anywhere;padding:14px;border-radius:8px;background:var(--color-muted);font-size:12px;line-height:1.7}.token-list,.audit ul{list-style:none;padding:0;margin:0;display:grid;gap:10px}.token-list li{background:var(--color-muted);border-radius:10px;padding:14px}.token-info{display:grid;gap:5px;min-width:0}.token-name{display:flex;flex-wrap:wrap;align-items:center;gap:9px}.token-name strong{overflow-wrap:anywhere;font-size:14px}.token-info code,.token-info p{font-size:12px;color:var(--color-muted-foreground)}.token-info small{font-size:12px}.status-tag{font-size:11px;color:var(--color-primary);padding:3px 8px;border-radius:20px;background:color-mix(in srgb,var(--color-primary) 10%,var(--color-card))}.status-tag.inactive{color:var(--color-muted-foreground);background:var(--color-card)}.audit li{padding:8px 0;font-size:12px}.audit li>div{display:grid;gap:3px}.audit small{font-size:12px}.audit li>span{color:var(--color-muted-foreground)}
@media(max-width:560px){.mcp-settings{padding:16px;gap:14px}.form-fields{grid-template-columns:minmax(0,1fr)}.token-list li{align-items:flex-start}.token-info small{display:block}.token-list li>button{flex-shrink:0}.scope-options{gap:0 14px}.token-actions>button{flex:1 1 auto}}
</style>
