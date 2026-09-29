import { createApp } from 'vue'
import App from './App.vue'
import AppIcon from './AppIcon.vue'
import './style.css'

try {
  const savedTheme = localStorage.getItem('job-tracker-theme')
  if (savedTheme === 'dark') document.documentElement.dataset.theme = 'dark'
  else delete document.documentElement.dataset.theme
} catch {
  delete document.documentElement.dataset.theme
}

createApp(App).component('AppIcon',AppIcon).mount('#app')
