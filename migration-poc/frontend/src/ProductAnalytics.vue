<script setup lang="ts">
import { computed } from 'vue'
import { isFormalInterview } from './eventClassification'
import { useJobTrackerStore } from './jobTrackerStore'

const store = useJobTrackerStore()
const stages = ['已投递', '测评', '笔试', '面试', 'Offer', '已结束']
const total = computed(() => store.applications.value.length)
const count = (predicate: (item: Record<string, unknown>) => boolean) => store.applications.value.filter(predicate).length
const interviews = computed(() => count(item => hasInterviewProgress(item)))
const offers = computed(() => count(item => item.stage === 'Offer' || item.status === '已通过'))
const interviewRate = computed(() => total.value ? Math.round(interviews.value / total.value * 100) : 0)
const offerRate = computed(() => total.value ? Math.round(offers.value / total.value * 100) : 0)
const byStage = computed(() => stages.map(name => ({ name, count: count(item => item.stage === name) })))
const byChannel = computed(() => grouped('channel'))
const maxStage = computed(() => Math.max(1, ...byStage.value.map(item => item.count)))
const maxChannel = computed(() => Math.max(1, ...byChannel.value.map(item => item.count)))
const trend = computed(() => {
  const months: { key: string; label: string; count: number }[] = []
  for (let offset = 11; offset >= 0; offset--) {
    const date = new Date(); date.setDate(1); date.setMonth(date.getMonth() - offset)
    const key = `${date.getFullYear()}-${pad(date.getMonth() + 1)}`
    months.push({ key, label: `${date.getMonth() + 1}月`, count: count(item => String(item.appliedDate || '').startsWith(key)) })
  }
  return months
})
const maxTrend = computed(() => Math.max(1, ...trend.value.map(item => item.count)))
const interviewRecords = computed(() => store.applications.value
  .filter(item => hasInterviewProgress(item))
  .map(item => {
    const related = store.events.value
      .filter(event => event.applicationId === item.id && !event.missed && isFormalInterview(event))
      .sort((a,b) => interviewTime(b).localeCompare(interviewTime(a)))
    const ended = item.stage === '已结束' || ['未通过','已放弃','已结束'].includes(String(item.status || ''))
    return { item, related, ended, latest:related[0] ? interviewTime(related[0]) : '' }
  })
  .sort((a,b) => Number(a.ended) - Number(b.ended) || b.latest.localeCompare(a.latest) || String(a.item.company || '').localeCompare(String(b.item.company || ''),'zh-CN')))

function pad(value: number) { return String(value).padStart(2, '0') }
function interviewTime(event: Record<string, unknown>) { return String(event.completedAt || event.startsAt || event.start || event.date || '') }
function displayDate(value: string) {
  if (!value) return '时间未记录'
  const date = new Date(value.replace(' ','T'))
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN',{month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit',hour12:false})
}
function hasInterviewProgress(item: Record<string, unknown>) {
  if (['面试', 'Offer'].includes(String(item.stage || '')) || item.status === '已通过') return true
  return store.events.value.some(event => event.applicationId === item.id && !event.missed && isFormalInterview(event))
}
function grouped(field: string) {
  const counts = new Map<string, number>()
  store.applications.value.forEach(item => { const name = String(item[field] || '未填写'); counts.set(name, (counts.get(name) || 0) + 1) })
  return [...counts].map(([name, count]) => ({ name, count })).sort((a, b) => b.count - a.count)
}
</script>

