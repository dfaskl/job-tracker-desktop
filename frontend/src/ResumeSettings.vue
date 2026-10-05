<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { api } from './api'
import { useJobTrackerStore } from './jobTrackerStore'

type Internship = { company: string; role: string; description: string; coreWork: string }
type Project = { name: string; description: string; coreWork: string }
type Education = { school: string; major: string; period: string }
type Resume = { education: Education[]; internships: Internship[]; projects: Project[] }
const store = useJobTrackerStore()
const resume = reactive<Resume>({ education: [], internships: [], projects: [] })
const saving = ref(false)
const editing = ref(false)
const error = ref('')
const message = ref('')
const savedResume = computed(() => (store.data.value.settings?.interviewWorkbench as { resume?: Resume } | undefined)?.resume)
const hasSavedResume = computed(() => !!savedResume.value)

function resetDraft() {
  const saved = savedResume.value
  resume.education = saved?.education?.map(item => ({ ...item })) || []
  resume.internships = saved?.internships?.map(item => ({ ...item })) || []
  resume.projects = saved?.projects?.map(item => ({ ...item })) || []
}

watch(() => JSON.stringify(savedResume.value || {}), serialized => {
  if (editing.value) return
  const saved = JSON.parse(serialized) as Resume
  resume.education = saved?.education?.map(item => ({ ...item })) || []
  resume.internships = saved?.internships?.map(item => ({ ...item })) || []
  resume.projects = saved?.projects?.map(item => ({ ...item })) || []
}, { immediate: true })

function startEditing() { resetDraft(); error.value = ''; message.value = ''; editing.value = true }
function addFirstEducation() { startEditing(); resume.education.push({ school: '', major: '', period: '' }) }
function addFirstInternship() { startEditing(); resume.internships.push({ company: '', role: '', description: '', coreWork: '' }) }
function addFirstProject() { startEditing(); resume.projects.push({ name: '', description: '', coreWork: '' }) }
function cancelEditing() { editing.value = false; resetDraft(); error.value = ''; message.value = '' }

async function save() {
  saving.value = true; error.value = ''; message.value = ''
  try {
    await api('/api/poc/interview-workbench/resume', { method: 'PUT', body: JSON.stringify(resume) })
    await store.refresh(true, false, false)
    editing.value = false
    message.value = '简历内容已保存，之后的面试考点汇总会参考这些信息'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '保存简历失败' }
  finally { saving.value = false }
}
</script>

