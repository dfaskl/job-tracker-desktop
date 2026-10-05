<script setup lang="ts">
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, ref, watch } from 'vue'
import { isFormalInterview } from './eventClassification'
import { useJobTrackerStore } from './jobTrackerStore'

const store = useJobTrackerStore()
const analyticsMain = ref<HTMLElement | null>(null)
const analyticsMainHeight = ref(0)
const interviewList = ref<HTMLElement | null>(null)
const revealActive = ref(false)
const recordsRevealActive = ref(false)
let mainResizeObserver: ResizeObserver | undefined
let interviewRevealObserver: IntersectionObserver | null = null
let nextInterviewRevealStart = 0
let viewActive = false
let revealRun = 0
const stages = ['已投递', '测评', '笔试', '面试', 'Offer', '已结束']
const total = computed(() => store.applications.value.length)
const count = (predicate: (item: Record<string, unknown>) => boolean) => store.applications.value.filter(predicate).length
const interviews = computed(() => count(item => hasInterviewProgress(item)))
const offers = computed(() => count(item => item.stage === 'Offer' || item.status === '已通过'))
const interviewRate = computed(() => total.value ? Math.round(interviews.value / total.value * 100) : 0)
const offerRate = computed(() => total.value ? Math.round(offers.value / total.value * 100) : 0)
const byStage = computed(() => stages.map(name => ({ name, count: count(item => item.stage === name) })))
const byChannel = computed(() => grouped('channel'))
const stageColors = ['#163f9f', '#1d55c4', '#286de0', '#4389f0', '#65a3f5', '#8cbcf8']
const channelColors = ['#39966f', '#6575d1', '#d49537', '#4795ad', '#9870ba', '#c66f73', '#7b9852', '#7b8599']
const donutCircumference = 2 * Math.PI * 38
type DistributionItem = { name: string; count: number }
function distribution(items: DistributionItem[], colors: string[]) {
  const total = items.reduce((sum, item) => sum + item.count, 0)
  let position = 0
  let elapsed = 0
  const slices = items.map((item, index) => {
    const start = position
    position += total ? item.count / total * 100 : 0
    const color = colors[index] || `hsl(${Math.round((index * 137.508) % 360)} 55% 50%)`
    const angle = (start + (position - start) / 2) * 3.6 - 90
    const radians = angle * Math.PI / 180
    const duration = item.count ? Math.max(115, item.count / total * 1050) : 0
    const delay = elapsed
    elapsed += duration
    return { ...item, color, showOnRing: item.count > 0 && position - start >= 13,
      x: `${50 + Math.cos(radians) * 40}%`, y: `${50 + Math.sin(radians) * 40}%`,
      startAngle: start * 3.6 - 90, arcLength: item.count / (total || 1) * donutCircumference,
      duration: `${duration}ms`, delay: `${delay}ms`, labelDelay: `${delay + duration * .7}ms` }
  })
  return { total, slices, centerDelay: `${Math.max(0, elapsed - 180)}ms` }
}
const stageDistribution = computed(() => distribution(byStage.value, stageColors))
const channelDistribution = computed(() => distribution(byChannel.value, channelColors))
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
    const offer = item.stage === 'Offer' || item.status === '已通过'
    return { item, related, ended, offer, latest:related[0] ? interviewTime(related[0]) : '', flow:compactFlow(item) }
  })
  .sort((a,b) => Number(a.ended) - Number(b.ended) || b.latest.localeCompare(a.latest) || String(a.item.company || '').localeCompare(String(b.item.company || ''),'zh-CN')))

