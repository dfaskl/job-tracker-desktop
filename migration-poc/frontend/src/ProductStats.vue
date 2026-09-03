<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

type Application = { id: string; stage: string; status: string; channel: string }
const stages = ['已投递', '测评', '笔试', '面试', 'Offer', '已结束']
const applications = ref<Application[]>([])
const total = ref(0)
const loading = ref(true)
const needsLogin = ref(false)
const error = ref('')

const byStage = computed(() => stages.map((name) => ({ name, count: applications.value.filter((item) => item.stage === name).length })))
const byChannel = computed(() => {
  const counts = new Map<string, number>()
  applications.value.forEach((item) => counts.set(item.channel || '未填写', (counts.get(item.channel || '未填写') || 0) + 1))
  return [...counts.entries()].map(([name, count]) => ({ name, count })).sort((a, b) => b.count - a.count)
})
const maxStage = computed(() => Math.max(1, ...byStage.value.map((item) => item.count)))
const ended = computed(() => applications.value.filter((item) => item.stage === '已结束' || ['未通过', '已放弃', '已结束'].includes(item.status)).length)

onMounted(load)

async function load() {
  loading.value = true
  needsLogin.value = false
  error.value = ''
  try {
    const response = await fetch('/api/poc/applications', { cache: 'no-store' })
    const body = await response.json().catch(() => ({}))
    if (response.status === 401) { needsLogin.value = true; return }
    if (!response.ok) throw new Error(body.message || '读取统计数据失败')
    applications.value = body.applications || []
    total.value = Number(body.total || 0)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '读取统计数据失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <p v-if="loading">正在汇总投递数据…</p>
  <section v-else-if="needsLogin" class="card"><h2>请先登录</h2><p>请在“投递记录”页面登录现有账号，再返回查看统计。</p></section>
  <template v-else>
    <div class="stats-grid">
      <article><span>投递总数</span><strong>{{ total }}</strong></article>
      <article><span>面试阶段</span><strong>{{ byStage.find((item) => item.name === '面试')?.count || 0 }}</strong></article>
      <article><span>Offer</span><strong>{{ byStage.find((item) => item.name === 'Offer')?.count || 0 }}</strong></article>
      <article><span>结束 / 未通过</span><strong>{{ ended }}</strong></article>
    </div>
    <div class="charts">
      <section class="card"><h2>阶段分布</h2><div v-for="item in byStage" :key="item.name" class="chart-row"><span>{{ item.name }}</span><div><i :style="{ width: `${item.count / maxStage * 100}%` }" /></div><strong>{{ item.count }}</strong></div></section>
      <section class="card"><h2>渠道分布</h2><div v-for="item in byChannel" :key="item.name" class="chart-row"><span>{{ item.name }}</span><div><i :style="{ width: `${item.count / Math.max(1, total) * 100}%` }" /></div><strong>{{ item.count }}</strong></div><p v-if="!byChannel.length">暂无数据。</p></section>
    </div>
  </template>
  <p v-if="error" class="danger">{{ error }}</p>
</template>

<style scoped>
.stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-top: 24px; }
.stats-grid article { display: grid; gap: 7px; padding: 20px; border: 1px solid #e4e9f2; border-radius: 15px; background: #fff; }
.stats-grid span, .chart-row span { color: #667085; }
.stats-grid strong { font-size: 28px; }
.charts { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
.chart-row { display: grid; grid-template-columns: 80px 1fr 32px; align-items: center; gap: 12px; margin-top: 15px; }
.chart-row > div { height: 9px; overflow: hidden; border-radius: 999px; background: #edf0f5; }
.chart-row i { display: block; height: 100%; border-radius: inherit; background: #526ddd; }
.chart-row strong { text-align: right; }

@media (max-width: 720px) {
  .stats-grid { grid-template-columns: 1fr 1fr; }
  .charts { grid-template-columns: 1fr; }
}
</style>
