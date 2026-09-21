import { createApp } from 'vue'
import App from './App.vue'
import AppIcon from './AppIcon.vue'
import './style.css'

delete document.documentElement.dataset.theme
localStorage.removeItem('job-tracker-theme')

createApp(App).component('AppIcon',AppIcon).mount('#app')
