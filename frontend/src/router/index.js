import { createRouter, createWebHistory } from 'vue-router'
import SuperAssistantPage from '../components/SuperAssistantPage.vue'
import RdAssistantPage from '../components/RdAssistantPage.vue'
import OpsAssistantPage from '../components/OpsAssistantPage.vue'
import QueryAssistantPage from '../components/QueryAssistantPage.vue'
import InferencePlatformManager from '../components/InferencePlatformManager.vue'
import AdminCenterPage from '../components/AdminCenterPage.vue'
import LangChainEditor from '../components/workflow-editor/LangChainEditor.vue'
import LoginScreen from '../components/LoginScreen.vue'
import { useUserStore } from '../stores/user.js'

const routes = [
  { path: '/', redirect: '/assistant' },
  { path: '/login', name: 'login', component: LoginScreen },
  { path: '/assistant', name: 'assistant', component: SuperAssistantPage },
  { path: '/rd', name: 'rd', component: RdAssistantPage },
  { path: '/ops', name: 'ops', component: OpsAssistantPage },
  { path: '/query', name: 'query', component: QueryAssistantPage },
  { path: '/admin', name: 'admin', component: AdminCenterPage },
  { path: '/model-config', name: 'model-config', component: InferencePlatformManager },
  { path: '/workflow-editor', name: 'workflow-editor', component: LangChainEditor },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 全局认证守卫：未登录访问业务页 → 跳转登录页；已登录访问 /login → 回首页
router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.name === 'login') {
    return userStore.isLoggedIn ? { name: 'assistant' } : true
  }
  return userStore.isLoggedIn ? true : { name: 'login', query: { redirect: to.fullPath } }
})
