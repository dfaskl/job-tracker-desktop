<script setup lang="ts">
import { computed } from 'vue'
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
  return store.events.value.some(event => event.applicationId === item.id && !event.missed && /面试|[一二三四五六七八九]面|HR面|电话面/.test(String(event.type || '') + ' ' + String(event.title || '')))
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
      <section class="card"><h2>阶段分布</h2><div v-for="item in byStage" :key="item.name" class="bar-row"><span>{{ item.name }}</span><div><i :style="{width:`${item.count/maxStage*100}%`}" /></div><b>{{ item.count }}</b></div></section>
      <section class="card"><h2>渠道分布</h2><div v-for="item in byChannel" :key="item.name" class="bar-row"><span>{{ item.name }}</span><div><i :style="{width:`${item.count/maxChannel*100}%`}" /></div><b>{{ item.count }}</b></div><p v-if="!byChannel.length">暂无数据。</p></section>
    </div>
    <section class="card"><div class="section-head"><div><h2>近 12 个月投递趋势</h2><p>按投递日期汇总</p></div></div><div class="trend"><div v-for="item in trend" :key="item.key"><span><i :style="{height:`${Math.max(3,item.count/maxTrend*100)}%`}" /><b>{{ item.count }}</b></span><small>{{ item.label }}</small></div></div></section>
  </template>
</template>

<style scoped>
.metrics{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;margin-top:24px}.metrics article{display:grid;gap:7px;padding:20px;border:1px solid #e4e9f2;border-radius:15px;background:#fff}.metrics span,.metrics small,.section-head p{color:#667085}.metrics strong{font-size:29px}.two-column{display:grid;grid-template-columns:1fr 1fr;gap:18px}.bar-row{display:grid;grid-template-columns:85px 1fr 34px;align-items:center;gap:11px;margin-top:14px}.bar-row span{overflow:hidden;color:#667085;text-overflow:ellipsis;white-space:nowrap}.bar-row>div{height:9px;overflow:hidden;border-radius:99px;background:#edf0f5}.bar-row i{display:block;height:100%;border-radius:inherit;background:#526ddd}.bar-row b{text-align:right}.section-head{display:flex;align-items:center;justify-content:space-between}.section-head h2{margin-bottom:4px}.section-head p{margin:0}.trend{display:grid;grid-template-columns:repeat(12,1fr);align-items:end;height:210px;gap:8px;margin-top:20px}.trend>div{display:grid;grid-template-rows:175px auto;gap:8px;text-align:center}.trend span{position:relative;display:flex;align-items:end;justify-content:center;height:100%;border-bottom:1px solid #e4e9f2}.trend i{display:block;width:min(34px,75%);border-radius:7px 7px 0 0;background:linear-gradient(#7287e3,#526ddd)}.trend b{position:absolute;top:0;font-size:11px}.trend small{color:#667085}@media(max-width:800px){.metrics{grid-template-columns:1fr 1fr}.two-column{grid-template-columns:1fr}.trend{gap:3px}.trend small{font-size:9px}}@media(max-width:500px){.metrics{grid-template-columns:1fr}}
</style>