<template>
  <section id="resume-settings" class="card resume-settings" aria-labelledby="resume-settings-title">
    <div class="section-head resume-head"><div><span>面试复盘</span><h2 id="resume-settings-title">我的简历</h2></div><button v-if="hasSavedResume && !editing" type="button" class="secondary" @click="startEditing">编辑简历</button></div>
    <p class="intro">记录教育、实习和项目经历，帮助 AI 结合你的背景整理面试考点。只在你主动汇总考点时作为上下文发送。</p>
    <div v-if="hasSavedResume && !editing" class="resume-preview">
      <section class="resume-preview-group"><h3>教育经历</h3><div v-if="!savedResume?.education?.length" class="empty-state"><p class="empty">还没有添加教育经历。</p><button type="button" class="secondary" @click="addFirstEducation">＋ 添加第一条</button></div><article v-for="(item, index) in savedResume?.education || []" :key="index" class="resume-preview-entry"><div class="preview-title"><strong>{{ item.school || `教育经历 ${index + 1}` }}</strong><span v-if="item.major">{{ item.major }}</span><span v-if="item.period">{{ item.period }}</span></div></article></section>
      <section class="resume-preview-group"><h3>实习经历</h3><div v-if="!savedResume?.internships?.length" class="empty-state"><p class="empty">还没有添加实习经历。</p><button type="button" class="secondary" @click="addFirstInternship">＋ 添加第一段</button></div><article v-for="(item, index) in savedResume?.internships || []" :key="index" class="resume-preview-entry"><div class="preview-title"><strong>{{ item.company || `实习 ${index + 1}` }}</strong><span v-if="item.role">{{ item.role }}</span></div><div v-if="item.description" class="preview-field"><small>经历简介</small><p>{{ item.description }}</p></div><div v-if="item.coreWork" class="preview-field"><small>核心工作</small><p>{{ item.coreWork }}</p></div></article></section>
      <section class="resume-preview-group"><h3>项目经历</h3><div v-if="!savedResume?.projects?.length" class="empty-state"><p class="empty">还没有添加项目经历。</p><button type="button" class="secondary" @click="addFirstProject">＋ 添加第一个</button></div><article v-for="(item, index) in savedResume?.projects || []" :key="index" class="resume-preview-entry"><div class="preview-title"><strong>{{ item.name || `项目 ${index + 1}` }}</strong></div><div v-if="item.description" class="preview-field"><small>项目简介</small><p>{{ item.description }}</p></div><div v-if="item.coreWork" class="preview-field"><small>核心工作</small><p>{{ item.coreWork }}</p></div></article></section>
    </div>
    <form v-else @submit.prevent="save">
      <div class="group-heading"><h3>教育经历</h3><button type="button" class="secondary" :disabled="resume.education.length >= 10" @click="resume.education.push({ school: '', major: '', period: '' })">{{ resume.education.length ? '＋ 添加教育经历' : '＋ 添加第一条教育经历' }}</button></div>
      <p v-if="!resume.education.length" class="empty">还没有添加教育经历。</p>
      <div v-for="(item, index) in resume.education" :key="index" class="resume-entry"><div class="entry-title"><strong>教育经历 {{ index + 1 }}</strong><button type="button" class="remove" :aria-label="`删除第 ${index + 1} 条教育经历`" :title="`删除第 ${index + 1} 条教育经历`" @click="resume.education.splice(index, 1)">删除</button></div><label>学校<input v-model.trim="item.school" maxlength="120" placeholder="学校名称"></label><div class="field-pair"><label>专业<input v-model.trim="item.major" maxlength="120" placeholder="专业名称"></label><label>时间<input v-model.trim="item.period" maxlength="120" placeholder="例如：2022.09 - 2026.06"></label></div></div>
      <div class="group-heading"><h3>实习经历</h3><button type="button" class="secondary" :disabled="resume.internships.length >= 10" @click="resume.internships.push({ company: '', role: '', description: '', coreWork: '' })">{{ resume.internships.length ? '＋ 添加实习' : '＋ 添加第一段实习' }}</button></div>
      <p v-if="!resume.internships.length" class="empty">还没有添加实习经历。</p>
      <div v-for="(item, index) in resume.internships" :key="index" class="resume-entry"><div class="entry-title"><strong>实习 {{ index + 1 }}</strong><button type="button" class="remove" :aria-label="`删除第 ${index + 1} 段实习`" :title="`删除第 ${index + 1} 段实习`" @click="resume.internships.splice(index, 1)">删除</button></div><div class="field-pair"><label>公司<input v-model.trim="item.company" maxlength="120" placeholder="实习公司"></label><label>岗位<input v-model.trim="item.role" maxlength="120" placeholder="实习岗位"></label></div><label>经历简介<textarea v-model.trim="item.description" maxlength="1000" rows="2" placeholder="业务背景与团队职责"></textarea></label><label>核心工作<textarea v-model.trim="item.coreWork" maxlength="2000" rows="3" placeholder="具体工作、技术与成果"></textarea></label></div>
      <div class="group-heading project-heading"><h3>项目经历</h3><button type="button" class="secondary" :disabled="resume.projects.length >= 10" @click="resume.projects.push({ name: '', description: '', coreWork: '' })">{{ resume.projects.length ? '＋ 添加项目' : '＋ 添加第一个项目' }}</button></div>
      <p v-if="!resume.projects.length" class="empty">还没有添加项目经历。</p>
      <div v-for="(item, index) in resume.projects" :key="index" class="resume-entry"><div class="entry-title"><strong>项目 {{ index + 1 }}</strong><button type="button" class="remove" :aria-label="`删除第 ${index + 1} 个项目`" :title="`删除第 ${index + 1} 个项目`" @click="resume.projects.splice(index, 1)">删除</button></div><label>项目名称<input v-model.trim="item.name" maxlength="120" placeholder="项目名称"></label><label>项目简介<textarea v-model.trim="item.description" maxlength="1000" rows="2" placeholder="目标、背景和系统作用"></textarea></label><label>核心工作<textarea v-model.trim="item.coreWork" maxlength="2000" rows="3" placeholder="你负责的部分与实现方式"></textarea></label></div>
      <div class="form-actions"><button class="save-button primary-action" :disabled="saving">{{ saving ? '正在保存…' : '保存简历内容' }}</button><button v-if="editing" type="button" class="secondary" :disabled="saving" @click="cancelEditing">取消</button></div>
    </form>
    <p v-if="error" class="feedback error" role="alert">{{ error }}</p><p v-if="message" class="feedback success" role="status">{{ message }}</p>
  </section>