function pad(value: number) { return String(value).padStart(2, '0') }
function interviewTime(event: Record<string, unknown>) { return String(event.completedAt || event.startsAt || event.start || event.date || '') }
function timeOf(value: unknown) {
  const time = new Date(String(value || '').replace(' ', 'T')).getTime()
  return Number.isFinite(time) ? time : 0
}
function eventDeadline(item: Record<string, unknown>) { return String(item.endsAt || item.end || item.startsAt || item.start || item.date || '') }
function eventOccurrenceTime(item: Record<string, unknown>) { return String(item.startsAt || item.start || item.date || item.at || '') }
function compactDate(value: unknown) {
  const source = String(value || '')
  const match = source.match(/\d{4}-(\d{2})-(\d{2})/)
  return match ? `${Number(match[1])}/${Number(match[2])}` : source || '未记录'
}
function compactFlowVisual(label: unknown, type: unknown = '') {
  const value = `${type || ''} ${label || ''}`
  if (/未通过|错过|放弃|已结束/.test(value)) return { style: 'failed', icon: '×' }
  if (/Offer|录用|已通过/.test(value)) return { style: 'offer', icon: '★' }
  if (value.includes('测评')) return { style: 'assessment', icon: '◇' }
  if (value.includes('笔试')) return { style: 'test', icon: '✎' }
  if (/面试|[一二三四五六七八九]面|HR/.test(value)) return { style: 'interview', icon: '◎' }
  if (value.includes('电话')) return { style: 'phone', icon: '☎' }
  if (value.includes('等待')) return { style: 'waiting', icon: '◷' }
  if (value.includes('投递')) return { style: 'applied', icon: '↗' }
  return { style: 'other', icon: '＋' }
}
function compactEventDate(event: Record<string, unknown>) {
  const start = compactDate(eventOccurrenceTime(event))
  const end = String(event.endsAt || event.end || '')
  return end ? `${start}–${compactDate(end)}` : start
}
function compactFlow(item: Record<string, unknown>) {
  const events = store.events.value
    .filter(event => event.applicationId === item.id)
    .slice()
    .sort((a, b) => eventDeadline(a).localeCompare(eventDeadline(b)))
  const applied = compactFlowVisual('已投递')
  const nodes = [{ label: '已投递', at: compactDate(item.appliedDate), kind: events.length || item.stage !== '已投递' ? 'done' : 'current', ...applied }]
  events.forEach(event => {
    const deadline = timeOf(eventDeadline(event))
    const kind = event.missed ? 'failed' : event.completed ? 'done' : deadline > Date.now() ? 'upcoming' : 'current'
    const label = String(event.title || event.type || '日程')
    nodes.push({ label, at: compactEventDate(event), kind, ...compactFlowVisual(label, event.type) })
  })
  const last = nodes[nodes.length - 1]
  const latest = events[events.length - 1]
  const latestPending = latest && !latest.completed && !latest.missed
  const terminal = ['未通过', '已放弃', '已结束'].includes(String(item.status || ''))
  const offer = item.stage === 'Offer' || item.status === '已通过'
  const enteredInterview = hasInterviewProgress(item)
  let statusLabel = offer ? 'Offer' : terminal ? String(item.status) : String(item.status || '')
  if (statusLabel === '等待结果' && !enteredInterview) statusLabel = ''
  if (statusLabel && statusLabel !== last.label && !(statusLabel === '等待结果' && latestPending)) {
    nodes.push({ label: statusLabel, at: '当前', kind: offer ? 'success' : terminal ? 'failed' : 'current', ...compactFlowVisual(statusLabel) })
  } else if (!events.length && item.stage !== '已投递') {
    nodes.push({ label: String(item.stage), at: '当前', kind: 'current', ...compactFlowVisual(item.stage, item.stage) })
  }
  return nodes
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

function stopAnalyticsReveal() {
  revealRun += 1
  interviewRevealObserver?.disconnect()
  interviewRevealObserver = null
  revealActive.value = false
  recordsRevealActive.value = false
  interviewList.value?.querySelectorAll<HTMLElement>('.interview-fade-in').forEach(card => {
    card.classList.remove('interview-fade-in')
    card.style.removeProperty('--interview-reveal-delay')
  })
}
function observeInterviewRecords() {
  const list = interviewList.value
  if (!list || !interviewRevealObserver || !revealActive.value) return
  interviewRevealObserver.disconnect()
  list.querySelectorAll<HTMLElement>('article:not(.interview-fade-in)').forEach(card => interviewRevealObserver?.observe(card))
}
async function startAnalyticsReveal() {
  stopAnalyticsReveal()
  const run = revealRun
  if (!viewActive || store.loading.value || !store.user.value) return
  await nextTick()
  if (!viewActive || run !== revealRun) return
  revealActive.value = true
  recordsRevealActive.value = typeof IntersectionObserver !== 'undefined' && interviewRecords.value.length > 0
  await nextTick()
  if (!viewActive || run !== revealRun) return
  const list = interviewList.value
  if (!list || !recordsRevealActive.value) { recordsRevealActive.value = false; return }
  const duration = 520
  const interval = duration * .3
  list.style.setProperty('--interview-reveal-duration', `${duration}ms`)
  nextInterviewRevealStart = performance.now()
  const scrollRoot = list.scrollHeight > list.clientHeight + 2 ? list : null
  interviewRevealObserver = new IntersectionObserver(entries => {
    const cards = Array.from(list.querySelectorAll<HTMLElement>('article'))
    const visible = entries.filter(entry => entry.isIntersecting)
      .sort((left, right) => cards.indexOf(left.target as HTMLElement) - cards.indexOf(right.target as HTMLElement))
    const now = performance.now()
    nextInterviewRevealStart = Math.max(now, Math.min(nextInterviewRevealStart, now + duration))
    visible.forEach(entry => {
      const card = entry.target as HTMLElement
      card.style.setProperty('--interview-reveal-delay', `${Math.max(0, nextInterviewRevealStart - now)}ms`)
      card.classList.add('interview-fade-in')
      nextInterviewRevealStart += interval
      interviewRevealObserver?.unobserve(card)
    })
  }, { root: scrollRoot, threshold: .05 })
  observeInterviewRecords()
}

onActivated(() => { viewActive = true; void startAnalyticsReveal() })
onDeactivated(() => { viewActive = false; stopAnalyticsReveal() })
watch([() => store.loading.value, () => store.user.value], () => {
  if (viewActive && !store.loading.value && store.user.value) void startAnalyticsReveal()
})
watch(interviewRecords, (records, previous) => {
  if (!viewActive) return
  if (!previous?.length && records.length) { void startAnalyticsReveal(); return }
  if (recordsRevealActive.value) void nextTick(observeInterviewRecords)
})

watch(analyticsMain, element => {
  mainResizeObserver?.disconnect()
  mainResizeObserver = undefined
  analyticsMainHeight.value = 0
  if (!element) return
  mainResizeObserver = new ResizeObserver(([entry]) => {
    analyticsMainHeight.value = Math.ceil(entry.borderBoxSize?.[0]?.blockSize || entry.contentRect.height)
  })
  mainResizeObserver.observe(element)
}, { flush: 'post' })
onBeforeUnmount(() => { viewActive = false; stopAnalyticsReveal(); mainResizeObserver?.disconnect() })
</script>

<template>
  <p v-if="store.loading.value">正在汇总完整业务数据…</p>
  <section v-else-if="!store.user.value" class="card"><h2>请先登录</h2><p>登录后查看投递分析。</p></section>
  <div v-else class="analytics-layout" :class="{'analytics-revealing':revealActive}" :style="analyticsMainHeight ? {'--analytics-main-height':`${analyticsMainHeight}px`} : undefined">
    <main ref="analyticsMain" class="analytics-main">
      <div class="metrics">
      <article><span>投递总数</span><strong>{{ total }}</strong><small>全部投递记录</small></article>
      <article><span>有过面试</span><strong>{{ interviews }}</strong><small>占投递总数 {{ interviewRate }}%</small></article>
      <article><span>通过 / Offer 数</span><strong>{{ offers }}</strong><small>占投递总数 {{ offerRate }}%</small></article>
    </div>
    <div class="two-column">
      <section class="card distribution-card" aria-labelledby="stage-distribution-title">
        <h2 id="stage-distribution-title">阶段分布</h2>
        <div class="distribution-content">
          <div class="donut-chart" role="img" :aria-label="`阶段分布，共 ${stageDistribution.total} 条；${stageDistribution.slices.map(item => `${item.name} ${item.count} 条`).join('，')}`" :style="{'--donut-circumference':donutCircumference,'--donut-center-delay':stageDistribution.centerDelay}">
            <svg class="donut-ring" viewBox="0 0 100 100" aria-hidden="true">
              <circle class="donut-track" cx="50" cy="50" r="38" />
              <circle v-for="item in stageDistribution.slices.filter(slice => slice.count > 0)" :key="item.name" class="donut-slice" cx="50" cy="50" r="38" :stroke="item.color" :stroke-dasharray="`${item.arcLength} ${donutCircumference}`" :transform="`rotate(${item.startAngle} 50 50)`" :style="{'--slice-length':item.arcLength,'--slice-duration':item.duration,'--slice-delay':item.delay}" />
            </svg>
            <span v-for="item in stageDistribution.slices.filter(slice => slice.showOnRing)" :key="item.name" class="donut-value" :style="{left:item.x,top:item.y,'--slice-label-delay':item.labelDelay}">{{ item.count }}</span>
            <span class="donut-center" aria-hidden="true"><strong>{{ stageDistribution.total }}</strong><small>条投递</small></span>
          </div>
          <ul class="distribution-legend" aria-label="各阶段数量">
            <li v-for="item in stageDistribution.slices" :key="item.name" :style="{'--slice-label-delay':item.labelDelay}"><i :style="{background:item.color}" aria-hidden="true" /><span :class="{notranslate:item.name==='Offer'}" :translate="item.name==='Offer'?'no':undefined" :title="item.name">{{ item.name }}</span><strong>{{ item.count }}</strong></li>
          </ul>
        </div>
      </section>
      <section class="card distribution-card" aria-labelledby="channel-distribution-title">
        <h2 id="channel-distribution-title">渠道分布</h2>
        <div class="distribution-content">
          <div class="donut-chart" role="img" :aria-label="`渠道分布，共 ${channelDistribution.total} 条；${channelDistribution.slices.map(item => `${item.name} ${item.count} 条`).join('，') || '暂无数据'}`" :style="{'--donut-circumference':donutCircumference,'--donut-center-delay':channelDistribution.centerDelay}">
            <svg class="donut-ring" viewBox="0 0 100 100" aria-hidden="true">
              <circle class="donut-track" cx="50" cy="50" r="38" />
              <circle v-for="item in channelDistribution.slices.filter(slice => slice.count > 0)" :key="item.name" class="donut-slice" cx="50" cy="50" r="38" :stroke="item.color" :stroke-dasharray="`${item.arcLength} ${donutCircumference}`" :transform="`rotate(${item.startAngle} 50 50)`" :style="{'--slice-length':item.arcLength,'--slice-duration':item.duration,'--slice-delay':item.delay}" />
            </svg>
            <span v-for="item in channelDistribution.slices.filter(slice => slice.showOnRing)" :key="item.name" class="donut-value" :style="{left:item.x,top:item.y,'--slice-label-delay':item.labelDelay}">{{ item.count }}</span>
            <span class="donut-center" aria-hidden="true"><strong>{{ channelDistribution.total }}</strong><small>条投递</small></span>
          </div>
          <ul v-if="channelDistribution.slices.length" class="distribution-legend" aria-label="各渠道数量">
            <li v-for="item in channelDistribution.slices" :key="item.name" :style="{'--slice-label-delay':item.labelDelay}"><i :style="{background:item.color}" aria-hidden="true" /><span :title="item.name">{{ item.name }}</span><strong>{{ item.count }}</strong></li>
          </ul>
          <p v-else class="distribution-empty">暂无数据</p>
        </div>
      </section>
    </div>
      <section class="card"><div class="section-head"><div><h2>近 12 个月投递趋势</h2><p>按投递日期汇总</p></div></div><div class="trend"><div v-for="(item,index) in trend" :key="item.key" :style="{'--trend-delay':`${index*55}ms`}"><span role="img" :aria-label="`${item.label}投递 ${item.count} 次`"><span class="trend-bar" :style="{height:`${Math.max(3,item.count/maxTrend*100)}%`}"><i /><b>{{ item.count }}</b></span></span><small>{{ item.label }}</small></div></div></section>
    </main>
    <aside class="interview-panel card" aria-labelledby="interview-records-title">
      <header><div><span>只读概览</span><h2 id="interview-records-title">面试投递记录</h2><p>展示所有进入过正式面试环节的投递</p></div><b>{{interviewRecords.length}} 条</b></header>
      <div v-if="interviewRecords.length" ref="interviewList" class="interview-list" :class="{'interview-revealing':recordsRevealActive}" tabindex="0" aria-label="面试投递记录，可上下滚动">
        <article v-for="record in interviewRecords" :key="record.item.id" :class="record.offer?'is-offer':record.ended?'is-ended':'is-active'">
          <div class="record-head"><strong>{{record.item.company||'未填写公司'}}</strong><span>{{record.offer?'Offer':record.ended?'已结束':'面试中'}}</span></div>
          <p>{{record.item.position||'未填写岗位'}}</p>
          <div class="compact-flow" :style="{'--flow-count':record.flow.length}" role="list" :aria-label="`${record.item.company||'该投递'}的投递流程`">
            <span v-for="(node,index) in record.flow" :key="`${node.label}-${index}`" class="compact-node" :class="[node.kind,`flow-${node.style}`]" role="listitem" :title="`${node.label} ${node.at}`"><i aria-hidden="true">{{node.icon}}</i><b>{{node.label}}</b><small>{{node.at}}</small></span>
          </div>
          <footer><span>{{record.related.length?`${record.related.length} 场正式面试`:'阶段记录显示已进入面试'}}</span></footer>
        </article>
      </div>
      <p v-else class="interview-empty">暂无进入过正式面试环节的投递。</p>
    </aside>
  </div>
</template>

<style scoped>
.analytics-layout{display:grid;width:100%;grid-template-columns:minmax(0,1fr) minmax(340px,1fr);gap:20px;align-items:start;padding:22px 0 28px}.analytics-main{display:grid;min-width:0;gap:18px}.analytics-main .metrics{margin-top:0}
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
.distribution-card { min-width:0; }
.distribution-card:nth-child(2) { --donut-offset:140ms; }
.distribution-card h2 { margin-bottom:18px; }
.distribution-content { display:grid; grid-template-columns:minmax(128px,170px) minmax(0,1fr); align-items:center; gap:18px; }
.donut-chart { position:relative; width:100%; max-width:170px; aspect-ratio:1; border-radius:50%; }
.donut-ring { display:block; width:100%; height:100%; overflow:visible; }
.donut-ring circle { fill:none; stroke-width:24; stroke-linecap:butt; }
.donut-track { stroke:var(--color-muted); opacity:.65; }
.donut-center { position:absolute; z-index:1; inset:26%; display:flex; align-items:center; justify-content:center; flex-direction:column; border-radius:50%; color:var(--color-foreground); background:var(--color-background); box-shadow:0 0 0 1px color-mix(in srgb,var(--color-border) 50%,transparent); }
.donut-center strong { font-size:25px; line-height:1.1; font-variant-numeric:tabular-nums; }
.donut-center small { margin-top:2px; color:var(--color-muted-foreground); font-size:10px; white-space:nowrap; }
.donut-value { position:absolute; z-index:1; transform:translate(-50%,-50%); color:#fff; font-size:11px; font-weight:800; font-variant-numeric:tabular-nums; line-height:1; text-shadow:0 1px 3px rgba(0,0,0,.48); pointer-events:none; }
.distribution-legend { display:grid; gap:9px; min-width:0; margin:0; padding:0; list-style:none; }
.distribution-legend li { display:grid; grid-template-columns:9px minmax(0,1fr) auto; align-items:center; gap:8px; min-width:0; color:var(--color-muted-foreground); font-size:12px; }
.distribution-legend i { width:9px; height:9px; border-radius:3px; }
.distribution-legend span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.distribution-legend strong { color:var(--color-foreground); font-size:12px; font-variant-numeric:tabular-nums; }
.distribution-empty { margin:0; color:var(--color-muted-foreground); font-size:12px; }
.analytics-revealing .metrics article strong { animation:analytics-number-appear 600ms ease-out both; }
.analytics-revealing .metrics article:nth-child(2) strong { animation-delay:120ms; }
.analytics-revealing .metrics article:nth-child(3) strong { animation-delay:240ms; }
.analytics-revealing .donut-slice { animation:analytics-donut-slice var(--slice-duration) linear both; animation-delay:calc(260ms + var(--donut-offset, 0ms) + var(--slice-delay)); }
.analytics-revealing .donut-value,.analytics-revealing .distribution-legend li { animation:analytics-detail-appear 320ms ease-out both; animation-delay:calc(260ms + var(--donut-offset, 0ms) + var(--slice-label-delay)); }
.analytics-revealing .donut-center { animation:analytics-detail-appear 360ms ease-out both; animation-delay:calc(260ms + var(--donut-offset, 0ms) + var(--donut-center-delay)); }
@keyframes analytics-number-appear { from { opacity:.08; transform:translateY(6px); } to { opacity:1; transform:translateY(0); } }
@keyframes analytics-donut-slice { from { stroke-dasharray:0 var(--donut-circumference); } to { stroke-dasharray:var(--slice-length) var(--donut-circumference); } }
@keyframes analytics-detail-appear { from { opacity:0; } to { opacity:1; } }
.section-head { display:flex; align-items:center; justify-content:space-between; }
.section-head h2 { margin-bottom:4px; }
.section-head p { margin:0; font-size:12px; }
.trend { position:relative; display:grid; grid-template-columns:repeat(12,minmax(34px,1fr)); align-items:end; height:230px; gap:8px; margin-top:18px; padding-top:12px; background:repeating-linear-gradient(to bottom,transparent 0 43px,color-mix(in srgb,var(--color-border) 55%,transparent) 43px 44px); }
.trend>div { display:grid; grid-template-rows:190px auto; gap:8px; text-align:center; }
.trend>div>span { position:relative; display:flex; align-items:end; justify-content:center; height:100%; padding-top:20px; border-bottom:1px solid var(--color-border-strong); box-sizing:border-box; }
.trend .trend-bar { position:relative; display:flex; width:100%; min-height:4px; align-items:end; justify-content:center; }
.trend i { display:block; width:min(34px,72%); height:100%; border-radius:4px 4px 0 0; background:var(--color-primary); box-shadow:inset 0 1px 0 rgba(255,255,255,.35); }
.trend b { position:absolute; bottom:calc(100% + 4px); color:var(--color-muted-foreground); font:600 11px/1 "Fira Code",monospace; white-space:nowrap; }
.trend small { color:var(--color-muted-foreground); font-size:11px; }
.analytics-revealing .trend i { transform-origin:bottom; animation:analytics-bar-grow 850ms cubic-bezier(.16,.74,.23,1) both; animation-delay:calc(580ms + var(--trend-delay)); }
.analytics-revealing .trend b { animation:analytics-detail-appear 330ms ease-out both; animation-delay:calc(1110ms + var(--trend-delay)); }
@keyframes analytics-bar-grow { from { transform:scaleY(0); } to { transform:scaleY(1); } }
.interview-list.interview-revealing article:not(.interview-fade-in) { opacity:0; }
.interview-list.interview-revealing article.interview-fade-in { animation:analytics-record-appear var(--interview-reveal-duration,520ms) linear both; animation-delay:var(--interview-reveal-delay,0ms); }
@keyframes analytics-record-appear { from { opacity:0; } to { opacity:1; } }
.interview-panel{display:flex;height:var(--analytics-main-height,auto);min-width:0;min-height:0;margin:0;padding:18px;flex-direction:column;overflow:hidden}.interview-panel>header{display:flex;flex:none;align-items:flex-start;justify-content:space-between;gap:12px;padding-bottom:14px;border-bottom:1px solid var(--color-border)}.interview-panel>header>div{display:grid;min-width:0;gap:3px}.interview-panel>header span{color:var(--color-primary);font-size:11px;font-weight:800;letter-spacing:.08em}.interview-panel>header h2{margin:0;font-size:19px}.interview-panel>header p{margin:0;color:var(--color-muted-foreground);font-size:12px;line-height:1.5}.interview-panel>header>b{flex:none;padding:5px 8px;border-radius:999px;color:#315e64;background:#e6f3f1;font-size:11px}.interview-list{display:grid;min-height:0;flex:1 1 auto;align-content:start;gap:10px;margin:14px -6px 0 0;padding-right:6px;overflow-y:auto;overscroll-behavior:contain;scrollbar-color:#9ebdb7 transparent;scrollbar-width:thin}.interview-list article{--record-bg:#fff;display:grid;gap:8px;padding:13px 14px;border:1px solid;border-left-width:5px;border-radius:11px}.interview-list article.is-active{--record-bg:#eaf7ef;border-color:#a9d7bd;border-left-color:#278759;background:var(--record-bg)}.interview-list article.is-ended{--record-bg:#fbeceb;border-color:#e2b8b4;border-left-color:#bd4942;background:var(--record-bg)}.record-head{display:flex;align-items:center;justify-content:space-between;gap:10px}.record-head strong{min-width:0;overflow:hidden;color:var(--color-foreground);font-size:14px;text-overflow:ellipsis;white-space:nowrap}.record-head span{flex:none;padding:4px 7px;border-radius:999px;font-size:10px;font-weight:800;white-space:nowrap}.is-active .record-head span{color:#12623d;background:#d1eddc}.is-ended .record-head span{color:#98342f;background:#f4d3d0}.interview-list article>p{margin:0;color:#4e5d58;font-size:12px}.compact-flow{display:grid;grid-template-columns:repeat(var(--flow-count),minmax(56px,1fr));min-width:max(100%,calc(var(--flow-count) * 62px));align-items:start;margin:2px 0;padding:4px 1px 3px;overflow-x:auto;scrollbar-width:none}.compact-flow::-webkit-scrollbar{display:none}.compact-node{--node-color:#718078;--node-soft:#eef2ef;position:relative;display:grid;min-width:56px;justify-items:center;text-align:center}.compact-node:not(:first-child)::before{content:"";position:absolute;left:calc(-50% + 11px);top:11px;width:calc(100% - 22px);height:2px;background:color-mix(in srgb,var(--node-color) 24%,#dce3df)}.compact-node>i{position:relative;z-index:1;display:grid;width:23px;height:23px;place-items:center;border:2px solid color-mix(in srgb,var(--node-color) 72%,white);border-radius:8px;color:var(--node-color);background:var(--node-soft);font:800 12px "Segoe UI Symbol","Microsoft YaHei UI",sans-serif;box-shadow:0 0 0 3px var(--record-bg)}.compact-node>b{max-width:64px;margin-top:5px;overflow:hidden;color:var(--node-color);font-size:10px;text-overflow:ellipsis;white-space:nowrap}.compact-node>small{margin-top:1px;color:#718079;font-size:9px;white-space:nowrap}.compact-node.done>i::after{content:"✓";position:absolute;right:-5px;top:-6px;display:grid;width:12px;height:12px;place-items:center;border:2px solid var(--record-bg);border-radius:50%;color:#fff;background:var(--node-color);font-size:8px}.compact-node.current>i,.compact-node.upcoming>i{box-shadow:0 0 0 3px var(--record-bg),0 0 0 5px color-mix(in srgb,var(--node-color) 14%,transparent)}.compact-node.upcoming>i{border-style:dashed}.compact-node.failed>i,.compact-node.success>i{color:#fff;background:var(--node-color)}.flow-applied{--node-color:#4775be;--node-soft:#eaf1fc}.flow-assessment{--node-color:#7a57ad;--node-soft:#f1ebfa}.flow-test{--node-color:#b77718;--node-soft:#fff2d9}.flow-interview{--node-color:#24828b;--node-soft:#e3f5f5}.flow-phone{--node-color:#596bc2;--node-soft:#ebedfb}.flow-offer{--node-color:#258254;--node-soft:#e3f5e9}.flow-waiting{--node-color:#b06424;--node-soft:#fff0e1}.flow-failed{--node-color:#bc4c48;--node-soft:#fde9e8}.flow-other{--node-color:#69766f;--node-soft:#edf1ef}.interview-list footer{display:flex;align-items:center;justify-content:space-between;gap:8px;color:#68766f;font-size:10px}.interview-list time{text-align:right}.interview-empty{margin:14px 0 0;padding:24px 12px;color:var(--color-muted-foreground);background:var(--color-muted);text-align:center}
.trend{height:158px;grid-template-columns:repeat(12,minmax(30px,1fr));gap:6px}.trend>div{grid-template-rows:120px auto}.trend>div>span{padding-top:12px}
.interview-list article.is-active{--record-bg:#edf4ff;border-color:#b7cbed;border-left-color:#3974c6;background:var(--record-bg)}.interview-list article.is-ended{--record-bg:#fbeceb;border-color:#e2b8b4;border-left-color:#bd4942;background:var(--record-bg)}.interview-list article.is-offer{--record-bg:#eaf7ef;border-color:#a9d7bd;border-left-color:#278759;background:var(--record-bg)}.is-active .record-head span{color:#245ca6;background:#dceaff}.is-ended .record-head span{color:#98342f;background:#f4d3d0}.is-offer .record-head span{color:#12623d;background:#d1eddc}
@media(max-width:1180px){.analytics-layout{grid-template-columns:minmax(0,1fr) minmax(300px,1fr)}.metrics{grid-template-columns:1fr 1fr}.two-column{grid-template-columns:1fr}.trend{gap:4px}}
@media(max-width:900px){.analytics-layout{grid-template-columns:1fr}.interview-panel{height:auto;max-height:none;grid-row:2;overflow:visible}.interview-list{grid-template-columns:repeat(2,minmax(0,1fr));overflow:visible}}
@media(max-width:640px){.metrics{grid-template-columns:1fr}.metrics article{min-height:118px}.analytics-main>.card:last-child{overflow-x:auto}.trend{min-width:620px}}
@media(max-width:380px){.distribution-content{grid-template-columns:120px minmax(0,1fr);gap:10px}.distribution-legend{gap:7px}}
@media(max-width:640px){.analytics-layout{padding-top:14px}.interview-list{grid-template-columns:1fr}.interview-list footer{align-items:flex-start;flex-direction:column}.interview-list time{text-align:left}}
</style>
