import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import './assets/styles/main.css'

// 修复 Leaflet 默认图标在 Vite 打包后丢失的问题
delete L.Icon.Default.prototype._getIconUrl
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
})

const app = createApp(App)
app.use(createPinia())
app.mount('#app')
