<script setup lang="ts">
import { computed, ref } from 'vue'
import { useJobTrackerStore } from './jobTrackerStore'

const store = useJobTrackerStore()
const stages = ['已投递', '测评', '笔试', '面试', 'Offer', '已结束']
const heatMonth = ref(new Date(new Date().getFullYear(), new Date().getMonth(), 1))
const total = computed(() => store.applications.value.length)
const count = (predicate: (item: Record<string, unknown>) => boolean) => store.applications.value.filter(predicate).length
const interviews = computed(() => count(item => item.stage === '面试' || item.stage === 'Offer'))
const offers = computed(() => count(item => item.stage === 'Offer' || item.status === '已通过'))
const ended = computed(() => count(item => item.stage === '已结束' || ['未通过', '已放弃', '已结束'].includes(String(item.status || ''))))
const active = computed(() => total.value - ended.value)
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
const heatmap = computed(() => {
  const year = heatMonth.value.getFullYear(), month = heatMonth.value.getMonth()
  const prefix = `${year}-${pad(month + 1)}`, offset = (new Date(year, month, 1).getDay() + 6) % 7
  const days = new Date(year, month + 1, 0).getDate()
  const counts = new Map<string, number>()
  store.applications.value.forEach(item => { const date = String(item.appliedDate || ''); if (date.startsWith(prefix)) counts.set(date, (counts.get(date) || 0) + 1) })
  const max = Math.max(1, ...counts.values())
  return Array.from({ length: 42 }, (_, index) => {
    const day = index - offset + 1
    if (day < 1 || day > days) return { outside: true, day: 0, count: 0, level: 0, key: `${index}` }
    const key = `${prefix}-${pad(day)}`, value = counts.get(key) || 0
    return { outside: false, day, count: value, level: value ? Math.max(1, Math.ceil(value / max * 4)) : 0, key }
  })
})
const heatTotal = computed(() => heatmap.value.reduce((sum, cell) => sum + cell.count, 0))

function pad(value: number) { return String(value).padStart(2, '0') }
function grouped(field: string) {
  const counts = new Map<string, number>()
  store.applications.value.forEach(item => { const name = String(item[field] || '未填写'); counts.set(name, (counts.get(name) || 0) + 1) })
  return [...counts].map(([name, count]) => ({ name, count })).sort((a, b) => b.count - a.count)
}
function moveHeatMonth(offset: number) { heatMonth.value = new Date(heatMonth.value.getFullYear(), heatMonth.value.getMonth() + offset, 1) }
</script>

<template>
  <p v-if="store.loading.value">正在汇总完整业务数据…</p>
  <section v-else-if="!store.user.value" class="card"><h2>请先登录</h2><p>登录后查看投递分析。</p></section>
  <template v-else>
    <div class="metrics">
      <article><span>投递总数</span><strong>{{ total }}</strong><small>{{ active }} 个仍在推进</small></article>
      <article><span>进入面试</span><strong>{{ interviews }}</strong><small>转化率 {{ interviewRate }}%</small></article>
      <article><span>Offer / 通过</span><strong>{{ offers }}</strong><small>成功率 {{ offerRate }}%</small></article>
      <article><span>结束 / 未通过</span><strong>{{ ended }}</strong><small>历史沉淀</small></article>
    </div>
    <div class="two-column">
      <section class="card"><h2>阶段分布</h2><div v-for="item in byStage" :key="item.name" class="bar-row"><span>{{ item.name }}</span><div><i :style="{width:`${item.count/maxStage*100}%`}" /></div><b>{{ item.count }}</b></div></section>
      <section class="card"><h2>渠道分布</h2><div v-for="item in byChannel" :key="item.name" class="bar-row"><span>{{ item.name }}</span><div><i :style="{width:`${item.count/maxChannel*100}%`}" /></div><b>{{ item.count }}</b></div><p v-if="!byChannel.length">暂无数据。</p></section>
    </div>
    <section class="card"><div class="section-head"><div><h2>近 12 个月投递趋势</h2><p>按投递日期汇总</p></div></div><div class="trend"><div v-for="item in trend" :key="item.key"><span><i :style="{height:`${Math.max(3,item.count/maxTrend*100)}%`}" /><b>{{ item.count }}</b></span><small>{{ item.label }}</small></div></div></section>
    <section class="card"><div class="heat-head"><div><h2>每日投递热力图</h2><p>{{ heatMonth.getFullYear() }}年{{ heatMonth.getMonth()+1 }}月，共 {{ heatTotal }} 个岗位</p></div><div><button class="secondary" @click="moveHeatMonth(-1)">‹</button><button class="secondary" @click="moveHeatMonth(1)">›</button></div></div><div class="weekdays"><b v-for="day in ['一','二','三','四','五','六','日']" :key="day">{{ day }}</b></div><div class="heatmap"><i v-for="cell in heatmap" :key="cell.key" :class="[`level-${cell.level}`,{outside:cell.outside}]" :title="cell.outside?'':`${cell.key}：${cell.count} 个岗位`"><small v-if="!cell.outside">{{ cell.day }}</small></i></div></section>
  </template>
</template>

<style scoped>
.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-top:24px}.metrics article{display:grid;gap:7px;padding:20px;border:1px solid #e4e9f2;border-radius:15px;background:#fff}.metrics span,.metrics small,.section-head p,.heat-head p{color:#667085}.metrics strong{font-size:29px}.two-column{display:grid;grid-template-columns:1fr 1fr;gap:18px}.bar-row{display:grid;grid-template-columns:85px 1fr 34px;align-items:center;gap:11px;margin-top:14px}.bar-row span{overflow:hidden;color:#667085;text-overflow:ellipsis;white-space:nowrap}.bar-row>div{height:9px;overflow:hidden;border-radius:99px;background:#edf0f5}.bar-row i{display:block;height:100%;border-radius:inherit;background:#526ddd}.bar-row b{text-align:right}.section-head,.heat-head{display:flex;align-items:center;justify-content:space-between}.section-head h2,.heat-head h2{margin-bottom:4px}.section-head p,.heat-head p{margin:0}.trend{display:grid;grid-template-columns:repeat(12,1fr);align-items:end;height:210px;gap:8px;margin-top:20px}.trend>div{display:grid;grid-template-rows:175px auto;gap:8px;text-align:center}.trend span{position:relative;display:flex;align-items:end;justify-content:center;height:100%;border-bottom:1px solid #e4e9f2}.trend i{display:block;width:min(34px,75%);border-radius:7px 7px 0 0;background:linear-gradient(#7287e3,#526ddd)}.trend b{position:absolute;top:0;font-size:11px}.trend small{color:#667085}.heat-head>div:last-child{display:flex;gap:7px}.weekdays,.heatmap{display:grid;grid-template-columns:repeat(7,1fr);gap:6px}.weekdays{margin-top:18px}.weekdays b{padding:6px;color:#667085;font-size:11px;text-align:center}.heatmap i{display:grid;aspect-ratio:1.8;place-items:center;border-radius:5px;background:#eef1f5;font-style:normal}.heatmap i.outside{background:transparent}.heatmap .level-1{background:#dbe2ff}.heatmap .level-2{background:#aebcf4}.heatmap .level-3{color:#fff;background:#788de4}.heatmap .level-4{color:#fff;background:#4058bd}.heatmap small{font-size:10px}@media(max-width:800px){.metrics{grid-template-columns:1fr 1fr}.two-column{grid-template-columns:1fr}.trend{gap:3px}.trend small{font-size:9px}}@media(max-width:500px){.metrics{grid-template-columns:1fr}.heatmap i{aspect-ratio:1}}
</style>