<template>
  <p v-if="store.loading.value">正在汇总完整业务数据…</p>
  <section v-else-if="!store.user.value" class="card"><h2>请先登录</h2><p>登录后查看投递分析。</p></section>
  <div v-else class="analytics-layout">
    <main class="analytics-main">
      <div class="metrics">
      <article><span>投递总数</span><strong>{{ total }}</strong><small>全部投递记录</small></article>
      <article><span>有过面试</span><strong>{{ interviews }}</strong><small>占投递总数 {{ interviewRate }}%</small></article>
      <article><span>通过 / Offer 数</span><strong>{{ offers }}</strong><small>占投递总数 {{ offerRate }}%</small></article>
    </div>
    <div class="two-column">
      <section class="card"><h2>阶段分布</h2><div v-for="item in byStage" :key="item.name" class="bar-row"><span>{{ item.name }}</span><div role="progressbar" :aria-label="`${item.name}：${item.count}`" aria-valuemin="0" :aria-valuemax="maxStage" :aria-valuenow="item.count"><i :style="{width:`${item.count/maxStage*100}%`}" /></div><b>{{ item.count }}</b></div></section>
      <section class="card"><h2>渠道分布</h2><div v-for="item in byChannel" :key="item.name" class="bar-row"><span>{{ item.name }}</span><div role="progressbar" :aria-label="`${item.name}：${item.count}`" aria-valuemin="0" :aria-valuemax="maxChannel" :aria-valuenow="item.count"><i :style="{width:`${item.count/maxChannel*100}%`}" /></div><b>{{ item.count }}</b></div><p v-if="!byChannel.length">暂无数据。</p></section>
    </div>
      <section class="card"><div class="section-head"><div><h2>近 12 个月投递趋势</h2><p>按投递日期汇总</p></div></div><div class="trend"><div v-for="item in trend" :key="item.key"><span role="img" :aria-label="`${item.label}投递 ${item.count} 次`"><i :style="{height:`${Math.max(3,item.count/maxTrend*100)}%`}" /><b>{{ item.count }}</b></span><small>{{ item.label }}</small></div></div></section>
    </main>
    <aside class="interview-panel card" aria-labelledby="interview-records-title">
      <header><div><span>只读概览</span><h2 id="interview-records-title">面试投递记录</h2><p>展示所有进入过正式面试环节的投递</p></div><b>{{interviewRecords.length}} 条</b></header>
      <div v-if="interviewRecords.length" class="interview-list">
        <article v-for="record in interviewRecords" :key="record.item.id" :class="record.ended?'is-ended':'is-active'">
          <div class="record-head"><strong>{{record.item.company||'未填写公司'}}</strong><span>{{record.ended?'已结束':'正在推进'}}</span></div>
          <p>{{record.item.position||'未填写岗位'}}</p>
          <dl><div><dt>当前阶段</dt><dd>{{record.item.stage||'未设置'}}</dd></div><div><dt>当前状态</dt><dd>{{record.item.status||'未设置'}}</dd></div></dl>
          <footer><span>{{record.related.length?`${record.related.length} 场正式面试`:'阶段记录显示已进入面试'}}</span><time>{{record.latest?`最近：${displayDate(record.latest)}`:'面试时间未记录'}}</time></footer>
        </article>
      </div>
      <p v-else class="interview-empty">暂无进入过正式面试环节的投递。</p>
    </aside>
  </div>
</template>