</template>

<style scoped>
.resume-settings{display:grid;gap:14px}.section-head span{color:var(--color-primary);font-size:11px;font-weight:800;letter-spacing:.1em}.section-head h2{margin:4px 0 0}.intro,.empty{margin:0;color:var(--color-muted-foreground);font-size:13px;line-height:1.6}form{display:grid;gap:13px}.group-heading,.entry-title{display:flex;align-items:center;justify-content:space-between;gap:12px}.group-heading{padding-top:8px}.group-heading h3{margin:0;font-size:15px}.group-heading button{min-height:36px;padding:6px 10px;font-size:12px}.project-heading{padding-top:20px;border-top:1px solid var(--color-border)}.resume-entry{display:grid;gap:11px;padding:15px 0;border-top:1px solid var(--color-border)}.entry-title strong{font-size:13px}.remove{padding:4px 8px;border:0;color:var(--color-destructive);background:transparent;font-size:12px}.field-pair{display:grid;grid-template-columns:1fr 1fr;gap:10px}label{display:grid;gap:5px;color:var(--color-muted-foreground);font-size:12px;font-weight:650}input,textarea{width:100%;min-width:0;resize:vertical}.save-button{width:100%;margin-top:12px;color:var(--color-on-primary);background:var(--color-primary)}.feedback{margin:0;padding:9px 11px;border-radius:8px;font-size:13px}.error{color:var(--color-destructive);background:color-mix(in srgb,var(--color-destructive) 10%,var(--color-background))}.success{color:var(--color-primary);background:color-mix(in srgb,var(--color-primary) 9%,var(--color-background))}@media(max-width:620px){.field-pair{grid-template-columns:1fr}}
.resume-settings :is(.group-heading button,.remove){min-height:44px}.resume-settings .remove{padding-inline:12px}
.resume-settings.resume-highlight{animation:resume-attention .7s ease-in-out 2}
.resume-settings .empty{padding:11px 13px;border:1px dashed var(--color-border);border-radius:9px;background:var(--color-muted);font-size:12px}.empty-state{display:flex;align-items:center;justify-content:space-between;gap:10px}.empty-state button{min-height:38px;white-space:nowrap}
@keyframes resume-attention{0%,100%{box-shadow:0 0 0 0 transparent;border-color:var(--color-border)}35%,70%{box-shadow:0 0 0 4px color-mix(in srgb,var(--color-primary) 34%,transparent),0 0 28px color-mix(in srgb,var(--color-primary) 30%,transparent);border-color:var(--color-primary)}}
@media(prefers-reduced-motion:reduce){.resume-settings.resume-highlight{animation:none;outline:3px solid var(--color-primary);outline-offset:3px}}
.resume-head{display:flex;align-items:center;justify-content:space-between;gap:16px}.resume-head button{flex:none;min-height:40px;padding:8px 13px}.resume-preview{display:grid;gap:22px}.resume-preview-group{display:grid;gap:0}.resume-preview-group+.resume-preview-group{padding-top:18px;border-top:1px solid var(--color-border)}.resume-preview-group h3{margin:0 0 10px;font-size:15px}.resume-preview-entry{display:grid;gap:11px;padding:14px 0;border-top:1px solid var(--color-border)}.preview-title{display:flex;align-items:baseline;flex-wrap:wrap;gap:6px 10px}.preview-title strong{font-size:14px;overflow-wrap:anywhere}.preview-title span{color:var(--color-muted-foreground);font-size:13px;overflow-wrap:anywhere}.preview-field small{display:block;margin-bottom:4px;color:var(--color-muted-foreground);font-size:12px;font-weight:650}.preview-field p{margin:0;line-height:1.6;white-space:pre-wrap;overflow-wrap:anywhere}.form-actions{display:flex;gap:10px;margin-top:12px}.form-actions .save-button{flex:1;width:auto;margin:0}.form-actions .secondary{min-width:76px}
</style>
