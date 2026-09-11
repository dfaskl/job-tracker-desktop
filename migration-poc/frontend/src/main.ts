import { createApp } from 'vue'
import App from './App.vue'
import './style.css'

delete document.documentElement.dataset.theme
localStorage.removeItem('job-tracker-theme')

createApp(App).mount('#app')
