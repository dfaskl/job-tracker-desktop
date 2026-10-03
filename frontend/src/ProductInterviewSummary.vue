<template>
  <section class="interview-summary" aria-labelledby="interview-summary-title">
    <header class="page-heading">
      <span class="eyebrow">面试复盘</span>
      <h1 id="interview-summary-title">面试总结</h1>
      <p>集中整理面试中的问题与经验，回顾仍在日程编辑中记录。</p>
    </header>

    <div class="workspace-placeholder">
      <span class="placeholder-index" aria-hidden="true">01 / 面试回顾</span>
      <h2>整理空间已就绪</h2>
      <p>这里将汇总已记录的面试回顾。整理方式与总结内容，等后续规划确定后再完善。</p>
    </div>
  </section>
</template>

<style scoped>
.interview-summary { width: min(1120px, 100%); margin: 0 auto; padding: 44px 0 80px; }
.page-heading { padding-bottom: 28px; border-bottom: 1px solid var(--color-border); }
.eyebrow { color: var(--color-primary); font-size: 12px; font-weight: 650; letter-spacing: .08em; }
h1 { margin: 10px 0 8px; color: var(--color-foreground); font-size: clamp(27px, 3vw, 36px); line-height: 1.2; }
.page-heading p, .workspace-placeholder p { margin: 0; color: var(--color-muted-foreground); line-height: 1.7; }
.workspace-placeholder { max-width: 560px; padding: 72px 0; }
.placeholder-index { color: var(--color-primary); font-size: 12px; font-weight: 600; }
h2 { margin: 14px 0 10px; color: var(--color-foreground); font-size: 20px; }
@media (max-width: 820px) {
  .interview-summary { padding: 26px 4px 60px; }
  .workspace-placeholder { padding: 52px 0; }
}
</style>
