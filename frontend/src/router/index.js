import { createRouter, createWebHistory } from 'vue-router'
import RdAssistantPage from '../components/RdAssistantPage.vue'
import OpsAssistantPage from '../components/OpsAssistantPage.vue'
import InferencePlatformManager from '../components/InferencePlatformManager.vue'

const routes = [
  { path: '/', redirect: '/rd' },
  { path: '/rd', name: 'rd', component: RdAssistantPage },
  { path: '/ops', name: 'ops', component: OpsAssistantPage },
  { path: '/model-config', name: 'model-config', component: InferencePlatformManager },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})
