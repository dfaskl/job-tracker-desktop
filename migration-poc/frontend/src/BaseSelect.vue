<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

export type SelectOption = { value: string; label: string; group?: string; disabled?: boolean }
const props = withDefaults(defineProps<{ modelValue:string; options:SelectOption[]|string[]; disabled?:boolean; placeholder?:string; align?:'left'|'center'; ariaLabel?:string }>(), { disabled:false, placeholder:'请选择', align:'left' })
const emit = defineEmits<{ 'update:modelValue':[value:string] }>()
const root=ref<HTMLElement|null>(null), trigger=ref<HTMLButtonElement|null>(null), open=ref(false), activeIndex=ref(-1)
const normalized=computed<SelectOption[]>(()=>props.options.map(item=>typeof item==='string'?{value:item,label:item}:item))
const selected=computed(()=>normalized.value.find(item=>item.value===props.modelValue))
function close(){open.value=false;activeIndex.value=-1}
function toggle(){if(props.disabled)return;open.value=!open.value;activeIndex.value=open.value?Math.max(0,normalized.value.findIndex(item=>item.value===props.modelValue)):-1}
function choose(item:SelectOption){if(item.disabled)return;emit('update:modelValue',item.value);close();nextTick(()=>trigger.value?.focus())}
function move(offset:number){if(!open.value){toggle();return}if(!normalized.value.length)return;let index=activeIndex.value;do index=(index+offset+normalized.value.length)%normalized.value.length;while(normalized.value[index]?.disabled);activeIndex.value=index;nextTick(()=>root.value?.querySelector<HTMLElement>(`[data-index="${index}"]`)?.scrollIntoView({block:'nearest'}))}
function onKeydown(event:KeyboardEvent){if(event.key==='ArrowDown'){event.preventDefault();move(1)}else if(event.key==='ArrowUp'){event.preventDefault();move(-1)}else if(event.key==='Enter'||event.key===' '){event.preventDefault();open.value&&activeIndex.value>=0?choose(normalized.value[activeIndex.value]):toggle()}else if(event.key==='Escape'){event.preventDefault();close();trigger.value?.focus()}}
function onDocumentClick(event:MouseEvent){if(!root.value?.contains(event.target as Node))close()}
onMounted(()=>document.addEventListener('pointerdown',onDocumentClick));onBeforeUnmount(()=>document.removeEventListener('pointerdown',onDocumentClick))
</script>
<template><div ref="root" :class="['base-select',`align-${align}`,{open,disabled}]" @keydown="onKeydown"><button ref="trigger" type="button" class="select-trigger" :disabled="disabled" :aria-expanded="open" :aria-label="ariaLabel||placeholder" aria-haspopup="listbox" @click="toggle"><span :class="{placeholder:!selected}">{{selected?.label||placeholder}}</span><i aria-hidden="true"></i></button><div v-if="open" class="select-menu" role="listbox"><template v-for="(item,index) in normalized" :key="`${item.group||''}:${item.value}`"><div v-if="item.group&&item.group!==normalized[index-1]?.group" class="select-group">{{item.group}}</div><button type="button" role="option" :data-index="index" :aria-selected="item.value===modelValue" :class="['select-option',{selected:item.value===modelValue,active:index===activeIndex}]" :disabled="item.disabled" @mouseenter="activeIndex=index" @click="choose(item)"><span>{{item.label}}</span><b v-if="item.value===modelValue">✓</b></button></template></div></div></template>
<style scoped>
.base-select { position: relative; width: 100%; min-width: 0; color: var(--color-card-foreground); font: inherit; }
.select-trigger { display:flex; width:100%; height:var(--control-height,44px); align-items:center; justify-content:space-between; gap:12px; padding:0 14px; border:1px solid var(--color-border); border-radius:var(--radius-md,8px); color:inherit; background:var(--color-card); font:inherit; font-weight:600; text-align:left; transition:border-color .18s ease,box-shadow .18s ease,background-color .18s ease; }
.select-trigger:hover { border-color:var(--color-border-strong); background:color-mix(in srgb,var(--color-primary) 3%,#fff); }
.select-trigger:focus-visible,.open .select-trigger { border-color:var(--color-primary); box-shadow:0 0 0 3px color-mix(in srgb,var(--color-ring) 15%,transparent); }
.select-trigger span { min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.select-trigger .placeholder { color:var(--color-muted-foreground); font-weight:500; }
.select-trigger i { width:8px; height:8px; flex:0 0 8px; border-right:2px solid var(--color-muted-foreground); border-bottom:2px solid var(--color-muted-foreground); transform:translateY(-2px) rotate(45deg); transition:transform .18s ease; }
.open .select-trigger i { transform:translateY(2px) rotate(225deg); }
.select-menu { position:absolute; top:calc(100% + 7px); right:0; left:0; z-index:90; max-height:min(330px,45vh); overflow:auto; padding:6px; border:1px solid var(--color-border); border-radius:var(--radius-lg,12px); background:var(--color-card); box-shadow:var(--shadow-lg); scrollbar-width:thin; }
.select-option { display:flex; width:100%; min-height:42px; align-items:center; justify-content:space-between; gap:12px; padding:8px 10px; border-radius:var(--radius-sm,6px); color:var(--color-card-foreground); background:transparent; font:inherit; font-weight:500; text-align:left; }
.select-option:hover,.select-option.active { color:var(--color-primary); background:color-mix(in srgb,var(--color-primary) 8%,white); }
.select-option.selected { color:var(--color-primary); background:color-mix(in srgb,var(--color-primary) 12%,white); font-weight:700; }
.select-option b { color:var(--color-primary); }
.select-group { padding:10px 10px 5px; color:var(--color-muted-foreground); font-size:11px; font-weight:700; }
.align-center .select-trigger,.align-center .select-option { text-align:center; }
.align-center .select-trigger span,.align-center .select-option span { flex:1; }
.disabled { opacity:.68; }
.disabled .select-trigger { cursor:not-allowed; background:var(--color-muted); }
</style>
