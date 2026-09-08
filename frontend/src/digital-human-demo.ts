import { createApp } from 'vue'
import { createPinia } from 'pinia'
import DigitalHumanDemo from './DigitalHumanDemo.vue'
import './digital-human-demo.css'

createApp(DigitalHumanDemo).use(createPinia()).mount('#app')
