<script setup lang="ts">
const props=withDefaults(defineProps<{name:string;size?:number}>(),{size:18})
const icons:Record<string,string[]>={
  close:['M6 6l12 12','M18 6 6 18'],
  refresh:['M20 6v5h-5','M4 18v-5h5','M18.5 9A7 7 0 0 0 6.2 6.2L4 9','M5.5 15A7 7 0 0 0 17.8 17.8L20 15'],
  edit:['M12 20h9','M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4Z'],
  trash:['M3 6h18','M8 6V4h8v2','M19 6l-1 14H6L5 6','M10 10v6','M14 10v6'],
  check:['m5 12 4 4L19 6'],
  undo:['M9 7 4 12l4 5','M5 12h8a6 6 0 0 1 6 6v1'],
  eye:['M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12Z','M12 9a3 3 0 1 1 0 6 3 3 0 0 1 0-6Z'],
  'eye-off':['M3 3l18 18','M10.6 10.6a2 2 0 0 0 2.8 2.8','M9.8 5.2A10 10 0 0 1 12 5c6.5 0 10 7 10 7a17 17 0 0 1-2.1 3','M6.6 6.6C3.6 8.4 2 12 2 12s3.5 7 10 7a10 10 0 0 0 4.2-.9'],
  calendar:['M4 5h16v15H4Z','M8 3v4','M16 3v4','M4 10h16'],
  logout:['M10 17l5-5-5-5','M15 12H3','M14 4h5a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-5'],
  power:['M12 2v10','M6.3 5.3a8 8 0 1 0 11.4 0'],
  'chevron-left':['m15 18-6-6 6-6'],
  'chevron-right':['m9 18 6-6-6-6'],
  ban:['M5 5l14 14','M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18Z'],
  'clock-x':['M12 7v5l3 2','M4.9 4.9a10 10 0 1 0 14.2 0','M17 17l4 4','M21 17l-4 4'],
  info:['M12 11v6','M12 7h.01','M12 2a10 10 0 1 1 0 20 10 10 0 0 1 0-20Z'],
  broom:['m4 20 5-5','m14 4 6 6-9 9-6-6Z','m8 16 3 3'],
  download:['M12 3v12','m7 10 5 5 5-5','M5 21h14'],
  upload:['M12 21V9','m7 14 5-5 5 5','M5 3h14'],
  sun:['M12 3v2.5','M12 18.5V21','M3 12h2.5','M18.5 12H21','m5.6 5.6 1.8 1.8','m10.8 10.8 2.4 2.4','m5.6 18.4 1.8-1.8','m16.6 7.4 1.8-1.8','M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z'],
  moon:['M20.5 14.5A8.5 8.5 0 0 1 9.5 3.5 8.5 8.5 0 1 0 20.5 14.5Z']
}
</script>

<template><svg :width="size" :height="size" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path v-for="path in icons[props.name]||icons.info" :key="path" :d="path" /></svg></template>
