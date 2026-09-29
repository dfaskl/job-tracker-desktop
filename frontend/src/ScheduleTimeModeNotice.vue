<script setup lang="ts">
defineProps<{ mode:'point'|'range'; detected?:boolean }>()
</script>

<template>
  <aside class="time-mode-notice" :class="`is-${mode}`" role="note" aria-live="polite">
    <span class="notice-icon" aria-hidden="true">
      <svg viewBox="0 0 24 24"><path d="M12 3 2.8 19h18.4L12 3Z"/><path d="M12 8v5"/><circle cx="12" cy="16.5" r=".8"/></svg>
    </span>
    <div class="notice-copy">
      <strong>{{detected?'请核对 AI 识别的时间类型':'请先区分时间点与时间段'}}</strong>
      <p>选错会影响小组时间轴和后续自动安排，请根据通知原文确认。</p>
      <div class="mode-examples">
        <span :class="{active:mode==='point'}"><b>时间点</b><small>明确在某一时刻发生，例如“9 月 25 日 14:00 面试”；会进入小组时间轴。</small></span>
        <span :class="{active:mode==='range'}"><b>时间段</b><small>在截止前任选时间完成，例如“9 月 25–27 日完成测评”；用于后续安排，不作为固定时间点。</small></span>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.time-mode-notice{display:grid;grid-template-columns:34px minmax(0,1fr);gap:12px;padding:14px;border:1px solid #e0a94a;border-left:5px solid #d88719;border-radius:12px;color:#54370b;background:linear-gradient(110deg,#fff6dc,#fffaf0);box-shadow:0 7px 18px rgba(146,91,14,.08)}
.notice-icon{display:grid;width:34px;height:34px;place-items:center;border-radius:10px;color:#a85f08;background:#ffe7a9}.notice-icon svg{width:22px;height:22px;fill:none;stroke:currentColor;stroke-width:1.9;stroke-linecap:round;stroke-linejoin:round}
.notice-copy{display:grid;min-width:0;gap:4px}.notice-copy>strong{font-size:14px}.notice-copy>p{margin:0;color:#76531b;font-size:12px;line-height:1.55}
.mode-examples{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:5px}.mode-examples>span{display:grid;gap:2px;padding:8px 10px;border:1px solid rgba(174,119,28,.18);border-radius:8px;background:rgba(255,255,255,.58)}.mode-examples>span.active{border-color:#cd7a11;background:#fff;box-shadow:inset 3px 0 #d88719}.mode-examples b{font-size:12px}.mode-examples small{color:#725b36;font-size:11px;font-weight:500;line-height:1.45}
.is-point .mode-examples>span.active b{color:#176f79}.is-range .mode-examples>span.active b{color:#a45d08}
@media(max-width:620px){.time-mode-notice{grid-template-columns:1fr}.notice-icon{width:30px;height:30px}.mode-examples{grid-template-columns:1fr}}
</style>
