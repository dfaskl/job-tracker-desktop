<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import AppIcon from './AppIcon.vue'

const emit = defineEmits<{ navigate: [page: string]; close: [] }>()
const current = ref(0)
const hoveredIndex = ref<number | null>(null)
const pages = [
  { eyebrow: 'WELCOME · 01', title: '先完成基础配置', description: '配置好这些功能，CareerFlow 才能更完整地协助你管理求职过程和准备面试。暂时不配置也可以继续手动使用。', next: '从一条投递开始' },
  { eyebrow: 'TRACK · 02', title: '从新建投递开始管理进度', description: '先为公司和岗位建立一条投递记录。收到笔试或面试安排后，再把日程添加到这条投递下，进展就能集中查看。', next: '也可以用邮件自动整理' },
  { eyebrow: 'INBOX · 03', title: '用邮件识别减少手动录入', description: '连接邮箱后，在邮件识别页粘贴招聘或面试邮件。检查识别出的信息，再保存为投递或日程。保存前请核对关键信息。', next: '面试结束后记录问题' },
  { eyebrow: 'REVIEW · 04', title: '留下问题，整理成复习资料', description: '面试结束后，编辑对应面试日程并记录面试官的问题。之后在面试总结中分类查看问题和学习讲解，复习知识点并准备后续面试。', next: '开始使用 CareerFlow' }
]
const progress = computed(() => `${String(current.value + 1).padStart(2, '0')} / ${String(pages.length).padStart(2, '0')}`)
const configVisuals = [
  { title: '我的简历', location: '个人设置 → 我的简历', page: '个人主页', kicker: '教育 · 实习 · 项目', target: '补充经历后，项目考点可以结合你的背景整理。', kind: 'resume' },
  { title: '大模型 API', location: '个人设置 → 大模型 API', page: '智能能力', kicker: 'API 配置', target: '连接模型服务，启用 AI 总结与学习讲解。', kind: 'api' },
  { title: '邮件 API', location: '个人设置 → 邮件配置', page: '邮件接入', kicker: '连接邮箱', target: '连接邮箱后，识别邮件并辅助创建求职日程。', kind: 'mail-config' }
]
const applicationVisuals = [
  { title: '找到「新建投递」', location: '投递记录页面右上方', page: '投递记录', kicker: '新增一条投递', target: '点这里创建一条公司与岗位记录。', kind: 'new-application' },
  { title: '填写投递信息', location: '点击后打开编辑窗口', page: '编辑投递', kicker: '公司 · 岗位 · 渠道', target: '先保存投递，后续的笔试、面试日程再关联到它。', kind: 'application-form' }
]
const mailVisual = { title: '邮件识别工作区', location: '左侧导航 → 邮件识别', page: '邮件识别', kicker: '粘贴邮件内容', target: '识别完成后先核对公司、岗位和时间，再保存。', kind: 'mail-recognition' }
const interviewVisuals = [
  { title: '编辑面试回顾', location: '投递详情 → 编辑面试日程', page: '面试日程', kicker: '面试官问题', target: '尽量记录问题原文，也可以补充当时回答或上下文。', kind: 'interview-edit' },
  { title: '在面试总结中复习', location: '左侧导航 → 面试总结', page: '面试总结', kicker: '分类 · 问题 · 学习讲解', target: '汇总后按项目、八股和其他分类浏览，逐题学习。', kind: 'interview-review' }
]
const dockItems = [
  { label: '基础配置', icon: 'M12 3 13.8 8.2 19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3Z' },
  { label: '新建投递', icon: 'M8 4h8M7 3h10v3H7ZM5 6h14v15H5zM8 11h8M8 15h5' },
  { label: '邮件识别', icon: 'M3 5h18v14H3zM3 6l9 7 9-7' },
  { label: '面试复习', icon: 'M5 3.5h10l4 4V20H5a2 2 0 0 1-2-2V5.5a2 2 0 0 1 2-2Zm9 0V8h5M7 12h9M7 16h6' }
]
function previous() { if (current.value > 0) current.value -= 1 }
function next() { if (current.value < pages.length - 1) current.value += 1 }
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'ArrowLeft') { event.preventDefault(); previous() }
  if (event.key === 'ArrowRight') { event.preventDefault(); next() }
  if (event.key === 'Escape') { event.preventDefault(); emit('close') }
}
onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <section class="guide-page" aria-label="CareerFlow 新手指南">
    <header class="guide-header">
      <div class="guide-brand"><span class="guide-brand-icon"><AppIcon name="sparkles" :size="19" /></span><div><span>CAREERFLOW GUIDE</span><strong>新手指南</strong></div></div>
      <div class="guide-progress"><span>{{ progress }}</span><div><i :style="{ width: `${(current + 1) * 25}%` }"></i></div></div>
      <button type="button" class="guide-close" aria-label="关闭新手指南" title="关闭指南" @click="emit('close')"><AppIcon name="close" :size="21" /><span>关闭</span></button>
    </header>

    <button type="button" class="guide-arrow guide-arrow-left" :disabled="current === 0" aria-label="上一页" title="上一页" @click="previous"><AppIcon name="chevron-left" :size="25" /></button>
    <button type="button" class="guide-arrow guide-arrow-right" :disabled="current === pages.length - 1" aria-label="下一页" title="下一页" @click="next"><AppIcon name="chevron-right" :size="25" /></button>

    <Transition name="guide-turn" mode="out-in">
      <main :key="current" class="guide-content">
        <div class="guide-copy">
          <span class="guide-eyebrow">{{ pages[current].eyebrow }}</span>
          <h1>{{ pages[current].title }}</h1>
          <p>{{ pages[current].description }}</p>
        </div>

        <div v-if="current === 0" class="visual-grid config-grid">
          <article v-for="visual in configVisuals" :key="visual.kind" class="guide-visual-card">
            <div class="visual-card-heading"><div><span>配置入口</span><h2>{{ visual.title }}</h2><p>{{ visual.location }}</p></div><AppIcon name="chevron-right" :size="18" /></div>
            <div class="mini-window" :class="`mini-${visual.kind}`" aria-hidden="true">
              <div class="mini-topbar"><span></span><span></span><span></span><b>CareerFlow</b><i>个人设置</i></div>
              <div class="mini-layout"><aside><b>设置</b><span>个人中心</span><span :class="{ selected: visual.kind === 'resume' }">我的简历</span><span :class="{ selected: visual.kind === 'api' }">大模型 API</span><span :class="{ selected: visual.kind === 'mail-config' }">邮件配置</span></aside>
                <div class="mini-main"><small>{{ visual.page }}</small><strong>{{ visual.kicker }}</strong>
                  <div class="mini-target" :class="`target-${visual.kind}`"><span>{{ visual.kind === 'resume' ? '学校 / 专业 / 时间' : visual.kind === 'api' ? '模型服务 · API Key' : '邮箱地址 · 授权码' }}</span><i></i><i></i></div>
                  <div class="mini-button">{{ visual.kind === 'resume' ? '＋ 添加经历' : visual.kind === 'api' ? '保存配置' : '连接邮箱' }}</div>
                </div>
              </div>
            </div>
            <p class="visual-note">{{ visual.target }}</p>
          </article>
        </div>

        <div v-else-if="current === 1" class="visual-grid two-grid">
          <article v-for="visual in applicationVisuals" :key="visual.kind" class="guide-visual-card">
            <div class="visual-card-heading"><div><span>手动管理</span><h2>{{ visual.title }}</h2><p>{{ visual.location }}</p></div><AppIcon name="chevron-right" :size="18" /></div>
            <div class="mini-window application-mini" :class="`mini-${visual.kind}`" aria-hidden="true">
              <div class="mini-topbar"><span></span><span></span><span></span><b>CareerFlow</b><i>{{ visual.page }}</i></div>
              <div class="application-mini-body"><div class="mini-toolbar"><strong>{{ visual.page }}</strong><div v-if="visual.kind === 'new-application'" class="mini-primary">＋ 新建投递</div></div>
                <div v-if="visual.kind === 'new-application'" class="mini-row"><b>星河科技</b><span>Java 开发工程师</span><i>投递中</i></div>
                <div v-else class="mini-form"><b>基本信息</b><div class="form-line"><span>公司名称</span><i>星河科技</i></div><div class="form-line"><span>应聘岗位</span><i>Java 开发工程师</i></div><div class="form-line"><span>投递渠道</span><i>招聘官网</i></div><div class="mini-primary">保存投递</div></div>
                <div class="mini-focus" :class="visual.kind"></div>
              </div>
            </div>
            <p class="visual-note">{{ visual.target }}</p>
          </article>
        </div>

        <div v-else-if="current === 2" class="mail-page-layout">
          <article class="guide-visual-card mail-visual-card">
            <div class="visual-card-heading"><div><span>自动整理</span><h2>{{ mailVisual.title }}</h2><p>{{ mailVisual.location }}</p></div><AppIcon name="chevron-right" :size="18" /></div>
            <div class="mini-window mail-mini" aria-hidden="true">
              <div class="mini-topbar"><span></span><span></span><span></span><b>CareerFlow</b><i>邮件识别</i></div>
              <div class="mail-mini-body"><section><small>粘贴邮件原文</small><div class="mail-text-lines"><i></i><i></i><i></i><i></i><i></i></div><div class="mini-primary">开始识别</div></section><span class="mini-arrow">→</span><section class="recognized"><small>核对识别结果</small><div class="form-line"><span>公司</span><i>星河科技</i></div><div class="form-line"><span>岗位</span><i>开发工程师</i></div><div class="form-line"><span>面试时间</span><i>周四 14:00</i></div><div class="mini-button">确认并保存</div></section></div>
              <div class="mini-focus mail-recognition"></div>
            </div>
            <p class="visual-note">{{ mailVisual.target }}</p>
          </article>
          <aside class="guide-side-note"><span class="note-icon"><AppIcon name="check" :size="20" /></span><div><strong>保存前快速核对</strong><p>公司、岗位、日期和时间最重要。识别不准确时，可以先修改再保存。</p></div></aside>
        </div>

        <div v-else class="visual-grid two-grid">
          <article v-for="visual in interviewVisuals" :key="visual.kind" class="guide-visual-card">
            <div class="visual-card-heading"><div><span>面试复盘</span><h2>{{ visual.title }}</h2><p>{{ visual.location }}</p></div><AppIcon name="chevron-right" :size="18" /></div>
            <div class="mini-window interview-mini" :class="`mini-${visual.kind}`" aria-hidden="true">
              <div class="mini-topbar"><span></span><span></span><span></span><b>CareerFlow</b><i>{{ visual.page }}</i></div>
              <div class="interview-mini-body"><aside><b>{{ visual.kind === 'interview-edit' ? '投递详情' : '面试回顾' }}</b><span>基本信息</span><span>安排记录</span><span>状态历史</span></aside>
                <section v-if="visual.kind === 'interview-edit'" class="review-editor"><small>编辑面试日程</small><strong>星河科技 · 技术面试</strong><label>面试官问题<textarea readonly>1. 介绍一下你负责的项目？
