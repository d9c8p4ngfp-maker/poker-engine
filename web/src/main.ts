import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './styles/tokens.css'
import App from './App.vue'
import router from './router'

// ngrok 免费版会在浏览器请求中插入拦截页，
// 加上这个头可以自动跳过，不影响本地开发。
const _origFetch = window.fetch
window.fetch = function (input: RequestInfo | URL, init?: RequestInit) {
  const headers = new Headers(init?.headers)
  if (!headers.has('ngrok-skip-browser-warning')) {
    headers.set('ngrok-skip-browser-warning', '1')
  }
  return _origFetch(input, { ...init, headers })
}

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
