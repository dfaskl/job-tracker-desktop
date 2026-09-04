import { createApp } from 'vue'
import App from './App.vue'
import './style.css'

document.documentElement.dataset.theme = localStorage.getItem('job-tracker-theme') || 'blue'

createApp(App).mount('#app')