2. 如何处理接口超时和重试？</textarea></label><div class="mini-primary">保存日程</div></section>
                <section v-else class="review-board"><small>AI 归纳</small><strong>考点整理</strong><div class="mini-tabs"><b>项目考点</b><b>八股考点</b><b>其他</b></div><div class="mini-question"><b>问题 1</b><strong>如何设计可靠的异步任务？</strong><p>学习讲解 · 核心思路、流程与常见取舍…</p></div><div class="mini-question"><b>问题 2</b><strong>如何定位线上性能问题？</strong><p>学习讲解 · 指标、排查步骤和优化方案…</p></div></section>
                <div class="mini-focus" :class="visual.kind"></div>
              </div>
            </div>
            <p class="visual-note">{{ visual.target }}</p>
          </article>
        </div>

        <footer class="guide-footer">
          <button type="button" class="guide-jump" @click="emit('navigate', current === 0 ? 'profile' : current === 1 ? 'applications' : current === 2 ? 'mail' : 'interview-summary')">{{ current === 0 ? '去配置' : current === 1 ? '去投递记录' : current === 2 ? '去邮件识别' : '去面试总结' }}</button>
          <button v-if="current < pages.length - 1" type="button" class="guide-next" @click="next">下一步 <span aria-hidden="true">→</span></button>
          <button v-else type="button" class="guide-next" @click="emit('close')">完成指引 <span aria-hidden="true">✓</span></button>
        </footer>
      </main>
    </Transition>

    <nav class="guide-dock" aria-label="新手指南页面">
      <button v-for="(item, index) in dockItems" :key="item.label" type="button" class="dock-item" :class="{ 'dock-current': current === index, 'dock-hovered': hoveredIndex === index, 'dock-neighbor': hoveredIndex !== null && Math.abs(hoveredIndex - index) === 1 }" :aria-label="`${item.label}，第 ${index + 1} 页`" :aria-current="current === index ? 'step' : undefined" @mouseenter="hoveredIndex = index" @mouseleave="hoveredIndex = null" @focus="hoveredIndex = index" @blur="hoveredIndex = null" @click="current = index">
        <span class="dock-label" aria-hidden="true">{{ item.label }}</span>
        <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="item.icon" /></svg>
        <i v-if="current === index" class="dock-dot" aria-hidden="true"></i>
      </button>
    </nav>
  </section>
