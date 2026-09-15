import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { applyStoredPrefs } from './stores/prefs'
import './styles.css'

// 先落主题与语言，再挂载应用。
// 顺序反过来（放进组件的 onMounted）会让浅色界面先画一帧再跳成深色，
// 对深色模式用户表现为「每次刷新闪一下白屏」。
applyStoredPrefs()

createApp(App).use(createPinia()).use(router).mount('#app')
