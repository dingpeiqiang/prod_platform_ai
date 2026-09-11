import { createRouter, createWebHistory } from 'vue-router'
import RdAssistantPage from '../components/RdAssistantPage.vue'
import InferencePlatformManager from '../components/InferencePlatformManager.vue'
import AdminCenterPage from '../components/AdminCenterPage.vue'
import LangChainEditor from '../components/workflow-editor/LangChainEditor.vue'
import ApiDocsPage from '../components/ApiDocsPage.vue'
import LoginScreen from '../components/LoginScreen.vue'
import { useUserStore } from '../stores/user.js'

// 统一入口：研发/运营/查询三场景合并至 /rd 单路由（原 /ops /query 已移除）
const routes = [
  { path: '/', redirect: '/rd' },
  { path: '/login', name: 'login', component: LoginScreen },
  { path: '/rd', name: 'rd', component: RdAssistantPage },
  { path: '/admin', name: 'admin', component: AdminCenterPage },
  { path: '/model-config', name: 'model-config', component: InferencePlatformManager },
  { path: '/workflow-editor', name: 'workflow-editor', component: LangChainEditor },
  { path: '/api-docs', name: 'api-docs', component: ApiDocsPage },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 全局认证守卫：未登录访问业务页 → 跳转登录页；已登录访问 /login → 回首页
router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.name === 'login') {
    return userStore.isLoggedIn ? { name: 'rd' } : true
  }
  return userStore.isLoggedIn ? true : { name: 'login', query: { redirect: to.fullPath } }
})