</template>

<style scoped>
.guide-page{position:relative;display:flex;min-height:calc(100dvh - 32px);flex-direction:column;overflow:clip;color:var(--color-foreground);background:radial-gradient(ellipse at 50% 14%,color-mix(in srgb,var(--color-primary) 12%,transparent),transparent 48%),var(--color-background)}
.guide-header{position:sticky;top:0;z-index:12;display:flex;min-height:68px;align-items:center;justify-content:space-between;gap:24px;padding:0 clamp(14px,3vw,42px);border-bottom:1px solid var(--color-border);background:color-mix(in srgb,var(--color-background) 90%,transparent);backdrop-filter:blur(16px)}
.guide-brand{display:flex;align-items:center;gap:11px}.guide-brand-icon{display:grid;width:38px;height:38px;place-items:center;border:1px solid color-mix(in srgb,var(--color-primary) 35%,var(--color-border));border-radius:12px;color:var(--color-primary);background:color-mix(in srgb,var(--color-primary) 12%,var(--color-card))}.guide-brand>div{display:grid;gap:2px}.guide-brand span:not(.guide-brand-icon){color:var(--color-muted-foreground);font-size:9px;font-weight:800;letter-spacing:.14em}.guide-brand strong{font-size:15px}.guide-progress{display:flex;flex:1;align-items:center;justify-content:center;gap:12px}.guide-progress>span{color:var(--color-muted-foreground);font-size:11px;font-variant-numeric:tabular-nums;font-weight:700}.guide-progress>div{width:min(220px,28vw);height:4px;overflow:hidden;border-radius:10px;background:var(--color-muted)}.guide-progress i{display:block;height:100%;border-radius:inherit;background:var(--color-primary);transition:width .45s cubic-bezier(.22,1,.36,1)}.guide-close{display:flex;min-width:88px;min-height:44px;align-items:center;justify-content:center;gap:8px;border:1px solid var(--color-border);border-radius:11px;color:var(--color-muted-foreground);background:var(--color-card);font-size:13px;font-weight:650}.guide-close:hover{border-color:var(--color-border-strong);color:var(--color-foreground);background:var(--color-muted)}
.guide-content{display:grid;width:min(1540px,100%);min-height:0;flex:1;align-self:center;grid-template-rows:132px minmax(360px,1fr) 62px;gap:16px;padding:clamp(22px,4vh,44px) clamp(28px,4vw,64px) 116px}.guide-copy{display:grid;width:100%;height:132px;grid-template-rows:14px 46px 48px;align-content:center;justify-items:center;gap:5px;margin:0 auto;text-align:center;overflow:hidden}.guide-eyebrow{align-self:end;color:var(--color-primary);font-size:10px;font-weight:850;letter-spacing:.16em;line-height:14px}.guide-copy h1{display:-webkit-box;max-width:100%;align-self:center;margin:0;overflow:hidden;font-size:clamp(25px,3vw,36px);letter-spacing:-.035em;line-height:1.2;-webkit-box-orient:vertical;-webkit-line-clamp:1}.guide-copy>p{display:-webkit-box;width:100%;max-width:840px;align-self:start;margin:0 auto;overflow:hidden;color:var(--color-muted-foreground);font-size:14px;line-height:24px;-webkit-box-orient:vertical;-webkit-line-clamp:2}
.visual-grid{display:grid;min-height:0;height:100%;align-items:stretch;gap:16px}.config-grid{grid-template-columns:repeat(3,minmax(0,1fr))}.two-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.guide-visual-card{position:relative;display:grid;min-width:0;min-height:0;height:100%;grid-template-rows:56px minmax(0,1fr) 40px;gap:10px;padding:15px;border:1px solid var(--color-border);border-radius:16px;background:color-mix(in srgb,var(--color-card) 88%,transparent);box-shadow:var(--shadow-sm);overflow:hidden}.guide-visual-card::before{position:absolute;top:0;right:0;left:0;height:1px;background:linear-gradient(90deg,transparent,color-mix(in srgb,var(--color-primary) 55%,transparent),transparent);content:''}.visual-card-heading{display:flex;min-width:0;height:56px;align-items:center;justify-content:space-between;gap:12px;margin:0;overflow:hidden}.visual-card-heading>div{min-width:0;overflow:hidden}.visual-card-heading>div>span{color:var(--color-primary);font-size:10px;font-weight:750}.visual-card-heading h2{overflow:hidden;margin:3px 0;font-size:15px;text-overflow:ellipsis;white-space:nowrap}.visual-card-heading p{overflow:hidden;margin:0;color:var(--color-muted-foreground);font-size:11px;text-overflow:ellipsis;white-space:nowrap}.visual-card-heading>svg{flex:none;color:var(--color-primary)}.guide-visual-card>.mini-window{display:flex;min-height:0;height:100%;flex:1;flex-direction:column}.guide-visual-card>.visual-note{display:-webkit-box;min-height:40px;height:40px;margin:0 1px;overflow:hidden;color:var(--color-muted-foreground);font-size:12px;line-height:20px;-webkit-box-orient:vertical;-webkit-line-clamp:2}
.mini-window{position:relative;min-height:286px;overflow:hidden;border:1px solid color-mix(in srgb,var(--color-border) 82%,#8792b6);border-radius:12px;color:#e9ebf4;background:#13151b;box-shadow:0 7px 18px #0002}.mini-topbar{display:flex;height:34px;align-items:center;gap:5px;padding:0 11px;border-bottom:1px solid #2c303b;background:#1b1e27}.mini-topbar>span{width:6px;height:6px;border-radius:50%;background:#5a6070}.mini-topbar>b{margin-left:6px;color:#f0f1f5;font-size:10px}.mini-topbar>i{margin-left:auto;color:#aab0c3;font-size:10px;font-style:normal}.mini-layout{display:grid;min-height:250px;grid-template-columns:112px minmax(0,1fr)}.mini-layout>aside,.interview-mini-body>aside{display:grid;align-content:start;gap:9px;padding:14px 9px;border-right:1px solid #2a2e38;color:#a6acc0;font-size:10px}.mini-layout>aside b,.interview-mini-body>aside b{margin-bottom:3px;color:#e5e7f1;font-size:11px}.mini-layout>aside span,.interview-mini-body>aside span{padding:7px 6px;border-radius:5px}.mini-layout>aside span.selected{color:#c0c5ff;background:#383b52}.mini-main{position:relative;display:grid;align-content:start;gap:9px;padding:15px}.mini-main>small,.application-mini-body small,.interview-mini-body small{color:#b5bbff;font-size:10px;font-weight:750}.mini-main>strong,.application-mini-body strong,.interview-mini-body>section>strong{font-size:13px}.mini-target{position:relative;display:grid;min-height:76px;align-content:center;gap:7px;padding:11px;border:1px solid #3a4052;border-radius:7px;background:#1d2029}.mini-target span{color:#d6d9e4;font-size:10px}.mini-target i{display:block;width:68%;height:5px;border-radius:4px;background:#343846}.mini-target i:last-child{width:44%}.mini-button,.mini-primary{width:max-content;padding:7px 11px;border-radius:6px;color:#fff;background:#6268cf;font-size:10px;font-weight:700}.mini-api .mini-target,.mini-mail-config .mini-target,.mini-resume .mini-target{outline:1px solid #99a1ff;outline-offset:2px}.visual-note{min-height:38px}
.application-mini{min-height:330px}.application-mini-body{position:relative;min-height:295px;padding:18px}.mini-toolbar{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}.mini-toolbar strong{font-size:14px}.mini-row{display:grid;grid-template-columns:1fr 1.4fr auto;align-items:center;gap:10px;padding:18px 12px;border:1px solid #343947;border-radius:8px;background:#1e212a;font-size:11px}.mini-row span{color:#b0b5c4}.mini-row i{color:#aeb5ff;font-size:10px;font-style:normal}.mini-primary{position:relative;z-index:1;padding:9px 13px;color:#fff;background:#646bdb}.new-application .mini-focus{position:absolute;top:42px;right:13px;width:112px;height:34px;border:2px solid #a4aaff;border-radius:7px;box-shadow:0 0 0 100vmax #090b1266}.mini-form{display:grid;gap:10px;padding:12px;border:1px solid #343947;border-radius:8px;background:#1e212a}.mini-form>b{margin-bottom:2px;font-size:11px}.form-line{display:grid;grid-template-columns:100px 1fr;align-items:center;gap:10px;font-size:10px}.form-line span{color:#b4bacb}.form-line i{padding:8px;border:1px solid #3b4050;border-radius:5px;color:#e0e2ec;background:#171920;font-size:10px;font-style:normal}.mini-form .mini-primary{justify-self:end;margin-top:4px}.application-form .mini-focus{position:absolute;top:113px;left:21px;width:min(88%,360px);height:40px;border:2px solid #a4aaff;border-radius:6px;box-shadow:0 0 0 100vmax #090b1266}
.mail-page-layout{display:grid;min-height:0;height:100%;grid-template-columns:minmax(0,1fr) 275px;align-items:stretch;gap:24px}.mail-visual-card{width:100%;max-width:1000px;justify-self:center}.mail-mini{min-height:0}.mail-mini-body{display:grid;min-height:0;flex:1;grid-template-columns:1fr 40px 1fr;align-items:center;gap:12px;padding:19px;overflow:hidden}.mail-mini-body section{display:grid;min-height:0;align-content:start;gap:13px;padding:14px;border:1px solid #353a48;border-radius:9px;background:#1b1e27}.mail-mini-body section>small{color:#b5bbff;font-size:11px;font-weight:750}.mail-text-lines{display:grid;gap:11px;padding:12px;border:1px dashed #596075;border-radius:7px}.mail-text-lines i{height:7px;border-radius:5px;background:#424758}.mail-text-lines i:nth-child(2){width:82%}.mail-text-lines i:nth-child(4){width:68%}.mail-mini-body .mini-primary,.mail-mini-body .mini-button{justify-self:end}.recognized .form-line{grid-template-columns:68px 1fr}.mini-arrow{color:#abb1ff;text-align:center;font-size:20px}.mail-recognition{position:absolute;inset:40px 15px 15px;border:2px solid #a4aaff;border-radius:10px;pointer-events:none;box-shadow:0 0 0 100vmax #090b1266}.guide-side-note{display:flex;align-items:center;gap:12px;padding:19px 15px;border:1px solid color-mix(in srgb,var(--color-primary) 25%,var(--color-border));border-radius:13px;background:color-mix(in srgb,var(--color-primary) 7%,var(--color-card));overflow:hidden}.note-icon{display:grid;width:34px;height:34px;flex:none;place-items:center;border-radius:9px;color:var(--color-primary);background:color-mix(in srgb,var(--color-primary) 14%,var(--color-card))}.guide-side-note strong{font-size:13px}.guide-side-note p{margin:6px 0 0;color:var(--color-muted-foreground);font-size:12px;line-height:1.65}
.interview-mini{min-height:340px}.interview-mini-body{position:relative;display:grid;min-height:305px;grid-template-columns:112px minmax(0,1fr)}.interview-mini-body>aside{align-content:start}.interview-mini-body>section{position:relative;display:grid;align-content:start;gap:10px;padding:15px}.review-editor label{display:grid;gap:7px;color:#b6bdce;font-size:10px}.review-editor textarea{min-height:105px;resize:none;padding:10px;border:1px solid #44495c;border-radius:6px;color:#e5e7f0;background:#20232d;font-size:10px;line-height:1.6}.review-editor .mini-primary{justify-self:end}.interview-edit .mini-focus{position:absolute;top:126px;right:18px;left:127px;height:112px;border:2px solid #a4aaff;border-radius:7px;box-shadow:0 0 0 100vmax #090b1266}.mini-tabs{display:flex;gap:7px;margin:4px 0}.mini-tabs b{padding:7px 9px;border:1px solid #3b4050;border-radius:6px;color:#c4c9ff;font-size:9px;background:#222531}.mini-tabs b:first-child{border-color:#9299f2;background:#383b53}.mini-question{display:grid;gap:7px;padding:10px;border:1px solid #373b49;border-radius:7px;background:#1e212b}.mini-question>b{width:max-content;padding:4px 6px;border-radius:4px;color:#c3c7ff;background:#383b52;font-size:9px}.mini-question>strong{font-size:10px}.mini-question p{margin:0;color:#c8cddd;font-size:9px;line-height:1.5}.interview-review .mini-focus{position:absolute;inset:40px 13px 13px;border:2px solid #a4aaff;border-radius:8px;pointer-events:none;box-shadow:0 0 0 100vmax #090b1266}
.guide-footer{display:flex;height:62px;align-items:center;justify-content:center;gap:12px;margin:0;padding:0}.guide-next{display:flex;min-height:48px;align-items:center;gap:10px;padding:0 22px;border:1px solid #747bea;border-radius:11px;color:#fff;background:#555cc7;font-size:14px;font-weight:750;box-shadow:0 7px 20px #454cb844;transition:filter .2s,transform .2s,box-shadow .2s}.guide-next:hover{filter:brightness(1.12);transform:translateY(-2px);box-shadow:0 10px 25px #454cb855}
.guide-jump{min-height:48px;padding:0 18px;border:1px solid var(--color-border-strong);border-radius:11px;color:var(--color-foreground);background:var(--color-card);font-size:13px;font-weight:700;transition:border-color .2s,background .2s,transform .2s}.guide-jump:hover{transform:translateY(-1px);border-color:var(--color-primary);background:color-mix(in srgb,var(--color-primary) 10%,var(--color-card))}
.guide-dock{position:fixed;z-index:30;bottom:calc(18px + env(safe-area-inset-bottom));left:calc(50% + var(--sidebar-width)/2);display:flex;align-items:flex-end;gap:10px;padding:12px 14px 14px;background:transparent;transform:translateX(-50%)}.dock-item{position:relative;display:grid;width:52px;height:52px;flex:none;place-items:center;padding:0;border:0;border-radius:0;color:var(--color-muted-foreground);background:transparent;outline-offset:5px}.dock-item svg{width:24px;height:24px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round;transform:translateY(0) scale(1);transform-origin:center bottom;transition:transform .52s cubic-bezier(.22,.72,.25,1),color .35s ease,filter .35s ease;will-change:transform}.dock-item.dock-current{color:var(--color-primary)}.dock-item.dock-current svg{filter:drop-shadow(0 2px 5px color-mix(in srgb,var(--color-primary) 48%,transparent));transform:translateY(-1px) scale(1.08)}.dock-item.dock-hovered{z-index:2;color:var(--color-foreground)}.dock-item.dock-hovered svg{transform:translateY(-7px) scale(1.34)}.dock-item.dock-current.dock-hovered{color:var(--color-primary)}.dock-item.dock-neighbor{color:color-mix(in srgb,var(--color-foreground) 84%,var(--color-primary))}.dock-item.dock-neighbor svg{transform:translateY(-3px) scale(1.15)}.dock-label{position:absolute;bottom:calc(100% + 11px);left:50%;padding:6px 10px;border:1px solid var(--color-border);border-radius:8px;color:var(--color-foreground);background:var(--color-card);box-shadow:var(--shadow-md);font-size:11px;font-weight:700;white-space:nowrap;opacity:0;pointer-events:none;transform:translate(-50%,5px);transition:opacity .28s ease,transform .38s cubic-bezier(.22,.72,.25,1)}.dock-item:hover .dock-label,.dock-item:focus-visible .dock-label{opacity:1;transform:translate(-50%,0)}.dock-dot{position:absolute;bottom:2px;left:50%;width:5px;height:5px;border-radius:50%;background:var(--color-primary);box-shadow:0 0 8px color-mix(in srgb,var(--color-primary) 72%,transparent);transform:translateX(-50%)}
.guide-arrow{position:fixed;z-index:5;top:50%;display:grid;width:46px;height:46px;place-items:center;padding:0;border:1px solid var(--color-border);border-radius:50%;color:var(--color-foreground);background:color-mix(in srgb,var(--color-card) 86%,transparent);box-shadow:var(--shadow-md);backdrop-filter:blur(10px);transform:translateY(-50%);transition:color .2s,border-color .2s,background .2s,transform .2s}.guide-arrow:hover:not(:disabled){transform:translateY(-50%) scale(1.07);border-color:var(--color-primary);color:var(--color-primary);background:var(--color-card)}.guide-arrow:disabled{opacity:.38}.guide-arrow-left{left:calc(var(--sidebar-width) + 18px)}.guide-arrow-right{right:18px}
.guide-turn-enter-active,.guide-turn-leave-active{transition:opacity .2s ease,transform .2s ease}.guide-turn-enter-from{transform:translateY(9px);opacity:0}.guide-turn-leave-to{transform:translateY(-7px);opacity:0}
@media(max-width:1200px){.guide-content{width:min(1100px,100%)}.config-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.config-grid>.guide-visual-card:last-child{grid-column:1/-1}.guide-arrow-left{left:calc(var(--sidebar-width) + 8px)}.guide-arrow-right{right:8px}}
@media(max-width:900px){.guide-page{min-height:calc(100dvh - 108px);overflow:visible}.guide-header{top:76px;padding-inline:14px}.guide-content{display:flex;width:100%;min-height:0;flex:none;flex-direction:column;gap:18px;padding:22px 58px 132px}.guide-copy{height:140px;min-height:140px;grid-template-rows:14px 46px minmax(48px,auto)}.guide-copy>p{max-height:48px}.config-grid,.two-grid{height:auto;grid-template-columns:minmax(0,1fr)}.config-grid>.guide-visual-card:last-child{grid-column:auto}.guide-visual-card{height:500px;min-height:500px;padding:14px}.visual-card-heading{flex:none}.guide-visual-card>.mini-window{min-height:0;flex:1}.guide-visual-card>.visual-note{flex:none}.mail-page-layout{height:auto;grid-template-columns:minmax(0,1fr)}.mail-visual-card{height:500px}.guide-side-note{min-height:88px;max-width:700px;align-self:stretch;justify-self:center}.guide-dock{left:50%;bottom:calc(12px + env(safe-area-inset-bottom));gap:7px;padding:9px 10px 12px}.dock-item{width:48px;height:48px}.guide-arrow-left{left:9px}.guide-arrow-right{right:9px}}
@media(max-width:620px){.guide-page{min-height:calc(100dvh - 108px)}.guide-header{top:76px;min-height:60px;gap:10px;padding-inline:12px}.guide-brand-icon{width:34px;height:34px}.guide-brand>div>span{font-size:8px}.guide-brand strong{font-size:13px}.guide-progress{gap:7px}.guide-progress>div{width:42px}.guide-close{min-width:42px;width:42px;height:42px}.guide-close span{display:none}.guide-content{padding:20px 48px 122px}.guide-copy{margin-bottom:18px}.guide-copy h1{font-size:25px}.guide-copy>p{font-size:13px;line-height:1.65}.guide-visual-card{padding:12px}.mini-window{min-height:240px}.mini-layout{min-height:205px;grid-template-columns:78px minmax(0,1fr)}.mini-layout>aside{gap:6px;padding:10px 5px;font-size:9px}.mini-layout>aside span{padding:5px 3px}.mini-main{padding:10px}.visual-note{min-height:auto}.application-mini{min-height:285px}.application-mini-body{min-height:250px;padding:13px}.mail-mini{min-height:300px}.mail-mini-body{min-height:266px;gap:5px;padding:10px;grid-template-columns:minmax(0,1fr) 20px minmax(0,1fr)}.mail-mini-body section{min-height:210px;gap:8px;padding:8px}.interview-mini{min-height:300px}.interview-mini-body{min-height:265px;grid-template-columns:78px minmax(0,1fr)}.interview-mini-body>section{gap:7px;padding:9px}.interview-mini-body>aside{gap:6px;padding:10px 5px;font-size:9px}.interview-mini-body>aside span{padding:5px 3px}.review-editor textarea{min-height:80px}.mini-tabs{gap:3px}.mini-tabs b{padding:5px 4px;font-size:8px}.guide-footer{gap:8px;margin-top:18px}.guide-jump,.guide-next{min-height:44px;padding-inline:13px;font-size:12px}.guide-dock{gap:5px;padding:7px 8px 10px}.dock-item{width:42px;height:44px}.dock-item svg{width:21px;height:21px}.dock-item.dock-hovered svg{transform:translateY(-5px) scale(1.23)}.dock-item.dock-neighbor svg{transform:translateY(-2px) scale(1.08)}.dock-dot{bottom:3px;width:4px;height:4px}.guide-arrow{width:36px;height:36px}.guide-arrow-left{left:5px}.guide-arrow-right{right:5px}.form-line{grid-template-columns:45px 1fr}.guide-side-note{padding:13px}}
@media(prefers-reduced-motion:reduce){.guide-progress i,.guide-arrow,.guide-next,.guide-turn-enter-active,.guide-turn-leave-active,.dock-item svg,.dock-label{transition:none}}
</style>
