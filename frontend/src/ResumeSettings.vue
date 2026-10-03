<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { api } from './api'
import { useJobTrackerStore } from './jobTrackerStore'

type Internship = { company: string; role: string; description: string; coreWork: string }
type Project = { name: string; description: string; coreWork: string }
type Resume = { internships: Internship[]; projects: Project[] }
const store = useJobTrackerStore()
const resume = reactive<Resume>({ internships: [], projects: [] })
const saving = ref(false)
const error = ref('')
const message = ref('')

watch(() => JSON.stringify((store.data.value.settings?.interviewWorkbench as { resume?: Resume } | undefined)?.resume || {}), serialized => {
  const saved = JSON.parse(serialized) as Resume
  resume.internships = saved?.internships?.map(item => ({ ...item })) || []
  resume.projects = saved?.projects?.map(item => ({ ...item })) || []
}, { immediate: true })

async function save() {
  saving.value = true; error.value = ''; message.value = ''
  try {
    await api('/api/poc/interview-workbench/resume', { method: 'PUT', body: JSON.stringify(resume) })
    await store.refresh(true, false, false)
    message.value = '简历内容已保存，之后的面试考点汇总会参考这些经历'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '保存简历失败' }
  finally { saving.value = false }
}
</script>

<template>
  <section class="card resume-settings" aria-labelledby="resume-settings-title">
    <div class="section-head"><div><span>面试复盘</span><h2 id="resume-settings-title">我的简历</h2></div></div>
    <p class="intro">记录实习和项目经历，帮助 AI 区分简历追问与通用知识点。只在你主动汇总考点时作为上下文发送。</p>
    <form @submit.prevent="save">
      <div class="group-heading"><h3>实习经历</h3><button type="button" class="secondary" :disabled="resume.internships.length >= 10" @click="resume.internships.push({ company: '', role: '', description: '', coreWork: '' })">＋ 添加实习</button></div>
      <p v-if="!resume.internships.length" class="empty">还没有添加实习经历。</p>
      <div v-for="(item, index) in resume.internships" :key="index" class="resume-entry"><div class="entry-title"><strong>实习 {{ index + 1 }}</strong><button type="button" class="remove" :aria-label="`删除第 ${index + 1} 段实习`" :title="`删除第 ${index + 1} 段实习`" @click="resume.internships.splice(index, 1)">删除</button></div><div class="field-pair"><label>公司<input v-model.trim="item.company" maxlength="120" placeholder="实习公司"></label><label>岗位<input v-model.trim="item.role" maxlength="120" placeholder="实习岗位"></label></div><label>经历简介<textarea v-model.trim="item.description" maxlength="1000" rows="2" placeholder="业务背景与团队职责"></textarea></label><label>核心工作<textarea v-model.trim="item.coreWork" maxlength="2000" rows="3" placeholder="具体工作、技术与成果"></textarea></label></div>
      <div class="group-heading project-heading"><h3>项目经历</h3><button type="button" class="secondary" :disabled="resume.projects.length >= 10" @click="resume.projects.push({ name: '', description: '', coreWork: '' })">＋ 添加项目</button></div>
      <p v-if="!resume.projects.length" class="empty">还没有添加项目经历。</p>
      <div v-for="(item, index) in resume.projects" :key="index" class="resume-entry"><div class="entry-title"><strong>项目 {{ index + 1 }}</strong><button type="button" class="remove" :aria-label="`删除第 ${index + 1} 个项目`" :title="`删除第 ${index + 1} 个项目`" @click="resume.projects.splice(index, 1)">删除</button></div><label>项目名称<input v-model.trim="item.name" maxlength="120" placeholder="项目名称"></label><label>项目简介<textarea v-model.trim="item.description" maxlength="1000" rows="2" placeholder="目标、背景和系统作用"></textarea></label><label>核心工作<textarea v-model.trim="item.coreWork" maxlength="2000" rows="3" placeholder="你负责的部分与实现方式"></textarea></label></div>
      <button class="save-button primary-action" :disabled="saving">{{ saving ? '正在保存…' : '保存简历内容' }}</button>
    </form>
    <p v-if="error" class="feedback error" role="alert">{{ error }}</p><p v-if="message" class="feedback success" role="status">{{ message }}</p>
  </section>
</template>

<style scoped>
.resume-settings{display:grid;gap:14px}.section-head span{color:var(--color-primary);font-size:11px;font-weight:800;letter-spacing:.1em}.section-head h2{margin:4px 0 0}.intro,.empty{margin:0;color:var(--color-muted-foreground);font-size:13px;line-height:1.6}form{display:grid;gap:13px}.group-heading,.entry-title{display:flex;align-items:center;justify-content:space-between;gap:12px}.group-heading{padding-top:8px}.group-heading h3{margin:0;font-size:15px}.group-heading button{min-height:36px;padding:6px 10px;font-size:12px}.project-heading{padding-top:20px;border-top:1px solid var(--color-border)}.resume-entry{display:grid;gap:11px;padding:15px 0;border-top:1px solid var(--color-border)}.entry-title strong{font-size:13px}.remove{padding:4px 8px;border:0;color:var(--color-destructive);background:transparent;font-size:12px}.field-pair{display:grid;grid-template-columns:1fr 1fr;gap:10px}label{display:grid;gap:5px;color:var(--color-muted-foreground);font-size:12px;font-weight:650}input,textarea{width:100%;min-width:0;resize:vertical}.save-button{width:100%;margin-top:12px;color:var(--color-on-primary);background:var(--color-primary)}.feedback{margin:0;padding:9px 11px;border-radius:8px;font-size:13px}.error{color:var(--color-destructive);background:color-mix(in srgb,var(--color-destructive) 10%,var(--color-background))}.success{color:var(--color-primary);background:color-mix(in srgb,var(--color-primary) 9%,var(--color-background))}@media(max-width:620px){.field-pair{grid-template-columns:1fr}}
.resume-settings :is(.group-heading button,.remove){min-height:44px}.resume-settings .remove{padding-inline:12px}
</style>