<style scoped>
.analytics-layout{display:grid;grid-template-columns:minmax(0,1.7fr) minmax(310px,.68fr);gap:20px;align-items:start;padding:22px 0 28px}.analytics-main{display:grid;min-width:0;gap:18px}.analytics-main .metrics{margin-top:0}
.metrics { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:16px; margin-top:22px; }
.metrics article { position:relative; display:grid; min-height:142px; align-content:space-between; gap:7px; overflow:hidden; padding:20px 22px 18px 25px; border:1px solid var(--color-border); border-radius:var(--radius-panel); background:var(--color-paper); }
.metrics article::before { content:""; position:absolute; inset:0 auto 0 0; width:4px; background:var(--color-primary); }
.metrics article:nth-child(2)::before { background:var(--color-progress); }
.metrics article:nth-child(3)::before { background:var(--color-stage); }
.metrics span,.metrics small,.section-head p { color:var(--color-muted-foreground); }
.metrics span { font-size:13px; font-weight:700; }
.metrics strong { color:var(--color-foreground); font-family:"Fira Code","Noto Sans SC",sans-serif; font-size:34px; line-height:1; letter-spacing:-.06em; }
.metrics small { font-size:12px; }
.two-column { display:grid; grid-template-columns:minmax(0,1fr) minmax(0,1fr); gap:18px; }
.two-column>.card { min-height:286px; }
.card h2 { margin:0 0 22px; font-size:19px; }
.bar-row { display:grid; grid-template-columns:minmax(76px,auto) minmax(100px,1fr) 38px; align-items:center; gap:12px; margin-top:15px; }
.bar-row span { overflow:hidden; color:var(--color-muted-foreground); font-size:13px; text-overflow:ellipsis; white-space:nowrap; }
.bar-row>div { height:8px; overflow:hidden; border-radius:2px; background:var(--color-muted); }
.bar-row i { display:block; height:100%; border-radius:2px; background:linear-gradient(90deg,var(--color-primary),color-mix(in srgb,var(--color-primary) 68%,#51b6cc)); }
.two-column>.card:nth-child(2) .bar-row i { background:linear-gradient(90deg,var(--color-progress),#4aa981); }
.bar-row b { color:var(--color-foreground); font-family:"Fira Code",monospace; text-align:right; }
.section-head { display:flex; align-items:center; justify-content:space-between; }
.section-head h2 { margin-bottom:4px; }
.section-head p { margin:0; font-size:12px; }
.trend { position:relative; display:grid; grid-template-columns:repeat(12,minmax(34px,1fr)); align-items:end; height:230px; gap:8px; margin-top:18px; padding-top:12px; background:repeating-linear-gradient(to bottom,transparent 0 43px,color-mix(in srgb,var(--color-border) 55%,transparent) 43px 44px); }
.trend>div { display:grid; grid-template-rows:190px auto; gap:8px; text-align:center; }
.trend span { position:relative; display:flex; align-items:end; justify-content:center; height:100%; border-bottom:1px solid var(--color-border-strong); }
.trend i { display:block; width:min(34px,72%); border-radius:4px 4px 0 0; background:var(--color-primary); box-shadow:inset 0 1px 0 rgba(255,255,255,.35); }
.trend b { position:absolute; top:0; color:var(--color-muted-foreground); font:600 11px "Fira Code",monospace; }
.trend small { color:var(--color-muted-foreground); font-size:11px; }
.interview-panel{min-width:0;margin:0;padding:18px}.interview-panel>header{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;padding-bottom:14px;border-bottom:1px solid var(--color-border)}.interview-panel>header>div{display:grid;min-width:0;gap:3px}.interview-panel>header span{color:var(--color-primary);font-size:11px;font-weight:800;letter-spacing:.08em}.interview-panel>header h2{margin:0;font-size:19px}.interview-panel>header p{margin:0;color:var(--color-muted-foreground);font-size:12px;line-height:1.5}.interview-panel>header>b{flex:none;padding:5px 8px;border-radius:999px;color:#315e64;background:#e6f3f1;font-size:11px}.interview-list{display:grid;gap:10px;margin-top:14px}.interview-list article{display:grid;gap:8px;padding:13px 14px;border:1px solid;border-left-width:5px;border-radius:11px}.interview-list article.is-active{border-color:#a9d7bd;border-left-color:#278759;background:#eaf7ef}.interview-list article.is-ended{border-color:#e2b8b4;border-left-color:#bd4942;background:#fbeceb}.record-head{display:flex;align-items:center;justify-content:space-between;gap:10px}.record-head strong{min-width:0;overflow:hidden;color:var(--color-foreground);font-size:14px;text-overflow:ellipsis;white-space:nowrap}.record-head span{flex:none;padding:4px 7px;border-radius:999px;font-size:10px;font-weight:800;white-space:nowrap}.is-active .record-head span{color:#12623d;background:#d1eddc}.is-ended .record-head span{color:#98342f;background:#f4d3d0}.interview-list article>p{margin:0;color:#4e5d58;font-size:12px}.interview-list dl{display:grid;grid-template-columns:1fr 1fr;gap:7px;margin:0}.interview-list dl>div{display:grid;gap:2px;padding:7px 8px;border-radius:7px;background:rgba(255,255,255,.58)}.interview-list dt{color:#718079;font-size:10px}.interview-list dd{margin:0;color:#34443d;font-size:11px;font-weight:700}.interview-list footer{display:flex;align-items:center;justify-content:space-between;gap:8px;color:#68766f;font-size:10px}.interview-list time{text-align:right}.interview-empty{margin:14px 0 0;padding:24px 12px;color:var(--color-muted-foreground);background:var(--color-muted);text-align:center}
@media(max-width:1180px){.analytics-layout{grid-template-columns:minmax(0,1fr) minmax(290px,.55fr)}.metrics{grid-template-columns:1fr 1fr}.two-column{grid-template-columns:1fr}.trend{gap:4px}}
@media(max-width:900px){.analytics-layout{grid-template-columns:1fr}.interview-panel{grid-row:2}.interview-list{grid-template-columns:repeat(2,minmax(0,1fr))}}
@media(max-width:640px){.metrics{grid-template-columns:1fr}.metrics article{min-height:118px}.bar-row{grid-template-columns:72px 1fr 30px}.analytics-main>.card:last-child{overflow-x:auto}.trend{min-width:620px}}
@media(max-width:640px){.analytics-layout{padding-top:14px}.interview-list{grid-template-columns:1fr}.interview-list footer{align-items:flex-start;flex-direction:column}.interview-list time{text-align:left}}
</style>
