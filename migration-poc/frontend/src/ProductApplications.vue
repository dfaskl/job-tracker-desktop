<script setup lang="ts">
import { computed, ref } from 'vue'
import { useJobTrackerStore, type JobApplication } from './jobTrackerStore'

const store = useJobTrackerStore()
const query = ref('')
const stage = ref('全部')
const selected = ref<JobApplication | null>(null)
const stages = ['全部', '已投递', '测评', '笔试', '面试', 'Offer', '已结束']

const filtered = computed(() => {
  const keyword = query.value.trim().toLocaleLowerCase('zh-CN')
  return store.applications.value.filter((item) => {
    const stageMatches = stage.value === '全部' || item.stage === stage.value
    const keywordMatches = !keyword || `${item.company || ''} ${item.position || ''} ${item.city || ''} ${item.channel || ''} ${item.stage || ''} ${item.status || ''} ${item.notes || ''}`.toLocaleLowerCase('zh-CN').includes(keyword)
    return stageMatches && keywordMatches
  }).sort((a, b) => String(b.updatedAt || b.appliedDate || '').localeCompare(String(a.updatedAt || a.appliedDate || '')))
})
const selectedEvents = computed(() => selected.value ? store.events.value.filter((event) => event.applicationId === selected.value?.id) : [])
const selectedTimeline = computed(() => Array.isArray(selected.value?.timeline) ? selected.value?.timeline as Record<string, unknown>[] : [])

function text(value: unknown, fallback = '未填写') { return String(value || fallback) }
</script>

<template>
  <section class="card applications-workspace">
    <div class="section-head">
      <div><span class="section-kicker">APPLICATIONS</span><h2>全部投递</h2></div>
      <span v-if="store.user.value" class="count">{{ filtered.length }} / {{ store.applications.value.length }}</span>
    </div>
    <div v-if="store.user.value" class="toolbar">
      <input v-model="query" type="search" placeholder="搜索公司、岗位、地点、渠道或备注" />
      <select v-model="stage"><option v-for="item in stages" :key="item">{{ item }}</option></select>
      <button class="secondary" type="button" @click="store.refresh">刷新</button>
    </div>
    <div v-if="store.user.value && filtered.length" class="application-grid">
      <button v-for="item in filtered" :key="item.id" type="button" class="application-card" @click="selected = item">
        <div class="card-title"><strong>{{ text(item.company, '未填写公司') }}</strong><span>{{ text(item.position, '未填写岗位') }}</span></div>
        <div class="badges"><b>{{ text(item.stage, '未标记阶段') }}</b><i>{{ text(item.status, '未标记状态') }}</i></div>
        <p>{{ text(item.city, '地点未填') }} · {{ text(item.channel, '渠道未填') }}</p>
        <small>{{ item.appliedDate ? `投递于 ${item.appliedDate}` : '投递日期未填' }}</small>
      </button>
    </div>
    <p v-else-if="store.user.value" class="empty">没有符合条件的投递记录。</p>
    <p v-else>登录后查看完整投递列表和详情。</p>
  </section>

  <div v-if="selected" class="modal-backdrop" @click.self="selected = null">
    <section class="detail-modal" role="dialog" aria-modal="true" aria-label="投递详情">
      <button class="close" type="button" aria-label="关闭" @click="selected = null">×</button>
      <div class="detail-head"><div><h2>{{ text(selected.company) }}</h2><p>{{ text(selected.position) }} · {{ text(selected.city, '地点未填') }}</p></div><div class="badges"><b>{{ text(selected.stage) }}</b><i>{{ text(selected.status) }}</i></div></div>
      <h3>基本信息</h3>
      <dl><div><dt>投递日期</dt><dd>{{ text(selected.appliedDate) }}</dd></div><div><dt>投递渠道</dt><dd>{{ text(selected.channel) }}</dd></div><div class="wide"><dt>备注</dt><dd>{{ text(selected.notes, '暂无备注') }}</dd></div></dl>
      <h3>安排记录</h3>
      <div v-if="selectedEvents.length" class="timeline"><article v-for="event in selectedEvents" :key="event.id"><strong>{{ text(event.title || event.type, '未命名安排') }}</strong><span>{{ text(event.start || event.date) }} · {{ text(event.status, event.completed ? '已完成' : '待处理') }}</span></article></div><p v-else class="empty">还没有笔试或面试安排。</p>
      <h3>状态历史</h3>
      <div v-if="selectedTimeline.length" class="timeline"><article v-for="(item, index) in selectedTimeline" :key="String(item.id || index)"><strong>{{ text(item.title, '状态更新') }}</strong><span>{{ text(item.at, '') }}</span></article></div><p v-else class="empty">暂无历史。</p>
      <p class="readonly-note">当前连接生产数据只读；编辑、追加安排、快速 Offer/未通过与删除逻辑将在隔离写库启用后开放。</p>
    </section>
  </div>
</template>

<style scoped>
.section-head,.detail-head,.toolbar { display:flex;align-items:center;justify-content:space-between;gap:14px }.section-kicker{color:#526ddd;font-size:11px;font-weight:800;letter-spacing:.1em}.section-head h2{margin:4px 0 0}.count{padding:6px 10px;border-radius:999px;background:#edf1ff;color:#3d55bd;font-weight:800}.toolbar{margin:18px 0}.toolbar input{flex:1}.toolbar select{width:150px}.application-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:12px}.application-card{display:grid;gap:11px;padding:17px;color:#172033;border:1px solid #e2e7f0;background:#fbfcfe;text-align:left}.application-card:hover{border-color:#9dacdf;transform:translateY(-1px)}.card-title{display:grid;gap:4px}.card-title strong{font-size:17px}.card-title span,.application-card p,.application-card small{color:#667085}.application-card p{margin:0}.badges{display:flex;gap:7px;flex-wrap:wrap}.badges b,.badges i{padding:5px 8px;border-radius:999px;font-size:12px;font-style:normal}.badges b{color:#3d55bd;background:#edf1ff}.badges i{color:#475467;background:#eef2f6}.modal-backdrop{position:fixed;inset:0;z-index:40;display:grid;place-items:center;padding:20px;background:rgba(17,24,39,.58)}.detail-modal{position:relative;width:min(760px,100%);max-height:90vh;overflow:auto;padding:28px;border-radius:18px;background:#fff;box-shadow:0 30px 80px rgba(0,0,0,.3)}.close{position:absolute;top:14px;right:14px;padding:4px 11px;color:#475467;background:#eef2f6;font-size:22px}.detail-head{padding-right:38px}.detail-head h2{margin:0}.detail-head p{margin:5px 0 0}.detail-modal h3{margin:24px 0 10px;font-size:14px}dl{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin:0}dl div{padding:12px;border-radius:10px;background:#f7f9fc}dl .wide{grid-column:1/-1}dt{color:#667085;font-size:11px}dd{margin:5px 0 0;white-space:pre-wrap}.timeline{display:grid;gap:8px}.timeline article{display:grid;gap:4px;padding:11px 13px;border-left:3px solid #8396e9;background:#f7f9fc}.timeline span{color:#667085;font-size:12px}.readonly-note{margin-top:22px;padding:12px;border-radius:10px;color:#476050;background:#edf8f1;font-size:12px}.empty{color:#667085}@media(max-width:720px){.toolbar{align-items:stretch;flex-direction:column}.toolbar select{width:auto}.application-grid,dl{grid-template-columns:1fr}}
</style>
