import { createApp } from 'vue'
import './styles/style.css'
import './style.css'
import App from './App.vue'
import { router } from './router'
import 'swiper/css'
import 'swiper/css/pagination'
import 'swiper/css/navigation'

const installChunkLoadRecovery = () => {
  const reloadGuardKey = 'deskit:chunk-reload-global'

  const recover = () => {
    const alreadyReloaded = sessionStorage.getItem(reloadGuardKey) === '1'
    if (alreadyReloaded) {
      sessionStorage.removeItem(reloadGuardKey)
      return
    }
    sessionStorage.setItem(reloadGuardKey, '1')
    const sep = window.location.href.includes('?') ? '&' : '?'
    window.location.replace(`${window.location.href}${sep}__chunk_reload=${Date.now()}`)
  }

  const isChunkError = (message: string): boolean =>
    message.includes('Failed to fetch dynamically imported module') ||
    message.includes('Importing a module script failed')

  window.addEventListener('error', (event) => {
    if (isChunkError(String(event.message ?? ''))) {
      recover()
    }
  })

  window.addEventListener('unhandledrejection', (event) => {
    const reason = event.reason as { message?: string } | undefined
    if (isChunkError(String(reason?.message ?? reason ?? ''))) {
      recover()
    }
  })
}

installChunkLoadRecovery()

const app = createApp(App)

app.use(router)
app.mount('#app')
