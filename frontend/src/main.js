import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { router } from './router'
import { registerEventHandler } from './composables/useIntentRegistry.js'
import { setTokenProvider, setUnauthorizedHandler } from './services/httpClient.js'
import { setAuthFetchTokenProvider, setAuthFetchUnauthorizedHandler } from './services/authFetch.js'
import ProductOpsPanel from './components/intent-panels/ProductOpsPanel.vue'

// Vue Flow 样式
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'

// 设计系统 - Design System
import './styles/variables.css'  // 设计令牌
import './styles/global.css'     // 全局样式
import './styles/workbench.css'  // 工作台通用样式（导航图标/还原条等）

import App from './App.vue'

const app = createApp(App)
const pinia = createPinia()

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

registerEventHandler('product_ops_query', (data, msg) => {
  msg.intentType = 'product_ops_query'
  msg.action = data.action || 'query'
}, { panel: ProductOpsPanel })

registerEventHandler('product_ops_policy', (data, msg) => {
  msg.intentType = 'product_ops_policy'
  msg.action = data.action || 'policy'
  msg.stats = { ...(msg.stats || {}), ...data.stats }
}, { panel: ProductOpsPanel })

registerEventHandler('product_ops_reason', (data, msg) => {
  msg.intentType = 'product_ops_reason'
  msg.action = data.action || 'reason'
}, { panel: ProductOpsPanel })

app.use(pinia)
app.use(router)
app.use(ElementPlus)

// 认证接入：httpClient/authFetch 请求附带 token；401 时清理本地登录态并刷新到登录页
import { useUserStore } from './stores/user.js'
const userStore = useUserStore(pinia)
const _tokenProvider = () => userStore.getToken()
const _unauthorizedHandler = () => {
  userStore.logout()
  if (router.currentRoute.value?.name !== 'login') {
    router.push({ name: 'login' }).catch(() => window.location.reload())
  }
}
setTokenProvider(_tokenProvider)
setUnauthorizedHandler(_unauthorizedHandler)
setAuthFetchTokenProvider(_tokenProvider)
setAuthFetchUnauthorizedHandler(_unauthorizedHandler)

app.mount('#app')
