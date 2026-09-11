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

function pad(value: number) { return String(value).padStart(2, '0') }
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
  <template v-else>
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
  </template>
</template>

<style scoped>
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
@media(max-width:900px){.metrics{grid-template-columns:1fr 1fr}.two-column{grid-template-columns:1fr}.trend{gap:4px}}
@media(max-width:640px){.metrics{grid-template-columns:1fr}.metrics article{min-height:118px}.bar-row{grid-template-columns:72px 1fr 30px}.card:last-child{overflow-x:auto}.trend{min-width:620px}}
</style>
